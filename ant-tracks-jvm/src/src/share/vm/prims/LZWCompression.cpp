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
 * File:   Encoder.cpp
 * Author: Philipp Lengauer
 * 
 * Created on December 3, 2013, 1:49 PM
 */

#include "precompiled.hpp"
#include "LZWCompression.hpp"
#include "VarInt.hpp"
#include "Sequence.hpp"
#include "HashMap.hpp"
#ifndef _WINDOWS
#include "ListMap.hpp"
#endif
#include "PrefixTreeMap.hpp"
#include "LZWDecompression.hpp"

class ClearableArena : public Arena {
public:
    ClearableArena() : Arena(mtOther) {}
    
    ClearableArena(size_t init_size) : Arena(mtOther, init_size) {}
    
    void clear() {
        if(_first != NULL) {
            if(_first->next() != NULL) {
                _first->next_chop();
            }
            _chunk = _first;
            _hwm = _chunk->bottom();
            _max = _chunk->top();
            set_size_in_bytes(_first->length());
        }
    }
};

bool equalsSequence(Sequence* sequence1, Sequence* sequence2) {
    return sequence1->equals(sequence2);
}

int hashSequence(Sequence* sequence) {
    return sequence->hash();
}

int compareSequence(Sequence* sequence1, Sequence* sequence2) {
    return sequence1->compare(sequence2);
}

int compareSequenceByLength(Sequence* sequence1, Sequence* sequence2) {
    return sequence1->compareByLength(sequence2);
}

int compareEntry(MapEntry<Sequence*, uint> entry1, MapEntry<Sequence*, uint> entry2) {
    return compareSequence(entry1.get_key(), entry2.get_key());
}

int compareEntryByLength(MapEntry<Sequence*, uint> entry1, MapEntry<Sequence*, uint> entry2) {
    return compareSequenceByLength(entry1.get_key(), entry2.get_key());
}

bool isSequenceFloorOf(Sequence* floor, Sequence* sequence) {
    return sequence->starts_with(floor);
}

bool isEntryFloorOf(MapEntry<Sequence*, uint> floor, MapEntry<Sequence*, uint> entry) {
    return isSequenceFloorOf(floor.get_key(), entry.get_key());
}

size_t getSequenceClass(Sequence* sequence) {
    return sequence->get_length() - 1;
}

Sequence* reduceSequence(Arena* arena, Sequence* sequence, size_t clazz) {
    return sequence->trim(arena, (int) clazz + 1, false);
}

size_t getSequenceLength(Sequence* sequence) {
    return sequence->get_length();
}

unsigned char getSequenceElement(Sequence* sequence, int index) {
    return sequence->get(index);
}

size_t getSequenceNibbleLength(Sequence* sequence) {
    return sequence->get_length() * 2;
}

unsigned char getSequenceNibbleElement(Sequence* sequence, int index) {
    const char nibble_mask = ((1 << 4) - 1);
    int byte_index = index / 2;
    unsigned char c = sequence->get(byte_index);
    int hibble_index = index % 2;
    unsigned char nibble = (c >> (((2-1) - hibble_index) * 4)) & nibble_mask;
    return nibble;
}

size_t getSequenceHibbleLength(Sequence* sequence) {
    return sequence->get_length() * 4;
}

unsigned char getSequenceHibbleElement(Sequence* sequence, int index) {
    const char hibble_mask = ((1 << 2) - 1);
    int byte_index = index / 4;
    unsigned char c = sequence->get(byte_index);
    int hibble_index = index % 4;
    unsigned char hibble = (c >> (((4-1) - hibble_index) * 2)) & hibble_mask;
    return hibble;
}

int hashChar(unsigned char c) {
    return (int) c;
}

bool equalsChar(unsigned char c1, unsigned char c2) {
    return c1 == c2;
}

Map<unsigned char, PrefixTreeMapNodeID>* createMap(Arena* arena) {
    return new(arena) HashMap<unsigned char, PrefixTreeMapNodeID, hashChar, equalsChar>(arena, 8);
}

size_t getMapSize(size_t id) {
    return id == 0 ? (1 << 8) : (size_t) (2.0 * K / id);
}

LZWCompression::LZWCompression(int collection_strategy) {
    arena_elements = new(mtOther) ClearableArena(Chunk::size);
    arena_dictionary = new(mtOther) Arena(mtOther, Chunk::tiny_size);
    switch(collection_strategy) {
        case 1:
            dictionary = new(arena_dictionary) NavigableHashMap<Arena*, Sequence*, uint, hashSequence, equalsSequence, getSequenceClass, reduceSequence>(arena_elements, arena_dictionary, 4 * K);
            break;
        case 2:
            dictionary = new(arena_dictionary) NavigableHashMap<Arena*, Sequence*, uint, hashSequence, equalsSequence, getSequenceClass, reduceSequence, true, 3>(arena_elements, arena_dictionary, 4 * K);
            break;
        case 3:
            dictionary = new(arena_dictionary) NavigableHashMap<Arena*, Sequence*, uint, hashSequence, equalsSequence, getSequenceClass, reduceSequence, true, 4>(arena_elements, arena_dictionary, 4 * K);
            break;
        case 4:
            dictionary = new(arena_dictionary) NavigableHashMap<Arena*, Sequence*, uint, hashSequence, equalsSequence, getSequenceClass, reduceSequence, true, 5>(arena_elements, arena_dictionary, 4 * K);
            break;
            
        case 5:
            dictionary = new(arena_dictionary) NavigableHashMap<Arena*, Sequence*, uint, hashSequence, equalsSequence, getSequenceClass, reduceSequence, true>(arena_elements, arena_dictionary, sizeof(void*), getMapSize);
            break;
        case 6:
            dictionary = new(arena_dictionary) NavigableHashMap<Arena*, Sequence*, uint, hashSequence, equalsSequence, getSequenceClass, reduceSequence, true, 3>(arena_elements, arena_dictionary, sizeof(void*), getMapSize);
            break;
        case 7: default:
            dictionary = new(arena_dictionary) NavigableHashMap<Arena*, Sequence*, uint, hashSequence, equalsSequence, getSequenceClass, reduceSequence, true, 4>(arena_elements, arena_dictionary, sizeof(void*), getMapSize);
            break;
        case 8:
            dictionary = new(arena_dictionary) NavigableHashMap<Arena*, Sequence*, uint, hashSequence, equalsSequence, getSequenceClass, reduceSequence, true, 5>(arena_elements, arena_dictionary, sizeof(void*), getMapSize);
            break;

#ifndef _WINDOWS
        case 9:
            dictionary = new(arena_dictionary) ListMap<Sequence*, uint, compareEntry, isEntryFloorOf>(arena_dictionary);
            break;
        case 10:
            dictionary = new(arena_dictionary) ListMap<Sequence*, uint, compareEntryByLength, isEntryFloorOf>(arena_dictionary);
            break;
#else
        case 9: case 10:
            warning("not supported, falling back to strategy 11");
#endif

        case 11:
            dictionary = new(arena_dictionary) PrefixTreeMap<Sequence*, unsigned char, uint, getSequenceLength, getSequenceElement, equalsChar, createMap>(arena_dictionary, K * 4);
            break;
        case 12:
            dictionary = new(arena_dictionary) PrefixTreeMap<Sequence*, unsigned char, uint, getSequenceNibbleLength, getSequenceNibbleElement, equalsChar, createMap>(arena_dictionary, K * 4);
            break;
        case 13:
            dictionary = new(arena_dictionary) PrefixTreeMap<Sequence*, unsigned char, uint, getSequenceHibbleLength, getSequenceHibbleElement, equalsChar, createMap>(arena_dictionary, K * 4);
            break;
    }
    reset();
}

LZWCompression::~LZWCompression() {
    dictionary->~NavigableMap<Sequence*, uint>(); dictionary = NULL;
    delete arena_dictionary; arena_dictionary = NULL;
    delete arena_elements; arena_elements = NULL;
}

void LZWCompression::reset() {
    dictionary->clear();
    
    //arena_elements->destruct_contents();
    ((ClearableArena*) arena_elements)->clear();    
    next_index = 0;
    max_length = 0;
    
    for(uint index = 0; index < FIRST_CUSTOM_INDEX; index++) {
        unsigned char c = (unsigned char) (index & 0xFF);
        registerIndex(&c, 1);
    }
}

Compression::Result LZWCompression::encode(unsigned char* src_array, size_t src_length, unsigned char* dest_buffer, size_t dest_length) {
#ifdef DEBUG_COMPRESSION
    debug_print_array("uncompressed", src_array, src_length, 0);
    debug_print_array("  compressed", dest_buffer, 0, 0);
#endif
    VarIntWriter writer = VarIntWriter();
    size_t src_index = 0, dest_index = 0;
    while(src_index < src_length && dest_index + INDEX_MAX_WIDTH * 2 < dest_length) {
        size_t length = MIN2(max_length, src_length - src_index);
        uint index = resolveIndex(src_array + src_index, &length);
        //dest_index += VarInt::write(dest_array + dest_index, index);
        dest_index += writer.write(dest_buffer + dest_index, index);
        if(src_index + length + 1 < src_length) {
            registerIndex(src_array + src_index, length + 1);
        }
        src_index += length;
#ifdef DEBUG_COMPRESSION
        debug_print_array("uncompressed", src_array, src_length, src_index);
        debug_print_array("  compressed", dest_buffer, dest_index, dest_index);
#endif
        assert(dest_index <= dest_length, "destination not big enough");
        assert(src_array != dest_buffer || dest_index <= src_index, "cannot compress in-place");
    }
    if(dest_index + INDEX_MAX_WIDTH * 2 < dest_length) dest_index += writer.flush(dest_buffer + dest_index);
    else src_index = 0;
    assert(src_length >= src_index, "operated on uninitialized memory!");
#ifdef DEBUG_COMPRESSION
    debug_print_array("uncompressed", src_array, src_length, src_index);
    debug_print_array("  compressed", dest_buffer, dest_index, dest_index);
#endif
    Result result;
    result.src_compressed_length = src_index;
    result.dest_length = dest_index;
    return result;
}

size_t LZWCompression::getDictionarySize() {
    return dictionary->get_size();
}

uint LZWCompression::resolveIndex(unsigned char* array, size_t* length) {
    Sequence sequence((int) *length, array, false);
    Maybe<MapEntry<Sequence*, uint> > maybe = dictionary->get_floor(&sequence);
    assert(maybe.has_value(), "must find floor");
    *length = maybe.get_value().get_key()->get_length();
    return maybe.get_value().get_value();
}

void LZWCompression::registerIndex(unsigned char* array, size_t length) {
    if(next_index <= MAX_INDEX) {
        Sequence* sequence = Sequence::create(arena_elements, (int) length, array, true);
        uint index = next_index++;
        dictionary->put(sequence, index);
        max_length = MAX2(max_length, length);
#ifdef ASSERT
        //check whether index can be resolved
        {
            size_t resolved_length = length;
            assert(resolveIndex(array, &resolved_length) == index && resolved_length == length, "just checking");
        }
#endif
#ifdef COMPRESSION_PARANOID_ASSERTIONS
        //check whether no longer sequence is in dictionary
        {
            unsigned char data[length + 1];
            sequence->copy_to(data);
            for(int c = 0; c < (1 << (sizeof(unsigned char) * 8)) / 50; c++) {
                data[length] = (unsigned char) (c & 0xFF);
                Sequence longer(length, data, false);
                Maybe<MapEntry<Sequence*, uint> > floor = dictionary->get_floor(&longer);
                assert(floor.has_value(), "could not find floor of longer sequence");
                assert(floor.get_value().get_key()->equals(sequence), "floor is not sequence");
                assert(floor.get_value().get_value() == index, "floor has different index");
            }
        }
        //check if all smaller are in dictionary
        {
            for(Sequence* shorter = sequence; shorter->get_length() > 0; shorter = shorter->trim(arena_elements, shorter->get_length() - 1, false)) {
                Maybe<MapEntry<Sequence*, uint> > floor = dictionary->get_floor(shorter);
                assert(floor.has_value(), "could not find smaller sequence");
                assert(floor.get_value().get_key()->equals(shorter), "sequence missing in between");
            }
        }
#endif
#ifdef DEBUG_COMPRESSION
        char index_str[16];
        sprintf(index_str, "%i", index);
        unsigned char data[sequence->get_length()];
        for(size_t index = 0; index < sizeof(data); index++) {
            data[index] = sequence->get(index);
        }
        debug_print_array(index_str, data, sizeof(data), -1);
#endif
    }
}

Decompression* LZWCompression::getDecompressor() {
    return new LZWDecompression();
}


