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
 * File:   Decompression.hpp
 * Author: Philipp Lengauer
 *
 * Created on December 4, 2013, 8:32 AM
 */

#ifndef LZWDECOMPRESSION_HPP
#define	LZWDECOMPRESSION_HPP

#include "Decompression.hpp"
#include "Map.hpp"

class Sequence;

class LZWDecompression : public Decompression {
private:
    Arena* arena_elements;
    Arena* arena_dictionary;
    Map<uint, Sequence*>* dictionary;
    uint next_index;
    Arena* arena_tmp;
    
public:
    LZWDecompression();
    ~LZWDecompression();
    
    void reset();
    size_t decode(unsigned char* src_array, size_t src_length, unsigned char* dest_array, size_t dest_length);
    
    size_t getDictionarySize();
private:
    Sequence* resolveIndex(uint index);
    void registerIndex(Sequence* last, Sequence* current);
};

#endif	/* DECOMPRESSION_HPP */

