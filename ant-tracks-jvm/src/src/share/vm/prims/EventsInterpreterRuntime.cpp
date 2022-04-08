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
 * File:   EventsInterpreterRuntime.cpp
 * Author: vmb
 * 
 * Created on December 9, 2013, 10:22 AM
 */

#include "precompiled.hpp"
#include "EventsInterpreterRuntime.hpp"
#include "runtime/interfaceSupport.hpp"
#include "EventsRuntime.hpp"
#include "../oops/method.hpp"
#include "../runtime/thread.hpp"

#define raw_to_jint(raw) ((jint) ((intptr_t) raw))

void EventsInterpreterRuntime::fire_obj_alloc_no_arrays_naked(JavaThread* thread, oopDesc* obj, Method* method, intptr_t bcx) {
    assert(obj->is_oop(false), "obj is not an object");
    assert(thread == Thread::current(), "incorrect thread");
    SingleCallSiteIterator call_sites = SingleCallSiteIterator(method, method->validate_bci_from_bcx(bcx));
    AllocationSiteIdentifier allocation_site = AllocationSites::method_to_allocation_site(&call_sites, obj->klass());
    if(TraceObjectsSaveAllocationSites) {
        AllocationSiteStorage::store(thread, obj, allocation_site);
    }
    HeapWord* location = (HeapWord*) obj;
    if(thread->tlab().start() <= location && location < thread->tlab().top()){  // no tlab
        EventsRuntime::fire_obj_alloc_fast_no_arrays(thread, obj, allocation_site, EVENTS_IR_ALLOC_FAST);
    } else {
        EventsRuntime::fire_obj_alloc_normal_no_arrays(thread, obj, allocation_site, EVENTS_IR_ALLOC_NORMAL);
    }
    thread->set_vm_result(obj); //obj is only passed through because a gc could happen and we need to return the (possibly) updated pointer
    assert(obj->is_oop(false), "obj has been destroyed");
}

IRT_ENTRY(void, EventsInterpreterRuntime::fire_obj_alloc_no_arrays(JavaThread* thread, oopDesc* obj, Method* method, intptr_t bcx))
    fire_obj_alloc_no_arrays_naked(thread, obj, method, bcx);
IRT_END
