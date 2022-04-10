/*
 * Copyright (c) 2016, 2017 dynatrace and/or its affiliates. All rights reserved.
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
 * File:   ClassDefinitionWriter.cpp
 * Author: Peter Feichtinger
 * 
 * Created on September 26, 2016, 3:08 PM
 */

#include "precompiled.hpp"
#include "ClassDefinitionWriter.hpp"
#include "AllocationTracingSynchronization.hpp"

//#define SPECIFY_NAME

ClassDefinitionWriter::ClassDefinitionWriter() : AllocationTracingIO(TraceObjectsClassDefinitionsFile) {
    this->init();
}

void ClassDefinitionWriter::write_header() {
    // For future use
    jint flags = 0;
    this->write(&flags, sizeof(jint));
    
#ifdef SPECIFY_NAME
    const char* trace = TraceObjectsTraceFile;
    size_t length = strlen(trace);
    int start = length - 1;
    while(start >= 0 && trace[start] != '/') start--;
    start++;
    this->write(trace + start, length - start + 1); // + 1 because of 0 byte
#else
    const char trace = '\0';
    this->write(&trace, 1);
#endif
}


int ClassDefinitionWriter::get_file_type() {
    return FILE_TYPE_CLASS_DEFINITIONS;
}

void ClassDefinitionWriter::write_symbol(SymbolsClassDefinition symbol) {
    synchronized(this->get_lock()) {
        size_t size_before = this->size();
        
        jbyte magic_byte = MAGIC_BYTE_CLASS_DEFINITION;
        this->write(&magic_byte, sizeof(jbyte));
        this->write(&symbol.allocated_type_id, sizeof(AllocatedTypeIdentifier));
        this->write(&symbol.length, sizeof(int));
        this->write(symbol.buffer, symbol.length);
        
        this->flush();
        
        self_monitoring(1) {
            AllocationTracingSelfMonitoring::report_class_definition_written(this->size() - size_before);
        }
    }
}

