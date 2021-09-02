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
 * File:   AllocationTracingIO.cpp
 * Author: vmb
 * 
 * Created on February 25, 2014, 1:13 PM
 */

#include "precompiled.hpp"
#include "AllocationTracingIO.hpp"
#include <time.h>
#include "AllocationTracingSynchronization.hpp"

#include <cstdio>

#ifdef _WINDOWS
#define snprintf _snprintf
#endif

jint AllocationTracingIO::identification_header_size = 0;
jint* AllocationTracingIO::identification_header = NULL;

AllocationTracingIO::AllocationTracingIO(const char* path_to_file, size_t max_files) {
    init_header();
    lock = new Monitor(Mutex::native, "Event Writer Lock", true);
    _path_to_file = path_to_file;
    _max_files = MAX2((size_t) 1, max_files);
    _cur_file_index = 0;
    _total_bytes_written = 0;
    _file = NULL;
    //init() //DO NOT DO THIS HERE! (virtual functions are called)
}

void AllocationTracingIO::init() {
    create_file();
}

void AllocationTracingIO::init_header() {
    critical_if(identification_header_size == 0) {
        identification_header_size = 42;
        identification_header = (jint*) calloc(sizeof(jint), identification_header_size);
        for(int i = 0; i < identification_header_size; i++) {
            *(identification_header + i) = rand();
        }
    }
}

AllocationTracingIO::~AllocationTracingIO() {
    close();
}


void AllocationTracingIO::create_file(){
    /* bool file_exists; */
    /* char* file_name = get_file_name(_cur_file_index % _max_files); */
    /* FILE *file = fopen(file_name, "r"); */
    /* if (file == NULL) file_exists = false; */
    /* else { */
    /*     file_exists = true; */ 
    /*     fclose(file); */
    /* } */
    /* if(file_exists){ */
    /*     _file = fopen(file_name, "wb"); */
    /* } else { */
    /*     _file = fopen(file_name, "w+b"); */
    /* } */
    /* free(file_name); */
    char* file_name = get_file_name(_cur_file_index % _max_files);
    char fileName [1000];
    sprintf(fileName, "%s_%ld", file_name, time(NULL));
    _file = fopen(fileName, "w+b");
    free(file_name);
    guarantee(_file != NULL, "Could not open file!");
    _total_bytes_written = 0;

    write_global_header();
}

void AllocationTracingIO::write_global_header() {
    assert(sizeof(jint) == 4, "would be difficult when parsing otherwise");
    const jint MAGIC = 0xC0FFEE;
    write(&MAGIC, sizeof(jint));
    write(&identification_header_size, sizeof(jint));
    write(identification_header, sizeof(jint) * identification_header_size);
    jint file_type = get_file_type();
    write(&file_type, sizeof(jint));
    write_header();
}

char* AllocationTracingIO::get_file_name(size_t index) {
    char* format = (char*) (_max_files > 1 ? "%s_%i" : "%s");
    size_t len = snprintf(NULL, 0, format, _path_to_file, index);
    char* file_name = (char*) calloc(sizeof(char), len + 1);
    snprintf(file_name, len + 1, format, _path_to_file, index);
    return file_name;
}

void AllocationTracingIO::write(const void* data, size_t length) {
    size_t bytes_written = fwrite(data, sizeof(char), length, _file);
    guarantee(bytes_written == length, "Could not write the entire data!");
    _total_bytes_written += bytes_written;
}

void AllocationTracingIO::flush() {
    synchronized(lock) {
        int result = fflush(_file);
        guarantee(result == 0, "Could not flush file!");
    }
}

void AllocationTracingIO::close() {
    synchronized(lock) {
        int result = fclose(_file);
        guarantee(result == 0, "Could not close file!");
        _file = NULL;
    }
}

void AllocationTracingIO::reset() {
    synchronized(lock) {
        close();
        _cur_file_index++;
        create_file();
    }
}
