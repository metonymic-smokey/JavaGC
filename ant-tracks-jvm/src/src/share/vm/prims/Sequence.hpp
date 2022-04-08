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
 * File:   Sequence.hpp
 * Author: Philipp Lengauer
 *
 * Created on September 30, 2014, 2:09 PM
 */

#ifndef SEQUENCE_HPP
#define	SEQUENCE_HPP

#include "utilities/globalDefinitions.hpp"
#include "memory/allocation.hpp"

//#define USE_MANUAL_MEMCPY
//#define USE_MANUAL_MEMCMP
//#define SEQUENCE_COMPLETE_HASH

typedef unsigned short sequence_metadata_primitive_t;

struct sequence_metadata_t {
    sequence_metadata_primitive_t length    : sizeof(sequence_metadata_primitive_t) * 8 - 1;
    sequence_metadata_primitive_t relocated : 1;
};

class Sequence {
private:
    sequence_metadata_t metadata;
    unsigned char* data;
    
    static bool is_value_supported();
    static bool can_be_value(int length);
    bool is_value();
    
    unsigned char* get_addr_assume_adjacent(int index);
    unsigned char* get_addr_assume_anywhere(int index);
    unsigned char* get_addr(int index);
    
    static Sequence* create_as_value(int length, unsigned char* array);
    static Sequence* create_as_obj(Arena* arena, int length, unsigned char* array, bool must_own_array);
public:
    static Sequence* create_empty(Arena* arena);
    static Sequence* create(Arena* arena, int length, unsigned char* array, bool must_own_array);
    static Sequence* concat(Arena* arena, Sequence* sequence1, Sequence* sequence2);

    void* operator new(size_t _, Arena* arena, int length, bool must_own_array);
    Sequence(int length, unsigned char* array, bool must_own_array);
    
    Sequence* operator &() { return can_be_value(metadata.length) ? create_as_value(metadata.length, get_addr(0)) : this; }
    
    int get_length();
    unsigned char get(int index);
    
    Sequence* trim(Arena* arena, int new_length, bool must_own_array);
    int copy_to(unsigned char* dest);
    int get_index_of_first_mismatch(Sequence* that);
    bool starts_with(Sequence* prefix);
    
    int hash();
    bool equals(Sequence* that);
    int compare(Sequence* that);
    int compareByLength(Sequence* that);
};

#ifdef USE_MANUAL_MEMCPY
#define memcpy_approx(type, _d, _s, _s_end)     \
do {                                            \
    type* __d     = (type*) (_d);               \
    type* __s     = (type*) (_s);               \
    type* __s_end = (type*) (_s_end);           \
    while(__s < __s_end) *(__d++) = *(__s++);   \
    _s = (unsigned char*) (__s - 1);            \
    _d = (unsigned char*) (__d - 1);            \
} while(0)                                                      

#define memcpy(_dest, _src, _length)                                                    \
do {                                                                                    \
    unsigned char* __dest = (unsigned char*) (_dest);                                   \
    unsigned char* __src  = (unsigned char*) (_src);                                    \
    int __length = (int) (_length);                                               \
    unsigned char* __src_end = __src + __length;                                        \
    if(sizeof( long) > sizeof(  int)) memcpy_approx(long , __dest, __src, __src_end);   \
    if(sizeof(  int) > sizeof(short)) memcpy_approx(int  , __dest, __src, __src_end);   \
    if(sizeof(short) > sizeof( char)) memcpy_approx(short, __dest, __src, __src_end);   \
    if(sizeof( char) == 1)            memcpy_approx(char , __dest, __src, __src_end);   \
    else                              memcpy(__dest, __src, __src_end - __src);         \
} while(0)
#endif

#ifdef USE_MANUAL_MEMCMP
#define memcmp(_dest, _src, _length) (memcmp(_dest, _src, _length))
#endif

inline unsigned char* Sequence::get_addr_assume_adjacent(int index) { return ((unsigned char*) this) + sizeof(metadata) + index; }

inline unsigned char* Sequence::get_addr_assume_anywhere(int index) { return data + index; }
    
inline unsigned char* Sequence::get_addr(int index) {
    unsigned char* addr;
    if(metadata.relocated) {
        addr = get_addr_assume_anywhere(index);
    } else {
        addr = get_addr_assume_adjacent(index);
    }
    return addr;
}

#define get_array_of(thiz) ((thiz)->is_value() ? ((unsigned char*) &(thiz)) + 1 : (thiz->get_addr(0)))

inline bool Sequence::is_value_supported() {
    return (~ARENA_ALIGN_M1 & 1) == 0 && sizeof(intptr_t) == sizeof(Sequence*);
}

inline bool Sequence::can_be_value(int length) {
    return is_value_supported() && length <= MIN2((int) sizeof(Sequence*) - 1, (1 << 7) - 1);
}

inline bool Sequence::is_value() {
    return is_value_supported() && ((((intptr_t) this) & 1) != 0);
}

inline Sequence* Sequence::create_as_value(int length, unsigned char* array) {
    assert(can_be_value(length), "internal error");
    const intptr_t one = 1;
    Sequence* obj = (Sequence*) (((*((intptr_t*) array) & ((one << (length * 8)) - 1)) << (1 * 8)) | (length << 1) | 1);
    assert(obj->is_value(), "what sorcery is this?");
    return obj;
}

inline Sequence* Sequence::create_as_obj(Arena* arena, int length, unsigned char* array, bool must_own_array) {
    Sequence* obj = new(arena, length, must_own_array) Sequence(length, array, must_own_array);
    assert(!obj->is_value(), "what sorcery is this?");
    return obj;
}

inline Sequence* Sequence::create_empty(Arena* arena) {
    intptr_t data = 0;
    return create(arena, 0, (unsigned char*) &data, true);
}

inline Sequence* Sequence::create(Arena* arena, int length, unsigned char* array, bool must_own_array) {
    Sequence* obj;
    if(can_be_value(length)) {
        obj = create_as_value(length, array);
    } else {
        obj = create_as_obj(arena, length, array, must_own_array);
    }
#ifdef ASSERT
    assert(obj->get_length() == length, "length incorrect");
    for(int i = 0; i < length; i++) {
        assert(obj->get(i) == array[i], "element incorrect");
    }
    Sequence same(length, array, false);
    assert(obj->equals(&same), "equals does not yield the correct result");
    assert((&same)->equals(obj), "equals does not yield the correct result");
    assert(same.get_length() == length, "length incorrect");
    assert(same.equals(obj), "equals does not yield the correct result");
#endif
    return obj;
}

inline Sequence* Sequence::concat(Arena* arena, Sequence* sequence1, Sequence* sequence2) {
    int length1 = sequence1->get_length(), length2 = sequence2->get_length();
#ifndef _WINDOWS
    unsigned char data[length1 + length2];
#else
    unsigned char* data = (unsigned char*) malloc(sizeof(unsigned char) * (length1 + length2));
#endif
    memcpy(data + 0      , get_array_of(sequence1), length1);
    memcpy(data + length1, get_array_of(sequence2), length2);
    Sequence* result = create(arena, length1 + length2, data, true);
#ifndef _WINDOWS
#else
    free(data); data = NULL;
#endif
    return result;
}

inline void* Sequence::operator new(size_t _, Arena* arena, int length, bool must_own_array) {
    size_t size;
    if(must_own_array) {
        size = sizeof(sequence_metadata_t) + length * sizeof(unsigned char);
    } else {
        size = sizeof(Sequence);
    }
    return arena->Amalloc(size);
}

inline Sequence::Sequence(int length, unsigned char* array, bool must_own_array) {
    assert((void*) &metadata == (void*) this, "internal error");
    metadata.length = length;
    unsigned char* addr = get_addr_assume_adjacent(0);
    if(must_own_array) {
        if(addr == array) {
            //nothing to do
        } else {
            memcpy(addr, array, length);
        }
        metadata.relocated = 0;
    } else {
        if(addr == array) {
            metadata.relocated = 0;
        } else {
            data = array;
            metadata.relocated = 1;
            assert(array == get_addr_assume_anywhere(0), "internal error");
        }
    }
    assert(!is_value(), "internal error");
    assert(get_length() == length, "internal error");
#ifdef ASSERT
    Sequence* thiz = this;
    for(int i = 0; i < get_length(); i++) {
        assert(array[i] == get(i), "internal error");
    }
    assert(memcmp(get_array_of(thiz), array, length) == 0, "");
#endif
}

inline int Sequence::get_length() {
    int length;
    if(is_value()) {
        length = (((intptr_t) this) & 0xFF) >> 1;
    } else {
        length = metadata.length;
    }
    return length;
}

inline unsigned char Sequence::get(int index) {
    unsigned char result;
    if(is_value()) {
        result = (unsigned char) ((((intptr_t) this) >> ((index + 1) * 8)) & 0xFF);
    } else {
        result = *get_addr(index);
    }
    return result;
}

inline Sequence* Sequence::trim(Arena* arena, int new_length, bool must_own_array) {
    Sequence* thiz = this;
    return create(arena, MIN2(get_length(), new_length), get_array_of(thiz), must_own_array);
}

inline int Sequence::copy_to(unsigned char* dest) {
    Sequence* thiz = this;
    int length = get_length();
    memcpy(dest, get_array_of(thiz), length);
    return length;
}

inline int Sequence::get_index_of_first_mismatch(Sequence* that) {
    Sequence* thiz = this;
    unsigned char* thiz_array = get_array_of(thiz);
    unsigned char* that_array = get_array_of(that);
    int length = MIN2(thiz->get_length(), that->get_length());
    for(int i = 0; i < length; i++) {
        if(thiz_array[i] != that_array[i]) {
            return i;
        }
    }
    return (int) length;
}

inline bool Sequence::starts_with(Sequence* prefix) {
    return prefix->get_length() <= get_length() && prefix->get_index_of_first_mismatch(this) == prefix->get_length();
}

inline int Sequence::hash() {
    int hash;

    int length = get_length();
    Sequence* thiz = this;
    unsigned char* array = get_array_of(thiz);
#ifdef SEQUENCE_COMPLETE_HASH 
    hash = (int) length;
    for(int index = 0; index < length; index++) {
        hash = (hash * 31) + array[index];
    }
#else
    intptr_t big_hash = *((intptr_t*) array);
    intptr_t mask = (((intptr_t) 1) << (length * 8)) - 1;
    big_hash = big_hash & mask; //this should not be necessary! or should it?
    if(sizeof(int) < sizeof(intptr_t)) {
        hash = (int) ((big_hash >> 00) ^ (big_hash >> 32));
    }
    hash = hash ^ length; //try get different hashes when sequences have different lengths but first bytes are the same
    const int some_big_prime_number = (1 << 19) - 1;
    hash = hash * some_big_prime_number;
#endif
    
    return hash;
}

inline bool Sequence::equals(Sequence* that) {
    if(this == that) {
        return true;
    } else if (this->get_length() == that->get_length()) {
        Sequence* thiz = this;
        return memcmp(get_array_of(thiz), get_array_of(that), get_length()) == 0;
    } else {
        return false;
    }
}

inline int Sequence::compare(Sequence* that) {
    if(this == that) {
        return 0;
    } else {
        Sequence* thiz = this;
        int this_length = thiz->get_length();
        int that_length = that->get_length();
        int diff = memcmp(get_array_of(thiz), get_array_of(that), MIN2(this_length, that_length));
        return (int) (diff != 0 ? diff : (this_length - that_length));
    }
}

inline int Sequence::compareByLength(Sequence* that) {
    if(this == that) {
        return 0;
    } else {
        Sequence* thiz = this;
        int this_length = thiz->get_length();
        int that_length = that->get_length();
        int diff = (int) (this_length - that_length);
        return diff != 0 ? diff : memcmp(get_array_of(thiz), get_array_of(that), MIN2(this_length, that_length));
    }
}

#undef get_array_of

#ifdef USE_MANUAL_MEMCPY
#undef memcpy
#endif

#ifdef USE_MANUAL_MEMCMP
#undef memcmp
#endif

#endif	/* SEQUENCE_HPP */

