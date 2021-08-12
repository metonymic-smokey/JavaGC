/* 
 * Copyright (c) 2014, 2015, 2016, 2017 dynatrace and/or its affiliates. All rights reserved.
 * This file is part of the AntTracks extension for the Hotspot VM. 
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * 
 * You should have received a copy of the GNU General Public License
 * along with with this work.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * File:   AllocationTracingRotation.cpp
 * Author: Philipp Lengauer
 * 
 * Created on July 7, 2014, 2:00 PM
 */

#include "precompiled.hpp"
#include "EventSynchronization.hpp"
#include "gc_implementation/parallelScavenge/gcTaskManager.hpp"
#include "runtime/atomic.hpp"
#include "AllocationTracingSynchronization.hpp"
#include "EventBuffers.hpp"
#include "gc_implementation/shared/vmGCOperations.hpp"
#include "gc_implementation/parallelScavenge/psScavenge.hpp"
#include "AllocationTracing.hpp"
#include "gc_implementation/g1/vm_operations_g1.hpp"

EventSynchronizationType EventSynchronization::type = None;
bool EventSynchronization::forced = false;

class EventSynchronizationSyncGC: public VM_Operation {
    public:
        EventSynchronizationSyncGC() : VM_Operation() {}
        
        ~EventSynchronizationSyncGC() {
            doit_epilogue();
        }
        
        virtual VMOp_Type type() const { return VMOp_EventSynchronization; }
        
        virtual Mode evaluation_mode() const { return _safepoint; }
        
        virtual bool is_cheap_allocated() const { return false; }
        
        virtual bool doit_prologue(){
            Heap_lock->lock();
            return EventSynchronization::should_rotate();
        }
        
        virtual void doit_epilogue() {
            if(Heap_lock->is_locked() && Heap_lock->owner() == Thread::current()) {
                Heap_lock->unlock();
            }
        };
        
        virtual void doit() {
            assert(EventSynchronization::should_rotate(), "should have been handled by prologue");
            GCCauseSetter _(Universe::heap(), GCCause::_allocation_profiler);
            if(TraceObjectsMaxTraceSizeSynchronizeOnMinor) {
                if(UseParallelGC) {
                    PSScavenge::invoke();
                } else if(UseG1GC) {
                    VM_G1IncCollectionPause op(G1CollectedHeap::heap()->total_collections(),
                    0,     /* word_size */
                    false,  /* should_initiate_conc_mark */
                    G1CollectedHeap::heap()->g1_policy()->max_pause_time_ms(),
                    GCCause::_allocation_profiler);
                    op.doit();
                } else {
                    Universe::heap()->do_full_collection(false);
                }
            } else {
                Universe::heap()->do_full_collection(false);
            }
            guarantee(!EventSynchronization::should_rotate(), "A single GC causes the trace to rotate!");
        }
};

class EventSynchronizationThread: public Thread {
private:
    static EventSynchronizationThread* instance;
    Monitor* lock;
    bool interrupted;
    bool triggered;

    EventSynchronizationThread() : Thread(), lock(new Monitor(Mutex::native, "EventSynchronizationThread Lock", true)), interrupted(false), triggered(false) {
        if (os::create_thread(this, os::eventw_thread)) {
            assert(!DisableStartThread, "");
            os::set_priority(this, MaxPriority);
            os::start_thread(this);
        } else {
            guarantee(false, "Something going awfully wrong :-[");
        }
    }

public:
    static EventSynchronizationThread* get_instance() {
        critical_if(instance == NULL) {
            instance = new EventSynchronizationThread();
        }
        return instance;
    }

    void interrupt() { synchronized(lock) { interrupted = true; synchronized_notify_all(lock); } }

    void trigger() { synchronized(lock) { triggered = true; synchronized_notify_all(lock); } }

    virtual void run() {
        initialize_thread_local_storage();
        synchronized(lock) {
            while(!interrupted) {
                if(triggered) {
                    lock->unlock();
                    EventSynchronizationSyncGC* op = new EventSynchronizationSyncGC();
                    VMThread::execute(op);
                    delete op;
                    lock->lock_without_safepoint_check();
                    triggered = false;
                } else {
                    synchronized_wait(lock);
                }
            }
        }
    }
};

EventSynchronizationThread* EventSynchronizationThread::instance = NULL;

void EventSynchronization::force_rotate() {
    forced = true;
}

void EventSynchronization::check_trigger_rotate() {
    if(TraceObjectsTraceSizeMaxDeviation >= 0 && should_rotate((size_t) (TraceObjectsMaxTraceSize * TraceObjectsTraceSizeMaxDeviation))) {
        EventSynchronizationThread::get_instance()->trigger();
    }
}

bool EventSynchronization::should_rotate(size_t epsilon) {
    return forced || TraceObjectsMaxTraceSize > 0 && AllocationTracing::get_trace_writer()->size() >= (size_t) (TraceObjectsMaxTraceSize + epsilon) / AllocationTracing::get_trace_writer()->get_max_file_count();
}

void EventSynchronization::start_synchronization(EventSynchronizationType type) {
    assert(Thread::current()->is_VM_thread(), "may only be called by the VM thread at the start of a major GC");
    assert(SafepointSynchronize::is_at_safepoint(), "VM must be at a safepoint");
    //assert(Universe::heap()->is_gc_active(), "GC must be active to synchronize"); // for G1, the flag might not be set yet
    assert(!is_synchronizing(), "already synchronizing");
    assert(should_rotate(), "should not rotate");
    EventBuffersFlushAll::flush_all();
    EventBuffersFlushAll::wait_for_all_serialized();
#ifdef ASSERT
    EventsRuntime::meta_events_only = true;
#endif
    AllocationTracing::get_trace_writer()->reset();
    EventSynchronization::type = type;

    if(PrintTraceObjects || PrintTraceObjectsMajorEvents) {
        ResourceMark _(Thread::current());
        char* name =  AllocationTracing::get_trace_writer()->get_file_name();
        AllocationTracing_log("rotated to new file %s", name);
        free(name);
    }
}

void EventSynchronization::stop_synchronization() {
    assert(Thread::current()->is_VM_thread(), "may only be called by the VM thread at the start of a major GC");
    assert(SafepointSynchronize::is_at_safepoint(), "VM must be at a safepoint");
    //assert(Universe::heap()->is_gc_active(), "GC must be active to synchronize"); // for G1, the flag might not be set yet
    assert(is_synchronizing(), "not synchronizing");
    EventSynchronization::type = None;
    forced = false;
}

class ThreadDumpClosure : public ThreadClosure {
public:
    void do_thread(Thread* thread) {
        EventsRuntime::fire_thread_alive(thread);
    };
};

class EventSynchronizationFireThreadAliveEventsTask : public GCTask, public AbstractGangTask {
public:
    EventSynchronizationFireThreadAliveEventsTask() : GCTask(), AbstractGangTask("") {}
    
    void* operator new(size_t size) {
        return GCTask::operator new(size);
    }
    
    void operator delete(void* p) {
        return GCTask::operator delete(p);
    }
    
    void do_it(GCTaskManager* manager, uint which) {
        _do_it();
    }
    
    void work(uint worker_id) {
        _do_it();
    }
    
private:
    void _do_it() {
        assert(TraceObjectsThreadEvents, "who added this task?");
        ThreadDumpClosure closure;
        Threads::threads_do(&closure);
    }
};

class EventSynchronizationFireSyncEvents : public GCTask, public AbstractGangTask {
private:
    HeapWord* bottom;
    HeapWord* top;
    uint worker_id;
public:
    EventSynchronizationFireSyncEvents(HeapWord* bottom, HeapWord* top) : GCTask(), AbstractGangTask(""), bottom(bottom), top(top), worker_id(0) {}
    EventSynchronizationFireSyncEvents(HeapWord* bottom, HeapWord* top, uint worker_id) : GCTask(), AbstractGangTask(""), bottom(bottom), top(top), worker_id(worker_id + 1) {}
    
    void* operator new(size_t size) {
        return GCTask::operator new(size);
    }
    
    void operator delete(void* p) {
        return GCTask::operator delete(p);
    }
    
    void do_it(GCTaskManager* manager, uint which) {
        _do_it(which);
    }
    
    void work(uint worker_id) {
        _do_it(worker_id);
    }
    
private:
    void _do_it(uint worker_id) {
        if(this->worker_id > 0 && this->worker_id - 1 != worker_id) return;
        HeapWord* ptr = bottom;
        while(ptr < top) {
            oop obj = oop(ptr);
            assert(obj->is_oop(), "just checking");
            int size = obj->size();
            EventsRuntime::fire_sync_obj(obj, size, true); //TODO optimize (bulk event?, only one address?)
            ptr += size;
        }
    }
};

void EventSynchronization::enqueue_synchronization_tasks_for_parallel_old_gc(GCTaskQueue* queue, int workers) {
    assert(UseParallelGC, "just checking");
    if((type == OnMajor || type == OnMinor) && TraceObjectsThreadEvents) {
        queue->enqueue(new EventSynchronizationFireThreadAliveEventsTask());
    }
    if(type == OnMinor) {
        PSOldGen* old_gen = ParallelScavengeHeap::heap()->old_gen();
        ObjectStartArray* object_start_array = old_gen->start_array();
        MutableSpace* old_space = old_gen->object_space();
        HeapWord* bottom = old_space->bottom();
        HeapWord* top = old_space->top();
        size_t step_size = (top - bottom) / (workers * 3);
        if(step_size == 0) {
            queue->enqueue(new EventSynchronizationFireSyncEvents(bottom, top));
        } else {
            while(bottom < top) {
                HeapWord* chunk_bottom = bottom;
                HeapWord* chunk_top;
                size_t multiplier = 1;
                do {
                    chunk_top = chunk_bottom + multiplier++ * step_size;
                    if(chunk_top >= top) {
                        chunk_top = top;
                    } else {
                        chunk_top = object_start_array->object_start(chunk_top);
                    }
                } while(chunk_top == chunk_bottom);               
                queue->enqueue(new EventSynchronizationFireSyncEvents(chunk_bottom, chunk_top));
                bottom = chunk_top;
            }
        }            
    }
}

void EventSynchronization::enqueue_synchronization_tasks_for_g1_gc(FlexibleWorkGang* gang) {
    assert(UseG1GC, "just checking");
    if((type == OnMajor || type == OnMinor) && TraceObjectsThreadEvents) {
        gang->run_task(new EventSynchronizationFireThreadAliveEventsTask());
    }
    if(type == OnMinor) {
        G1CollectedHeap* heap = G1CollectedHeap::heap();
        for(uint index = 0; index < heap->num_regions(); index++) {
            HeapRegion* region = heap->region_at(index);
            if(!region->in_collection_set() && !region->continuesHumongous()) {
                gang->run_task(new EventSynchronizationFireSyncEvents(region->bottom(), region->top(), index % gang->active_workers()));
            }
        }
    }
}

class FireKeepAliveClosure : public ObjectClosure {
public:
    void do_object(oop obj) {
        EventsGCRuntime::fire_gc_keep_alive(obj);
    }
};

void EventSynchronization::do_synchronization_tasks_for_generation_n(int n) {
    if(type != None) {
        GenCollectedHeap* heap = GenCollectedHeap::heap();
        Generation* gen = heap->get_gen(n);
        FireKeepAliveClosure c = FireKeepAliveClosure();
        gen->object_iterate(&c);
    }
}

