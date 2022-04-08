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
 * File:   EventRootPointerList.hpp
 * Author: Elias Gander
 *
 */

#ifndef EVENTROOTPOINTERLIST_HPP
#define	EVENTROOTPOINTERLIST_HPP
    
#define MAX_ROOTS_CAPACITY 3    // TODO use 12 but adjust MAX_EVENT_SIZE
#define MAX_ROOTS_WORDS MAX_ROOTS_CAPACITY * (2 + 6)

enum RootType {
    // roots with additional info
    CLASS_LOADER_ROOT = 0,
    CLASS_ROOT = 1,
    STATIC_FIELD_ROOT = 2,
    LOCAL_VARIABLE_ROOT = 3,
    VM_INTERNAL_THREAD_DATA_ROOT = 4,
    CODE_BLOB_ROOT = 5,
    JNI_LOCAL_ROOT = 6,
    JNI_GLOBAL_ROOT = 7,
    
    // other root types for distinction
    CLASS_LOADER_INTERNAL_ROOT = 8,
    UNIVERSE_ROOT = 9,
    SYSTEM_DICTIONARY_ROOT = 10,
    BUSY_MONITOR_ROOT = 11,
    INTERNED_STRING_ROOT = 12,
    FLAT_PROFILER_ROOT = 13,
    MANAGEMENT_ROOT = 14,
    JVMTI_ROOT = 15,
    
    // for debugging -> info contains a string indicating the corresponding jvm call
    DEBUG_ROOT = 16
};

class RootInfo {
public:
    RootType type;
    int64_t first_double_word;
    int64_t second_double_word;
    int32_t last_word;
    
    char* string;

    RootInfo() {
        first_double_word = -1;
        second_double_word = -1;
        last_word = -1;
    }    

    RootInfo(int class_id) {
        type = CLASS_ROOT;
        first_double_word = -1;
        second_double_word = -1;
        last_word = class_id;
    }
    RootInfo(int class_id, int offset_or_method_id, RootType static_field_or_code_blob) {
        assert(static_field_or_code_blob == STATIC_FIELD_ROOT || static_field_or_code_blob == CODE_BLOB_ROOT, "ooops");
        type = static_field_or_code_blob;
        first_double_word = offset_or_method_id;
        first_double_word = first_double_word << 32;
        first_double_word |= ((int64_t)class_id & 0xffffffff);
        second_double_word = -1;
        last_word = -1;
    }
    RootInfo(long thread_id, RootType jni_local_or_thread) {
        assert(jni_local_or_thread == JNI_LOCAL_ROOT || jni_local_or_thread == VM_INTERNAL_THREAD_DATA_ROOT, "ooops");
        type = jni_local_or_thread;
        first_double_word = thread_id;
        second_double_word = -1;
        last_word = -1;
    }
    RootInfo(long thread_id, int class_id, int method_id, int slot) {
        type = LOCAL_VARIABLE_ROOT;
        first_double_word = thread_id;
        second_double_word = method_id;
        second_double_word = second_double_word << 32;
        second_double_word |= ((int64_t)class_id & 0xffffffff); // mask
        last_word = slot;
    } 
    
    RootInfo(bool is_weak) {
        type = JNI_GLOBAL_ROOT;
        first_double_word = -1;
        second_double_word = -1;
        last_word = is_weak;
    }
    
    RootInfo(RootType type) { 
        this->type = type;
        first_double_word = -1;
        second_double_word = -1;
        last_word = -1;
    };

    void get_class_info(int32_t* word) {
        *word = last_word;
    }
    void get_static_info(int64_t* double_word) {
        *double_word = first_double_word;
    }
    void get_local_variable_info(int64_t* first_double_word, int64_t* second_double_word, int32_t* last_word) {
        *first_double_word = this->first_double_word;
        *second_double_word = this->second_double_word;
        *last_word = this->last_word;
    }    
    void get_vm_internal_thread_data_info(int64_t* double_word) {
        *double_word = first_double_word;
    }
    void get_code_blob_info(int64_t* double_word) {
        *double_word = first_double_word;
    }
    void get_jni_local_info(int64_t* double_word) {
        *double_word = first_double_word;
    }
    void get_jni_global_info(int32_t* word) {
        *word = last_word;
    }
};

class EventRootPointerList : public CHeapObj<mtInternal> {    
private:
    int top;
    int words;
    intptr_t addr_list[MAX_ROOTS_CAPACITY];
    
    RootInfo info_list[MAX_ROOTS_CAPACITY];
    
    void add(intptr_t p);
    
public:
    EventRootPointerList() {
        top = 0;
        words = 0;
    }
    
    void add_class_loader(intptr_t p, char* loader_name);
    void add_class(intptr_t p, int class_id);
    void add_static_field(intptr_t p, int class_id, int offset);
    void add_local_variable(intptr_t p, long thread_id, int class_id, int method_id, int slot);
    void add_vm_internal_thread_data(intptr_t p, long thread_id);
    void add_code_blob(intptr_t p, int class_id, int method_id);
    void add_jni_local(intptr_t p, long thread_id);    
    void add_jni_global(intptr_t p, bool is_weak);    
    
    void add_other(intptr_t p, RootType type);
    
    void add_debug(intptr_t p, char* vm_call);
    
    int get_size();
    int get_words();
    intptr_t get_next(int i, RootInfo** info);
    void clear();
    void flush();
    bool is_full();
};

#endif	/* EVENTROOTPOINTERLIST_HPP */

