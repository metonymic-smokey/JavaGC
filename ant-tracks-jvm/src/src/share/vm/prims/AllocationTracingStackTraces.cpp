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
 * File:   StackTraceCallSiteIterator.cpp
 * Author: Philipp Lengauer
 * 
 * Created on April 8, 2015, 8:44 AM
 */

#include "precompiled.hpp"
#include "AllocationTracingStackTraces.hpp"
#include "AllocationTracingSynchronization.hpp"

Monitor* AllocationSiteHotnessCounters::lock = NULL;
Arena* AllocationSiteHotnessCounters::arena = NULL;
Dict* AllocationSiteHotnessCounters::counters = NULL;

void AllocationSiteHotnessCounters::init() {
    lock = new Mutex(Mutex::native, "Allocation Site Hotness Counters Lock", true);
    arena = new (mtOther) Arena(mtOther);
    counters = new (arena) Dict(cmpkey, hashkey, arena);
}

void AllocationSiteHotnessCounters::destroy() {
    counters = NULL;
    delete arena; arena = NULL;
    delete lock; lock = NULL;
}

jlong* AllocationSiteHotnessCounters::get_counter(AllocationSiteIdentifier allocation_site) {
    synchronized(lock) {
        jlong* counter = (jlong*) (*counters)[(void*)(intptr_t) allocation_site];
        if(counter == NULL) {
            counter = (jlong*) arena->Amalloc(sizeof(jlong));
            *counter = 0;
            counters->Insert((void*)(intptr_t) allocation_site, (void*) counter);
        }
        return counter;
    }
    HERE_BE_DRAGONS(NULL);
}

StackTraceCallSiteIterator::StackTraceCallSiteIterator(JavaThread* thread, int max_depth, int recursion_max_detection_depth) : stack(NULL), length(0), index(0) {
    fill(thread, max_depth);
    eliminate_recursions(recursion_max_detection_depth < 0 ? length : recursion_max_detection_depth);
}

StackTraceCallSiteIterator::~StackTraceCallSiteIterator() {
    free(stack);
    stack = NULL;
    length = 0;
}

void StackTraceCallSiteIterator::fill(JavaThread* thread, int max_depth) {
    int capacity = 0;
    stack = NULL;
    length = 0;
    
    vframeStream stream = vframeStream(thread);
    while(!stream.at_end() && (max_depth == 0 || length < max_depth)) {
        if(length == capacity) {
            capacity = MAX2(25, capacity * 2);
            stack = (CallSite*) realloc(stack, sizeof(CallSite) * capacity);
        }
        CallSite* call = stack + length++;
        call->method = stream.method();
        call->bytecode_index = stream.bci();
        stream.next();
    }
}

void StackTraceCallSiteIterator::eliminate_recursions(int recursion_max_detection_depth) {
    for(int recursion_depth = 1; recursion_depth <= recursion_max_detection_depth; recursion_depth++) {
        for(CallSite* call = stack; call < stack + length; call++) {
            if(call->method == NULL) continue;

            int recursion_count = 0;
            while(is_recursion(call, call + recursion_depth + recursion_depth * recursion_count, recursion_depth)) recursion_count++;
            if(recursion_count == 0) continue;
            
            int removed = remove(call + recursion_depth, call + (recursion_depth + recursion_depth * recursion_count));
            assert(removed > 0, "just checking");
            assert(removed == recursion_count * recursion_depth, "just checking");
            CallSite* indicator = insert(call + recursion_depth);
            assert(stack <= indicator && indicator < stack + length, "just checking");
            indicator->method = NULL;
            indicator->bytecode_index = recursion_depth;
            call = indicator;
        }
    }
}

bool StackTraceCallSiteIterator::is_recursion(CallSite* callee, CallSite* caller, int recursion_depth) {
    assert(callee <= caller, "just checking");
    for(int i = 0; i < recursion_depth; i++) {
        if(callee + i >= stack + length || caller + i >= stack + length || !equals(callee + i, caller + i)) {
            return false;
        }
    }
    return true;
}

int StackTraceCallSiteIterator::remove(CallSite* begin, CallSite* end) {
    while(end < stack + length) {
        assert(stack <= begin && begin < stack + length, "just checking");
        assert(stack <= end && end <= stack + length, "just checking");
        assert(begin <= end, "just checking");
        *(begin++) = *(end++);
    }
    int removed = end - begin;
    length -= removed;
    return removed;
}

CallSite* StackTraceCallSiteIterator::insert(CallSite* dest) {
    assert(stack <= dest && dest <= stack + length, "just checking");
    for(CallSite* cursor = stack + length; cursor > dest; cursor--) {
        *cursor = *(cursor-1);
    }
    length++;
    return dest;
}


