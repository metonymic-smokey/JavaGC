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
 * File:   SortedList.hpp
 * Author: Philipp Lengauer
 *
 * Created on October 1, 2014, 10:04 AM
 */

#ifndef SORTEDLIST_HPP
#define	SORTEDLIST_HPP

#include "utilities/globalDefinitions.hpp"
#include "memory/allocation.hpp"

#define SORTED_LIST_USE_GAPS

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> class SortedList : public ResourceObj {
private:
    Arena* arena;
    element_t* data;
    size_t size;
    size_t capacity;
#ifdef SORTED_LIST_USE_GAPS
    int gap_start;
    #define gap_length (capacity - size)
    #define gap_end (gap_start + gap_length)
#endif
public:
    SortedList(Arena* arena, size_t init_capacity = 0);
    ~SortedList();
    size_t get_capacity();
    size_t get_size();
    element_t get(int index);
    void foreach(void (*visit)(element_t element));
    bool contains(element_t obj);
    int floor(element_t obj);
    void clear();
    void add(element_t element);
private:
    int get_index(element_t element, bool* exact_match);
    void grow();
#ifdef SORTED_LIST_USE_GAPS
    void move_gap_to(int index);
#endif
#ifdef ASSERT
    bool is_sorted();
#endif
};

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> SortedList<element_t, compare, is_floor_of>::SortedList(Arena* arena, size_t init_capacity) : arena(arena) {
    data = init_capacity == 0 ? NULL : (element_t*) arena->Amalloc(sizeof(element_t) * init_capacity);
    size = 0;
    capacity = init_capacity;
#ifdef SORTED_LIST_USE_GAPS
    gap_start = 0;
#endif
}

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> SortedList<element_t, compare, is_floor_of>::~SortedList() {}

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> size_t SortedList<element_t, compare, is_floor_of>::get_capacity() {
    return capacity;
}

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> size_t SortedList<element_t, compare, is_floor_of>::get_size() {
    return size;
}

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> inline element_t SortedList<element_t, compare, is_floor_of>::get(int index) {
#ifdef SORTED_LIST_USE_GAPS
    if(index >= gap_start) {
        index += gap_length;
    }
#endif
    return data[index];
}

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> void SortedList<element_t, compare, is_floor_of>::foreach(void (*visit)(element_t element)) {
    for(int index = 0; index < size; index++) {
        visit(get(index));
    }
}

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> bool SortedList<element_t, compare, is_floor_of>::contains(element_t obj) {
    bool found;
    get_index(obj, &found);
    return found;
}

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> inline int SortedList<element_t, compare, is_floor_of>::floor(element_t obj) {
    bool exact;
    int floor_index = get_index(obj, &exact);
    if(!exact) {
        floor_index--;
        while(floor_index >= 0 && !is_floor_of(get(floor_index), obj)) floor_index--;
    }
    assert(floor_index < 0 || compare(get(floor_index), obj) <= 0, "contract");
    return floor_index;
}

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> void SortedList<element_t, compare, is_floor_of>::clear() {
    size = 0;
#ifdef SORTED_LIST_USE_GAPS
    gap_start = 0;
#endif
}

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> inline void SortedList<element_t, compare, is_floor_of>::add(element_t element) {
    assert(is_sorted(), "invariant");
    if(size == capacity) {
        grow();
    }
    bool match;
    int index = get_index(element, &match);
    if(!match) {
#ifdef SORTED_LIST_USE_GAPS
        move_gap_to(index);
        gap_start++;
#else
        memmove(insert_pos + 1, insert_pos, (size - index) * sizeof(element_t));
#endif
        size++;
    }
    element_t* insert_pos = data + index;
    *insert_pos = element;
    assert(is_sorted(), "invariant");
}

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> inline int SortedList<element_t, compare, is_floor_of>::get_index(element_t element, bool* exact_match) {
#ifdef SORTED_LIST_USE_GAPS
    {
        int comparison_left = 1, comparison_right = 1;
        if((gap_start == 0 || (comparison_left = compare(get(gap_start - 1), element)) <= 0)
                && ((size_t) gap_start >= size || (comparison_right = compare(element, get(gap_start))) <= 0)) {
            if(comparison_left == 0) {
                *exact_match = true;
                return gap_start - 1;
            } else if (comparison_right == 0) {
                *exact_match = true;
                return gap_start;
            } else {
                *exact_match = false;
                return gap_start;
            }
        }
    }
#endif
    
    int lo = 0;
    int hi = size - 1;
    while(lo <= hi) {
        int mi = lo + (hi - lo) / 2;
        int comparison = compare(get(mi), element);
        if(comparison < 0) {
            lo = mi + 1;
        } else if (comparison > 0) {
            hi = mi - 1;
        } else {
            *exact_match = true;
            return mi;
        }
    }
    assert(!(0 <= lo+0 && lo+0 < (int) size) || compare(element, get(lo+0)) < 0, "binary search did not yield proper insert position (next element is not greater)");
    assert(!(0 <= lo-1 && lo-1 < (int) size) || compare(get(lo-1), element) < 0, "binary search did not yield proper insert position (prev element is not smaller)");
    *exact_match = false;
    return lo;
}

template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> void SortedList<element_t, compare, is_floor_of>::grow() {
#ifdef SORTED_LIST_USE_GAPS
    move_gap_to(size);
#endif
    size_t new_capacity = MAX(1, capacity * 2);
    data = (element_t*) arena->Arealloc(data, capacity * sizeof(element_t), new_capacity * sizeof(element_t));
    capacity = new_capacity;
}

#ifdef SORTED_LIST_USE_GAPS
template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> void SortedList<element_t, compare, is_floor_of>::move_gap_to(int index) {
    int relocation = index - gap_start;
    
    if(relocation != 0) {
        size_t elements_to_move = ABS(relocation);
        int dest, src;
        if(relocation >= 0) {
            dest = gap_start;
            src = gap_end;
        } else {
            dest = gap_end - elements_to_move;
            src = gap_start - elements_to_move;
        }
        if(elements_to_move <= gap_length) {
            memcpy(data + dest, data + src, elements_to_move * sizeof(element_t));
        } else {
            memmove(data + dest, data + src, elements_to_move * sizeof(element_t));
        }
    }
        
    gap_start += relocation;
}
#endif

#ifdef ASSERT
template <class element_t, int (*compare)(element_t element1, element_t element2), bool (*is_floor_of)(element_t floor, element_t obj)> bool SortedList<element_t, compare, is_floor_of>::is_sorted() {
    for(int index = 1; (size_t) index < size; index++) {
        if(compare(get(index - 1), get(index)) > 0) {
            return false;
        }
    }
    return true;
}
#endif

#ifdef SORTED_LIST_USE_GAPS
    #undef gap_length
    #undef gap_end
#endif

#endif	/* SORTEDARRAYLIST_HPP */

