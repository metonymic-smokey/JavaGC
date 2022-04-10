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
 * File:   Encoder.hpp
 * Author: Philipp Lengauer
 *
 * Created on December 3, 2013, 1:49 PM
 */

#ifndef LZWCOMPRESSION_HPP
#define	LZWCOMPRESSION_HPP

#include "Compression.hpp"
#include "Map.hpp"

class Sequence;

class LZWCompression : public Compression {
private:
    Arena* arena_elements;
    Arena* arena_dictionary;
    NavigableMap<Sequence*, uint>* dictionary;
    uint next_index;
    size_t max_length;
    
public:
    LZWCompression(int collection_strategy);
    ~LZWCompression();
    
    void reset();
    
    Result encode(unsigned char* src_array, size_t src_length, unsigned char* dest_buffer, size_t dest_length);

    Decompression* getDecompressor();
    
    size_t getDictionarySize();
    
private:
    uint resolveIndex(unsigned char* array, size_t* length);
    void registerIndex(unsigned char* array, size_t length);
};

#endif	/* ENCODER_HPP */

