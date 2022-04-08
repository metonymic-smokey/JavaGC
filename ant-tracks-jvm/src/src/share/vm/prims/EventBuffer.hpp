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
 * File:   EventBuffer.hpp
 * Author: Philipp Lengauer
 *
 * Created on February 13, 2014, 8:19 AM
 */

#ifndef EVENTBUFFER_HPP
#define	EVENTBUFFER_HPP

#include "runtime/mutex.hpp"

enum EventBufferSyncLevel {
    Sync_None = 0,
    Sync_Ensure_Order = 1,
    Sync_Full = 2
};

struct EventBuffer {
    jint* bottom;
    jint* top;
    jint* end;
    int id;
    Thread* owner;
    EventBufferSyncLevel sync;
    bool is_compressed;
};

#ifdef ASSERT
#define EventBuffer_debug_uninitialized_content ((jint) 0xDEADBEEF)
#endif

EventBuffer EventBuffer_create(size_t capacity, bool enforce_exact_capacity);
EventBuffer EventBuffer_create_dummy();
void EventBuffer_destroy(EventBuffer* thiz);

bool EventBuffer_is_valid(EventBuffer* thiz);

void EventBuffer_assert_is_valid(EventBuffer* thiz);
void EventBuffer_assert_is_dummy(EventBuffer* thiz);

jint* EventBuffer_get_data(EventBuffer* thiz);
bool EventBuffer_is_compressed(EventBuffer* thiz);
void EventBuffer_set_compression(EventBuffer* thiz, size_t compressed_length);
size_t EventBuffer_get_length(EventBuffer* thiz);
size_t EventBuffer_get_raw_length(EventBuffer* thiz);
size_t EventBuffer_get_capacity(EventBuffer* thiz);
jint* EventBuffer_allocate(EventBuffer* thiz, size_t size);
void EventBuffer_reset(EventBuffer* thiz);
void EventBuffer_set_owner(EventBuffer* thiz, Thread* thread);
Thread* EventBuffer_get_owner(EventBuffer* thiz);
#endif	/* EVENTBUFFER_HPP */

