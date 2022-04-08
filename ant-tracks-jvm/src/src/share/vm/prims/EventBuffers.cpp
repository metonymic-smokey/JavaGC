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
 * File:   Buffers.cpp
 * Author: vmb
 * 
 * Created on February 13, 2014, 12:44 PM
 */

#include "precompiled.hpp"
#include "EventBuffers.hpp"
#include "../runtime/thread.hpp"
#include "AllocationTracingSelfMonitoring.hpp"
#include "AllocationTracingSynchronization.hpp"
#include "EventsWriter.hpp"
#include "AllocationTracing.hpp"

EventBufferList* EventBuffers::flush_queue = NULL;
EventBufferList* EventBuffers::free_list = NULL;
EventBufferList* EventBuffers::compression_queue = NULL;

EventBuffer EventBuffers::create() {
    size_t buffer_size = TraceObjectsBufferSize / sizeof(jint);
    if(TraceObjectsFuzzyBufferSizes) {
        buffer_size = (size_t) 
                (
                    buffer_size - 
                    (buffer_size * TraceObjectsFuzzyBufferSizeMaxDeviation) +
                    (buffer_size * 2 * TraceObjectsFuzzyBufferSizeMaxDeviation * ((double) rand() / RAND_MAX))
                );
    }
    // Ensure minimum buffer size to fit at least one event
    buffer_size = MAX2(buffer_size, (size_t) MAX_EVENT_SIZE);
    EventBuffer buffer = EventBuffer_create(buffer_size, !TraceObjectsFuzzyBufferSizes);
    self_monitoring(1) {
        AllocationTracingSelfMonitoring::report_buffer_allocated(EventBuffer_get_capacity(&buffer), buffer_size);
    }
    return buffer;
}

void EventBuffers::destroy(EventBuffer* buffer) {
    self_monitoring(1) {
        AllocationTracingSelfMonitoring::report_buffer_freed(EventBuffer_get_capacity(buffer));
    }
    EventBuffer_destroy(buffer);
}

bool self_monitoring_report_flush_queue_overflow(EventBuffer _) {
    self_monitoring(1) AllocationTracingSelfMonitoring::report_flush_queue_overflow();
    return false;
}

void EventBuffers::init() {
    if(TraceObjectsAsyncIO) {
        flush_queue = new EventBufferList((char*) "Flush Queue", 1, resize_enabled_preserve_order, TraceObjectsMaxFlushBacklog, NULL, self_monitoring_report_flush_queue_overflow);
        compression_queue = new EventBufferList((char*) "Compression queue", 1, resize_enabled_preserve_order, TraceObjectsMaxFlushBacklog, NULL, NULL);
    } else {
        flush_queue = NULL;
        compression_queue = NULL;
    }
    free_list = new EventBufferList((char*) "Free List", 1, resize_enabled_no_order, TraceObjectsMaxFreeBuffers, create_event_buffer, destroy_event_buffer);
}

void EventBuffers::destroy() {
    EventBufferList* free_list_temp = free_list;
    EventBufferList* flush_queue_temp = flush_queue;
    EventBufferList* compression_queue_temp = compression_queue;
    
    free_list = NULL;
    flush_queue = NULL;
    compression_queue = NULL;
    
    delete free_list_temp; free_list_temp = NULL;
    delete flush_queue_temp; flush_queue_temp = NULL;
    delete compression_queue_temp; compression_queue_temp = NULL;
}

void EventBuffers::enqueue_to_flush_queue(EventBuffer buffer){
    assert(flush_queue != NULL, "?");
    flush_queue->enqueue(buffer);
}

bool EventBuffers::dequeue_from_flush_queue(EventBuffer* buffer_ptr, volatile bool* is_waiting_ptr){
    assert(flush_queue != NULL, "?");
    return flush_queue->dequeue(buffer_ptr, is_waiting_ptr);
}

// HINTERREITER: added for compression queue 

void EventBuffers::enqueue_to_compression(EventBuffer buffer){
    assert(compression_queue != NULL, "?");
    compression_queue->enqueue(buffer);
}

bool EventBuffers::dequeue_from_compression(EventBuffer* buffer_ptr){
    assert(compression_queue != NULL, "?");
    return compression_queue->dequeue(buffer_ptr);
}

bool EventBuffers::peek_from_compression(EventBuffer* buffer_ptr, volatile bool* is_waiting_ptr){
    assert(compression_queue != NULL, "?");
    return compression_queue->peek(buffer_ptr, is_waiting_ptr);
}

void EventBuffers::finalize_compression_queue(){
    assert(compression_queue != NULL, "?");
    compression_queue->finalize();
}

//

void EventBuffers::finalize_flush_queue(){
    assert(flush_queue != NULL, "?");
    flush_queue->finalize();
}

void EventBuffers::enqueue_to_free_list(EventBuffer buffer){    
    assert(EventBuffer_get_length(&buffer) == 0, "Buffer must be empty to be enqueued to free list.");
    free_list->enqueue(buffer);
}

bool EventBuffers::dequeue_from_free_list(EventBuffer* buffer_ptr){
    return free_list->dequeue(buffer_ptr);
}

void EventBuffers::flush(EventBuffer* buffer) {
    AllocationTracing::get_trace_writer()->write_buffer(buffer);
}

void EventBuffers::flush() {
    if(compression_queue != NULL){
        compression_queue->wait_until_empty();
        int i = 0;
        while(i < CompressionThread::max_compression_threads){
            if(CompressionThread::get_CompressionThread(i) != NULL){
                while(!CompressionThread::get_CompressionThread(i)->is_Idle()) os::yield_all();
            }
            i++;
        }
    }
    if(flush_queue != NULL) {
        flush_queue->wait_until_empty();
    }
    while(AllocationTracing::get_worker() != NULL && !AllocationTracing::get_worker()->is_idle()) os::yield_all();
    AllocationTracing::get_trace_writer()->flush();
}

void clear_and_add_to_free_list(EventBuffer buffer) {
    EventBuffer_reset(&buffer);
    EventBuffers::enqueue_to_free_list(buffer);
}

void EventBuffers::reset_flush_queue() {
    flush_queue->dequeue_all(clear_and_add_to_free_list);
}

double EventBuffers::flush_queue_fill_level() {
    return flush_queue != NULL ? flush_queue->fill_level() : 0;
}

void EventBuffers::reset_compression() {
    compression_queue->dequeue_all(clear_and_add_to_free_list);
}

double EventBuffers::compression_fill_level() {
    return compression_queue != NULL ? compression_queue->fill_level() : 0;
}

bool EventBuffers::create_event_buffer(EventBuffer* buffer_ptr) {
    *buffer_ptr = create();
    return true;
}

bool EventBuffers::destroy_event_buffer(EventBuffer buffer) {
    destroy(&buffer);
    return true;
}

  