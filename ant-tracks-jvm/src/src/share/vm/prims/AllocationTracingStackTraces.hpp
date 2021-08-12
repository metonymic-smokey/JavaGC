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
 * File:   StackTraceCallSiteIterator.hpp
 * Author: Philipp Lengauer
 *
 * Created on April 8, 2015, 8:44 AM
 */

#ifndef STACKTRACECALLSITEITERATOR_HPP
#define	STACKTRACECALLSITEITERATOR_HPP

#include "AllocationSites.hpp"

class AllocationSiteHotnessCounters : public AllStatic {
private:
    static Monitor* lock;
    static Arena* arena;
    static Dict* counters;
public:
    static void init();
    static void destroy();
    static jlong* get_counter(AllocationSiteIdentifier allocation_site);
};

class StackTraceCallSiteIterator : public CallSiteIterator {
private:
    CallSite* stack;
    int length;
    int index;
public:
    StackTraceCallSiteIterator(JavaThread* thread, int max_depth, int recursion_max_detection_depth);
    ~StackTraceCallSiteIterator();
    
    inline int count() { return length; }
    inline void reset() { index = 0; }
    inline bool has_next() { return index < length; }
    inline CallSite next() { return *(stack + index++); }
    
private:
    void fill(JavaThread* thread, int max_depth);
    
    void eliminate_recursions(int recursion_max_detection_depth);
    bool is_recursion(CallSite* callee, CallSite* caller, int recursion_depth);
    int remove(CallSite* begin, CallSite* end);
    CallSite* insert(CallSite* dest);
    
    inline bool equals(CallSite* call1, CallSite* call2) { return call1 == call2 || (call1->method == call2->method && call1->bytecode_index == call2->bytecode_index); }
};

#endif	/* STACKTRACECALLSITEITERATOR_HPP */

