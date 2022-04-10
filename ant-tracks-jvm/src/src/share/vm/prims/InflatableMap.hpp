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
 * File:   InflatableMap.hpp
 * Author: Philipp Lengauer
 *
 * Created on October 7, 2014, 12:08 PM
 */

#ifndef INFLATABLEMAP_HPP
#define	INFLATABLEMAP_HPP

#include "Map.hpp"
#include "SmallMap.hpp"

template <class key_t, class value_t, bool (*equals)(key_t key1, key_t key2), char thin_capacity, Map<key_t, value_t>* (*create_fat)(Arena* arena)> class InflatableMap : public Map<key_t, value_t> {
private:
    const static size_t MAP_SIZE = sizeof(Map<key_t, value_t>*);
    const static size_t SMALL_MAP_SIZE = sizeof(SmallMap<key_t, value_t, thin_capacity, equals>);
    char _[MAP_SIZE > SMALL_MAP_SIZE ? MAP_SIZE : SMALL_MAP_SIZE];
    bool is_inflated;
    
public:
    InflatableMap();
    ~InflatableMap() {}
    
    size_t get_size();
    Maybe<value_t> get(key_t key);
    
    bool put(key_t key, value_t value) { return put(key, value, NULL); }
    bool put(key_t key, value_t value, Arena* arena);
    bool remove(key_t key);
    
    void clear();
    
private:
    Map<key_t, value_t>* resolve();
    SmallMap<key_t, value_t, thin_capacity, equals>* as_thin();
    Map<key_t, value_t>* as_fat();
    char* payload_addr();
};

template <class key_t, class value_t, bool (*equals)(key_t key1, key_t key2), char thin_capacity, Map<key_t, value_t>* (*create_fat)(Arena* arena)> InflatableMap<key_t, value_t, equals, thin_capacity, create_fat>::InflatableMap() : Map<key_t, value_t>(), is_inflated(false) {
    new(payload_addr()) SmallMap<key_t, value_t, thin_capacity, equals>();
}

template <class key_t, class value_t, bool (*equals)(key_t key1, key_t key2), char thin_capacity, Map<key_t, value_t>* (*create_fat)(Arena* arena)> size_t InflatableMap<key_t, value_t, equals, thin_capacity, create_fat>::get_size() {
    return resolve()->get_size();
}

template <class key_t, class value_t, bool (*equals)(key_t key1, key_t key2), char thin_capacity, Map<key_t, value_t>* (*create_fat)(Arena* arena)> Maybe<value_t> InflatableMap<key_t, value_t, equals, thin_capacity, create_fat>::get(key_t key) {
    return resolve()->get(key);
}

template <class key_t, class value_t, bool (*equals)(key_t key1, key_t key2), char thin_capacity, Map<key_t, value_t>* (*create_fat)(Arena* arena)> bool InflatableMap<key_t, value_t, equals, thin_capacity, create_fat>::put(key_t key, value_t value, Arena* arena) {
    if(resolve()->put(key, value)) {
        return true;
    } else if(is_inflated) {
        return false; //what is happening?
    } else {
        Map<key_t, value_t>* fat = create_fat(arena);
        if(fat != NULL) {
            SmallMap<key_t, value_t, thin_capacity, equals>* thin = as_thin();
            for(size_t index = 0; index < thin->get_size(); index++) {
                MapEntry<key_t, value_t> entry = thin->get_at((int) index);
                fat->put(entry.get_key(), entry.get_value());
            }
            thin->clear();
            *((Map<key_t, value_t>**) payload_addr()) = fat;
            is_inflated = true;
            return fat->put(key, value);
        } else {
            return false;
        }
    }
}

template <class key_t, class value_t, bool (*equals)(key_t key1, key_t key2), char thin_capacity, Map<key_t, value_t>* (*create_fat)(Arena* arena)> bool InflatableMap<key_t, value_t, equals, thin_capacity, create_fat>::remove(key_t key) {
    return resolve()->remove(key);
}

template <class key_t, class value_t, bool (*equals)(key_t key1, key_t key2), char thin_capacity, Map<key_t, value_t>* (*create_fat)(Arena* arena)> void InflatableMap<key_t, value_t, equals, thin_capacity, create_fat>::clear() {
    resolve()->clear();
}

template <class key_t, class value_t, bool (*equals)(key_t key1, key_t key2), char thin_capacity, Map<key_t, value_t>* (*create_fat)(Arena* arena)> Map<key_t, value_t>* InflatableMap<key_t, value_t, equals, thin_capacity, create_fat>::resolve() {
    return is_inflated ? as_fat() : as_thin();
}

template <class key_t, class value_t, bool (*equals)(key_t key1, key_t key2), char thin_capacity, Map<key_t, value_t>* (*create_fat)(Arena* arena)> SmallMap<key_t, value_t, thin_capacity, equals>* InflatableMap<key_t, value_t, equals, thin_capacity, create_fat>::as_thin() {
    return (SmallMap<key_t, value_t, thin_capacity, equals>*) payload_addr();
}

template <class key_t, class value_t, bool (*equals)(key_t key1, key_t key2), char thin_capacity, Map<key_t, value_t>* (*create_fat)(Arena* arena)> Map<key_t, value_t>* InflatableMap<key_t, value_t, equals, thin_capacity, create_fat>::as_fat() {
    return *((Map<key_t, value_t>**) payload_addr());
}

template <class key_t, class value_t, bool (*equals)(key_t key1, key_t key2), char thin_capacity, Map<key_t, value_t>* (*create_fat)(Arena* arena)> char* InflatableMap<key_t, value_t, equals, thin_capacity, create_fat>::payload_addr() {
    return &_[0];
}

#endif	/* INFLATABLEMAP_HPP */

