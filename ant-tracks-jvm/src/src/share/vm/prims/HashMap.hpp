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
 * File:   HashDictionary.hpp
 * Author: Philipp Lengauer
 *
 * Created on October 4, 2014, 5:45 PM
 */

#ifndef HASHDICTIONARY_HPP
#define	HASHDICTIONARY_HPP

#include "Map.hpp"

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> class HashMap : public Map<key_t, value_t> {
private:    
    static const size_t BUCKET_ENTRY_SIZE = sizeof(key_t) + sizeof(value_t);
    
    struct Bucket {
        char* entries;
        int capacity;
        int count;
    };
    
    Arena* arena;
    Bucket* buckets;
    size_t bucket_count;
    
    size_t count;
public:
    HashMap(Arena* arena, size_t init_capacity = 1);
    ~HashMap();
    
    size_t get_size();
    Maybe<value_t> get(key_t key);
    
    bool put(key_t key, value_t value);
    bool remove(key_t key);
    
    void clear();
    
private:
    void grow();
    
    void init_bucket(Bucket* bucket);
    void grow_bucket(Bucket* bucket);
    void reset_bucket(Bucket* bucket);
};

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> HashMap<key_t, value_t, hash, equals>::HashMap(Arena* arena, size_t init_capacity) : arena(arena) {
    size_t better_init_capacity = 1;
    while(better_init_capacity < init_capacity) better_init_capacity = better_init_capacity * 2;
    init_capacity = better_init_capacity;
    
    buckets = (Bucket*) arena->Amalloc(init_capacity * sizeof(Bucket));
    bucket_count = init_capacity;
    for(size_t index = 0; index < bucket_count; index++) {
        init_bucket(buckets + index);
    }
    
    count = 0;
}

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> HashMap<key_t, value_t, hash, equals>::~HashMap() {
    count = 0;
    
    for(size_t index = 0; index < bucket_count; index++) {
        reset_bucket(buckets + index);
    }
    
    bucket_count = 0;
    buckets = NULL;
    arena->Afree(buckets, bucket_count + sizeof(Bucket));
}

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> inline size_t HashMap<key_t, value_t, hash, equals>::get_size() {
    return count;
}

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> inline Maybe<value_t> HashMap<key_t, value_t, hash, equals>::get(key_t key) {
    int h = hash(key);
    int index = h & (bucket_count - 1);
    Bucket* bucket = buckets + index;
    
    char* end = bucket->entries + bucket->count * BUCKET_ENTRY_SIZE;
    for(char* ptr = bucket->entries; ptr < end; ptr += BUCKET_ENTRY_SIZE) {
        key_t candidate = *((key_t*) ptr);
        if(equals(key, candidate)) {
            ptr += sizeof(key_t);
            value_t value = *((value_t*) ptr);
            return Maybe<value_t>(value);
        }
    }
    
    return Maybe<value_t>();
}

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> inline bool HashMap<key_t, value_t, hash, equals>::put(key_t key, value_t value) {
    if(count == bucket_count) {
        grow();
    }
    int h = hash(key);
    int index = h & (bucket_count - 1);
    Bucket* bucket = buckets + index;
    
    char* insert_ptr = bucket->entries + bucket->count * BUCKET_ENTRY_SIZE;
    for(char* ptr = bucket->entries; ptr < insert_ptr; ptr += BUCKET_ENTRY_SIZE) {
        key_t candidate = *((key_t*) ptr);
        if(equals(key, candidate)) {
            return false;
        }
    }
    
    if(bucket->count == bucket->capacity) {
        grow_bucket(bucket);
        insert_ptr = bucket->entries + bucket->count * BUCKET_ENTRY_SIZE;
    }
    *((key_t*) insert_ptr) = key;
    insert_ptr += sizeof(key_t);
    *((value_t*) insert_ptr) = value;
    insert_ptr += sizeof(value_t);
    bucket->count++;
    count++;
    return true;
}

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> inline bool HashMap<key_t, value_t, hash, equals>::remove(key_t key) {
    //TODO 
    return false;
}

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> inline void HashMap<key_t, value_t, hash, equals>::clear() {
    for(size_t index = 0; index < bucket_count; index++) {
        (buckets + index)->count = 0;
    }
    count = 0;
}

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> void HashMap<key_t, value_t, hash, equals>::grow() {
    size_t old_capacity = bucket_count;
    bucket_count = MAX2((size_t) 3, old_capacity * 2);
    buckets = (Bucket*) arena->Arealloc(buckets, old_capacity * sizeof(Bucket), bucket_count * sizeof(Bucket));
    for(size_t index = old_capacity; index < (size_t) bucket_count; index++) {
        init_bucket(buckets + index);
    }
    
    bool change_in_old;
    do {
        change_in_old = false;
        for(size_t bucket_index = 0; bucket_index < old_capacity; bucket_index++) {
            Bucket* bucket = buckets + bucket_index;
            for(size_t entry_index = 0; entry_index < (size_t) bucket->count; entry_index++) {
                key_t key = *((key_t*) (bucket->entries + entry_index * BUCKET_ENTRY_SIZE));
                int h = hash(key);
                int new_bucket_index = h & (bucket_count - 1);
                if(bucket_index != (size_t) new_bucket_index) {
                    value_t value = *((value_t*) (bucket->entries + entry_index * BUCKET_ENTRY_SIZE + sizeof(key_t)));

                    memmove(bucket->entries + entry_index * BUCKET_ENTRY_SIZE, bucket->entries + entry_index * BUCKET_ENTRY_SIZE + BUCKET_ENTRY_SIZE, (bucket->count - entry_index - 1) * BUCKET_ENTRY_SIZE);
                    entry_index--;
                    bucket->count--;
                    
                    Bucket* new_bucket = buckets + new_bucket_index;
                    if(new_bucket->count == new_bucket->capacity) {
                        grow_bucket(new_bucket);
                    }
                    char* insert_pos = new_bucket->entries + new_bucket->count * BUCKET_ENTRY_SIZE;
                    *((key_t*) insert_pos) = key;
                    insert_pos += sizeof(key_t);
                    *((value_t*) insert_pos) = value;
                    insert_pos += sizeof(value_t);
                    new_bucket->count++;
                    
                    change_in_old |= (size_t) new_bucket_index < old_capacity;
                }
            }
        }
    } while(change_in_old);
}

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> inline void HashMap<key_t, value_t, hash, equals>::init_bucket(Bucket* bucket) {
    bucket->entries = NULL;
    bucket->capacity = 0;
    bucket->count = 0;
}

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> inline void HashMap<key_t, value_t, hash, equals>::grow_bucket(Bucket* bucket) {
    size_t new_capacity = MAX2(1, bucket->capacity * 2);
    bucket->entries = (char*) arena->Arealloc(bucket->entries, bucket->capacity * BUCKET_ENTRY_SIZE, new_capacity * BUCKET_ENTRY_SIZE);
    bucket->capacity = (int) new_capacity;
}

template <class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t)> void HashMap<key_t, value_t, hash, equals>::reset_bucket(Bucket* bucket) {
    if(bucket->capacity > 0) {
        arena->Afree(bucket->entries, bucket->capacity * BUCKET_ENTRY_SIZE);
    }
    bucket->count = 0;
    bucket->capacity = 0;
    bucket->entries = NULL;
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements = false, size_t lucky_clazz = 0> class NavigableHashMap : public NavigableMap<key_t, value_t> {
private:
    context_t context;
    size_t dictionary_count;
    HashMap<key_t, value_t, hash, equals>* dictionaries;
    
    static size_t get_dummy_class(key_t key);
    static key_t do_not_reduce(context_t context, key_t key, size_t _);
public:
    NavigableHashMap(context_t context, Arena* arena, size_t init_capacity = 1);
    NavigableHashMap(context_t context, Arena* arena, size_t dictionary_count, size_t (*get_init_capacity)(size_t id));
    ~NavigableHashMap();
    
    size_t get_size();
    Maybe<value_t> get(key_t key);
    Maybe<MapEntry<key_t, value_t> > get_floor(key_t key);

    bool put(key_t key, value_t value);
    bool remove(key_t key);
    
    void clear();
private:
    Maybe<MapEntry<key_t, value_t> > get_floor_top_down(key_t key, size_t min_clazz, size_t max_clazz);
    Maybe<MapEntry<key_t, value_t> > get_floor_bottom_up(key_t key, size_t min_clazz, size_t max_clazz);
    Maybe<MapEntry<key_t, value_t> > get_floor_binary_search(key_t key, size_t min_clazz, size_t max_clazz);
    Maybe<MapEntry<key_t, value_t> > get_floor_lucky_guess(key_t key);
    Maybe<MapEntry<key_t, value_t> > try_get_floor(key_t key, size_t clazz);
    
    HashMap<key_t, value_t, hash, equals>* get_dictionary(size_t clazz);
};

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> size_t NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::get_dummy_class(key_t _) {
    return 0;
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> key_t NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::do_not_reduce(context_t context, key_t key, size_t _) {
    return key;
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::NavigableHashMap(context_t context, Arena* arena, size_t init_capacity) : NavigableMap<key_t, value_t>(), context(context), dictionary_count(1) {
    dictionaries = (HashMap<key_t, value_t, hash, equals>*) arena->Amalloc(sizeof(HashMap<key_t, value_t, hash, equals>) * dictionary_count);
    new(dictionaries) HashMap<key_t, value_t, hash, equals>(arena, init_capacity);
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::NavigableHashMap(context_t context, Arena* arena, size_t dictionary_count, size_t (*get_init_capacity)(size_t id)) : NavigableMap<key_t, value_t>(), context(context), dictionary_count(MAX2((size_t) 1, dictionary_count)) {
    dictionaries = (HashMap<key_t, value_t, hash, equals>*) arena->Amalloc(sizeof(HashMap<key_t, value_t, hash, equals>) * dictionary_count);
    for(size_t index = 0; index < dictionary_count; index++) {
        new(dictionaries + index) HashMap<key_t, value_t, hash, equals>(arena, get_init_capacity(index));
    }
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::~NavigableHashMap() {}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> size_t NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::get_size() {
    size_t size = 0;
    for(size_t index = 0; index < dictionary_count; index++) {
        size += (dictionaries + index)->get_size();
    }
    return size;
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> inline Maybe<value_t> NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::get(key_t key) {
    return get_dictionary(get_class(key))->get(key);
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> inline Maybe<MapEntry<key_t, value_t> > NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::get_floor(key_t key) {
    return get_floor_lucky_guess(key);
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> inline bool NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::put(key_t key, value_t value) {
    return get_dictionary(get_class(key))->put(key, value);
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> inline bool NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::remove(key_t key) {
    return get_dictionary(get_class(key))->remove(key);
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> void NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::clear() {
    for(size_t index = 0; index < dictionary_count; index++) {
        (dictionaries + index)->clear();
    }
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> inline Maybe<MapEntry<key_t, value_t> > NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::get_floor_top_down(key_t key, size_t min_clazz, size_t max_clazz) {
    Maybe<MapEntry<key_t, value_t> > result = Maybe<MapEntry<key_t, value_t> >();
    for(size_t clazz = max_clazz; clazz >= min_clazz && !(result = try_get_floor(key, clazz)).has_value(); clazz--);
    return result;
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> inline Maybe<MapEntry<key_t, value_t> > NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::get_floor_bottom_up(key_t key, size_t min_clazz, size_t max_clazz) {
    Maybe<MapEntry<key_t, value_t> > result = Maybe<MapEntry<key_t, value_t> >();
    for(size_t clazz = min_clazz; clazz <= max_clazz; clazz++) {
        Maybe<MapEntry<key_t, value_t> > next = try_get_floor(key, clazz);
        if(!next.has_value()) {
            break;
        }
        result = next;
    }
    return result;
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> inline Maybe<MapEntry<key_t, value_t> > NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::get_floor_binary_search(key_t key, size_t min_clazz, size_t max_clazz) {
    Maybe<MapEntry<key_t, value_t> > result = Maybe<MapEntry<key_t, value_t> >();
    size_t lo = min_clazz, hi = max_clazz;
    while(lo <= hi) {
        size_t mi = lo + (hi - lo) / 2;
        Maybe<MapEntry<key_t, value_t> > middle = try_get_floor(key, mi);
        if(middle.has_value()) {
            result = middle;
            lo = mi + 1;
        } else {
            hi = mi - 1;
        }
    }
    return result;
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> inline Maybe<MapEntry<key_t, value_t> > NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::get_floor_lucky_guess(key_t key) {
    size_t clazz = get_class(key);
    if(!assume_continous_elements) {
        return get_floor_top_down(key, 0, clazz);
    } else if(lucky_clazz > 0 && clazz >= lucky_clazz * 2 && try_get_floor(key, lucky_clazz).has_value()) {
        return get_floor_binary_search(key, lucky_clazz, clazz);
    } else {
        return get_floor_bottom_up(key, 0, clazz /* use clazz ant not lucky_clazz - 1 here!!!*/);
    }
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> inline Maybe<MapEntry<key_t, value_t> > NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::try_get_floor(key_t key, size_t clazz) {
    key_t reduced_key = reduce(context, key, clazz);
    Maybe<value_t> value = get_dictionary(clazz)->get(reduced_key);
    if(value.has_value()) {
        return Maybe<MapEntry<key_t, value_t> >(MapEntry<key_t, value_t>(reduced_key, value.get_value()));
    } else {
        return Maybe<MapEntry<key_t, value_t> >();
    }    
}

template <class context_t, class key_t, class value_t, int (*hash)(key_t), bool (*equals)(key_t, key_t), size_t (*get_class)(key_t key), key_t (*reduce)(context_t context, key_t key, size_t clazz), bool assume_continous_elements, size_t lucky_clazz> inline HashMap<key_t, value_t, hash, equals>* NavigableHashMap<context_t, key_t, value_t, hash, equals, get_class, reduce, assume_continous_elements, lucky_clazz>::get_dictionary(size_t clazz) {
    size_t index = MAX2((size_t) 0, MIN2(dictionary_count - 1, clazz));
    return dictionaries + index;
}

#endif	/* HASHDICTIONARY_HPP */

