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
 * File:   Buffers.hpp
 * Author: vmb
 *
 * Created on February 13, 2014, 12:44 PM
 */

#ifndef BUFFERS_HPP
#define	BUFFERS_HPP

#include "EventBufferList.hpp"
class EventsWriter;
class EventsWorkerThread;

class EventBuffers : public AllStatic { 
public:
    static void init();
    static void destroy();
    
    static void enqueue_to_flush_queue(EventBuffer buffer);
    static bool dequeue_from_flush_queue(EventBuffer* buffer_ptr, volatile bool* is_waiting_ptr);
    static void finalize_flush_queue();
    static void finalize_compression_queue();
    static void enqueue_to_free_list(EventBuffer buffer);
    static bool dequeue_from_free_list(EventBuffer* buffer_ptr);
    static void enqueue_to_compression(EventBuffer buffer);
    static bool dequeue_from_compression(EventBuffer* buffer_ptr);
    static bool peek_from_compression(EventBuffer* buffer_ptr, volatile bool* is_waiting_ptr);
    static void flush(EventBuffer* buffer);
    static void flush();
    static void reset_flush_queue();
    static double flush_queue_fill_level();
    static void reset_compression();
    static double compression_fill_level();
private:
    static EventBuffer create();
    static void destroy(EventBuffer* buffer);
    static EventBufferList* flush_queue;
    static EventBufferList* free_list;
    static EventBufferList* compression_queue;
    static bool create_event_buffer(EventBuffer* buffer_ptr);
    static bool destroy_event_buffer(EventBuffer buffer);
};

#endif	/* BUFFERS_HPP */

