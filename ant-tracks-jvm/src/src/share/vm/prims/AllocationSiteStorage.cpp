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
 * File:   AllocationSiteStorage.cpp
 * Author: phil
 * 
 * Created on October 18, 2014, 11:28 AM
 */

#include "precompiled.hpp"
#include "AllocationSiteStorage.hpp"
#include "AllocationTracingDefinitions.hpp"
#include "oops/oop.inline.hpp"
#include "runtime/objectMonitor.inline.hpp"
#include "AllocatedTypes.hpp"
#include "AllocationTracingSelfMonitoring.hpp"

#ifdef ASSERT
#include "classfile/systemDictionary.hpp"
#endif

void AllocationSiteStorage::store(Thread* thread, oop obj, AllocationSiteIdentifier allocation_site) {
    //this method assumes that it is the only one manipulating the object header!!!
    assert(TraceObjectsSaveAllocationSites, "who is calling?");
    assert(allocation_site != ALLOCATION_SITE_IDENTIFIER_UNKNOWN, "why do we want to store an unknown allocation site?");
    assert(obj->mark()->is_neutral() || obj->mark()->has_bias_pattern(), "new objects can only have a neutral header (unlocked) or biased (but unlocked)");
    assert(!obj->mark()->is_locked(), "how can newly created object be already locked?");
#ifdef ASSERT
    //there might already be an allocation site if this is called due to a slow path after the object has been allocated in a fast path
    if(obj->mark()->is_neutral()) {
        intptr_t hash = obj->mark()->hash();
        if(hash != markOopDesc::no_hash) {
            AllocationSiteIdentifier allocation_site_extracted = (AllocationSiteIdentifier) ((hash & allocation_site_mask_in_place(hash)) >> allocation_site_shift(hash));
            assert(AllocationSites::equals(allocation_site, allocation_site_extracted, true), "allocation sites do not match");
        }
    } else {
        assert(obj->mark()->has_bias_pattern(), "what else is the object?");
    }
#endif
    intptr_t hash;
    if(SafepointSynchronize::is_at_safepoint()) {
        // allocations at a safepoint will only be fillers which will never need a proper hash code (hopefully), assume 1
#ifdef ASSERT
        bool filler = (allocation_site == ALLOCATION_SITE_IDENTIFIER_TLAB_FILLER || allocation_site == ALLOCATION_SITE_IDENTIFIER_OBJECT_FILLER || allocation_site == ALLOCATION_SITE_IDENTIFIER_ARRAY_FILLER || allocation_site == ALLOCATION_SITE_IDENTIFIER_VM_INTERNAL || allocation_site == ALLOCATION_SITE_IDENTIFIER_VM_GC) && (obj->klass() == Universe::typeArrayKlassObj(T_INT) || obj->klass() == SystemDictionary::Object_klass());
        bool deadspace = (allocation_site == ALLOCATION_SITE_IDENTIFIER_VM_GC_DEAD_SPACE) && (obj->klass() == Universe::typeArrayKlassObj(T_INT) || obj->klass() == SystemDictionary::Object_klass());
        bool zombie = (allocation_site == ALLOCATION_SITE_IDENTIFIER_VM_GC_SCAVENGE_ZOMBIE);
        assert(filler || deadspace || zombie, "neither a filler, nor a deadspace, nor a zombie");
#endif
        hash = 1;
    } else if(TraceObjectsHashCodeElimination && allocation_site != ALLOCATION_SITE_IDENTIFIER_VM_INTERNAL && AllocatedTypes::has_custom_hash_code(obj->klass())) { //this method may run during VM startup, has_custom_hash_code may not be always called with a properly initialized class
        hash = 0;
        self_monitoring(2) {
            thread->get_self_monitoring_data()->hashes_eliminated++;
        }
    } else {
        hash = ObjectSynchronizer::FastHashCode(thread, obj);
        assert(hash == obj->mark()->hash(), "hash code must be easily accessible now");
    }
    hash = hash & ~allocation_site_mask_in_place(allocation_site);
    hash = hash | (((intptr_t) allocation_site) << allocation_site_shift(allocation_site));
    assert(obj->mark()->is_neutral(), "otherwise we cannot simply replace the hash code as we do");
    assert(hash != markOopDesc::no_hash, "cannot store no_hash");
    obj->set_mark(obj->mark()->copy_set_hash(hash));
    
    assert(allocation_site == load(thread, obj), "cannot load site just stored");
}

AllocationSiteIdentifier AllocationSiteStorage::load(Thread* thread, oop obj, bool is_mark_valid) {
    //this method assumes that it is the only one manipulating the object header!!!
    assert(TraceObjectsSaveAllocationSites, "who is calling?");
    assert(obj->is_oop(), "just checking");
    
    markOop mark = obj->mark();
    if(!is_mark_valid) {
        size_t count = MarkSweep::_preserved_count;
        StackIterator<oop, mtGC> oopIterator = StackIterator<oop, mtGC>(MarkSweep::_preserved_oop_stack);
        StackIterator<markOop, mtGC> markIterator = StackIterator<markOop, mtGC>(MarkSweep::_preserved_mark_stack);
        size_t i = 0;
        while(i < count) {
            oop candidate = oopIterator.next();
            mark = markIterator.next();
            if(candidate == obj) {
                break;
            }
            i++;
        }
        if(i == count && TraceObjectsSaveAllocationSiteAllowUnknown) {
            return ALLOCATION_SITE_IDENTIFIER_UNKNOWN;
        } else {
            assert(i < count, "could not find preserved mark of object with invalid mark");
        }
    } else if (mark->is_neutral()) {
        //nothing to do
    } else if (mark->has_displaced_mark()) {
        mark = mark->displaced_mark_helper();
    } else if (mark->has_monitor()) {
        mark = mark->monitor()->header();
    } else if (thread->is_lock_owned((address) mark->locker())) {
        mark = mark->displaced_mark_helper();
    } else if (mark->has_locker()) {
        mark = mark->locker()->displaced_header();
    } else {
        assert(false, "actually, we do not want to generate a hashcode, just access it ...");
        ObjectSynchronizer::FastHashCode(thread, obj);
        mark = obj->mark();
    }
    assert(mark->is_neutral(), "mark is not neutral");
    intptr_t hash = mark->hash();
    if(hash == markOopDesc::no_hash) {
        return ALLOCATION_SITE_IDENTIFIER_UNKNOWN;
    }
    assert(hash != markOopDesc::no_hash, "why is there no_hash?");
    AllocationSiteIdentifier id = (AllocationSiteIdentifier) ((hash & allocation_site_mask_in_place(hash)) >> allocation_site_shift(hash));
    return id;
}
