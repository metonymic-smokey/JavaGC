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
 * File:   EventBuffer.cpp
 * Author: Philipp Lengauer
 *
 * Created on February 13, 2014, 8:19 AM
 */

#include "precompiled.hpp"
#include "EventBuffer.hpp"
#include "runtime/thread.hpp"
#ifdef LINUX
#include <malloc.h>
#endif

#ifdef ASSERT
void EventBuffer_fill(EventBuffer* thiz) {
    for(jint* ptr = thiz->bottom; ptr < thiz->end; ptr++) {
        *ptr = EventBuffer_debug_uninitialized_content;
    }
}
#endif

EventBuffer EventBuffer_create(size_t capacity, bool enforce_exact_capacity) {
    // Ensure minimum buffer size
    capacity = MAX2((size_t) MAX_EVENT_SIZE, capacity);
    static int last_id = 0;
    EventBuffer thiz;
    thiz.id = Atomic::add(1, &last_id);
    thiz.bottom = (jint*) calloc(capacity, sizeof(jint));
#ifdef LINUX
    if(!enforce_exact_capacity) {
        size_t real_capacity = malloc_usable_size(thiz.bottom) / sizeof(jint);
        assert(real_capacity >= capacity, "?");
        capacity = real_capacity;
    }
#endif
    thiz.top = thiz.bottom;
    thiz.end = thiz.bottom + capacity;
    thiz.owner = NULL;
    thiz.sync = Sync_None;
    thiz.is_compressed = false;
#ifdef ASSERT
    EventBuffer_fill(&thiz);
#endif
    assert(EventBuffer_get_length(&thiz) == 0, "");
    assert(EventBuffer_get_capacity(&thiz) == capacity, "");
    return thiz;
}

EventBuffer EventBuffer_create_dummy() {
    EventBuffer thiz;
    thiz.id = -1;
    thiz.bottom = NULL;
    thiz.top = thiz.bottom;
    thiz.end = thiz.bottom;
    thiz.owner = NULL;
    thiz.sync = Sync_None;
    thiz.is_compressed = false;
    return thiz;
}

void EventBuffer_destroy(EventBuffer* thiz) {
    thiz->id = 0;
    free(thiz->bottom); thiz->bottom = NULL;
    thiz->top = NULL;
    thiz->end = NULL;
    thiz->owner = NULL;
    thiz->is_compressed = false;
}

// This method is used to detect dummy buffers
bool EventBuffer_is_valid(EventBuffer* thiz) {
    return thiz->id > 0;
}

void EventBuffer_assert_is_valid(EventBuffer* thiz) {
    assert(thiz != NULL, "why are you asserting on a null-buffer?");
    assert(thiz->id > 0, "id of event buffer is not valid! (destroyed? dummy? uninitialized memory?)");
    assert(thiz->bottom != NULL, "data area of buffer is NULL! (destroyed? dummy? uninitialized memory?) (this should have actual been caught by the assertion before)");
    assert(thiz->bottom < thiz->end, "end pointer is not bigger than bottom pointer! (uninitialized memory?)");
    assert(thiz->bottom <= thiz->top && thiz->top <= thiz->end, "top pointer is not within bottom and end! (uninitialized memory? manual allocation or top manipulations without proper checks?)");
    assert(EventBuffer_get_raw_length(thiz) % 4 == 0 || thiz->is_compressed, "buffer has a strange length and is not compressed");
}

void EventBuffer_assert_is_dummy(EventBuffer* thiz) {
    assert(thiz->id < 0, "illegal id for dummy");
}

jint* EventBuffer_get_data(EventBuffer* thiz) {
    return thiz->bottom;
}

bool EventBuffer_is_compressed(EventBuffer* thiz) {
    return thiz->is_compressed;
}

void EventBuffer_set_compression(EventBuffer* thiz, size_t compressed_length) {
    unsigned char* bottom = (unsigned char*) thiz->bottom;
    unsigned char* top = bottom + compressed_length;
    thiz->top = (jint*) top;
    thiz->is_compressed = true;
    EventBuffer_assert_is_valid(thiz);
}

size_t EventBuffer_get_raw_length(EventBuffer* thiz){
    unsigned char* top = (unsigned char*) thiz->top;
    unsigned char* bottom = (unsigned char*) thiz->bottom;
    size_t length = top - bottom;
    assert(length >= 0, "");
    return length;
}

size_t EventBuffer_get_length(EventBuffer* thiz) {
    size_t length = thiz->top - thiz->bottom;
    assert(length >= 0, "");
    return length;
}

size_t EventBuffer_get_capacity(EventBuffer* thiz) {
    size_t capacity = thiz->end - thiz->bottom;
    assert(capacity >= 0, "");
    return capacity;
}

jint* EventBuffer_allocate(EventBuffer* thiz, size_t size) {
    assert(NULL != thiz->owner, "who is allocating?");
    assert(Thread::current() == thiz->owner, "who is allocating?");
    assert(!thiz->is_compressed, "cannot allocate into already compressed buffer");
    jint* top = thiz->top;
    jint* top_new = thiz->top + size;
    if(top_new <= thiz->end) {
#ifdef ASSERT
        for(jint* p = top; p < top_new; p++) {
            assert(*p == EventBuffer_debug_uninitialized_content, "allocated space is already initialized");
        }
        for(jint* p = top_new; p < MIN2(thiz->end, top_new + MAX_EVENT_SIZE); p++) {
            assert(*p == EventBuffer_debug_uninitialized_content, "unallocated space is already initialized");
        }
#endif
        thiz->top = top_new;
        return top;
    } else {
        return NULL;
    }
}

void EventBuffer_reset(EventBuffer* thiz) {
    thiz->top = thiz->bottom;
    thiz->owner = NULL;
    thiz->sync = Sync_None;
    thiz->is_compressed = false;
#ifdef ASSERT
    EventBuffer_fill(thiz);
#endif
}

void EventBuffer_set_owner(EventBuffer* thiz, Thread* thread) {
    assert(thiz->owner == NULL, "someone else is owning this buffer");
    thiz->owner = thread;
}

Thread* EventBuffer_get_owner(EventBuffer* thiz) {
    assert(thiz->owner != NULL, "this buffer is not owned by any thread");
    return thiz->owner;
}
