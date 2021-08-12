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
 * File:   ListDictionary.hpp
 * Author: Philipp Lengauer
 *
 * Created on October 4, 2014, 3:10 PM
 */

#ifndef LISTDICTIONARY_HPP
#define	LISTDICTIONARY_HPP

#include "Map.hpp"
#include "SortedList.hpp"

template <class key_t, class value_t, int (*compare)(MapEntry<key_t, value_t> entry1, MapEntry<key_t, value_t> entry2), bool (*is_floor_of)(MapEntry<key_t, value_t> floor, MapEntry<key_t, value_t> entry)> class ListMap : public NavigableMap<key_t, value_t> {
private:        
    SortedList<MapEntry<key_t, value_t>, compare, is_floor_of> list;
    
public:
    ListMap(Arena* arena);
    ~ListMap();
    
    size_t get_size();
    Maybe<value_t> get(key_t key);
    Maybe<MapEntry<key_t, value_t> > get_floor(key_t key);
    
    bool put(key_t key, value_t value);
    bool remove(key_t);
    
    void clear();
    
};

template <class key_t, class value_t, int (*compare)(MapEntry<key_t, value_t> entry1, MapEntry<key_t, value_t> entry2), bool (*is_floor_of)(MapEntry<key_t, value_t> floor, MapEntry<key_t, value_t> entry)> ListMap<key_t, value_t, compare, is_floor_of>::ListMap(Arena* arena) : NavigableMap<key_t, value_t>(), list(SortedList<MapEntry<key_t, value_t>, compare, is_floor_of>(arena)) {}

template <class key_t, class value_t, int (*compare)(MapEntry<key_t, value_t> entry1, MapEntry<key_t, value_t> entry2), bool (*is_floor_of)(MapEntry<key_t, value_t> floor, MapEntry<key_t, value_t> entry)> ListMap<key_t, value_t, compare, is_floor_of>::~ListMap() {}

template <class key_t, class value_t, int (*compare)(MapEntry<key_t, value_t> entry1, MapEntry<key_t, value_t> entry2), bool (*is_floor_of)(MapEntry<key_t, value_t> floor, MapEntry<key_t, value_t> entry)> size_t ListMap<key_t, value_t, compare, is_floor_of>::get_size() {
    return list.get_size();
}

template <class key_t, class value_t, int (*compare)(MapEntry<key_t, value_t> entry1, MapEntry<key_t, value_t> entry2), bool (*is_floor_of)(MapEntry<key_t, value_t> floor, MapEntry<key_t, value_t> entry)> inline Maybe<value_t> ListMap<key_t, value_t, compare, is_floor_of>::get(key_t key) {
    Maybe<MapEntry<key_t, value_t> > floor = get_floor(key);
    if(floor.has_value() && compare(floor.get_value().get_key(), key) == 0) {
        return Maybe<value_t>(floor.get_value().get_value());
    } else {
        return Maybe<value_t>();
    }
}

template <class key_t, class value_t, int (*compare)(MapEntry<key_t, value_t> entry1, MapEntry<key_t, value_t> entry2), bool (*is_floor_of)(MapEntry<key_t, value_t> floor, MapEntry<key_t, value_t> entry)> inline Maybe<MapEntry<key_t, value_t> > ListMap<key_t, value_t, compare, is_floor_of>::get_floor(key_t key) {
    int floor_index = list.floor(MapEntry<key_t, value_t>(key));
    if(0 <= floor_index && (size_t) floor_index < list.get_size()) {
        return Maybe<MapEntry<key_t, value_t> >(list.get(floor_index));
    } else {
        return Maybe<MapEntry<key_t, value_t> >();
    }
}

template <class key_t, class value_t, int (*compare)(MapEntry<key_t, value_t> entry1, MapEntry<key_t, value_t> entry2), bool (*is_floor_of)(MapEntry<key_t, value_t> floor, MapEntry<key_t, value_t> entry)> inline bool ListMap<key_t, value_t, compare, is_floor_of>::put(key_t key, value_t value) {
    list.add(MapEntry<key_t, value_t>(key, value));
    return true; //TODO
}

template <class key_t, class value_t, int (*compare)(MapEntry<key_t, value_t> entry1, MapEntry<key_t, value_t> entry2), bool (*is_floor_of)(MapEntry<key_t, value_t> floor, MapEntry<key_t, value_t> entry)> inline bool ListMap<key_t, value_t, compare, is_floor_of>::remove(key_t key) {
    //TODO
    return false;
}

template <class key_t, class value_t, int (*compare)(MapEntry<key_t, value_t> entry1, MapEntry<key_t, value_t> entry2), bool (*is_floor_of)(MapEntry<key_t, value_t> floor, MapEntry<key_t, value_t> entry)> inline void ListMap<key_t, value_t, compare, is_floor_of>::clear() {
    list.clear();
} 

#endif	/* LISTDICTIONARY_HPP */

