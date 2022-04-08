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
 * File:   PrefixTree.hpp
 * Author: Philipp Lengauer
 *
 * Created on October 4, 2014, 5:28 PM
 */

#ifndef PREFIXTREE_HPP
#define	PREFIXTREE_HPP

#include "Map.hpp"
#include "InflatableMap.hpp"

typedef unsigned int PrefixTreeMapNodeID;
#define PrefixTreeMapNodeID_NO_NODE 0
#define PrefixTreeMapNodeID_ROOT 1

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> class PrefixTreeMap : public NavigableMap<key_t, value_t> {
private:
    struct Node {
        Maybe<MapEntry<key_t, value_t> > entry;
        InflatableMap<key_element_t, PrefixTreeMapNodeID, equals_element, 4, create_map> children;
    };
    
    Arena* arena;
    
    Node* nodes;
    size_t nodes_capacity;
    size_t node_count;
public:
    PrefixTreeMap(Arena* arena, size_t init_capacity = 1);
    ~PrefixTreeMap();
    
    size_t get_size();
    Maybe<value_t> get(key_t ke);
    Maybe<MapEntry<key_t, value_t> > get_floor(key_t key);
    
    bool put(key_t key, value_t value);
    bool remove(key_t key);
    
    void clear();
    
private:
    PrefixTreeMapNodeID get_or_put(PrefixTreeMapNodeID parent, key_t key, int index, bool* create_ptr);

    PrefixTreeMapNodeID new_node();
    PrefixTreeMapNodeID allocate_node();
    void* get_node(PrefixTreeMapNodeID id);
};

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::PrefixTreeMap(Arena* arena, size_t init_capacity) : arena(new(mtOther) Arena(mtOther)), nodes(NULL), nodes_capacity(0), node_count(0) {
    nodes_capacity = init_capacity;
    nodes = (Node*) malloc(init_capacity * sizeof(Node));
    clear();
}

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::~PrefixTreeMap() {
    clear();
    free(nodes);
    nodes = NULL;
}

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> size_t PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::get_size() {
    return node_count - 1;
}

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> inline Maybe<value_t> PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::get(key_t key) {
    Maybe<MapEntry<key_t, value_t> > result = get_floor(key);
    if(result.has_value() && get_length(result.get_value().get_key()) == get_length(key)) {
        return result.get_value().get_value();
    } else {
        return Maybe<value_t>();
    }
}

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> inline Maybe<MapEntry<key_t, value_t> > PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::get_floor(key_t key) {
    size_t length = get_length(key);
    PrefixTreeMapNodeID last_node = PrefixTreeMapNodeID_ROOT;
    {
        bool create = false;
        PrefixTreeMapNodeID node = last_node;
        for(size_t index = 0; index < length && node != PrefixTreeMapNodeID_NO_NODE; index++) {
            node = get_or_put(node, key, (int) index, &create);
            if(node != PrefixTreeMapNodeID_NO_NODE && ((Node*) get_node(node))->entry.has_value()) {
                last_node = node;
            }
        }
    }
    if(((Node*) get_node(last_node))->entry.has_value() && last_node != PrefixTreeMapNodeID_NO_NODE) {
        Node* node_data = (Node*) get_node(last_node);
        assert(node_data->entry.has_value(), "no floor");
        key_t floor_key = node_data->entry.get_value().get_key();
#ifdef ASSERT
        assert(get_length(node_data->entry.get_value().get_key()) <= length, "key cannot be floor (longer)");
        for(size_t index = 0; index < get_length(floor_key); index++) {
            assert(get_element(key, (int) index) == get_element(key, (int) index), "key not floor (element mismatch)");
        }
#endif
        value_t floor_value = node_data->entry.get_value().get_value();
        return Maybe<MapEntry<key_t, value_t> >(MapEntry<key_t, value_t>(floor_key, floor_value));
    } else {
        return Maybe<MapEntry<key_t, value_t> >();
    }
}

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> inline bool PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::put(key_t key, value_t value) {
    size_t length = get_length(key);
    bool create = false;
    PrefixTreeMapNodeID node = PrefixTreeMapNodeID_ROOT;
    for(size_t index = 0; index < length; index++) {
        create = true;
        node = get_or_put(node, key, (int) index, &create);
    }
    if(create) {
        assert(node != PrefixTreeMapNodeID_NO_NODE, "why is there no node if create was true?");
        Node* node_data = (Node*) get_node(node);
        node_data->entry = MapEntry<key_t, value_t>(key, value);
    }
    return create;
}

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> bool PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::remove(key_t key) {
    //TODO
    return false;
}

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> void PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::clear() {
    node_count = 0;
    arena->destruct_contents();

    PrefixTreeMapNodeID root_id = allocate_node();
    assert(root_id == PrefixTreeMapNodeID_ROOT, "internal error");
    Node* root = (Node*) get_node(root_id);
    root->entry = Maybe<MapEntry<key_t, value_t> >();
    new(&root->children) InflatableMap<key_element_t, PrefixTreeMapNodeID, equals_element, 4, create_map>();
}

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> inline PrefixTreeMapNodeID PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::get_or_put(PrefixTreeMapNodeID parent, key_t key, int index, bool* create_ptr) {
    key_element_t element = get_element(key, index);
    Maybe<PrefixTreeMapNodeID> maybe = ((Node*) get_node(parent))->children.get(element);
    PrefixTreeMapNodeID node = maybe.has_value() ? maybe.get_value() : PrefixTreeMapNodeID_NO_NODE;
    if(node != PrefixTreeMapNodeID_NO_NODE) {
        *create_ptr = false;
    } else {
        if(*create_ptr) {
            node = new_node();
            ((Node*) get_node(parent))->children.put(element, node, arena);
            *create_ptr = true;
        } else {
            node = PrefixTreeMapNodeID_NO_NODE;
            *create_ptr = false;
        }
    }
    return node;
}

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> inline PrefixTreeMapNodeID PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::new_node() {
    PrefixTreeMapNodeID node_id = allocate_node();
    Node* node = (Node*) get_node(node_id);
    node->entry = Maybe<MapEntry<key_t, value_t> >();
    new(&node->children) InflatableMap<key_element_t, PrefixTreeMapNodeID, equals_element, 4, create_map>();
    return node_id;
}

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> inline PrefixTreeMapNodeID PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::allocate_node() {
    if(node_count == nodes_capacity) {
        nodes_capacity = MAX2((size_t) 1, nodes_capacity * 2);
        nodes = (Node*) realloc(nodes, sizeof(Node) * nodes_capacity);
    }
    return (PrefixTreeMapNodeID) (int) ++node_count;
}

template <class key_t, class key_element_t, class value_t, size_t (*get_length)(key_t key), key_element_t (*get_element)(key_t key, int index), bool (*equals_element)(key_element_t element1, key_element_t element2), Map<key_element_t, PrefixTreeMapNodeID>* (*create_map)(Arena* arena)> inline void* PrefixTreeMap<key_t, key_element_t, value_t, get_length, get_element, equals_element, create_map>::get_node(PrefixTreeMapNodeID id) {
    size_t index = id - 1;
    assert(0 <= index && index < node_count, "illegal id");
    return nodes + index;
}


#endif	/* PREFIXTREE_HPP */

