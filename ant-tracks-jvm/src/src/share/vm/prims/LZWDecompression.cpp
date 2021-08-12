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
 * File:   Decompression.cpp
 * Author: Philipp Lengauer
 * 
 * Created on December 4, 2013, 8:32 AM
 */

#include "precompiled.hpp"
#include "LZWDecompression.hpp"
#include "VarInt.hpp"
#include "Sequence.hpp"
#include "HashMap.hpp"

bool equalsIndex(uint index1, uint index2) {
    return index1 == index2;
}

int hashIndex(uint index) {
    return (int) index;
}

LZWDecompression::LZWDecompression() {
    arena_elements = new(mtOther) Arena(mtOther, Chunk::size);
    arena_dictionary = new (mtOther) Arena(mtOther, Chunk::tiny_size);
    dictionary = new(arena_dictionary) HashMap<uint, Sequence*, hashIndex, equalsIndex>(arena_dictionary, K * 4);
    arena_tmp = new (mtOther) Arena(mtOther, Chunk::tiny_size);
    reset();
}

LZWDecompression::~LZWDecompression() {
    dictionary = NULL;
    delete arena_dictionary; arena_dictionary = NULL;
    delete arena_elements; arena_elements = NULL;
    delete arena_tmp; arena_tmp = NULL;
}

void LZWDecompression::reset() {
    dictionary->clear();
    arena_elements->destruct_contents();
    arena_tmp->destruct_contents();
    next_index = FIRST_CUSTOM_INDEX;
}

size_t LZWDecompression::decode(unsigned char* src_array, size_t src_length, unsigned char* dest_array, size_t dest_length) {
#ifdef DEBUG_DECOMPRESSION
    debug_print_array("  compressed", src_array, src_length, 0);
    debug_print_array("decompressed", dest_array, 0, 0);
#endif
    size_t src_index = 0, dest_index = 0;
    VarIntReader reader = VarIntReader();
    Sequence* last = Sequence::create_empty(arena_tmp);
    while(src_index < src_length) {
        uint index = 0;
        src_index += reader.read(src_array + src_index, &index);
        Sequence* current;
        bool merge_last_with_current = false;
        if(index < FIRST_CUSTOM_INDEX) {
            current = Sequence::create(arena_tmp, 1, (unsigned char*) &index, true);
        } else {
            current = resolveIndex(index);
            if(current != NULL) {
                assert(current->get_length() >= 2, "such an item should not be in the dictionary");
            } else {
                unsigned char c = last->get(0);
                current = Sequence::create(arena_tmp, 1, &c, true);
                dest_index += last->copy_to(dest_array + dest_index); // in this case something wrong is registered (last for next iteration is wrong?)
                merge_last_with_current = true;
            }
        }
        dest_index += current->copy_to(dest_array + dest_index);
        registerIndex(last, current);
        if(merge_last_with_current) {
            current = Sequence::concat(arena_tmp, last, current);
        }
        last = current;
#ifdef DEBUG_DECOMPRESSION
        debug_print_array("  compressed", src_array, src_length, src_index);
        debug_print_array("decompressed", dest_array, dest_index, dest_index);
#endif
        assert(dest_index <= dest_length, "destination not big enough");
    }
    assert(src_length == src_index, "operated on uninitialized memory!");
    arena_tmp->destruct_contents();
    return dest_index;
}

size_t LZWDecompression::getDictionarySize() {
    return dictionary->get_size() + FIRST_CUSTOM_INDEX;
}

Sequence* LZWDecompression::resolveIndex(uint index) {
    assert(index >= FIRST_CUSTOM_INDEX, "you should know better! (result is (char)index)");
    Maybe<Sequence*> maybe = dictionary->get(index);
    return maybe.has_value() ? maybe.get_value() : NULL;
}

void LZWDecompression::registerIndex(Sequence* last, Sequence* current) {
    if(last->get_length() != 0 && next_index <= MAX_INDEX) {
        uint index = next_index++;
        Sequence* sequence = Sequence::concat(arena_elements, last, current->trim(arena_tmp, 1, false));
        dictionary->put(index, sequence);
#ifdef DEBUG_DECOMPRESSION
        char index_str[16];
        sprintf(index_str, "%i", next_index - 1);
        unsigned char data[sequence->get_length()];
        for(size_t index = 0; index < sizeof(data); index++) {
            data[index] = sequence->get(index);
        }
        debug_print_array(index_str, data, sizeof(data), -1);
#endif
    }
}


