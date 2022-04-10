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
 * File:   EventsRuntime.cpp
 * Author: Verena Bitto
 * 
 * Created on November 27, 2013, 9:22 AM
 * 
 * Used for allocation tracing
 * 
 */

#include "precompiled.hpp"
#include "AllocationTracing.hpp"
#include "EventsRuntime.hpp"
#include "../memory/heap.hpp"
#include "../../vm/gc_interface/collectedHeap.hpp"
#include "../../vm/gc_implementation/parallelScavenge/parallelScavengeHeap.hpp"
#include "../../vm/memory/genCollectedHeap.hpp"
#include "../oops/oop.inline.hpp"
#include "EventsC1Runtime.hpp"
#include "EventBuffer.hpp"
#include "EventBuffers.hpp"
#include "AllocationTracingSelfMonitoring.hpp"
#include "../runtime/objectMonitor.inline.hpp"
#include "AllocationSites.hpp"
#include "AllocatedTypes.hpp"
#include "EventPointers.hpp"
#include "AllocationTracingSynchronization.hpp"

#define mask(size) ( (1 << (size)) - 1 )

class StuffedWord : StackObj {
private:
    jint value;
#ifdef ASSERT
    int size;
#endif
    
public:
    inline StuffedWord(jint value) : value(value)
#ifdef ASSERT
    , size(0)
#endif
    {}
    
    inline StuffedWord add(jint v, int s) {
        assert((v & mask(s)) == v, "value overflow");
        value = (value << s) | v;
#ifdef ASSERT
        size += s;
#endif
        return *this;
    }
    
    inline jint get() {
        assert(size == sizeof(jint) * 8, "word stuffing over/underflow");
        return value;
    }
};

#define with ).add(
#define stuff StuffedWord(0 with
#define into_word ).get()

#define EVENT_TYPE_SIZE 6
#define EVENT_TYPE_GAP (8 - EVENT_TYPE_SIZE)
#define EVENT_TYPE_LOCATION (((int) sizeof(jint)) * 8 - EVENT_TYPE_SIZE)

#define event_field(event) (event), EVENT_TYPE_SIZE
#define event_gap 0, EVENT_TYPE_GAP
#define gap(size) 0, (size)

#define is_in_tlab(thread, obj) (thread->tlab().start() <= ((HeapWord*) obj) && ((HeapWord*) obj) < thread->tlab().top())
#define is_last_in_tlab(thread, obj) (((HeapWord*) obj) + obj->size() == thread->tlab().top())

#define fire_event(thread, event_type, size, top)                                                                         \
        for(jint* top = EventsRuntime::allocate_event(thread, (size) + (TraceObjectsInsertAnchors ? 1 : 0)); top != NULL; top = NULL) \
        _block_(FireEventBlockControl, EventsRuntime::write_anchor, event_type, &top, (size))

#define fire_event_sync(thread, event_type, size, top, level, gc_only)   \
        _block_(SyncBlockControl, thread, level, gc_only)                \
        fire_event(thread, event_type, size, top)

jlong EventsRuntime::wo_pointer = 0;

#ifdef ASSERT
    bool EventsRuntime::meta_events_only = false;
#endif

enum ThreadType {
    VM_THREAD = 0,
    GC_THREAD = 1,
    COMPILER_THREAD = 2,
    JAVA_THREAD = 3,
    UNKNOWN_THREAD = 4
};

class FireEventBlockControl : public BlockControl {
    private:
        void (*write)(jint* top, EventType event_type);
        EventType event_type;
        jint* anchor_pos;
#ifdef ASSERT
        jint** top_ptr;
#endif
    public:
        inline FireEventBlockControl(void (*write)(jint* top, EventType event_type), EventType event_type, jint** top_ptr, jint size) : write(write), event_type(event_type), anchor_pos((*top_ptr) + size) {
            assert((*top_ptr) != NULL, "no memory?");
            assert(size < MAX_EVENT_SIZE, "event bigger than max event size, redefine this constant");
#ifdef ASSERT
            this->top_ptr = top_ptr;
#endif
        }
        
        inline ~FireEventBlockControl() {
            assert((*top_ptr) == anchor_pos, "top is not pointing to the expected location (are you missing a top++ somewhere?)");
            if(TraceObjectsInsertAnchors) {
                write(anchor_pos, event_type);
            }
            self_monitoring(2) AllocationTracingSelfMonitoring::report_event_fired(Thread::current(), event_type);
        }
};

class SyncBlockControl : public BlockControl {
private:
    Thread* thread;
    EventBufferSyncLevel level;
    bool _gc_only;
public:
    inline SyncBlockControl(Thread* thread, EventBufferSyncLevel level, bool gc_only) : thread(thread), level(level), _gc_only(gc_only) {
        if (_gc_only) {
            {
              EventBuffersFlushAll::flush_gc_threads();
            }
        } else if (level == Sync_Full) {
            EventBuffersFlushAll::flush_all();
        }
    }
    
    inline ~SyncBlockControl() {
        assert(thread != NULL, "just checking");
        thread->get_event_buffer()->sync = MAX2(thread->get_event_buffer()->sync, level);
        thread->flush_event_buffer();
    }
};

HeapWord* EventsRuntime::rel_addr_base = NULL;

jint EventsRuntime::create_obj_alloc_fast_prototype(AllocationSiteIdentifier allocation_site, EventType event_type){
    assert(is_allocation_fast_event(event_type), "expected fast allocation");
    assert(AllocationSites::is_consistent(allocation_site), "allocation site is not consistent");
    
    if(is_big_allocation_site(allocation_site)){
        return stuff event_field(event_type)
            with event_gap
            with allocation_site, 3*8
        into_word;
    } else {
        return stuff event_field(event_type)
            with event_gap
            with allocation_site, 2*8
            with gap(8)
        into_word;
    }
}

void EventsRuntime::fire_obj_alloc_fast_no_arrays(JavaThread* thread, oop obj, AllocationSiteIdentifier allocation_site, EventType event_type){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    assert(is_allocation_fast_event(event_type), "expected fast allocation");
    assert(!obj->klass()->oop_is_array(), "object should not be an array");
    assert(AllocationSites::is_consistent(allocation_site), "allocation site is not consistent");
    assert(is_big_allocation_site(allocation_site) ? !obj->is_array() : true, "invalid allocation site"); //big allocSites only (no array)!
    
    fire_event(thread, event_type, 1, local_buffer_top) {
        write_obj_alloc_event(local_buffer_top++, allocation_site, event_type, 0);
    }
    self_monitoring(2) AllocationTracingSelfMonitoring::report_instance_allocation(thread, obj->size(), AllocationSites::get_depth(allocation_site));
}

void EventsRuntime::fire_obj_alloc_normal_no_arrays(JavaThread* thread, oop obj, AllocationSiteIdentifier allocation_site, EventType event_type){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    assert(is_allocation_normal_event(event_type), "expected normal allocation");
    assert(!obj->is_array(), "object should not be an array");
    assert(!is_in_tlab(thread, obj), "object should not be in the TLAB of the current thread, should this be a fast event?");
    assert(AllocationSites::is_consistent(allocation_site), "allocation site is not consistent");
    assert(is_big_allocation_site(allocation_site) ? !obj->is_array() : true, "invalid allocation site"); //big allocSites only (no array)!

    fire_event(thread, event_type, 3, local_buffer_top) {
        write_obj_alloc_event(local_buffer_top++, allocation_site, event_type, 0);
        write_abs_obj_addr(local_buffer_top++, obj); local_buffer_top++;
    }
    self_monitoring(2) AllocationTracingSelfMonitoring::report_instance_allocation(thread, obj->size(), AllocationSites::get_depth(allocation_site));
}

void EventsRuntime::fire_obj_alloc_fast(JavaThread* thread, oop obj, jlong event){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    jint event_alloc = event >> 32;
    bool isBigAllocSite = ((event_alloc >> 23) & 1) != 0;
    jint array_length = isBigAllocSite ? 0 : event_alloc & 0x000000FF;
    
    assert(is_allocation_fast_event(event_alloc >> EVENT_TYPE_LOCATION), "expected fast allocation");

    if(array_length == ARRAY_LENGTH_MAX_SMALL){
        fire_event(thread, event_alloc >> EVENT_TYPE_LOCATION, 2, local_buffer_top) {
            write_word(local_buffer_top++, event_alloc);
            write_length(local_buffer_top++, (event & 0xFFFFFFFF));
        }
        self_monitoring(2) AllocationTracingSelfMonitoring::report_big_array_allocation(thread, obj->size(), event & 0xFFFFFFFF, AllocationSites::get_depth((AllocationSiteIdentifier)((event_alloc >> 8) & 0xFFFF)));
    } else {
        fire_event(thread, event_alloc >> EVENT_TYPE_LOCATION, 1, local_buffer_top) {
            write_word(local_buffer_top++, event_alloc);
        }
        self_monitoring(2) {
            AllocationSiteIdentifier allocSite = (AllocationSiteIdentifier) (isBigAllocSite ? event_alloc & 0xFFFFFF : (event_alloc >> 8) & 0xFFFF);
            size_t depth = AllocationSites::get_depth(allocSite);
            if(array_length > 0) {
                AllocationTracingSelfMonitoring::report_small_array_allocation(thread, obj->size(), array_length, depth);
            } else {
                AllocationTracingSelfMonitoring::report_instance_allocation(thread, obj->size(), depth);
            }
        }
    }
}

void EventsRuntime::fire_obj_alloc_fast(JavaThread* thread, oop obj, AllocationSiteIdentifier allocation_site, EventType event_type) {
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    assert(is_allocation_fast_event(event_type), "expected fast allocation");
    assert(is_in_tlab(thread, obj), "object not allocated in the TLAB of the current thread, therefore this should not be a fast event");
    assert(is_last_in_tlab(thread, obj), "object is not the last in the TLAB, either we are missing an event or they are out of order");
    assert(AllocationSites::is_consistent(allocation_site), "allocation site is not consistent");
    assert(is_big_allocation_site(allocation_site) ? !obj->is_array() : true, "Invalid allocation site");
    
    if(obj->is_array()) {
        assert(is_small_allocation_site(allocation_site), "Invalid allocation site");
        
        arrayOop array_obj = (arrayOop) obj;
        jint array_length = array_obj->length();
    
        if(array_length >= ARRAY_LENGTH_MAX_SMALL) {
            fire_event(thread, event_type, 2, local_buffer_top) {
                write_obj_alloc_event(local_buffer_top++, allocation_site, event_type, ARRAY_LENGTH_MAX_SMALL);
                write_length(local_buffer_top++, array_length);
            }
            self_monitoring(2) AllocationTracingSelfMonitoring::report_big_array_allocation(thread, obj->size(), array_length, AllocationSites::get_depth(allocation_site));
        } else {
            fire_event(thread, event_type, 1, local_buffer_top) {
                write_obj_alloc_event(local_buffer_top++, allocation_site, event_type, array_length);
            }
            self_monitoring(2) AllocationTracingSelfMonitoring::report_small_array_allocation(thread, obj->size(), array_length, AllocationSites::get_depth(allocation_site));
        }
    } else {
        fire_event(thread, event_type, 1, local_buffer_top) {
            write_obj_alloc_event(local_buffer_top++, allocation_site, event_type, 0);
        }
        self_monitoring(2) AllocationTracingSelfMonitoring::report_instance_allocation(thread, obj->size(), AllocationSites::get_depth(allocation_site));
    }
}

void EventsRuntime::fire_obj_alloc_normal(JavaThread* thread, oop obj, AllocationSiteIdentifier allocation_site, EventType event_type) {
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    assert(is_allocation_normal_event(event_type), "expected normal allocation");
    assert(!is_in_tlab(thread, obj), "object allocated in the TLAB of the current thread, therefore this should not be a normal event");
    assert(AllocationSites::is_consistent(allocation_site), "allocation site is not consistent");
    assert(is_big_allocation_site(allocation_site) ? !obj->is_array() : true, "Invalid allocation site");

    if(obj->is_array()) {
        assert(is_small_allocation_site(allocation_site), "Invalid allocation site");
        
        arrayOop array_obj = (arrayOop) obj;
        jint array_length = array_obj->length();

        if(array_length >= ARRAY_LENGTH_MAX_SMALL) {
            fire_event(thread, event_type, 4, local_buffer_top) {
                write_obj_alloc_event(local_buffer_top++, allocation_site, event_type, ARRAY_LENGTH_MAX_SMALL);
                write_abs_obj_addr(local_buffer_top++, obj); local_buffer_top++;
                write_length(local_buffer_top++, array_length);
            }
            self_monitoring(2) AllocationTracingSelfMonitoring::report_big_array_allocation(thread, obj->size(), array_length, AllocationSites::get_depth(allocation_site));
        } else {
            fire_event(thread, event_type, 3, local_buffer_top) {
                write_obj_alloc_event(local_buffer_top++, allocation_site, event_type, array_length);
                write_abs_obj_addr(local_buffer_top++, obj); local_buffer_top++;
            }
            self_monitoring(2) AllocationTracingSelfMonitoring::report_small_array_allocation(thread, obj->size(), array_length, AllocationSites::get_depth(allocation_site));
        }
    } else {
        fire_event(thread, event_type, 3, local_buffer_top) {
            write_obj_alloc_event(local_buffer_top++, allocation_site, event_type, 0);
            write_abs_obj_addr(local_buffer_top++, obj); local_buffer_top++;
        }
        self_monitoring(2) AllocationTracingSelfMonitoring::report_instance_allocation(thread, obj->size(), AllocationSites::get_depth(allocation_site));
    }
}

void EventsRuntime::fire_obj_alloc_slow(Thread* thread, oop obj, AllocationSiteIdentifier allocation_site, EventType event_type, size_t size) {
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    assert(is_allocation_slow_event(event_type), "expected slow allocation");
    assert(allocation_site != ALLOCATION_SITE_IDENTIFIER_UNKNOWN, "cannot fire event with unknown allocation site");
    assert(is_special_allocation_site(allocation_site) == (event_type == EVENTS_ALLOC_SLOW), "inconsistent slow allocation event");
    assert(AllocationSites::is_consistent(allocation_site), "allocation site is not consistent");
    assert(is_big_allocation_site(allocation_site) ? !obj->is_array() : true, "Invalid allocation site");
    
    int additional_words = 0;
    
    bool write_type_id = false;
    if(is_special_allocation_site(allocation_site) || is_allocation_slow_with_deviant_type_event(event_type)) {
        additional_words++;
        write_type_id = true;
    }
    
    #define write_type_id if(write_type_id) write_type(local_buffer_top++, AllocatedTypes::get_allocated_type_id(obj->klass()));  

    bool write_oop_size = false;
    if(obj->klass()->oop_is_instanceMirror()) {
        additional_words++;
        write_oop_size = true;
        assert(size > 0, "No size given");
        assert((size & 0xFFFFFFFF00000000) == 0, "obj size too big");
    } else {
        size = obj->size();
    }
    #define write_oop_size if(write_oop_size) write_word(local_buffer_top++, (jint) (size * HeapWordSize));
    
    if(obj->is_array()){
        assert(is_small_allocation_site(allocation_site), "Invalid allocation site");
        
        arrayOop array_obj = (arrayOop) obj;
        jint array_length = array_obj->length();
        if(array_length >= ARRAY_LENGTH_MAX_SMALL){
            fire_event(thread, event_type, 4 + additional_words, local_buffer_top) {
                write_obj_alloc_event(local_buffer_top++, allocation_site, event_type, ARRAY_LENGTH_MAX_SMALL);
                write_abs_obj_addr(local_buffer_top++, obj); local_buffer_top++;
                write_length(local_buffer_top++, array_length);
                write_type_id
                write_oop_size
            }
            self_monitoring(2) AllocationTracingSelfMonitoring::report_big_array_allocation(thread, size, array_length, AllocationSites::get_depth(allocation_site));
        } else {
            fire_event(thread, event_type, 3 + additional_words, local_buffer_top) {
                write_obj_alloc_event(local_buffer_top++, allocation_site, event_type, array_length);
                write_abs_obj_addr(local_buffer_top++, obj); local_buffer_top++;
                write_type_id
                write_oop_size
            }
            self_monitoring(2) AllocationTracingSelfMonitoring::report_small_array_allocation(thread, size, array_length, AllocationSites::get_depth(allocation_site));
        }
    } else {
        assert(!obj->is_instanceMirror(), "allocations of instance mirrors should be handled seperatly");
        fire_event(thread, event_type, 3 + additional_words, local_buffer_top) {
            write_obj_alloc_event(local_buffer_top++, allocation_site, event_type, 0);
            write_abs_obj_addr(local_buffer_top++, obj); local_buffer_top++;
            write_type_id
            write_oop_size
        }
        self_monitoring(2) AllocationTracingSelfMonitoring::report_instance_allocation(thread, size, AllocationSites::get_depth(allocation_site));
    }
    
    #undef write_oop_size
    #undef write_type_id
}

void EventsRuntime::fire_mirror_obj_alloc(Thread* thread, oop obj, size_t size, Klass* class_klass, Klass* mirrored_klass) {
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    assert(size > 0, "No size given");
    assert((size & 0xFFFFFFFF00000000) == 0, "obj size too big");
    
    fire_event(thread, EVENTS_ALLOC_SLOW, 5, local_buffer_top) {
        write_obj_alloc_event(local_buffer_top++, ALLOCATION_SITE_IDENTIFIER_VM_INTERNAL, EVENTS_ALLOC_SLOW, 0);
        write_abs_obj_addr(local_buffer_top++, obj); local_buffer_top++;
        write_type(local_buffer_top++, mirrored_klass != NULL ? -AllocatedTypes::get_allocated_type_id(mirrored_klass) : ALLOCATED_TYPE_IDENTIFIER_UNKNOWN_MIRROR);
        write_word(local_buffer_top++, (jint) (size * HeapWordSize));
    }
    self_monitoring(2) AllocationTracingSelfMonitoring::report_instance_allocation(thread, size, AllocationSites::get_depth(ALLOCATION_SITE_IDENTIFIER_VM_INTERNAL));
}

void EventsRuntime::fire_tlab_alloc(HeapWord* addr, size_t size){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    assert(Thread::current()->is_Java_thread(), "Expected java thread at tlab alloc");
    JavaThread* thread = JavaThread::current();
    fire_event(thread, EVENTS_TLAB_ALLOC, 5, local_buffer_top) {
        write_lab_alloc_event(local_buffer_top++, EVENTS_TLAB_ALLOC);
        write_size(local_buffer_top++, size); local_buffer_top++;
        write_abs_addr(local_buffer_top++, (HeapWord*) addr); local_buffer_top++;
    }
}

// Parameter gc_only only has an effect if event_type is EVENTS_GC_END!
void EventsRuntime::fire_gc_start_end(EventType event_type, jint id, GCType gc_type, GCCause::Cause gc_cause, bool concurrent, bool failed, bool gc_only){
    if(PrintTraceObjects || PrintTraceObjectsMajorEvents) AllocationTracing_log("%s %s GC (%d) (%s)%s",
            event_type == EVENTS_GC_START ? "start" : "end",
            gc_type == GCType_Major || gc_type == GCType_Major_Sync ? "major" : "minor",
            id, GCCause::to_string(gc_cause), failed ? " -> failed" : ""
        );
#ifdef ASSERT
    EventsRuntime::meta_events_only = false;
#endif
    Ticks ticks = Ticks::now();
    jlong millis = ticks.value() / (os::elapsed_frequency() / 1000L);
    
    fire_event_sync(Thread::current(), event_type, 6, local_buffer_top, Sync_Full, gc_only) {
        rel_addr_base = compute_rel_addr_base();
        write_gc_event(local_buffer_top++, event_type, gc_type, gc_cause, concurrent, failed);
        write_word(local_buffer_top++, id);
        write_time(local_buffer_top++, millis); local_buffer_top++;
        write_abs_addr(local_buffer_top++, rel_addr_base); local_buffer_top++;
    }
}

void EventsRuntime::fire_gc_info(uint collected_region, jint id) {
    if(PrintTraceObjects || PrintTraceObjectsMajorEvents) AllocationTracing_log("start minor GC (%d) in %d", id, (int) collected_region);
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    fire_event(Thread::current(), EVENTS_GC_INFO, 2, local_buffer_top) {
        write_word(local_buffer_top++, stuff event_field(EVENTS_GC_INFO) with event_gap with collected_region, 3*8 into_word);
        write_word(local_buffer_top++, id);
    }
}

void EventsRuntime::fire_gc_failed(uint collected_region) {
    if(PrintTraceObjects || PrintTraceObjectsMajorEvents) AllocationTracing_log("fail minor GC in %d", (int) collected_region);
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    fire_event(Thread::current(), EVENTS_GC_FAILED, 1, local_buffer_top) {
        write_word(local_buffer_top++, stuff event_field(EVENTS_GC_FAILED) with event_gap with collected_region, 3*8 into_word);
    }
}

void EventsRuntime::fire_gc_interrupt_continue(EventType event_type, jint gc_id, HeapWord* addr) {
    if(PrintTraceObjects || PrintTraceObjectsMajorEvents) AllocationTracing_log("%s GC %d at %p", event_type == EVENTS_GC_INTERRUPT ? "interrupt" : "continue", gc_id, addr);
    fire_event_sync(Thread::current(), event_type, 4, local_buffer_top, Sync_Full, true) {
        write_word(local_buffer_top++, stuff event_field(event_type) with event_gap with gap(3*8) into_word);
        write_word(local_buffer_top++, gc_id);
        write_abs_addr(local_buffer_top++, addr);
        local_buffer_top++;
    }
}


void EventsRuntime::fire_gc_move_slow(oop addr){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    const EventType event_type = EVENTS_GC_KEEP_ALIVE;
    Thread* thread = Thread::current();
    fire_event(thread, event_type, 3, local_buffer_top) {
        write_gc_move_event(local_buffer_top++, event_type, NO_REGION);
        write_abs_obj_addr(local_buffer_top++, addr); local_buffer_top++;
    }
}

void EventsRuntime::fire_gc_move_slow(oop from_addr, oop to_addr){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    const EventType event_type = EVENTS_GC_MOVE_SLOW;
    Thread* thread = Thread::current();
    
    fire_event(thread, event_type, 5, local_buffer_top) {
        write_gc_move_event(local_buffer_top++, event_type, NO_REGION);
        write_abs_obj_addr(local_buffer_top++, from_addr); local_buffer_top++;
        write_abs_obj_addr(local_buffer_top++, to_addr); local_buffer_top++;
    }
}

/*
 * is going to be merged with fire_gc_move_slow later on
 */
void EventsRuntime::fire_gc_move_slow_ptr(EventType event_type, HeapWord* from_addr, HeapWord* to_addr){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    if (event_type == EVENTS_GC_MOVE_SLOW) event_type = EVENTS_GC_MOVE_SLOW_PTR;
    else event_type = EVENTS_GC_KEEP_ALIVE_PTR;
   
    Thread* thread = Thread::current();
    EventPointerList* ptr_list = thread->get_event_obj_ptrs()->get();
    
    jint event_size = 3 + ptr_list->get_words();
    if(event_type == EVENTS_GC_MOVE_SLOW_PTR) event_size += 2;
    
    fire_event(thread, event_type, event_size, local_buffer_top) { 
            write_gc_ptr_event(local_buffer_top++, event_type, NO_TO_SPACE, ptr_list->get_kinds());
            if(event_type == EVENTS_GC_MOVE_SLOW_PTR) { 
                write_abs_addr(local_buffer_top++, from_addr); local_buffer_top++;
            }
            write_abs_addr(local_buffer_top++, to_addr); local_buffer_top++; 
    //        write_length(local_buffer_top++, ptr_list->get_size());
            fire_gc_ptr(&local_buffer_top, ptr_list);
    }
}

void EventsRuntime::fire_gc_move_region(oop from_addr, oop to_addr, jint no_of_objects){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    const EventType event_type = EVENTS_GC_MOVE_REGION;
    Thread* thread = Thread::current();
    
    fire_event(thread, event_type, 5, local_buffer_top) {
        write_gc_move_event(local_buffer_top++, event_type, no_of_objects);
        write_abs_obj_addr(local_buffer_top++, from_addr); local_buffer_top++;
        write_abs_obj_addr(local_buffer_top++, to_addr); local_buffer_top++;
    }
    
    self_monitoring(2) AllocationTracingSelfMonitoring::report_event_fired_GC_move_region(thread, no_of_objects);
}

void EventsRuntime::fire_gc_move_fast(oop from_addr, SpaceType to_space){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    Thread* thread = Thread::current();
    
    uint rel_from_addr = 0;
    if(get_rel_addr((HeapWord*) from_addr, &rel_from_addr, 32 - 8 - 2)) {
        const EventType event_type = EVENTS_GC_MOVE_FAST_NARROW;
        fire_event(thread, event_type, 1, local_buffer_top) {
            write_gc_move_fast_event(local_buffer_top++, event_type, (rel_from_addr << 2), to_space);
        }
    } else if(get_rel_addr((HeapWord*) from_addr, &rel_from_addr)) {
        const EventType event_type = EVENTS_GC_MOVE_FAST;
        fire_event(thread, event_type, 2, local_buffer_top) {
            write_gc_move_fast_event(local_buffer_top++, event_type, NO_ENCODED_FROM_ADDR, to_space);
            write_word(local_buffer_top++, rel_from_addr);
        }
    } else{
        const EventType event_type = EVENTS_GC_MOVE_FAST_WIDE;
        fire_event(thread, event_type, 3, local_buffer_top) {
            write_gc_move_fast_event(local_buffer_top++, event_type, NO_ENCODED_FROM_ADDR, to_space);
            write_abs_obj_addr(local_buffer_top++, from_addr); local_buffer_top++;
        }
    }
}

/*
 * is going to be merged with fire_gc_move_fast later on
 */

void EventsRuntime::fire_gc_move_fast_ptr(HeapWord* from_addr, HeapWord* to_addr, SpaceType to_space){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    Thread* thread = Thread::current();
    EventPointerList* ptr_list = thread->get_event_obj_ptrs()->get();
    
    uint rel_from_addr = 0;
    jint event_size = ptr_list->get_words();
    
    if(get_rel_addr(from_addr, &rel_from_addr)) {
       
        const EventType event_type = EVENTS_GC_MOVE_FAST_PTR;        
        event_size += 2;
        fire_event(thread, event_type, event_size, local_buffer_top) {  
            write_gc_ptr_event(local_buffer_top++, event_type, to_space, ptr_list->get_kinds());
            write_word(local_buffer_top++, rel_from_addr);
      //      write_length(local_buffer_top++, ptr_list->get_size());
            fire_gc_ptr(&local_buffer_top, ptr_list);
        }
    } else {
        const EventType event_type = EVENTS_GC_MOVE_FAST_WIDE_PTR;
        event_size += 3;
        fire_event(thread, event_type, event_size, local_buffer_top) { 
            write_gc_ptr_event(local_buffer_top++, event_type, to_space, ptr_list->get_kinds());
            write_abs_addr(local_buffer_top++, from_addr); local_buffer_top++;
   //         write_length(local_buffer_top++, ptr_list->get_size());
            fire_gc_ptr(&local_buffer_top, ptr_list);
        }
    }
}



void EventsRuntime::fire_gc_root_ptrs(EventRootPointerList* roots) {
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif    
    Thread* thread = Thread::current();
    
    fire_event(thread, EVENTS_GC_ROOT_PTR, 1 + roots->get_words() + (roots->is_full() ? 0 : 2), local_buffer_top) {
        write_gc_ptr_event(local_buffer_top++, EVENTS_GC_ROOT_PTR, NO_TO_SPACE, 0);
        for(int i = 0; i < roots->get_size(); i++) {
            RootInfo* info;
            intptr_t addr = roots->get_next(i, &info);
            
            write_wide_word(local_buffer_top, addr);
            local_buffer_top += 2;
            
            write_word(local_buffer_top++, info->type);

            int64_t first_info_dword;
            int64_t second_info_dword;
            int32_t last_word;
            int64_t* p_first_info_dword = &first_info_dword;
            int64_t* p_second_info_dword = &second_info_dword;
            int32_t* p_last_word = &last_word;
            switch(info->type) {
                case CLASS_LOADER_ROOT:
                    write_string(&local_buffer_top, info->string);   
                    break;
                
                case CLASS_ROOT:
                    info->get_class_info(p_last_word);
                    write_word(local_buffer_top++, last_word);
                    break;
                    
                case STATIC_FIELD_ROOT:
                    info->get_static_info(p_first_info_dword);
                    write_wide_word(local_buffer_top, first_info_dword);
                    local_buffer_top += 2;
                    break;
                    
                case LOCAL_VARIABLE_ROOT:
                    info->get_local_variable_info(p_first_info_dword, p_second_info_dword, p_last_word);
                    write_wide_word(local_buffer_top, first_info_dword);
                    local_buffer_top += 2;
                    write_wide_word(local_buffer_top, second_info_dword);
                    local_buffer_top += 2;
                    write_word(local_buffer_top++, last_word);
                    break;
                    
                case VM_INTERNAL_THREAD_DATA_ROOT:
                    info->get_vm_internal_thread_data_info(p_first_info_dword);
                    write_wide_word(local_buffer_top, first_info_dword);
                    local_buffer_top += 2;
                    break;
                    
                case CODE_BLOB_ROOT:
                    info->get_code_blob_info(p_first_info_dword);
                    write_wide_word(local_buffer_top, first_info_dword);
                    local_buffer_top += 2;
                    break;
                    
                case JNI_LOCAL_ROOT:
                    info->get_jni_local_info(p_first_info_dword);
                    write_wide_word(local_buffer_top, first_info_dword);
                    local_buffer_top += 2;
                    break;
                    
                case JNI_GLOBAL_ROOT:
                    info->get_jni_global_info(p_last_word);
                    write_word(local_buffer_top++, last_word);
                    break;
                
                // other roots
                default:
                    // no info to write
                    break;
                    
                case DEBUG_ROOT:
                    write_string(&local_buffer_top, info->string);   
                    break;
            }
        }

        if(!roots->is_full()) {
            // write additional -1 addr to terminate nonfull root block (note that -1 != NULLPTR, null is 0)
            write_wide_word(local_buffer_top, -1);
            local_buffer_top += 2;
        }
    }
}


void EventsRuntime::fire_gc_obj_ptr(EventType event_type, HeapWord* obj){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif    
    Thread* thread = Thread::current();
    EventPointerList* ptr_list = thread->get_event_obj_ptrs()->get();
    
    jint event_size = 3 + ptr_list->get_words();
    
    fire_event(thread, event_type, event_size, local_buffer_top) { 
        write_gc_ptr_event(local_buffer_top++, event_type, NO_TO_SPACE, ptr_list->get_kinds());
        write_abs_addr(local_buffer_top++, obj); local_buffer_top++;
    //    write_length(local_buffer_top++, ptr_list->get_size());
        fire_gc_ptr(&local_buffer_top, ptr_list);
    }
}


void EventsRuntime::fire_gc_ptr(jint** local_buffer_top, EventPointerList* ptr_list){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    jubyte i = 0;
    jubyte* kind = NULL;
    intptr_t ptr = 0;
    
    jubyte size = ptr_list->get_size();
    while(i < size){
        ptr = ptr_list->get_next(i++, &kind);
        if(*kind == NULL_PTR) { continue; }
        
        if(*kind == ABSOLUTE_PTR){
            write_abs_addr((*local_buffer_top)++, (HeapWord*) ptr); (*local_buffer_top)++;
        } else {
            write_word((*local_buffer_top)++, (jint) ptr);
        }
    }
}


void EventsRuntime::fire_plab_alloc(HeapWord* addr, size_t size){
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    Thread* thread = Thread::current();
    fire_event(thread, EVENTS_PLAB_ALLOC, 5, local_buffer_top) {
        write_lab_alloc_event(local_buffer_top++, EVENTS_PLAB_ALLOC);
        write_size(local_buffer_top++, size); local_buffer_top++;
        write_abs_addr(local_buffer_top++, (HeapWord*) addr); local_buffer_top++;
    }
}

void EventsRuntime::fire_sync_obj(oop from, oop obj, int size, bool is_mark_valid) {
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    //pass size because during a GC size can potentially not be calculated correctly
    bool is_array = obj->is_array(), is_instance_mirror = obj->is_instanceMirror();
    assert(!(is_array && is_instance_mirror), "?");
    Thread* thread = Thread::current();
    fire_event(thread, EVENTS_SYNC_OBJ, 6 + (is_array ? 1 : 0) + (is_instance_mirror ? 1 : 0), local_buffer_top) {
        AllocationSiteIdentifier allocsite = TraceObjectsSaveAllocationSites ? AllocationSiteStorage::load(thread, obj, is_mark_valid) : ALLOCATION_SITE_IDENTIFIER_UNKNOWN;
        if(is_big_allocation_site(allocsite)){
            write_word(local_buffer_top++, stuff event_field(EVENTS_SYNC_OBJ) with event_gap with (allocsite), 3*8 into_word);
        } else {
            write_word(local_buffer_top++, stuff event_field(EVENTS_SYNC_OBJ) with event_gap with (allocsite), 2*8 with gap(8) into_word);
        }
        write_word(local_buffer_top++, obj->klass()->get_allocated_type_identifier());
        write_abs_obj_addr(local_buffer_top++, from); local_buffer_top++;
        write_abs_obj_addr(local_buffer_top++, obj); local_buffer_top++;
        if(is_array) {
            write_word(local_buffer_top++, arrayOop(obj)->length());
        }
        if(is_instance_mirror) {
            write_word(local_buffer_top++, (size == 0 ? obj->size() : size) * HeapWordSize);
        }
    }
}

void EventsRuntime::fire_sync_obj(oop obj, int size, bool is_mark_valid) {
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    //pass size because during a GC size can potentially not be calculated correctly
    bool is_array = obj->is_array(), is_instance_mirror = obj->is_instanceMirror();
    assert(!(is_array && is_instance_mirror), "?");
    Thread* thread = Thread::current();
    fire_event(thread, EVENTS_SYNC_OBJ_NARROW, 4 + (is_array ? 1 : 0) + (is_instance_mirror ? 1 : 0), local_buffer_top) {
        AllocationSiteIdentifier allocsite = TraceObjectsSaveAllocationSites ? AllocationSiteStorage::load(thread, obj, is_mark_valid) : ALLOCATION_SITE_IDENTIFIER_UNKNOWN;
        if(is_big_allocation_site(allocsite)){
            write_word(local_buffer_top++, stuff event_field(EVENTS_SYNC_OBJ_NARROW) with event_gap with (allocsite), 3*8 into_word);
        } else {
            write_word(local_buffer_top++, stuff event_field(EVENTS_SYNC_OBJ_NARROW) with event_gap with (allocsite), 2*8 with gap(8) into_word);
        }
        write_word(local_buffer_top++, obj->klass()->get_allocated_type_identifier());
        write_abs_obj_addr(local_buffer_top++, obj); local_buffer_top++;
        if(is_array) {
            write_length(local_buffer_top++, arrayOop(obj)->length());
        }
        if(is_instance_mirror) {
            write_word(local_buffer_top++, (size == 0 ? obj->size() : size) * HeapWordSize);
        }
    }
}

void EventsRuntime::fire_sync(bool full) {
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    fire_event_sync(Thread::current(), EVENTS_NOP, 1, local_buffer_top, full ? Sync_Full : Sync_Ensure_Order, false) {
        write_word(local_buffer_top++, stuff event_field(EVENTS_NOP) with event_gap with gap(3*8) into_word);
    }
}

void EventsRuntime::fire_thread_alive(Thread* thread) {
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    ResourceMark rm(Thread::current());
    char* name = thread->name();
    jlong id;
    if(thread->is_Java_thread()) {
        id = java_lang_Thread::thread_id(((JavaThread*)(thread))->threadObj());
        // printf("Java thread #%ld - %s\n", id, thread->name());
    } else {
        id = -1;
        //printf("Non-java thread #%ld - %s\n", id, thread->name());
    } 
    int name_length = (int) MIN2(strlen(name) + 1, 12 * sizeof(jint));
    fire_event(Thread::current(), EVENTS_THREAD_ALIVE, 3 + (name_length / sizeof(jint)) + ((name_length % sizeof(jint) != 0) ? (1) : (0)), local_buffer_top) {
        write_word(local_buffer_top++, stuff event_field(EVENTS_THREAD_ALIVE) with event_gap with get_thread_type(thread), 2*8 with gap(8) into_word);
        write_wide_word(local_buffer_top, id);
        local_buffer_top += 2;
        jint word = 0;
        int filled = 0;
        for(int index = 0; index < name_length; index++) {
            if(filled == sizeof(jint)) {
                write_word(local_buffer_top++, word);
                word = 0;
                filled = 0;
            }
            word <<= 8;
            word |= name[index];
            filled++;
        }              
        if(filled > 0) {
            word <<= ((sizeof(jint) - filled) * 8);
            write_word(local_buffer_top++, word);
        }
    }
}

void EventsRuntime::fire_thread_death(Thread* thread) {
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    fire_event(Thread::current(), EVENTS_THREAD_DEATH, 3, local_buffer_top) {
        write_word(local_buffer_top++, stuff event_field(EVENTS_THREAD_DEATH) with event_gap with gap(3*8) into_word);
        if(thread->is_Java_thread()) {
            write_wide_word(local_buffer_top, (jlong)java_lang_Thread::thread_id(((JavaThread*)thread)->threadObj()));
        } else {
            write_wide_word(local_buffer_top, (jlong)thread->self_raw_id());
        }
        
        local_buffer_top += 2;
    }
}

void EventsRuntime::fire_mark(jlong id) {
#ifdef ASSERT
    assert(!EventsRuntime::meta_events_only, "only meta_events allowed at file start");
#endif
    fire_event(Thread::current(), EVENTS_MARK, 1 + 2, local_buffer_top) {
        write_word(local_buffer_top++, stuff event_field(EVENTS_MARK) with event_gap with gap(3*8) into_word);
        write_wide_word(local_buffer_top++, id); local_buffer_top++;
    }
}

void EventsRuntime::fire_space_creation(uint id, HeapWord* bottom, HeapWord* end) {
    if(PrintTraceObjects || PrintTraceObjectsMajorEvents) AllocationTracing_log("create space %d [%p, %p]", id, bottom, end);
    size_t size = (end - bottom) * HeapWordSize;
    fire_event_sync(Thread::current(), EVENTS_SPACE_CREATE, 6, local_buffer_top, Sync_Ensure_Order, false) {
        write_word(local_buffer_top++, stuff event_field(EVENTS_SPACE_CREATE) with event_gap with gap(3*8) into_word);
        write_word(local_buffer_top++, id);
        write_abs_addr(local_buffer_top++, (HeapWord*) bottom); local_buffer_top++;
        write_size(local_buffer_top++, size); local_buffer_top++;
    }
}

void EventsRuntime::fire_space_alloc(uint id, SpaceMode mode, SpaceType type) {
    if(PrintTraceObjects || PrintTraceObjectsMajorEvents) AllocationTracing_log("allocate space %d as %s %s", id, 
            mode == SPACE_MODE_NORMAL ? "normal" : mode == SPACE_MODE_HUMONGOUS_START ? "humongous start" : "humongous continued",
            type == EDEN_SPACE ? "eden" : type == SURVIVOR_SPACE ? "survivor" : "old"
        );
    // Could be done by not ensuring that the space is set before the first allocation in a region but only to assure that spaceType is set till GCStart
    fire_event_sync(Thread::current(), EVENTS_SPACE_ALLOC, 2, local_buffer_top, Sync_Ensure_Order, false) {
        write_word(local_buffer_top++, stuff event_field(EVENTS_SPACE_ALLOC) with type, 8 with mode, 8 with event_gap with gap(8) into_word);
        write_word(local_buffer_top++, id);
    }
}

void EventsRuntime::fire_space_release(uint id) {
    if(PrintTraceObjects || PrintTraceObjectsMajorEvents) AllocationTracing_log("release space %d", id);
    fire_event_sync(Thread::current(), EVENTS_SPACE_RELEASE, 2, local_buffer_top, Sync_Ensure_Order, false) {
        write_word(local_buffer_top++, stuff event_field(EVENTS_SPACE_RELEASE) with event_gap with gap(3*8) into_word);
        write_word(local_buffer_top++, id);
    }
}

void EventsRuntime::fire_space_redefine(uint id, HeapWord* bottom, HeapWord* end) {
    if(PrintTraceObjects || PrintTraceObjectsMajorEvents) AllocationTracing_log("redefine space %d [%p, %p]", id, bottom, end);
    size_t size = (end - bottom) * HeapWordSize;
    fire_event_sync(Thread::current(), EVENTS_SPACE_REDEFINE, 6, local_buffer_top, Sync_Ensure_Order, false) { //TODO this should be full for g1 pointers, but that breaks other stuff
        write_word(local_buffer_top++, stuff event_field(EVENTS_SPACE_REDEFINE) with event_gap with gap(3*8) into_word);
        write_word(local_buffer_top++, id);
        write_abs_addr(local_buffer_top++, (HeapWord*) bottom); local_buffer_top++;
        write_size(local_buffer_top++, size); local_buffer_top++;
    }
}

void EventsRuntime::fire_space_destroyed(uint startId, size_t numOfRegions) {
    if(PrintTraceObjects || PrintTraceObjectsMajorEvents) AllocationTracing_log("destroy space %d - %d", (int) startId, (int) (startId + numOfRegions));
    fire_event_sync(Thread::current(), EVENTS_SPACE_DESTROY, 4, local_buffer_top, Sync_Ensure_Order, false) { //TODO this should be full for g1 pointers, but that breaks other stuff
        write_word(local_buffer_top++, stuff event_field(EVENTS_SPACE_DESTROY) with event_gap with gap(3*8) into_word);
        write_word(local_buffer_top++, startId);
        write_size(local_buffer_top++, numOfRegions); local_buffer_top++;
    }
}

void EventsRuntime::fire_tag(char* tag) {
    int tagWordLength = ((int)((strlen(tag) % 4 == 0) ? strlen(tag)/4 : strlen(tag)/4 + 1));
    fire_event(Thread::current(), EVENTS_TAG, 1 + tagWordLength + 1, local_buffer_top) {
        write_word(local_buffer_top++, stuff event_field(EVENTS_TAG) with event_gap with gap(3*8) into_word);
        write_string(&local_buffer_top, tag);
    }
}

inline void EventsRuntime::write_obj_alloc_event(jint* local_buffer_top, AllocationSiteIdentifier allocation_site, EventType event_type, jint array_length){
    assert(is_allocation_event(event_type), "expected allocation event");
    assert(allocation_site != ALLOCATION_SITE_IDENTIFIER_UNKNOWN, "cannot fire event with unknown allocation site");
    assert(is_special_allocation_site(allocation_site) == (event_type == EVENTS_ALLOC_SLOW), "inconsistent slow allocation event");
    assert(array_length <= ARRAY_LENGTH_MAX_SMALL, "Array length bigger than expected"); 
    
    if(is_big_allocation_site(allocation_site)){ //must be an instance
        assert(array_length == 0, "Invalid allocation site");
        write_word(local_buffer_top, stuff event_field(event_type) with event_gap with allocation_site, 3*8 into_word);
    } else { //otherwise an array or allocated type was null (small allocSite))
        write_word(local_buffer_top, stuff event_field(event_type) with event_gap with allocation_site, 2*8 with array_length, 8 into_word);
    }
}

inline void EventsRuntime::write_lab_alloc_event(jint* local_buffer_top, EventType event_type){
    write_word(local_buffer_top, stuff event_field(event_type) with event_gap with gap(3*8) into_word); 
}

inline void EventsRuntime::write_gc_event(jint* local_buffer_top, EventType event_type, GCType gc_type, GCCause::Cause gc_cause, bool concurrent, bool failed){
    int gc_cause_id = (int) gc_cause;
    int flags = 0;
    flags = flags | ((failed ? 1 : 0) << 0);
    flags = flags | ((concurrent ? 1 : 0) << 1);
    write_word(local_buffer_top, stuff event_field(event_type) with event_gap with gc_type, 8 with gc_cause_id, 8 with flags, 8 into_word);
}

inline void EventsRuntime::write_gc_move_fast_event(jint* local_buffer_top, EventType event_type, jint from_addr, SpaceType space){
    write_word(local_buffer_top, stuff event_field(event_type) with space, 2 with from_addr, 3*8 into_word); 
}

inline void EventsRuntime::write_gc_ptr_event(jint* local_buffer_top, EventType event_type, SpaceType space, uint ptr_kinds){
    write_word(local_buffer_top, stuff event_field(event_type) with space, 2 with ptr_kinds, 3*8 into_word);     
}

inline void EventsRuntime::write_gc_move_event(jint* local_buffer_top, EventType event_type, jint no_of_objects){
    assert((no_of_objects & 0x00FFFFFF) == no_of_objects, "just checking");
    write_word(local_buffer_top, stuff event_field(event_type) with event_gap with no_of_objects, 3*8 into_word); 
}

inline void EventsRuntime::write_abs_obj_addr(jint* local_buffer_top, oop obj) {
    write_abs_addr(local_buffer_top, (HeapWord*) obj);
}

inline void EventsRuntime::write_abs_addr(jint* local_buffer_top, HeapWord* addr){
    assert(addr != NULL, "just checking");
    write_wide_word(local_buffer_top, (jlong) (intptr_t) addr);
}

inline void EventsRuntime::write_time(jint* local_buffer_top, jlong time) {
    write_wide_word(local_buffer_top, time);
}

inline void EventsRuntime::write_length(jint* local_buffer_top, jint length){
    write_word(local_buffer_top, length);
}

inline void EventsRuntime::write_type(jint* local_buffer_top, jint id) {
    write_word(local_buffer_top, id);
}

inline void EventsRuntime::write_size(jint* local_buffer_top, size_t size) {
    write_wide_word(local_buffer_top, (jlong) size);
}

inline void EventsRuntime::write_anchor(jint* local_buffer_top, EventType event_type) {
    write_word(local_buffer_top, (jint) get_debug_anchor(event_type)); //(jint) (EventBuffer_get_length(&thread->_local_buffer));
}

inline void EventsRuntime::write_wide_word(jint* local_buffer_top, jlong value) {
    jint low = (jint) (value >> 0);
    jint high = (jint) (value >> 32);
    write_word(local_buffer_top + 0, low);
    write_word(local_buffer_top + 1, high);
}

inline void EventsRuntime::write_word(jint* local_buffer_top, jint value) {
    assert(local_buffer_top != NULL, "buffer cursor invalid");
    assert(*local_buffer_top == EventBuffer_debug_uninitialized_content, "buffer has already been initialized at that address");
    *local_buffer_top = value;
}

inline void EventsRuntime::write_string(jint** local_buffer_top, char* string) {
    int length = (int)strlen(string);
    write_word((*local_buffer_top)++, length);
    jint word = 0;
    int filled = 0;
    for(int index = 0; index < length; index++) {
        if(filled == sizeof(jint)) {
            write_word((*local_buffer_top)++, word);
            word = 0;
            filled = 0; 
        }
        word <<= 8;
        word |= string[index];
        filled++;
    }              
    if(filled > 0) {
        word <<= ((sizeof(jint) - filled) * 8);
        write_word((*local_buffer_top)++, word);
    }
}

bool EventsRuntime::get_rel_addr(HeapWord* addr, uint* addr_ptr, jint bits){
    intptr_t rel_addr = ((intptr_t) addr) - ((intptr_t) rel_addr_base);
    rel_addr = rel_addr >> LogMinObjAlignmentInBytes;
    const jlong one = 1;
    jlong mask = (one << bits) - 1;
    *addr_ptr = (uint) rel_addr;
    return (rel_addr & ~mask) == 0;
}

jbyte EventsRuntime::get_thread_type(Thread* thread) {
    if(thread->is_VM_thread()) {
        return VM_THREAD;
    } else if (thread->is_GC_task_thread() || thread->is_ConcurrentGC_thread()) {
        return GC_THREAD;
    } else if (thread->is_Compiler_thread()) {
        return COMPILER_THREAD;
    } else if (thread->is_Java_thread()) {
        return JAVA_THREAD;
    } else {
        return UNKNOWN_THREAD;
    }
}

inline jint* EventsRuntime::allocate_event(Thread* thread, jint req_size){
    jint* local_buffer_top = EventBuffer_allocate(thread->get_event_buffer(), req_size);
    if(local_buffer_top == NULL) {
        thread->flush_event_buffer(true);
        local_buffer_top = EventBuffer_allocate(thread->get_event_buffer(), req_size);
#ifdef ASSERT
        EventBuffer_assert_is_valid(thread->get_event_buffer());
#endif
        assert(local_buffer_top != NULL, "Unable to allocate space in buffer");
    }
    return local_buffer_top;
}

bool EventsRuntime::is_allocation_event(EventType event_type) {
    return is_allocation_fast_event(event_type) || is_allocation_normal_event(event_type) || is_allocation_slow_event(event_type);
}

bool EventsRuntime::is_allocation_fast_event(EventType event_type) {
    switch(event_type) {
        case EVENTS_IR_ALLOC_FAST:
        case EVENTS_C1_ALLOC_FAST:
        case EVENTS_C2_ALLOC_FAST:
        case EVENTS_C2_ALLOC_FAST_DEVIANT_TYPE:
            return true;
        default:
            return false;
    }
}

bool EventsRuntime::is_allocation_normal_event(EventType event_type) {
    switch(event_type) {
        case EVENTS_IR_ALLOC_NORMAL:
        case EVENTS_C1_ALLOC_NORMAL:
        case EVENTS_C2_ALLOC_NORMAL:
            return true;
        default:
            return false;
    }
}

bool EventsRuntime::is_allocation_slow_event(EventType event_type) {
    switch(event_type) {
        case EVENTS_ALLOC_SLOW:
        case EVENTS_IR_ALLOC_SLOW:
        case EVENTS_C1_ALLOC_SLOW:
        case EVENTS_C2_ALLOC_SLOW:
        case EVENTS_IR_ALLOC_SLOW_DEVIANT_TYPE:
        case EVENTS_C1_ALLOC_SLOW_DEVIANT_TYPE:
        case EVENTS_C2_ALLOC_SLOW_DEVIANT_TYPE:
            return true;
        default:
            return false;
    }    
}

bool EventsRuntime::is_allocation_slow_with_deviant_type_event(EventType event_type) {
    switch(event_type) {
        case EVENTS_IR_ALLOC_SLOW_DEVIANT_TYPE:
        case EVENTS_C1_ALLOC_SLOW_DEVIANT_TYPE:
        case EVENTS_C2_ALLOC_SLOW_DEVIANT_TYPE:
            return true;
        default:
            return false;
    }    
}

HeapWord* EventsRuntime::compute_rel_addr_base() {
    if(UseParallelGC) {
        return ParallelScavengeHeap::heap()->young_gen()->eden_space()->bottom();
    } else if(UseG1GC) {
        G1CollectedHeap* heap = G1CollectedHeap::heap();
        HeapRegion* region = NULL;
        region = heap->g1_policy()->collection_set();
        if(region == NULL) region = heap->young_list()->first_region();
        if(region == NULL) region = heap->region_at(0);
        if(region == NULL) return NULL;
        return region->bottom();
    } else if (UseConcMarkSweepGC || UseParNewGC || UseSerialGC) {
        return (*GenCollectedHeap::heap()->get_gen(0)->top_addr());
    } else {
        assert(false, "here be dragons");
        return NULL;
    }
}

SlowAllocPatcher::SlowAllocPatcher() : thread(NULL) {}

SlowAllocPatcher::SlowAllocPatcher(Thread* thread, EventType event, Method* method, intptr_t bcx, Klass* klass) : thread(thread), prev_event_type(thread->get_patched_event_type()), prev_allocation_site(thread->get_patched_allocation_site()) {
    assert(TraceObjectsAllocations, "here be dragons");
    SingleCallSiteIterator call_sites = SingleCallSiteIterator(method, method->validate_bci_from_bcx(bcx));
    AllocationSiteIdentifier allocation_site = AllocationSites::method_to_allocation_site(&call_sites, klass);
    thread->set_patched_allocation_info(event, allocation_site);
}

SlowAllocPatcher::SlowAllocPatcher(Thread* thread, EventType event, AllocationSiteIdentifier allocation_site, Klass* klass, bool relaxed) : thread(thread), prev_event_type(thread->get_patched_event_type()), prev_allocation_site(thread->get_patched_allocation_site()) {
    assert(TraceObjectsAllocations, "here be dragons");
    bool special = allocation_site < ALLOCATION_SITE_IDENTIFIER_FIRST_CUSTOM;
    if(!special && relaxed) {
        allocation_site = AllocationSites::allocation_site_to_allocation_site_with_alternate_type(allocation_site, klass);
    }
    thread->set_patched_allocation_info(event, allocation_site);
    if(!special) {
        AllocationSites::check_allocation_site(allocation_site, klass);
    }
}

SlowAllocPatcher::SlowAllocPatcher(Thread* thread, EventType event, AllocationSiteIdentifier allocation_site) : thread(thread), prev_event_type(thread->get_patched_event_type()), prev_allocation_site(thread->get_patched_allocation_site()) {
    assert(TraceObjectsAllocations, "here be dragons");
    thread->set_patched_allocation_info(event, allocation_site);
}

SlowAllocPatcher::~SlowAllocPatcher() {
    assert(TraceObjectsAllocations == (thread != NULL), "here be dragons");
    if(thread != NULL) {
        thread->set_patched_allocation_info(prev_event_type, prev_allocation_site);
    }
}

