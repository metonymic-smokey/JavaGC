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
 * File:   AllocatedTypes.hpp
 * Author: vmb
 *
 * Created on February 19, 2014, 12:25 PM
 */

#ifndef ALLOCATEDTYPES_HPP
#define	ALLOCATEDTYPES_HPP


#include "../prims/jni.h"

typedef jint AllocatedTypeIdentifier;

#define ALLOCATED_TYPE_IDENTIFIER_UNKNOWN 0
#define ALLOCATED_TYPE_IDENTIFIER_UNKNOWN_MIRROR 1
#define ALLOCATED_TYPE_IDENTIFIER_FIRST_CUSTOM 2

/* mutex.hpp seems to include the klass.hpp. 
 * Since klass.hpp uses the AllocatedTypeIdentifier, 
 * the definition of it must be before the mutex.hpp include.
 */
#include "../runtime/mutex.hpp"
#include "../libadt/dict.hpp"
#include "../libadt/vectset.hpp"

class SymbolsWriter;

class AllocatedTypes : public AllStatic {
public:
    static void init(SymbolsWriter* writer);
    static void destroy();
    
    static AllocatedTypeIdentifier get_allocated_type_id(Klass* allocated_type);
    static AllocatedTypeIdentifier to_allocated_type(Klass* allocated_type);    
    static void remove_allocated_type(Klass* klass);
    
    static bool is_valid_allocated_type_id(AllocatedTypeIdentifier id);
    static bool has_custom_hash_code(Klass* klass);
private:
    static Monitor* lock;
    static Arena* arena;
    static AllocatedTypeIdentifier next_allocated_type_identifier;
    static SymbolsWriter* writer;
    static void write_symbols(AllocatedTypeIdentifier allocated_type_id, Klass* allocated_type);
    static int get_size(bool is_instance_klass, Klass* klass);
};

#endif	/* ALLOCATEDTYPES_HPP */

