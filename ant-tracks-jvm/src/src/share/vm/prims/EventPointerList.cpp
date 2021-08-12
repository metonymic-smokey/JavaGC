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
#include "AllocationTracingSelfMonitoring.hpp"

EventPointerList::EventPointerList() {
    top = 0;
    relative_ptrs = 0;
    absolute_ptrs = 0;
    null_ptrs = 0;
    ptr_kinds = 0;
    capacity = 2;
    list = (uintptr_t*) calloc(sizeof(uintptr_t), capacity);
}

EventPointerList::~EventPointerList() {
    free(list); list = NULL;
    capacity = 0;
    top = 0;
}

intptr_t EventPointerList::get_next(jubyte i, jubyte** kind) {
    jubyte k = (ptr_kinds >> (MAX_CAPACITY-1-i) * 2) & 0x3;
    *kind = &k;
    return (intptr_t) list[i];
}
    
void EventPointerList::add(uintptr_t p, const jubyte kind){
    if(top == capacity){
        resize();
        assert(capacity <= MAX_CAPACITY, "EventPointerList getting too big for EventBuffer...");
    }
    assert(top < capacity, "resizing failed");
        
    ptr_kinds = (ptr_kinds << 2) | kind;
       
    if (kind == NULL_PTR) {                                                                                                                       
        null_ptrs++;
    } else if (kind == RELATIVE_PTR){
        list[top] = p;
        relative_ptrs++;
    } else if (kind == ABSOLUTE_PTR){ 
        assert(p != 0, "Not an absolute ptr");
        list[top] = p;
        absolute_ptrs++;
    } else { 
        assert(false, "What kind of ptr is this?");
    }
    top++;
}


void EventPointerList::resize() {
    jubyte new_capacity = capacity * 2 > MAX_CAPACITY ? MAX_CAPACITY : capacity * 2;
    if(new_capacity != capacity){
        list = (uintptr_t*) realloc(list, sizeof(uintptr_t) * new_capacity);
        capacity = new_capacity;
        assert(list != NULL, "resizing failed");
    }
    
}

jubyte EventPointerList::get_size() {
    return top;
}

jubyte EventPointerList::get_words() {
    return relative_ptrs + (2 * absolute_ptrs);
}

juint EventPointerList::get_kinds(){
    if(top != 0 && top != MAX_CAPACITY) { 
        ptr_kinds = ptr_kinds << ((MAX_CAPACITY - top) * 2);
    }
    return ptr_kinds;
}

void EventPointerList::clear() {
    assert((relative_ptrs + absolute_ptrs + null_ptrs) == top, "Messed up pointers");
    self_monitoring(2) AllocationTracingSelfMonitoring::report_ptrs(Thread::current(), relative_ptrs, absolute_ptrs, null_ptrs);
    top = 0;
    relative_ptrs = 0;
    absolute_ptrs = 0;
    null_ptrs = 0;
    ptr_kinds = 0;
}
