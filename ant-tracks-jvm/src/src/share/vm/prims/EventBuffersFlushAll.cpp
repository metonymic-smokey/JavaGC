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
 * File:   EventBuffersFlushAll.cpp
 * Author: Philipp Lengauer
 * 
 * Created on February 18, 2014, 12:38 PM
 */

#include "precompiled.hpp"
#include "EventBuffersFlushAll.hpp"
#include "runtime/vm_operations.hpp"
#include "runtime/vmThread.hpp"
#include "EventBuffers.hpp"

class FlushBufferThreadClosure: public ThreadClosure {
private:
    Thread* _last;
    bool(*_condition)(Thread* t);
public:
    FlushBufferThreadClosure(Thread* last) : _last(last) { _condition = NULL; }
    FlushBufferThreadClosure(Thread* last, bool(*condition)(Thread* t)) {
        _last = last;
        _condition = condition;
    }
    
    ~FlushBufferThreadClosure() {
      if (_condition == NULL || _condition(_last)) {
        do_thread0(_last);
      }
    }
    
    void do_thread(Thread* thread) {
      if(thread != NULL && thread != _last
           && (_condition == NULL || _condition(thread))) {
        do_thread0(thread);
      }
    }
    
private:
    void do_thread0(Thread* thread) {
        //retire tlabs so that trace is consistent when shutting down
        // <= because end is not the real end but only the last position where a filler oop can be inserted (see tlab hard_end)
	//NOTE: do not do this because making it parsable associates the allocation with this (the VM) thread and not the thread owning the TLAB!!!
        if(thread->is_Java_thread() && thread->tlab().top() <= thread->tlab().end()) {
            thread->tlab().make_parsable(true);
        }
        thread->flush_event_buffer();
    }
};

class FlushAllEventBuffersVMOperation : public VM_Operation {
private:
    bool async;
public:
    FlushAllEventBuffersVMOperation(bool async) : async(async) {}
    
    VM_Operation::VMOp_Type type() const {
        return VMOp_FlushEventBuffers;
    }
    
    VM_Operation::Mode evaluation_mode() const {
        //safepoint is necessary because they write on the buffers without locking and flushing would interfere ...
        //so we keep the threads from allocating using a safepoint
        return async ? _async_safepoint : _safepoint;
    }
    
    bool is_cheap_allocated() const {
        return true;
    }
    
    void doit() {
        EventBuffersFlushAll::flush_all();
    }
};

void EventBuffersFlushAll::begin_flush_all() {
    VMThread::execute(new FlushAllEventBuffersVMOperation(true));
}

void EventBuffersFlushAll::wait_for_all_serialized() {
    EventBuffers::flush();
}

void EventBuffersFlushAll::flush_all_and_wait_for_all_serialized() {
    VMThread::execute(new FlushAllEventBuffersVMOperation(false));
    wait_for_all_serialized();
}

void EventBuffersFlushAll::flush_all() {
    assert(SafepointSynchronize::is_at_safepoint(), "must be");
    FlushBufferThreadClosure tc = FlushBufferThreadClosure(Thread::current());
    Threads::threads_do(&tc);
}

bool flush_gc_condition(Thread* t) {
    return t != NULL && (t->is_VM_thread() || t->is_GC_task_thread() || t->is_ConcurrentGC_thread());
}

void EventBuffersFlushAll::flush_gc_threads() {
    FlushBufferThreadClosure gctc = FlushBufferThreadClosure(Thread::current(), &flush_gc_condition);
    Universe::heap()->gc_threads_do(&gctc);
}

FlushAndSyncEventBufferOnLeave::FlushAndSyncEventBufferOnLeave(bool flush_at_beginning) {
    if(flush_at_beginning) {
        do_it();
    }
}

FlushAndSyncEventBufferOnLeave::~FlushAndSyncEventBufferOnLeave() { 
    do_it();
}

void FlushAndSyncEventBufferOnLeave::do_it() {
    if(TraceObjects) {
        EventsRuntime::fire_sync();
    }
}
