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

#ifndef EVENTPOINTERLIST_HPP
#define	EVENTPOINTERLIST_HPP
    
#define MAX_CAPACITY 12
const jubyte END = 0;
const jubyte RELATIVE_PTR = 1;
const jubyte ABSOLUTE_PTR = 2;
const jubyte NULL_PTR = 3;

class EventPointerList : public CHeapObj<mtInternal> {
public:
private:
    jubyte capacity;
    uintptr_t* list;
    juint ptr_kinds;
    jubyte top;
    jubyte relative_ptrs;
    jubyte absolute_ptrs;
    jubyte null_ptrs;
public:
    EventPointerList();
    ~EventPointerList();
    void add(uintptr_t p, const jubyte kind);
    jubyte get_size();
    jubyte get_words();
    intptr_t get_next(jubyte i, jubyte** kind);
    juint get_kinds();
    void clear();
private:
    void resize();
};

#endif	/* EVENTPOINTERLIST_HPP */

