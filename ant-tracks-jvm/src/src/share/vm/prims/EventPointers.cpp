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
 * File:   EventPointerList.cpp
 * Author: Verena Bitto
 *
 * Created on February 13, 2015, 8:28 AM
 */

#include "precompiled.hpp"
#include "EventPointers.hpp"
#include "AllocationTracingSelfMonitoring.hpp"

EventPointers::EventPointers() {
    event = -1;
    obj = 0;
    obj_is_inside_heap = false;
    ptr_list = new EventPointerList();
    from_addr = NO_ENCODED_FROM_ADDR;
    to_space = NO_TO_SPACE;
    is_clean = false;
}

EventPointers::~EventPointers() {
   delete ptr_list; ptr_list = NULL;
}

void EventPointers::set_meta(EventType event, uintptr_t obj, bool obj_is_inside_heap, uintptr_t from_addr, SpaceType to_space, bool is_clean){
    assert(this != NULL, "just checking");
    assert(this->ptr_list != NULL, "just checking");
    assert(ptr_list->get_size() == 0, "Not all pointers have been flushed...");
    assert(this->event == -1, "Meta information has already been set");
    self_monitoring(2) { 
        AllocationTracingSelfMonitoring::report_ptr_referrer(Thread::current()); 
    }
    this->event = event;
    this->obj = obj;
    this->obj_is_inside_heap = obj_is_inside_heap;
    this->from_addr = from_addr;
    this->to_space = to_space;
    this->last_referent = 0;
    this->is_clean = is_clean;
}

// only non-forwarded pointers are stored!

void EventPointers::add(oop p){
    assert(event != -1, "Meta information has not been set");
    
    self_monitoring(2) AllocationTracingSelfMonitoring::report_dirty_ptr(Thread::current());
    if(ptr_list->get_size() == MAX_CAPACITY ){   
        flush(false);
    }
        
    HeapWord* ptr = (HeapWord*) p;
    if(p == NULL){
        ptr_list->add(0, NULL_PTR);
    } else if(obj_is_inside_heap){ 
        HeapWord* o = (HeapWord*) obj;
        intptr_t offset = (intptr_t) pointer_delta(o, ptr);
        offset = set_new_referent(offset);
            
        intptr_t cmp_offset = offset < 0 ? ~offset + 1 : offset;
        if((cmp_offset & CONST64(0xffffffff80000000)) == 0){ 
            if (!is_clean) ptr_list->add(offset, RELATIVE_PTR);
            else  {
                self_monitoring(2) AllocationTracingSelfMonitoring::report_clean_ptr(Thread::current(), 4);
            }
        } else {
            assert(ptr != NULL, "ptr is null");
            if (!is_clean) ptr_list->add((uintptr_t) ptr, ABSOLUTE_PTR);                
            else {
                self_monitoring(2) AllocationTracingSelfMonitoring::report_clean_ptr(Thread::current(), 8);
            }
        }
    } else { // referee is outside the heap
        assert(ptr != NULL, "ptr is null");
        ptr_list->add((uintptr_t) ptr, ABSOLUTE_PTR);           
    }
}

intptr_t EventPointers::set_new_referent(intptr_t offset){
    intptr_t offset_to_prev = (offset - last_referent);   
    last_referent = offset;
    
    return offset_to_prev;
}

EventPointerList* EventPointers::get() {
    return ptr_list;
}

void EventPointers::flush(bool last){
    if(event == EVENTS_GC_MOVE_FAST || event == EVENTS_GC_MOVE_SLOW || event == EVENTS_GC_KEEP_ALIVE){
        assert(obj_is_inside_heap, "GC move event which comprises a non-pointer");
        EventsGCRuntime::fire_gc_move_ptr(event, obj, from_addr, to_space);
        event = EVENTS_GC_PTR_EXTENSION; // fire move event only once
        from_addr = NO_ENCODED_FROM_ADDR;
        to_space = NO_TO_SPACE;
    } else {
        if(ptr_list->get_size() > 0){
            EventsGCRuntime::fire_gc_ptr(event, obj, obj_is_inside_heap);
        }
    }
    last_referent = 0;
    
    ptr_list->clear();
#ifdef ASSERT
    if(last) {
        event = -1;
    }
#endif
}

void EventPointers::clear() {
    ptr_list->clear();
}
