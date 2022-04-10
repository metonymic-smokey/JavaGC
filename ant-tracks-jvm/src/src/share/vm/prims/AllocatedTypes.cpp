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
 * File:   AllocatedTypes.cpp
 * Author: vmb
 * 
 * Created on February 19, 2014, 12:25 PM
 */

#include "precompiled.hpp"
#include "AllocationTracing.hpp"
#include "AllocatedTypes.hpp"
#include "../memory/resourceArea.hpp"
#include "../runtime/thread.hpp"
#include "SymbolsWriter.hpp"
#include "AllocationTracingSynchronization.hpp"
#include "oops/instanceMirrorKlass.hpp"

AllocatedTypeIdentifier AllocatedTypes::next_allocated_type_identifier = 0;
Monitor* AllocatedTypes::lock = NULL;
Arena* AllocatedTypes::arena = NULL;
SymbolsWriter* symbols_writer = NULL;

void AllocatedTypes::init(SymbolsWriter* writer) {
    lock = new Mutex(Mutex::native, "Allocated Types Lock", true);
    next_allocated_type_identifier = ALLOCATED_TYPE_IDENTIFIER_FIRST_CUSTOM;
    arena = new(mtOther) Arena(mtOther);
    symbols_writer = writer;
    //TOOD reset ids in all Klass*
}

void AllocatedTypes::destroy() {
    symbols_writer = NULL;
    delete arena; arena = NULL;
    delete lock; lock = NULL;
}

AllocatedTypeIdentifier AllocatedTypes::get_allocated_type_id(Klass* allocated_type){
    if(allocated_type != NULL) {
         synchronized_if(lock, allocated_type->get_allocated_type_identifier() == ALLOCATED_TYPE_IDENTIFIER_UNKNOWN) {
            AllocatedTypeIdentifier allocated_type_id = next_allocated_type_identifier++;
            if(next_allocated_type_identifier < ALLOCATED_TYPE_IDENTIFIER_FIRST_CUSTOM) {
                fatal("Ran out of allocated type identifiers");
            }
            //printf("get: %d\n", allocated_type_id);
            allocated_type->set_allocated_type_identifier(allocated_type_id); 
            write_symbols(allocated_type_id, allocated_type);
            self_monitoring(1) {
                AllocationTracingSelfMonitoring::report_new_allocated_type();
            }
            
            if(PrintTraceObjects || PrintTraceObjectsSymbols) {
                AllocationTracing_log("type %i => %s", allocated_type_id, allocated_type->signature_name());
            }
        }
        return allocated_type->get_allocated_type_identifier();
    } else {
        return ALLOCATED_TYPE_IDENTIFIER_UNKNOWN;
    }
}

AllocatedTypeIdentifier AllocatedTypes::to_allocated_type(Klass* allocated_type){
    if(allocated_type != NULL){
        synchronized_if(lock, allocated_type->get_allocated_type_identifier() == ALLOCATED_TYPE_IDENTIFIER_UNKNOWN || allocated_type->oop_is_instanceMirror()) {
            AllocatedTypeIdentifier allocated_type_id = next_allocated_type_identifier++;
            if(next_allocated_type_identifier < ALLOCATED_TYPE_IDENTIFIER_FIRST_CUSTOM) {
                fatal("Ran out of allocated type identifiers");
            }
            allocated_type->set_allocated_type_identifier(allocated_type_id);
            if(!allocated_type->oop_is_instanceMirror()) { 
                // non-mirror classes just got their id, let's write them
                write_symbols(allocated_type_id, allocated_type);
                //printf("normal to: %d\n", allocated_type_id);
            } else {
                // mirror classes may have gotten a new id
                // instance mirrors are written later on
                // MW: Why? Probably ask PL about this, maybe he still remembers.
                //printf("mirror to: %d\n", allocated_type_id);
            }
            self_monitoring(1) {
                AllocationTracingSelfMonitoring::report_new_allocated_type();
            }
            
            if(PrintTraceObjects || PrintTraceObjectsSymbols) {
                AllocationTracing_log("type %i => %s", allocated_type_id, allocated_type->signature_name());
            }
        }
        return allocated_type->get_allocated_type_identifier();
    } else {
        return ALLOCATED_TYPE_IDENTIFIER_UNKNOWN;
    }
}

void AllocatedTypes::remove_allocated_type(Klass* klass) {
    if(klass->array_klass_or_null() != NULL) {
        remove_allocated_type(klass->array_klass_or_null());
    }
}

bool AllocatedTypes::has_custom_hash_code(Klass* klass) {
    if(klass == NULL) return false;
    if(klass->oop_is_array()) return false; //fast path for arrays
    
    Method* method;
    {
        ResourceMark _(Thread::current());
        static int vtable_index = -1;
        klassVtable* vtable = klass->vtable();
        for(int i = 0; vtable_index < 0 && i < vtable->length(); i++) {
            Method* method = vtable->method_at(i);
            Symbol* name = method->name();
            if(name->equals("hashCode")) {
                vtable_index = i;
            }
        }
        assert(vtable_index >= 0, "no hashCode function? there should be at least in j.l.Object");
    
        method = vtable->unchecked_method_at(vtable_index);
    }

    if(method == NULL) {
        static Symbol* name = SymbolTable::new_symbol("hashCode", Thread::current());
        static Symbol* sig = SymbolTable::new_symbol("()I", Thread::current());
        method = klass->lookup_method(name, sig);
    }
    
    assert(method != NULL, "could not find method");
    assert(method->name()->equals("hashCode"), "not the hashCode function");
    
    bool is_custom = !method->klass_name()->equals("java/lang/Object");
    return is_custom;
}

bool AllocatedTypes::is_valid_allocated_type_id(AllocatedTypeIdentifier id) {
    return id < next_allocated_type_identifier;
}

void AllocatedTypes::write_symbols(AllocatedTypeIdentifier allocated_type_id, Klass* allocated_type){
    bool is_instance_klass = allocated_type->oop_is_instance();
    assert(is_instance_klass || (!is_instance_klass && allocated_type->oop_is_array()), "What kind of klass is this?");
    
    ResourceMark rm(Thread::current());
    
    SymbolsType symbol;
    symbol.allocated_type_id = allocated_type_id;
    symbol.allocated_type = allocated_type;
    symbol.allocated_type_size = get_size(is_instance_klass, allocated_type);
        
    symbols_writer->write_symbols(symbol);
}

int AllocatedTypes::get_size(bool is_instance_klass, Klass* klass) {
    if(is_instance_klass) {
        return ((InstanceKlass*) klass)->size_helper() * HeapWordSize;
    } else {
        int header_size = ((ArrayKlass*) klass)->array_header_in_bytes();
        int elem_size = type2aelembytes(((ArrayKlass*) klass)->element_type());
        assert((header_size & 0xFF) == header_size, "header_size does not fit within one byte");
        assert((elem_size & 0xFF) == elem_size, "elem_size does not fit within one byte");
        return (header_size << 8) | (elem_size << 0);
    } 
}

