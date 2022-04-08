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
 * File:   EventBufferList.cpp
 * Author: Philipp Lengauer
 *
 * Created on February 13, 2014, 8:28 AM
 */

#include "precompiled.hpp"
#include "EventBufferList.hpp"
#include "AllocationTracingSelfMonitoring.hpp"
#include "AllocationTracingUtil.hpp"
#include "AllocationTracingSynchronization.hpp"

EventBufferList::EventBufferList(char* name, int capacity, EventBufferListResizeMode resize_mode, int max_capacity, HandleUnderflow underflow, HandleOverflow overflow) {
    if(capacity < 1) capacity = 1;
    this->name = name;
    this->lock = new Monitor(Mutex::native, name, true);
    this->list = (EventBuffer*) calloc(sizeof(EventBuffer), capacity);
    this->capacity = capacity;
    this->max_capacity = max_capacity;
    this->head = -1;
    this->tail = 0;
    this->resize_mode = resize_mode;
    this->underflow = underflow;
    this->overflow = overflow;
    this->finalized = false;
    assert(get_length() == 0, "");
    assert(is_empty(), "");
    assert(!is_full(), "");
#ifdef ASSERT
    clear(list, capacity);
#endif
}

EventBufferList::~EventBufferList() {
    //locale variable statt statische
    
    //liste ausgeben
//    assert(finalized, "not finalized");
    while(!is_empty()) {
        EventBuffer buffer;
        if(dequeue(&buffer)) {
            bool handled = overflow(buffer);
            assert(handled, "must be");
        } else {
            break;
        }
    }
    assert(is_empty(), "must be");
    free(list); list = NULL;
    delete lock; lock = NULL;
    capacity = 0;
    head = -1;
    tail = 0;
}

int EventBufferList::get_length() {
    synchronized(lock) {
        return head < 0 ? 0 : (tail > head ? (tail - head) : (capacity - (head - tail)));
    }
    HERE_BE_DRAGONS(0u);
}

bool EventBufferList::is_empty() {
    synchronized(lock) {
        bool empty = head < 0 && tail == 0;
        assert(!empty || get_length() == 0, "get_length and is_empty deviate!");
        return empty;
    }
    HERE_BE_DRAGONS(false);
}

bool EventBufferList::is_full() {
    synchronized(lock) {
        bool full = head == tail;
        assert(!full || get_length() == capacity, "get_length and is_full deviate!");
        return full;
    }
    HERE_BE_DRAGONS(false);
}

double EventBufferList::fill_level() {
    synchronized(lock) {
        int length = get_length();
        double fill_level = 1.0 * length / max_capacity;
        return fill_level;
    }
    HERE_BE_DRAGONS(0);
}

bool EventBufferList::enqueue(EventBuffer buffer, volatile bool* is_waiting_ptr) {
    synchronized(lock) {
        while(!finalized && is_full()) {
            if(resize_mode != resize_disabled && capacity < max_capacity) {
                resize_to(capacity * 2, resize_mode == resize_enabled_preserve_order);
            } else if(overflow != NULL && overflow(buffer)) {
                return false;
            } else {
                *is_waiting_ptr = true;
                synchronized_wait(lock);
                *is_waiting_ptr = false;
            }
        }
        if(finalized) {
            if(overflow != NULL) overflow(buffer);
            return false;
        }

        if(head < 0) {
            head = tail;
        }

        list[tail] = buffer;
        tail = (tail + 1) % capacity;

        synchronized_notify_all(lock);
        return true;
    }
    HERE_BE_DRAGONS(false);
}

bool EventBufferList::dequeue(EventBuffer* buffer_ptr, volatile bool* is_waiting_ptr) {
    synchronized(lock) {
        while(!finalized && is_empty()) {
            if(underflow != NULL && underflow(buffer_ptr)) {
                return true;
            } else {
                *is_waiting_ptr = true;
                synchronized_wait(lock);
                *is_waiting_ptr = false;
            }
        }
        if(finalized) {
            return false;
        }

        EventBuffer buffer = list[head];
#ifdef ASSERT
        clear(list + head, 1);
#endif
        head = (head + 1) % capacity;

        if(head == tail) { // empty -> reset
            head = -1;
            tail = head + 1;
        }

        *buffer_ptr = buffer;
        synchronized_notify_all(lock);
        return true;
    }
    HERE_BE_DRAGONS(false);
}

//Hinterreiter Daniel
/**/
//just get the next queue element, not dequeue it
bool EventBufferList::peek(EventBuffer* buffer_ptr, volatile bool* is_waiting_ptr) {
    synchronized(lock){
        while(!finalized && is_empty()) {
            if(underflow != NULL && underflow(buffer_ptr)) {
                return true;
            } else {
                *is_waiting_ptr = true;
                synchronized_wait(lock);
                *is_waiting_ptr = false;
            }
        }
        if(finalized) {
            return false;
        }
        EventBuffer buffer = list[head];
        *buffer_ptr = buffer;
        synchronized_notify_all(lock);
        return true;
    }
    HERE_BE_DRAGONS(false);
}

void EventBufferList::dequeue_all(HandleElement handle) {
    synchronized(lock) {
        while(!is_empty()) {
            EventBuffer buffer;
            bool dequeued = dequeue(&buffer);
            if(dequeued) {
                handle(buffer);
            } else {
                break;
            }
        }
    }
}

void EventBufferList::for_each(Iterate iterate) {
    for_each(iterate, true);
}

void EventBufferList::finalize() {
    synchronized(lock) {
        finalized = true;
        synchronized_notify_all(lock);
    }
}

void EventBufferList::wait_until_empty() {
    synchronized_wait_until(lock, is_empty());
}

void EventBufferList::for_each(Iterate iterate, bool elements) {
    synchronized(lock) {
        //these two checks are needed because if empty, head is illegal!!!
        if(elements && is_empty()) return;
        if(!elements && is_full()) return;
        int start = elements ? head : tail;
        int end = elements ? tail : head;
        for(int i = start; i != end; i = ((i + 1) % capacity)) {
            iterate(&list[i]);
        }
    }
}

void EventBufferList::resize_to(int new_capacity, bool preserve_order) {
    synchronized(lock) {
        synchronized_wait_until(lock, get_length() <= new_capacity);
        assert(new_capacity > capacity && is_full(), "currently this only works in one direction ...");

        int length = get_length();
        list = (EventBuffer*) realloc(list, sizeof(EventBuffer) * new_capacity);
#ifdef ASSERT
        clear(list + capacity, (new_capacity - capacity));
#endif
        if(preserve_order) {
            assert(new_capacity >= 2*capacity, "to lazy to implement these cases");
            memcpy(list + capacity, list, head * sizeof(EventBuffer));
            tail = head + capacity;
            capacity = new_capacity;
#ifdef ASSERT
            clear(list, head);
#endif
        } else {
            assert(length > 0, "");
            head = 0;
            tail = length;
            capacity = new_capacity;
        }
        // Just notify locks on enqueue, deqeue and finalize
        // synchronized_notify_all(lock);
        assert(get_length() == length, "length is not the same after resizing");
#ifdef ASSERT
        for_each(EventBuffer_assert_is_valid, true);
        for_each(EventBuffer_assert_is_dummy, false);
#endif
    }
}

#ifdef ASSERT
void EventBufferList::clear(EventBuffer* list, int size) {
    for(int i = 0; i < size; i++) {
        list[i] = EventBuffer_create_dummy();
    }
}
#endif
