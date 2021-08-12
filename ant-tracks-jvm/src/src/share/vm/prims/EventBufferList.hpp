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
 * File:   EventBufferList.hpp
 * Author: Philipp Lengauer
 *
 * Created on February 13, 2014, 8:28 AM
 */

#ifndef EVENTBUFFERLIST_HPP
#define	EVENTBUFFERLIST_HPP

#include "EventBuffer.hpp"
#include "runtime/mutex.hpp"

typedef bool (*HandleUnderflow)(EventBuffer* buffer_ptr);
typedef bool (*HandleOverflow)(EventBuffer buffer);
typedef void (*Iterate)(EventBuffer* buffer_ptr);
typedef void (*HandleElement)(EventBuffer buffer);

enum EventBufferListResizeMode {
    resize_disabled,
    resize_enabled_preserve_order,
    resize_enabled_no_order
};
    
class EventBufferList : public CHeapObj<mtInternal> {
public:
private:
    char* name;
    Monitor* lock;
    EventBuffer* list;
    int capacity, max_capacity;
    int head;
    int tail;
    EventBufferListResizeMode resize_mode;
    HandleUnderflow underflow;
    HandleOverflow overflow;
    int consumers_waiting;
    int producers_waiting;
    bool finalized;
public:
    EventBufferList(char* name, int capacity, EventBufferListResizeMode resize_mode = resize_disabled, int max_capacity = ~0, HandleUnderflow underflow = NULL, HandleOverflow overflow = NULL);
    ~EventBufferList();
    int get_length();
    bool is_empty();
    bool is_full();
    double fill_level();
    inline bool enqueue(EventBuffer buffer) { bool _ = false; return enqueue(buffer, &_); }
    inline bool dequeue(EventBuffer* buffer_ptr) { bool _ = false; return dequeue(buffer_ptr, &_); }
    inline bool peek(EventBuffer* buffer_ptr) { bool _ = false; return peek(buffer_ptr, &_); }
    bool enqueue(EventBuffer buffer, volatile bool* is_waiting_ptr);
    bool dequeue(EventBuffer* buffer_ptr, volatile bool* is_waiting_ptr);
    bool peek(EventBuffer* buffer_ptr, volatile bool* is_waiting_ptr);
    void dequeue_all(HandleElement handle);
    void for_each(Iterate iterate);
    void finalize();
    
    void wait_until_empty();
private:
    void for_each(Iterate iterate, bool elements);
    void resize_to(int new_capacity, bool preserve_order);
#ifdef ASSERT
    static void clear(EventBuffer* list, int size);
#endif
};

#endif	/* EVENTBUFFERLIST_HPP */

