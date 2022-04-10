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
 * File:   EventsC2Runtime.cpp
 * Author: Philipp Lengauer
 * 
 * Created on August 22, 2014, 9:27 AM
 */

#include "precompiled.hpp"
#include "EventsC2Runtime.hpp"
#include "runtime/thread.hpp"
#include "EventsRuntime.hpp"
#include "../runtime/interfaceSupport.hpp"
#include "AllocationTracingStackTraces.hpp"
#include "AllocationTracingSelfMonitoring.hpp"

#define EVENTS_C2_RUNTIME_ENTRY(return_type, signature) JRT_ENTRY(return_type, signature)
//#define EVENTS_C2_RUNTIME_ENTRY(return_type, signature) return_type signature {
#define EVENTS_C2_RUNTIME_END JRT_END

#define FAST_EVENT_SIZE (1 + (TraceObjectsInsertAnchors ? 1 : 0))
#define requires_slow_path(thread, obj, additional_words) (TraceObjectsC2AlwaysSlowPath || EventBuffer_get_length(thread->get_event_buffer()) + (FAST_EVENT_SIZE + additional_words) >= EventBuffer_get_capacity(thread->get_event_buffer()) || (obj->is_array() && arrayOop(obj)->length() >= ARRAY_LENGTH_MAX_SMALL))

address EventsC2Runtime::fire_obj_alloc_fast_Java = NULL;
address EventsC2Runtime::fire_obj_alloc_fast_unknown_Java = NULL;

EVENTS_C2_RUNTIME_ENTRY(void, EventsC2Runtime::fire_obj_alloc_fast(oopDesc* obj, AllocationSiteIdentifier allocation_site, JavaThread* thread))
    assert(obj->is_oop(false), "obj is not an object");
    fire_obj_alloc_fast_naked(obj, allocation_site);
    //obj may be partially initialized (or not initialized at all), however, if we do a GC, this might be a problem. (currently handled by calling code)
    if(!TraceObjectsC2ZeroObjectsBeforeSlowPathCall) {
        zero_object(obj);
    }
    assert(obj->is_oop(false), "obj has been destroyed");
EVENTS_C2_RUNTIME_END

void EventsC2Runtime::fire_obj_alloc_fast_naked(oopDesc* obj, AllocationSiteIdentifier allocation_site) {
    assert(obj->is_oop(false), "obj is not an object");
    JavaThread* thread = JavaThread::current();
    assert(allocation_site == ALLOCATION_SITE_IDENTIFIER_UNKNOWN || requires_slow_path(thread, obj, 0), "why have we taken the slow path?");
    if(allocation_site == ALLOCATION_SITE_IDENTIFIER_UNKNOWN) {
        self_monitoring_measure_time(1, &thread->get_self_monitoring_data()->stack_walk_timer) {
            StackTraceCallSiteIterator call_sites = StackTraceCallSiteIterator(thread, TraceObjectsMaxStackTraceDepth, TraceObjectsStackTraceRecursionDetectionDepth);
            allocation_site = AllocationSites::method_to_allocation_site(&call_sites, obj->klass());
        }
    }
    EventsRuntime::fire_obj_alloc_fast(thread, obj, allocation_site, EVENTS_C2_ALLOC_FAST);
    thread->set_vm_result(obj);
    assert(obj->is_oop(false), "obj has been destroyed");
}

EVENTS_C2_RUNTIME_ENTRY(void, EventsC2Runtime::fire_obj_alloc_fast_unknown(oopDesc* obj, AllocationSiteIdentifier allocation_site, JavaThread* thread))
    assert(obj->is_oop(false), "obj is not an object");
    fire_obj_alloc_fast_unknown_naked(obj, allocation_site);
    //obj may be partially initialized (or not initialized at all), however, if we do a GC, this might be a problem. (currently handled by calling code)
    if(!TraceObjectsC2ZeroObjectsBeforeSlowPathCall) {
        zero_object(obj);
    }
    assert(obj->is_oop(false), "obj has been destroyed");
EVENTS_C2_RUNTIME_END

void EventsC2Runtime::fire_obj_alloc_fast_unknown_naked(oopDesc* obj, AllocationSiteIdentifier allocation_site) {
    assert(obj->is_oop(false), "obj is not an object");
    JavaThread* thread = JavaThread::current();    
    assert(!TraceObjectsC2FastDeviantTypeEvent || obj->klass()->get_allocated_type_identifier() == ALLOCATED_TYPE_IDENTIFIER_UNKNOWN || allocation_site == ALLOCATION_SITE_IDENTIFIER_UNKNOWN || requires_slow_path(thread, obj, 1), "why have we taken the slow path?");
    if(allocation_site == ALLOCATION_SITE_IDENTIFIER_UNKNOWN) {
        self_monitoring_measure_time(1, &thread->get_self_monitoring_data()->stack_walk_timer) {
            StackTraceCallSiteIterator call_sites = StackTraceCallSiteIterator(thread, TraceObjectsMaxStackTraceDepth, TraceObjectsStackTraceRecursionDetectionDepth);
            allocation_site = AllocationSites::method_to_allocation_site(&call_sites, obj->klass(), true);
        }
    } else {
        allocation_site = AllocationSites::allocation_site_to_allocation_site_with_alternate_type(allocation_site, obj->klass());   
    }
    EventsRuntime::fire_obj_alloc_fast(thread, obj, allocation_site, EVENTS_C2_ALLOC_FAST);
    thread->set_vm_result(obj);
    assert(obj->is_oop(false), "obj has been destroyed");
}

void EventsC2Runtime::store_allocation_site_naked(oopDesc* obj, AllocationSiteIdentifier allocation_site) {
    assert(obj->is_oop(false), "obj is not an object");
    JavaThread* thread = JavaThread::current();
    AllocationSiteStorage::store(thread, obj, allocation_site);
    thread->set_vm_result(obj);
    assert(obj->is_oop(false), "obj has been destroyed");
}

void EventsC2Runtime::zero_object(oopDesc* obj) {
    int header_size = obj->klass()->oop_is_array() ? (arrayOopDesc::base_offset_in_bytes(ArrayKlass::cast(obj->klass())->element_type())) : (instanceOopDesc::base_offset_in_bytes());
    int object_size = obj->size() * HeapWordSize;
    memset(((char*) obj) + header_size, 0, object_size - header_size);
}

