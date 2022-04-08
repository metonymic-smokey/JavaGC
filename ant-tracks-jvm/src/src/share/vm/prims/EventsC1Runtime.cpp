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
 * File:   EventsC1Runtime.cpp
 * Author: vmb
 * 
 * Created on December 11, 2013, 10:43 AM
 */

#include "precompiled.hpp"
#include "EventsC1Runtime.hpp"
#include "../runtime/thread.hpp"
#include "../runtime/interfaceSupport.hpp"

#define FAST_EVENT_SIZE (1 + (TraceObjectsInsertAnchors ? 1 : 0))
#define requires_slow_path(thread, obj) (TraceObjectsC1AlwaysSlowPath || EventBuffer_get_length(thread->get_event_buffer()) + FAST_EVENT_SIZE >= EventBuffer_get_capacity(thread->get_event_buffer()) || (obj->is_array() && arrayOop(obj)->length() >= ARRAY_LENGTH_MAX_SMALL))

#define EVENTS_C1_RUNTIME_ENTRY(return_type, signature) JRT_ENTRY(return_type, signature)
//#define EVENTS_C1_RUNTIME_ENTRY(return_type, signature) return_type signature {
#define EVENTS_C1_RUNTIME_END JRT_END

//The following method should use JRT_ENTRY but then a gc could happen because the JRT_END is a safepoint!
//If you change this in the future, be very careful that there is an oop map for each call (which is currently not the case because the stub frame
//code for this method is called directly without a stub in the method)
//For now we have to live with it that there is not a proper transition from in_Java to in_VM in this case. However, if this is a problem in the future,
//set -XX:+TraceObjectsC1RelocateSlowPath, although probably a tiny bit slower, this one implements a clean thread transition
void EventsC1Runtime::fire_obj_alloc_fast_with_event_naked(JavaThread* thread, oopDesc* obj, jlong event) {
    assert(obj->is_oop(false), "obj is not an object");
    assert(requires_slow_path(thread, obj), "why are we here?");
    EventsRuntime::fire_obj_alloc_fast(thread, obj, event);
    thread->set_vm_result(obj); // pass oop through because a gc might occur at JRT_END (currently there is no JRT_END but the caller does not know that and expects the oop in the TLS)
    assert(obj->is_oop(false), "obj has been destroyed");
}

EVENTS_C1_RUNTIME_ENTRY(void, EventsC1Runtime::fire_obj_alloc_fast_with_oop(JavaThread* thread, oopDesc* obj, AllocationSiteIdentifier allocation_site))
    fire_obj_alloc_fast_with_oop_naked(thread, obj, allocation_site);
EVENTS_C1_RUNTIME_END

void EventsC1Runtime::fire_obj_alloc_fast_with_oop_naked(JavaThread* thread, oopDesc* obj, AllocationSiteIdentifier allocation_site) {
    assert(obj->is_oop(false), "obj is not an object");
    assert(requires_slow_path(thread, obj), "why are we here?");
    EventsRuntime::fire_obj_alloc_fast(thread, obj, allocation_site, EVENTS_C1_ALLOC_FAST);
    thread->set_vm_result(obj); // pass oop through because a gc might occur at JRT_END
    assert(obj->is_oop(false), "obj has been destroyed");
}

EVENTS_C1_RUNTIME_ENTRY(void, EventsC1Runtime::fire_obj_alloc_normal_with_oop(JavaThread* thread, oopDesc* obj, AllocationSiteIdentifier allocation_site))
    fire_obj_alloc_normal_with_oop_naked(thread, obj, allocation_site);
EVENTS_C1_RUNTIME_END

void EventsC1Runtime::fire_obj_alloc_normal_with_oop_naked(JavaThread* thread, oopDesc* obj, AllocationSiteIdentifier allocation_site) {
    assert(obj->is_oop(false), "obj is not an object");
    EventsRuntime::fire_obj_alloc_normal(thread, obj, allocation_site, EVENTS_C1_ALLOC_NORMAL);
    thread->set_vm_result(obj); // pass oop through because a gc might occur at JRT_END
    assert(obj->is_oop(false), "obj has been destroyed");
}

void EventsC1Runtime::store_alloc_site_naked(JavaThread* thread, oopDesc* obj, AllocationSiteIdentifier allocation_site) {
    assert(obj->is_oop(false), "obj is not an object");
    AllocationSiteStorage::store(thread, obj, allocation_site);
    thread->set_vm_result(obj); // pass oop through because a gc might occur at JRT_END
    assert(obj->is_oop(false), "obj has been destroyed");
}

