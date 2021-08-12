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
 * File:   EventsWriter.cpp
 * Author: Verena Bitto
 * 
 * Created on December 2, 2013, 2:41 PM
 */

#include "precompiled.hpp"
#include "EventsWriter.hpp"
#include "AllocationTracingSelfMonitoring.hpp"
#include "AllocationTracingSynchronization.hpp"
#include "EventBuffers.hpp"
#include "AllocationTracing.hpp"

EventsWriter::EventsWriter() : AllocationTracingIO(TraceObjectsTraceFile, TraceObjectsMaxTraceSize > 0 ? (TraceObjectsMaxTraceFileCount > 0 ? TraceObjectsMaxTraceFileCount : MAX2(1, (int) (1 / TraceObjectsTraceSizeMaxDeviation))) : 1){
    io_timer = elapsedTimer();
    init();
}

void EventsWriter::write_buffer(EventBuffer* buffer) {
    synchronized(get_lock()) {
        write_buffer_unlocked(buffer);
    }
}

void EventsWriter::flush() {
    AllocationTracingIO::flush();
}

void EventsWriter::write_buffer_unlocked(EventBuffer* local_buffer) { 
    unsigned char* buffer = (unsigned char*) EventBuffer_get_data(local_buffer);
    bool compressed = EventBuffer_is_compressed(local_buffer);
    size_t length = EventBuffer_get_raw_length(local_buffer);
    
   // fprintf(stdout, "compressed?%d, length=%ld \n", compressed?1:0, length);
   // fflush(stdout);
    jint metadata = (jint) (length | ((compressed ? 1 : 0) << (sizeof(jint) * 8 - 1)) | ((local_buffer->sync) << (sizeof(jint) * 8 - 3)));

    
    
    Thread* owner = EventBuffer_get_owner(local_buffer);
    self_monitoring_measure_time(1, get_io_timer()) {
        write(&owner, sizeof(Thread*) * 1);
        write(&metadata, sizeof(jint) * 1);
        write(buffer, sizeof(char) * length);
    }
    if(TraceObjectsEagerFlush) {
        flush();
    }
    
    EventBuffer_reset(local_buffer);
    
    self_monitoring(1) {
        AllocationTracingSelfMonitoring::report_buffer_written(sizeof(Thread*) + sizeof(jint) + length * sizeof(char));
    }
}

void EventsWriter::write_header() {
    jint index = (jint) get_cur_file_index();
    write(&index, sizeof(jint));
}

int EventsWriter::get_file_type() {
    return FILE_TYPE_TRACE;
}
