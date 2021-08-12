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
 * File:   CompressionDefinitions.hpp
 * Author: Philipp Lengauer
 *
 * Created on December 6, 2013, 9:22 AM
 */

#ifndef COMPRESSIONDEFINITIONS_HPP
#define	COMPRESSIONDEFINITIONS_HPP

#define INDEX_MAX_WIDTH (2)

#define INDEX_MASK ((1 << (INDEX_MAX_WIDTH * 8)) - 1)
#define FIRST_CUSTOM_INDEX (1 << 8)
#define MAX_INDEX (((~0u) & INDEX_MASK) >> INDEX_MAX_WIDTH)

#ifdef ASSERT
//#define COMPRESSION_PARANOID_ASSERTIONS
// #define DEBUG_COMPRESSION
// #define DEBUG_DECOMPRESSION
#endif

#if defined(DEBUG_COMPRESSION) || defined(DEBUG_DECOMPRESSION)
inline void debug_print_array(const char* name, const unsigned char* array, size_t length, size_t index) {
    fprintf(stderr, "### %s:", name);
    for(size_t i = 0; i < length; i++) {
        fprintf(stderr, "%s%02x", index == i ? "|" : " ", ((int) array[i]) & 0xFF);
    }
    fprintf(stderr, "%s\n", index == length ? "|" : " ");
    fflush(stderr);
}
#endif

#if defined(ASSERT)
inline bool compare(unsigned char* array0, size_t length0, unsigned char* array1, size_t length1) {
#if defined(DEBUG_COMPRESSION) || defined(DEBUG_DECOMPRESSION)
    debug_print_array("0", array0, length0, -1);
    debug_print_array("1", array1, length1, -1);
#endif
    if(length0 != length1) return false;
    if(array0 == array1) return true;
    if(array0 == NULL || array1 == NULL) return false;
    for(size_t index = 0; index < length0; index++) {
        if(array0[index] != array1[index]) return false;
    }
    return true;
}
#endif

#endif	/* COMPRESSIONDEFINITIONS_HPP */

