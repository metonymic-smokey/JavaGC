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
 * File:   QuyteTreeCompression.hpp
 * Author: Philipp Lengauer (original code by Günther Blaschek)
 *
 * Created on April 11, 2015, 10:49 AM
 */

#ifndef QUYTETREECOMPRESSION_HPP
#define	QUYTETREECOMPRESSION_HPP

#include "Compression.hpp"
#include "Decompression.hpp"

class QuyteTreeCompression : public Compression {
private:
    void* tempStorage;
    size_t tempStorageLength;
public:
    QuyteTreeCompression();
    ~QuyteTreeCompression();    

    void reset();
    Result encode(unsigned char* src_array, size_t src_length, unsigned char* dest_buffer, size_t dest_length);
    
    Decompression* getDecompressor();
};

class QuyteTreeDecompression : public Decompression {
private:
    void* tempStorage;
    size_t tempStorageLength;
public:
    QuyteTreeDecompression();
    ~QuyteTreeDecompression();    

    void reset();
    size_t decode(unsigned char* src_array, size_t src_length, unsigned char* dest_array, size_t dest_length);
    
private:
};

#endif	/* QUYTETREECOMPRESSION_HPP */

