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
 * File:   EventRootPointerList.cpp
 * Author: Elias Gander
 *
 */

#include "precompiled.hpp"
#include "AllocationTracingSelfMonitoring.hpp"
#include "EventRootPointerList.hpp"
#include "EventsRuntime.hpp"

intptr_t EventRootPointerList::get_next(int i, RootInfo** info) {
    assert(i < MAX_ROOTS_CAPACITY, "i too large");
    *info = info_list + i;
    return (intptr_t) addr_list[i];
}
    
void EventRootPointerList::add(intptr_t p){
    if(top >= MAX_ROOTS_CAPACITY) {
        EventsRuntime::fire_gc_root_ptrs(this);
        clear();
    } 
       
    assert(top < MAX_ROOTS_CAPACITY, "top too large");
    addr_list[top] = p;
}

void EventRootPointerList::add_class_loader(intptr_t p, char* loader_name) {
    add(p);
    info_list[top] = RootInfo(CLASS_LOADER_ROOT);
    info_list[top].string = loader_name;
    top++;
    words += 2 + 1 + 1 + ((int)((strlen(loader_name) % 4 == 0) ? strlen(loader_name)/4 : strlen(loader_name)/4 + 1));
}

void EventRootPointerList::add_class(intptr_t p, int class_id) {
    add(p);
    info_list[top] = RootInfo(class_id);
    top++;
    words += 2 + (1 + 1);
}

void EventRootPointerList::add_static_field(intptr_t p, int class_id, int offset) {
    add(p);
    info_list[top] = RootInfo(class_id, offset, STATIC_FIELD_ROOT);
    top++;
    words += 2 + (1 + 1 + 1);
}

void EventRootPointerList::add_local_variable(intptr_t p, long thread_id, int class_id, int method_id, int slot) {
    add(p);
    info_list[top] = RootInfo(thread_id, class_id, method_id, slot);
    top++;
    words += 2 + (1 + 2 + 1 + 1 + 1);
}

void EventRootPointerList::add_vm_internal_thread_data(intptr_t p, long thread_id) {
    add(p);
    info_list[top] = RootInfo(thread_id, VM_INTERNAL_THREAD_DATA_ROOT);
    top++;
    words += 2 + (1 + 2);
}

void EventRootPointerList::add_code_blob(intptr_t p, int class_id, int method_id) {
    add(p);
    info_list[top] = RootInfo(class_id, method_id, CODE_BLOB_ROOT);
    top++;
    words += 2 + (1 + 1 + 1);
}

void EventRootPointerList::add_jni_local(intptr_t p, long thread_id) {
    add(p);
    info_list[top] = RootInfo(thread_id, JNI_LOCAL_ROOT);
    top++;
    words += 2 + (1 + 2);
}

void EventRootPointerList::add_jni_global(intptr_t p, bool is_weak) {
    add(p);
    info_list[top] = RootInfo(is_weak);
    top++;
    words += 2 + (1 + 1);
}

void EventRootPointerList::add_other(intptr_t p, RootType root_type) {
    add(p);
    info_list[top] = RootInfo(root_type);
    top++;
    words += 2 + (1);
}

void EventRootPointerList::add_debug(intptr_t p, char* vm_call) {
    add(p);
    info_list[top] = RootInfo(DEBUG_ROOT);
    info_list[top].string = vm_call;    
    top++;
    words += 2 + 1 + 1 + ((int)((strlen(vm_call) % 4 == 0) ? strlen(vm_call)/4 : strlen(vm_call)/4 + 1));
}

int EventRootPointerList::get_size() {
    return top;
}

int EventRootPointerList::get_words() {
    return words;
}

void EventRootPointerList::clear() {
    top = 0;
    words = 0;
}

void EventRootPointerList::flush() {
    if(get_size() != 0) {
        EventsRuntime::fire_gc_root_ptrs(this);
        clear();
    }
}

bool EventRootPointerList::is_full() {
    return top == MAX_ROOTS_CAPACITY;
}
