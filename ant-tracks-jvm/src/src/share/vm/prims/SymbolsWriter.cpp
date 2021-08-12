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
 * File:   SymbolsWriter.cpp
 * Author: vmb
 * 
 * Created on February 25, 2014, 11:51 AM
 */

#include "precompiled.hpp"
#include "SymbolsWriter.hpp"
#include "AllocatedTypes.hpp"
#include "AllocationSites.hpp"
#include "oops/method.hpp"
#include "memory/resourceArea.hpp"
#include "AllocationTracingSynchronization.hpp"
#include "oops/instanceMirrorKlass.hpp"

// This const has to be changed EVERY TIME(!) the symbols format gets changed
#define SYMBOLS_VERSION 5

#define DEBUG false

#define ANCHOR_FLAG_INDEX 0
#define POINTERS_FLAG_INDEX 1
#define FRAGMENTED_HEAP_FLAG_INDEX 2
char const l = 'L';
char const semicolon = ';';
char const nul = ' ';

#ifdef _WINDOWS
#define snprintf _snprintf
#endif

//#define SPECIFY_NAME

SymbolsWriter::SymbolsWriter() : AllocationTracingIO(TraceObjectsSymbolsFile){
    init();
}

void SymbolsWriter::write_header() {
    int version = SYMBOLS_VERSION;
    write(&version, sizeof(jint) * 1);
    
    jint flags = 0;
    if(TraceObjectsInsertAnchors) {
        flags |= (1 << ANCHOR_FLAG_INDEX);
    }
    //if(!UseG1GC){
        if(TraceObjectsPointers) {
            flags |= (1 << POINTERS_FLAG_INDEX);
        }
    //}
    if (UseConcMarkSweepGC) {
        flags |= (1 << FRAGMENTED_HEAP_FLAG_INDEX);
    } else {
        flags &= ~(1 << FRAGMENTED_HEAP_FLAG_INDEX);
    }
    write(&flags, sizeof(jint));
    write(&HeapWordSize, sizeof(jint));
    
#ifdef SPECIFY_NAME
    const char* trace = TraceObjectsTraceFile;
    size_t length = strlen(trace);
    int start = length - 1;
    while(start >= 0 && trace[start] != '/') start--;
    start++;
    write(trace + start, length - start + 1); // + 1 because of 0 byte
#else
    const char trace = '\0';
    write(&trace, 1);
#endif
    
    for(jint cause_id = 0; cause_id < (int) GCCause::_last_gc_cause; cause_id++) {
        GCCause::Cause cause = static_cast<GCCause::Cause>(cause_id);
        jbyte magic_byte = MAGIC_BYTE_GC_CAUSE;
        const char* name = GCCause::to_string(cause);
        bool common = cause == GCCause::_allocation_failure
            || cause == GCCause::_adaptive_size_policy
            || cause == GCCause::_g1_inc_collection_pause
            || cause == GCCause::_cms_initial_mark
            || cause == GCCause::_cms_concurrent_mark
            || cause == GCCause::_cms_final_remark
            || cause == GCCause::_cms_sweeping
            || cause == GCCause::_no_cause_specified;
        jbyte kind = common ? 1 : 0;
        write(&magic_byte, sizeof(jbyte) * 1);
        write(&cause_id, sizeof(jint));
        write(name, (strlen(name) + 1) * sizeof(char));
        write(&kind, sizeof(jbyte));
    }
}

int SymbolsWriter::get_file_type() {
    return FILE_TYPE_SYMBOLS;
}

void SymbolsWriter::write_symbols(SymbolsSite symbol){
    ResourceMark rm(Thread::current());
    
    synchronized(get_lock()) {
        size_t size_before = size();
        
        jbyte magic_byte = MAGIC_BYTE_SITE;
        write(&magic_byte, sizeof(jbyte) * 1);
        if(is_big_allocation_site(symbol.allocation_site_id)){ //if big allocSite
            jshort hbyte_allocSite = symbol.allocation_site_id >> 8;
            write(&hbyte_allocSite, sizeof(jshort)); //write 2 highest bytes first
            write(&symbol.allocation_site_id, sizeof(jbyte) * 1); //then write lowbyte
        } else { //if small allocSite
            write(&symbol.allocation_site_id, SIZE_OF_ALLOCATION_SITE_IDENTIFIER_SMALL * 1);
        }
        
        write(&symbol.call_sites->length, sizeof(jint));
        for(size_t index = 0; index < symbol.call_sites->length; index++) {
            CallSite* site = symbol.call_sites->elements + index;
            char* signature;
            if(site->method != NULL) { 
                signature = site->method->name_and_sig_as_C_string();
            } else {
                const char* format = "$$Recursion.repeat_%i_last_frames_n_times()V";
                signature = (char*) Thread::current()->resource_area()->Amalloc(strlen(format) * 2);
                sprintf(signature, format, site->bytecode_index);
            }
            write(signature, sizeof(char) * strlen(signature));
            write(&site->bytecode_index, sizeof(jint) * 1);
        }
        
        write(&symbol.allocated_type_id, sizeof(AllocatedTypeIdentifier) * 1);
    
        flush();
        
        self_monitoring(1) {
            AllocationTracingSelfMonitoring::report_symbol_written(size() - size_before);
        }
    }
}

void SymbolsWriter::write_symbols(SymbolsSimpleSite symbol) {
    synchronized(get_lock()) {
        size_t size_before = size();

        jbyte magic_byte = MAGIC_BYTE_SIMPLE_SITE;
        write(&magic_byte, sizeof(jbyte) * 1);
        if(is_big_allocation_site(symbol.allocation_site_id)){ //if big allocSite
            jshort hbyte_allocSite = symbol.allocation_site_id >> 8;
            write(&hbyte_allocSite, sizeof(jshort)); //write 2 highest bytes first 
            write(&symbol.allocation_site_id, sizeof(jbyte) * 1); //then write lowbyte
        } else { //if small allocSite
            write(&symbol.allocation_site_id, SIZE_OF_ALLOCATION_SITE_IDENTIFIER_SMALL * 1);
        }
        
        write(symbol.signature, sizeof(char) * strlen(symbol.signature) + 1);
        write(&symbol.allocated_type_id, sizeof(AllocatedTypeIdentifier) * 1);
        flush();
        
        self_monitoring(1) {
            AllocationTracingSelfMonitoring::report_symbol_written(size() - size_before);
        }
    }
}

void SymbolsWriter::write_symbols(SymbolsType symbol){
    ResourceMark rm(Thread::current());

    synchronized(get_lock()) {
        size_t size_before = size();
        
        // Get super class. If super class has not been written yet, it gets written now
        AllocatedTypeIdentifier super_type_id = ALLOCATED_TYPE_IDENTIFIER_UNKNOWN;
        Klass* klass = symbol.allocated_type;   
        if(klass->super() != NULL) {
            // Make sure that the klass's super class has already been written by using AllocatedTypes::get_allocated_type_id
            super_type_id = AllocatedTypes::get_allocated_type_id(klass->super());
        }
        
        // Write magic byte
        jbyte magic_byte = MAGIC_BYTE_TYPE;
        write(&magic_byte, sizeof(jbyte) * 1);
        
        // Write ID
        write(&symbol.allocated_type_id, sizeof(AllocatedTypeIdentifier) * 1);
        
        // Write super class
        write(&super_type_id, sizeof(jint) * 1);
        
        // Write class name
        const char* signature = symbol.allocated_type->signature_name();      
        write(signature, strlen(signature));

        // Write type size
        write(&symbol.allocated_type_size, sizeof(jint) * 1);

        if(DEBUG) {
            printf("%d: %s (size: %d)\n", symbol.allocated_type_id, signature, symbol.allocated_type_size);
            fflush(stdout);
        }
        
        if(symbol.allocated_type->oop_is_instance()) {
            // Instance class case: Write fields
            magic_byte = MAGIC_BYTE_TYPE_FIELD_INFO;
            write(&magic_byte, sizeof(byte) * 1);
            write_fields((InstanceKlass*) symbol.allocated_type, true);
            
            magic_byte = MAGIC_BYTE_TYPE_SUPER_FIELD_INFO;
            InstanceKlass* super = (InstanceKlass*) symbol.allocated_type;
            do {
                super = super->superklass();
                if(super!=NULL) {
                    write(&magic_byte, sizeof(byte) * 1);
                    write_fields(super, true);
                }
            } while(super != NULL);     
        } else {
            // No additional info stored for arrays
        }
               
        // write class methods
        if(symbol.allocated_type->oop_is_instance()) {
            magic_byte = MAGIC_BYTE_TYPE_METHOD_INFO;
            write(&magic_byte, sizeof(byte) * 1);
            Array<Method*>* methods = ((InstanceKlass*)symbol.allocated_type)->methods();
            int count = methods->length();
            write(&count, sizeof(int));
            for(int i = 0; i < methods->length(); i++) {
                Method* m = methods->at(i);

                // write method idnum
                int idnum = (int)m->method_idnum();
                write(&idnum, sizeof(int));

                // write method name
                char* name = m->name()->as_C_string();
                write(name, strlen(name)*sizeof(char));
                write(&nul, sizeof(char));

                
                int localCount = m->localvariable_table_length();
                write(&localCount, sizeof(int));
                if(m->has_localvariable_table()) {
                    LocalVariableTableElement* elem = m->localvariable_table_start();
                    for(int j = 0; j < localCount; j++) {
                        int slot = elem->slot;
                        write(&slot, sizeof(int));
                        char* localName = m->constants()->symbol_at((int)(elem->name_cp_index))->as_C_string();
                        write(localName, strlen(localName)*sizeof(char));
                        write(&nul, sizeof(char));
                        elem++;
                    }
                }
                
                // TODO superclass methods too?
            }
        }
        
        flush();
        
        self_monitoring(1) {
            AllocationTracingSelfMonitoring::report_symbol_written(size() - size_before);
        }
    }
}

void SymbolsWriter::write_fields(InstanceKlass* type, bool reverse) {
    instanceKlassHandle handle (type);
    
    // Taken from heapDumper.cpp
    // Runs through all fields, including fields in superclasses
    
    jint count = handle->java_fields_count(); // Seen in FieldStream#length
    int i = 0;
    char bufSig[256];
    char bufName[256];
    // Count of fields
    write(&count, sizeof(jint) * 1);
    if(DEBUG) {
        printf("Class: %s\n# of fields: %d\n", handle->signature_name(), count);
    }
    
    // Signature: 1. handle to class 2. only local fields (true) or include super class fields (false) 3. only classes (false) or also interfaces (true)
    for (FieldStream fld(handle, true, true); !fld.eos(); fld.next()) {
      // 1. Offset     
      jint offset = fld.offset();
      write(&offset, sizeof(jint) * 1);
      
      // 2. Type signature
      Symbol* sig = fld.signature();                 
      sig->as_C_string(bufSig, sizeof(bufSig));
      int len = (int)strlen(bufSig);     
      write(bufSig, sizeof(char) * len);
      write(&nul, sizeof(char) * 1);
      
      // 3. Field name
      Symbol* name = fld.name();
      name->as_C_string(bufName, sizeof(bufName));
      len = (int)strlen(bufName);
      write(bufName, sizeof(char) * len);        
      write(&nul, sizeof(char) * 1);
      
      // 4. Access flags
      jint flags = fld.access_flags().get_flags();
      write(&flags, sizeof(jint) * 1);
      
      i++;
      
      if(DEBUG) {
        printf("Field (Offset %d): %s %s (Access = %d)\n", fld.offset(), bufSig, bufName, flags);
      }  
    }
    
    char assertMessage[100];
    sprintf(assertMessage, "Field count (%d) must match with number of visited fields (%d)", count, i);
    assert(i == count, assertMessage);
    
    // Verena Version
    /*
    int count = handle->java_fields_count();
    int i = reverse ? count - 1 : 0;
    FieldInfo* info;
    ConstantPool* constants = handle->constants();
        
    char bufSig[256];
    char bufName[256];
    write(&count, sizeof(jint) * 1);                 
    
    if(DEBUG) {
        printf("Class: %s\n# of fields: %d\n", handle->signature_name(), count);
    }
    
    while(reverse? i >= 0 : i < count){
        int idx = i;
        write(&idx, sizeof(jint) * 1);                 // used as offset
        
        info = handle->field_info(i);
        reverse ? i-- : i++;
        
        Symbol* signature = info->signature(constants);                
        signature->as_klass_external_name(bufSig, sizeof(bufSig));
        int len = (int)strlen(bufSig);     
        write(bufSig, sizeof(char) * len);
                
        Symbol* name = info->name(constants);
        name->as_klass_external_name(bufName, sizeof(bufName));
        
        len = (int)strlen(bufName);
        write(bufName, sizeof(char) * len);        
        write(&nul, sizeof(char) * 1);
        
        int flags = info->access_flags();
        write(&flags, sizeof(jshort) * 1);
        
        if(DEBUG) {
          printf("Field %d (Offset %d):\t%s\t%s (Access = %d)\n", idx, info->offset(), bufSig, bufName, flags);
        }
    }
    */
}