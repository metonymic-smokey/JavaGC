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
 * File:   SmallMap.hpp
 * Author: Philipp Lengauer
 *
 * Created on October 5, 2014, 8:10 AM
 */

#ifndef SMALLMAP_HPP
#define	SMALLMAP_HPP

#include "Map.hpp"

template <class key_t, class value_t, char capacity, bool (*equals)(key_t key1, key_t key2)> class SmallMap : public Map<key_t, value_t> {
private:
    char _[capacity * (sizeof(key_t) + sizeof(value_t))];
    unsigned char size;
    
    char* get_payload_addr() { return &_[0]; }
    
public:
    SmallMap();
    ~SmallMap();
    
    size_t get_size();
    Maybe<value_t> get(key_t key);
    MapEntry<key_t, value_t> get_at(int index);
    
    bool put(key_t key, value_t value);
    bool remove(key_t key);
    
    void clear();

private:
    Maybe<int> get_index(key_t key);
    char* get_location(int index);
};

template <class key_t, class value_t, char capacity, bool (*equals)(key_t key1, key_t key2)> SmallMap<key_t, value_t, capacity, equals>::SmallMap() : size(0) {}

template <class key_t, class value_t, char capacity, bool (*equals)(key_t key1, key_t key2)> SmallMap<key_t, value_t, capacity, equals>::~SmallMap() {}

template <class key_t, class value_t, char capacity, bool (*equals)(key_t key1, key_t key2)> size_t SmallMap<key_t, value_t, capacity, equals>::get_size() {
    return size;
}

template <class key_t, class value_t, char capacity, bool (*equals)(key_t key1, key_t key2)> Maybe<value_t> SmallMap<key_t, value_t, capacity, equals>::get(key_t key) {
    Maybe<int> index = get_index(key);
    if(index.has_value()) {
        return Maybe<value_t>(*((value_t*) (get_location(index.get_value()) + sizeof(key_t))));
    } else {
        return Maybe<value_t>();
    }
}

template <class key_t, class value_t, char capacity, bool (*equals)(key_t key1, key_t key2)> MapEntry<key_t, value_t> SmallMap<key_t, value_t, capacity, equals>::get_at(int index) {
    char* location = get_location(index);
    key_t key = *((key_t*) (location));
    value_t value = *((value_t*) (location + sizeof(key_t)));
    return MapEntry<key_t, value_t>(key, value);
}

template <class key_t, class value_t, char capacity, bool (*equals)(key_t key1, key_t key2)> bool SmallMap<key_t, value_t, capacity, equals>::put(key_t key, value_t value) {
    if(get(key).has_value()) {
        return false;
    } else if(size < capacity) {
        char* location = get_location(size);
        *((key_t*) location) = key;
        *((value_t*) (location + sizeof(key_t))) = value;
        size++;
        return true;
    } else {
        return false;
    }
}

template <class key_t, class value_t, char capacity, bool (*equals)(key_t key1, key_t key2)> bool SmallMap<key_t, value_t, capacity, equals>::remove(key_t key) {
    //TODO
    return false;
}

template <class key_t, class value_t, char capacity, bool (*equals)(key_t key1, key_t key2)> void SmallMap<key_t, value_t, capacity, equals>::clear() {
    size = 0;
}

template <class key_t, class value_t, char capacity, bool (*equals)(key_t key1, key_t key2)> Maybe<int> SmallMap<key_t, value_t, capacity, equals>::get_index(key_t key) {
    for(int index = 0; index < size; index++) {
        char* entry = get_payload_addr() + index * (sizeof(key_t) + sizeof(value_t));
        key_t candidate = *((key_t*) entry);
        if(equals(key, candidate)) {
            return Maybe<int>(index);
        }
    }
    return Maybe<int>();
}

template <class key_t, class value_t, char capacity, bool (*equals)(key_t key1, key_t key2)> char* SmallMap<key_t, value_t, capacity, equals>::get_location(int index) {
    return get_payload_addr() + index * (sizeof(key_t) + sizeof(value_t));
}

#endif	/* SMALLMAP_HPP */

