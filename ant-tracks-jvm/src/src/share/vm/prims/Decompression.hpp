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
 * Created on April 11, 2015, 10:26 AM
 */

#ifndef DECOMPRESSION_HPP
#define	DECOMPRESSION_HPP

#include "utilities/globalDefinitions.hpp"
#include "memory/allocation.hpp"
#include "CompressionDefinitions.hpp"

class Decompression : public CHeapObj<mtInternal> {
public:
    Decompression() {}
    virtual ~Decompression() {}
    
    virtual void reset() = 0;
    virtual size_t decode(unsigned char* src_array, size_t src_length, unsigned char* dest_array, size_t dest_length) = 0;
};

#endif	/* DECOMPRESSION_HPP */

