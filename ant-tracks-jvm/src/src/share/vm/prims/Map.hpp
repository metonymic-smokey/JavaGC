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
 * File:   Dictionary.hpp
 * Author: Philipp Lengauer
 *
 * Created on October 4, 2014, 10:11 PM
 */

#ifndef MAP_HPP
#define	MAP_HPP

#include "utilities/globalDefinitions.hpp"
#include "memory/allocation.hpp"

template <class T> class Maybe {
private:
    bool has;
    T value;
public:
    Maybe() : has(false) {}
    Maybe(T value) : has(true), value(value) {}
    
    bool has_value() { return has; }
    T get_value() { return value; }
};

template <class key_t, class value_t> class MapEntry {
private:
    key_t key;
    value_t value;
public:
    MapEntry() {}
    MapEntry(key_t key) : key(key) {}
    MapEntry(key_t key, value_t value) : key(key), value(value) {}
    
    key_t get_key() { return key; }
    value_t get_value() { return value; }
};

template <class key_t, class value_t> class Map {
public:
    void* operator new(size_t size, Arena* arena) {
        return arena->Amalloc(size);
    }
    
    void* operator new(size_t size, void* ptr) {
        return ptr;
    }
    
    Map() {}
    virtual ~Map() {}
    
    virtual size_t get_size() = 0;
    virtual Maybe<value_t> get(key_t key) = 0;
    
    virtual bool put(key_t key, value_t value) = 0;
    virtual bool remove(key_t key) = 0;
    
    virtual void clear() = 0;
};

template <class key_t, class value_t> class NavigableMap : public Map<key_t, value_t> {
public:
    NavigableMap() : Map<key_t, value_t>() {}
    virtual ~NavigableMap() {}
    
    virtual Maybe<MapEntry<key_t, value_t> > get_floor(key_t key) = 0;
};

#endif	/* DICTIONARY_HPP */

