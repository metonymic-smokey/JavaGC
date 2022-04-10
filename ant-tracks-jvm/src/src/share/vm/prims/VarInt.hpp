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
 * File:   VarInt.hpp
 * Author: Philipp Lengauer
 *
 * Created on December 6, 2013, 1:07 PM
 */

#ifndef VARINT_HPP
#define	VARINT_HPP

#include "utilities/globalDefinitions.hpp"
#include "memory/allocation.hpp"

#define BITWISE_COMPRESSION

class VarIntReader : public StackObj {
private:
#ifdef BITWISE_COMPRESSION
    int width;
    int in_byte_offset;
#endif
    
public:
    VarIntReader()
#ifdef BITWISE_COMPRESSION
    : width(8), in_byte_offset(0)
#endif
    {}
    
private:
#ifdef BITWISE_COMPRESSION
    size_t read0(unsigned char* ptr, uint* value_ptr, int bits) {
        unsigned char* cursor = ptr;
        do {
            if(in_byte_offset == 8) {
                in_byte_offset = 0;
                cursor++;
            }
            int bits_to_read = MIN2(8 - in_byte_offset, bits);
            unsigned char value_part_mask = (1u << bits_to_read) - 1;
            unsigned char value_part_in_place = *cursor;
            unsigned char value_part = (value_part_in_place >> (8 - in_byte_offset - bits_to_read)) & value_part_mask;
            *value_ptr = *value_ptr | (value_part << (bits - bits_to_read));
            bits = bits - bits_to_read;
            in_byte_offset = in_byte_offset + bits_to_read;
        } while(bits > 0);
        return cursor - ptr;
    }
#endif
    
public:
    size_t read(unsigned char* ptr, uint* value_ptr) {
        *value_ptr = 0;
#ifdef BITWISE_COMPRESSION
        unsigned char* cursor = ptr;
        cursor += read0(cursor, value_ptr, width);
        if(*value_ptr == (1u << width) - 1) {
            width++;
            cursor += read(cursor, value_ptr);
        }
        return cursor - ptr;
#else
        size_t length = 0;
        for(;;) {
            uint raw_part = ((uint) *ptr) & 0xFF;
            *value_ptr = (*value_ptr) | ((raw_part & ~(1 << 7)) << (7 * length));
            length++;
            if((raw_part & (1 << 7)) == 0) {
                break;
            }
            ptr++;
        }
        return length;
#endif
    }
    
};

class VarIntWriter : public StackObj {
private:
#ifdef BITWISE_COMPRESSION
    int width;
    int fill_level;
    char buffer;
#endif
#ifdef ASSERT
    VarIntReader reader;
#endif
    
public:
    VarIntWriter()
#if defined(BITWISE_COMPRESSION) | defined(ASSERT)
    :
#endif
#ifdef BITWISE_COMPRESSION
    width(8), fill_level(0), buffer(0)
#endif
#if defined(BITWISE_COMPRESSION) & defined(ASSERT)
    ,
#endif
#ifdef ASSERT
    reader()
#endif
    {
        assert(sizeof(char) == 1, "must be");
    }
    
private:
#ifdef BITWISE_COMPRESSION
    inline size_t write0(unsigned char* ptr, uint value, int bits) {
        unsigned char* cursor = ptr;
        do {
            if(fill_level == 8) {
                *(cursor++) = buffer;
                buffer = 0;
                fill_level = 0;
            }
            int bits_to_write = MIN2(8 - fill_level, bits);
            uint value_mask = ((1u << bits_to_write) - 1);
            unsigned char value_to_write = (unsigned char) ((value >> (bits - bits_to_write)) & value_mask);
            int location = 8 - fill_level - bits_to_write;
            unsigned char value_in_place = value_to_write << location;
            buffer = buffer | value_in_place;
            fill_level = fill_level + bits_to_write;
            bits = bits - bits_to_write;
        } while(bits > 0);
        return cursor - ptr;
    }
    
    inline size_t expand(unsigned char* ptr) {
        uint marker = (1 << width) - 1;
        return write0(ptr, marker, width++);
    }
#endif
    
public:    
    inline size_t write(unsigned char* ptr, uint value) {
#ifdef BITWISE_COMPRESSION
        unsigned char* cursor = ptr;
        while(value >= (1u << width) - 1 - 1) {
            cursor += expand(cursor);
        }
        cursor += write0(cursor, value, width);
        return cursor - ptr;
#else
#ifdef ASSERT
        uint value1 = value;
#endif
        size_t length = 0;
        for(;;) {
            *ptr = value & ~(1 << 7);
            value = value >> 7 & ~(0xFE << (sizeof(uint) * 8 - 7));
            length++;
            if(value == 0) {
                break;
            }
            *ptr |= (1 << 7);
            ptr++;
        }
#ifdef ASSERT
        uint value2 = 0;
        VarIntReader reader = VarIntReader();
        assert(length == reader.read(ptr - length + 1, &value2) && value1 == value2, "what sorcery is this?");
#endif
        return length;
#endif
    }
    
    inline size_t flush(unsigned char* ptr) {
#ifdef BITWISE_COMPRESSION
        unsigned char* cursor = ptr;
        cursor += expand(cursor);
        cursor += write0(cursor, 0, width);
        *(cursor++) = buffer;
        return cursor - ptr;
#else
        return 0;
#endif
    }
};

#endif	/* VARINT_HPP */

