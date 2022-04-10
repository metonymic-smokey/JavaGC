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
 * File:   EventsSharedRuntime.cpp
 * Author: Philipp Lengauer
 * 
 * Created on March 20, 2014, 4:31 PM
 */

#include "precompiled.hpp"
#include "EventsSharedRuntime.hpp"
#include "../runtime/thread.hpp"
#include "../runtime/interfaceSupport.hpp"

//#include "../runtime/interfaceSupport.hpp"

void EventsSharedRuntime::fire_filler_alloc(JavaThread* thread, oopDesc* obj) {
    AllocationSiteIdentifier allocation_site = ALLOCATION_SITE_IDENTIFIER_TLAB_FILLER;
    if(TraceObjectsSaveAllocationSites) {
        AllocationSiteStorage::store(thread, obj, allocation_site);
    }
    EventsRuntime::fire_obj_alloc_slow(thread, obj, allocation_site, EVENTS_ALLOC_SLOW);
    thread->set_vm_result(obj);
}

void EventsSharedRuntime::fire_tlab_alloc(JavaThread* thread) {
    EventsRuntime::fire_tlab_alloc(thread->tlab().start(), (thread->tlab().hard_end() - thread->tlab().start()) * HeapWordSize);
}

