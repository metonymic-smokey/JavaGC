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
 * File:   EventPointerList.hpp
 * Author: Verena Bitto
 *
 * Created on February 13, 2015, 8:28 AM
 */

#ifndef EVENTPOINTERS_HPP
#define	EVENTPOINTERS_HPP

#include "EventPointerList.hpp"  
#include "EventsGCRuntime.hpp"


class EventPointers : public CHeapObj<mtInternal> {
public:
private:
    EventType event;
    EventPointerList* ptr_list;
    uintptr_t obj;
    uintptr_t from_addr;
    bool obj_is_inside_heap;
    SpaceType to_space;
    bool is_clean;
    intptr_t last_referent;
    intptr_t set_new_referent(intptr_t offset);
public:
    EventPointers();
    ~EventPointers();
    void set_meta(EventType event, uintptr_t obj, bool obj_is_inside_heap, uintptr_t from_addr = NO_ENCODED_FROM_ADDR, SpaceType to_space = NO_TO_SPACE, bool is_clean = false);
    void add(oop p);
    EventPointerList* get();
    void flush(bool last = true);
    void clear();
};

#endif	/* EVENTPOINTERLISTS_HPP */

