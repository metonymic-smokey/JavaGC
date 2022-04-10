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
 * File:   AllocationSites.hpp
 * Author: Verena Bitto
 *
 * Created on November 26, 2013, 10:36 AM
 */

#ifndef ALLOCATIONSITES_HPP
#define	ALLOCATIONSITES_HPP


#include "../prims/jni.h"
#include "AllocationTracingIO.hpp"
#include "../runtime/mutex.hpp"
#include "../libadt/dict.hpp"
#include "../libadt/vectset.hpp"
#include "Map.hpp"

class Method;
class ciMethod;
class SymbolsWriter;

typedef jint AllocationSiteIdentifier;
#define SIZE_OF_ALLOCATION_SITE_IDENTIFIER_SMALL 2
#define SIZE_OF_ALLOCATION_SITE_IDENTIFIER_BIG 3
#define ALLOCATION_SITE_IDENTIFIER_UNKNOWN 0
#define ALLOCATION_SITE_IDENTIFIER_VM_INTERNAL 1
#define ALLOCATION_SITE_IDENTIFIER_VM_GC 2
#define ALLOCATION_SITE_IDENTIFIER_VM_GC_DEAD_SPACE 3
#define ALLOCATION_SITE_IDENTIFIER_VM_GC_SCAVENGE_ZOMBIE 4
#define ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_OBJECT__CLONE 5
#define ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_STRING__INTERN 6
#define ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_IO_OBJECT_INPUT_STREAM__ALLOCATE_NEW_OBJECT 7
#define ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_IO_OBJECT_INPUT_STREAM__ALLOCATE_NEW_ARRAY 8
#define ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_REFLECT_ARRAY__NEW_ARRAY 9
#define ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_REFLECT_ARRAY__NEW_MULTI_ARRAY 10
#define ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_REFLECT_CONSTRUCTOR__NEW_INSTANCE 11
#define ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_REFLECT_METHOD__INVOKE 12
#define ALLOCATION_SITE_IDENTIFIER_TLAB_FILLER 13
#define ALLOCATION_SITE_IDENTIFIER_ARRAY_FILLER 14
#define ALLOCATION_SITE_IDENTIFIER_OBJECT_FILLER 14
#define ALLOCATION_SITE_IDENTIFIER_FIRST_CUSTOM 15

#define is_special_allocation_site(allocation_site) ((allocation_site) < ALLOCATION_SITE_IDENTIFIER_FIRST_CUSTOM)
#define is_big_allocation_site(allocation_site) ((allocation_site) >= 0x800000)
#define is_small_allocation_site(allocation_site) ((allocation_site) < 0x800000)

struct AllocationSiteString {
    AllocationSiteString() : length(0) {}
    char *string;
    size_t length;
};

struct CallSite {
    Method* method;
    int bytecode_index;
};

struct CallSiteArray {
    size_t length;
    CallSite* elements;
};

struct AllocationSiteInfo {
    CallSiteArray sites;
    AllocatedTypeIdentifier allocated_type_id;
    bool savable;
};

class CallSiteIterator : public StackObj {
public:
    virtual int count() = 0;
    virtual void reset() = 0;
    virtual bool has_next() = 0;
    virtual CallSite next() = 0;
};

class AllocationSites : public AllStatic {
public:
    static void init(SymbolsWriter* writer);
    static void destroy();
    
    static AllocationSiteIdentifier method_to_allocation_site(CallSiteIterator* call_sites, Klass* allocated_type, bool relax_expected_allocated_type = false, bool C2_ALLOC_FAST_DEVIANT = false);
    static void check_allocation_site(AllocationSiteIdentifier allocation_site_id, Klass* allocated_type);
    
    static AllocationSiteIdentifier allocation_site_to_allocation_site_with_alternate_type(AllocationSiteIdentifier allocation_site, Klass* allocated_type);
    
    static void remove_allocation_site(Method* allocation_site_method);
    static void remove_allocation_site(AllocationSiteIdentifier allocation_site);
    
    static void save_unloaded_allocation_sites(Klass* unloadedKlasses);
    
    static bool is_consistent(AllocationSiteIdentifier allocation_site);
    static void check_consistency();
    
    static size_t get_depth(AllocationSiteIdentifier id);
    static bool equals(AllocationSiteIdentifier id1, AllocationSiteIdentifier id2, bool ignore_allocated_types);
    
private:
    static Monitor* lock;
    static Arena* arena;
    static Arena* arena_temp;
    static Arena* arena_elements;
    static Arena* arena_dictionary;
    static bool is_in_yellow_zone;
    static NavigableMap<AllocationSiteString, AllocationSiteIdentifier>* dictionary;
    static Dict* info_to_id;
    static Dict* id_to_info;
    static AllocationSiteIdentifier next_allocation_site_identifier_small, next_allocation_site_identifier_big;
    static SymbolsWriter* symbols_writer;
    
    static void* allocation_site_info_to_void_ptr(AllocationSiteInfo* site);
    static AllocationSiteIdentifier allocation_site_id_from_void_ptr(void* value);
    static AllocationSiteInfo* allocation_site_info_from_void_ptr(void* value);
    static void* allocation_site_id_to_void_ptr(AllocationSiteIdentifier id);
    static bool equals(AllocationSiteInfo* info1, AllocationSiteInfo* info2, bool ignore_allocated_types);
    static int32 compare_keys(const void *key1, const void *key2);
    static int32 hash_key(const void* key);
    static void write_symbols(AllocationSiteIdentifier allocation_site_id, char* signature);
    static void write_symbols(AllocationSiteIdentifier allocation_site_id, AllocationSiteInfo* allocation_site_info);
    static void log(AllocationSiteIdentifier id, AllocationSiteInfo* info, Klass* allocated_type, bool patched = false);
    static char* mystrcat(char* dest, char* src);
    static AllocationSiteString CallSitesToString(CallSiteIterator* call_sites, Arena *arena, bool fittingBuff = false);
};

class SingleCallSiteIterator : public CallSiteIterator {
private:
    CallSite site;
    bool returned;
public:
    SingleCallSiteIterator(Method* method, int bytecode_index) {
        site.method = method;
        site.bytecode_index = bytecode_index;
        returned = false;
    }
    
    int count() {
        return 1;
    }
    
    void reset() {
        returned = false;
    }
    
    bool has_next() {
        return !returned;
    }
    
    CallSite next() {
        returned = true;
        return site;
    }
};

#endif	/* ALLOCATIONSITES_HPP */

