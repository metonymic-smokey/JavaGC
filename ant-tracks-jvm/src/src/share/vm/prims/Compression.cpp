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
 * File:   Compression.cpp
 * Author: Philipp Lengauer
 * 
 * Created on April 11, 2015, 10:16 AM
 */

#include "precompiled.hpp"
#include "Compression.hpp"


bool Compression::encode(EventBuffer* src_buffer, EventBuffer* dest_buffer) {
    unsigned char* src = (unsigned char*) EventBuffer_get_data(src_buffer);
    size_t src_length = EventBuffer_get_length(src_buffer) * sizeof(jint);
    
    unsigned char* dest = (unsigned char*) EventBuffer_get_data(dest_buffer);
    size_t dest_length = EventBuffer_get_capacity(dest_buffer) * sizeof(jint); // length == 0 !
    
    Result result = encode(src, src_length, dest, dest_length);
    guarantee(result.dest_length <= dest_length, "buffer overflow in compression");
    if(result.src_compressed_length == src_length) {
        EventBuffer_set_compression(dest_buffer, result.dest_length);
        return true;
    } else {
        return false;
    }
}