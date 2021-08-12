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
 * File:   AllocationTracingIO.hpp
 * Author: vmb
 *
 * Created on February 25, 2014, 1:13 PM
 */

#ifndef ALLOCATIONTRACINGIO_HPP
#define	ALLOCATIONTRACINGIO_HPP


#include <stdio.h>
//include mutex.hpp instead of memory/allocation.hpp here because this one seems to work - the other results in a link error when loading release builds
//don't know why ...
#include "runtime/mutex.hpp"
#include "prims/jni.h"

#define FILE_TYPE_SYMBOLS 0
#define FILE_TYPE_TRACE 1
#define FILE_TYPE_CLASS_DEFINITIONS 2

class AllocationTracingIO : public CHeapObj<mtInternal> {
private:
    static jint identification_header_size;
    static jint* identification_header;
public:
    AllocationTracingIO(const char* path_to_file, size_t max_files = 1);
    virtual ~AllocationTracingIO();
    inline size_t size() { return _total_bytes_written; }
    inline size_t get_max_file_count() { return _max_files; }
    virtual void flush();
    virtual void close();
    virtual void reset();
    char* get_file_name() { return get_file_name(get_cur_file_index()); }
protected:
    Monitor* get_lock() { return lock; }
    void init();
    void write(const void* data, size_t size);
    virtual int get_file_type() = 0;
    virtual void write_header() = 0;
    size_t get_cur_file_index() { return _cur_file_index; }
private:
    static void init_header();
    void create_file();
    void write_global_header();
    char* get_file_name(size_t index);
    FILE* _file;
    Monitor* lock;
    const char* _path_to_file;
    size_t _max_files;
    size_t _cur_file_index;
    size_t _total_bytes_written;
};

#endif	/* ALLOCATIONTRACINGIO_HPP */

