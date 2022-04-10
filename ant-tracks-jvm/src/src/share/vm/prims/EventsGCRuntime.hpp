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
 * File:   EventsGCRuntime.hpp
 * Author: Philipp Lengauer
 *
 * Created on April 2, 2014, 1:43 PM
 */

#ifndef EVENTSGCRUNTIME_HPP
#define	EVENTSGCRUNTIME_HPP

#include "EventsRuntime.hpp"
#include "../gc_interface/gcCause.hpp"
#include "gc_implementation/shared/mutableSpace.hpp"
#include "AllocationTracingDefinitions.hpp"
#include "EventSynchronization.hpp"
#include "ClosureCollection.hpp"
#include "../gc_implementation/parallelScavenge/parMarkBitMap.hpp"

class ParMarkBitMap;

struct SpaceRedefinitionInfo {
    uint index;
    HeapWord* bottom;
    HeapWord* end;
};

struct SpaceDestroyInfo {
    uint index;
    uint count;
};

void flush_g1_pointers(HeapWord *first_from, HeapWord *first_to, ParMarkBitMap* bitmap, int count);

void add_obj_pointers(HeapWord* addr);

class EventsGCRuntime : public AllStatic {
    friend class PostponeSpaceEventsBlock;
private:
    static bool postpone_space;
    static jlong start;
    static Arena* postponed_space_redefine_arena;
    static Arena* postponed_space_destroy_arena;
    static GrowableArray<SpaceRedefinitionInfo*>* postponed_space_redefines;
    static GrowableArray<SpaceDestroyInfo*>* postponed_space_destroys;
    static size_t objects_in_eden;
    static EventRootPointerList* roots;
    
    static void process_basic_data_type_arrays(Klass* klass);
    static void write_root_pointers();

public:
    static void init();
    static void destroy();
    
    static jint fire_gc_start(GCType type, GCCause::Cause cause, bool concurrent = false, bool gc_only = false, bool allow_rotation = true);
    static void fire_gc_end(GCType type, jint id, GCCause::Cause cause, bool failed = false, bool gc_only = false);
    static void fire_gc_info(jint space_id, jint gc_id);
    static void fire_gc_failed(jint space_id);
    static void fire_gc_interrupt(jint gc_id, HeapWord* addr);
    static void fire_gc_continue(jint gc_id, HeapWord* addr);
    static void fire_plab_alloc(HeapWord* addr, size_t size);
    static void fire_plab_flushed(HeapWord* addr, oop filler);
    static inline void fire_gc_keep_alive(oop addr, int size = 0, bool is_mark_valid = true);
    static inline void fire_gc_keep_alive_region(HeapWord* from, HeapWord* to, bool are_marks_valid = true);
    static inline void fire_gc_move_slow(oop from, oop to, int size = 0);
    static void fire_gc_move_region(oop from, oop to, jint num_of_objects, ParMarkBitMap* bitmap);
    static inline void fire_gc_move_fast(oop from, oop to, SpaceType to_addr);
    static inline void fire_gc_filler_alloc(oop filler);
    static inline void fire_gc_move_ptr(EventType event, uintptr_t referee, uintptr_t from_addr, SpaceType to_space);
    static inline void fire_gc_ptr(EventType event, uintptr_t obj, bool obj_is_inside_heap);

    static void sync();

public:
    static void schedule_space_redefine(uint index, HeapWord* bottom, HeapWord* end, bool just_expanded = true);
    static void schedule_space_redefine(uint index, HeapWord* bottom, HeapWord* end, HeapWord* bottom_old, HeapWord* end_old) {
        schedule_space_redefine(index, bottom, end, bottom <= bottom_old && end_old <= end);
    }
    static void schedule_space_destroyed(uint index, uint count);
private:
    static void fire_postponed_space_events();
    
    static size_t count_eden_objects();
    static size_t count_eden_survivors();
#ifdef ASSERT
private:
    static Monitor* lock;
    static Arena* arena;
    static Dict* handled_objects;
    volatile static int32 _collectioncounter;
    static HeapWord* old_watermark;
    static void add_handled_object(oop src, oop dest, bool allow_to_be_handled_again = false);
    static void add_handled_objects(HeapRegion* region, bool is_prepared, bool allow_to_be_handled_again = false);
    static void assert_handled_object(oop obj);
    static void assert_handled_object_space(oop obj);
    static bool clear_handled_objects(bool should_be_empty);
    static void clear_objects_in(Space* space);
    static void verify_all_objects_handled();
    static void verify_all_objects_handled_in(Space* space, HeapWord* first = NULL);
    static void verify_all_objects_handled_in(MutableSpace* space, HeapWord* first = NULL);
    static void verify_all_objects_handled_in(Generation* generation, HeapWord* first = NULL);
    static void verify_all_objects_handled_in(CollectedHeap* heap);
    
    static uint32 nr_of_objects_traced(Space* space);
    
    static void verify_allocation_sites();
    
    static void verify_pointers(oop obj);

public:
    static void dump_region(HeapWord* from, HeapWord* to);
    
    static bool is_gc_active();
#endif
};

inline void EventsGCRuntime::fire_gc_keep_alive(oop addr, int size, bool is_mark_valid) {
    if(EventSynchronization::is_synchronizing()) {
        EventsRuntime::fire_sync_obj(addr, size, is_mark_valid);
    } else {
        EventsRuntime::fire_gc_move_slow(addr);
    }
#ifdef ASSERT
    if(TraceObjectsGCEnableParanoidAssertions) {
        add_handled_object(addr, addr);
    }
#endif
}

inline void EventsGCRuntime::fire_gc_keep_alive_region(HeapWord* from, HeapWord* to, bool are_marks_valid) {
    for(HeapWord* obj = from; obj < to; obj = obj + oop(obj)->size()) {
        assert(oop(obj)->is_oop(false), "wtf");
        EventsGCRuntime::fire_gc_keep_alive(oop(obj), 0, are_marks_valid);
    }
}

inline void EventsGCRuntime::fire_gc_move_slow(oop from, oop to, int size) {   
    if(EventSynchronization::is_synchronizing()) {
        EventsRuntime::fire_sync_obj(from, to, size, true);
    } else {
        EventsRuntime::fire_gc_move_slow(from, to);
    }
#ifdef ASSERT
    if(TraceObjectsGCEnableParanoidAssertions) {
        add_handled_object(from, to);
    }
#endif
}

inline void EventsGCRuntime::fire_gc_move_fast(oop from, oop to, SpaceType to_space) {
    if(EventSynchronization::is_synchronizing()) {
        EventsRuntime::fire_sync_obj(from, to, 0, true);
    } else {
        EventsRuntime::fire_gc_move_fast(from, to_space);
    }
#ifdef ASSERT
    if(TraceObjectsGCEnableParanoidAssertions) {
        add_handled_object(from, to);
    }
#endif
}

inline void EventsGCRuntime::fire_gc_filler_alloc(oop filler) {
    //actually, this method doesn't fire an event but is only used for consistency checking
#ifdef ASSERT
    if(TraceObjectsGCEnableParanoidAssertions) {
        add_handled_object(filler, filler, true);
    }
#endif
}

inline void EventsGCRuntime::fire_gc_move_ptr(EventType event, uintptr_t referee, uintptr_t from_addr, SpaceType to_space){
    HeapWord* from = (HeapWord*) from_addr;
    HeapWord* to = (HeapWord*) referee;

#ifdef ASSERT
    if (UseG1GC) {
      // because G1 first adjusts all pointers during MarkSweep::phase3,
      // and then copies them in MarkSweep::phase4, it may happen that we record
      // a pointer into a HeapRegion, which has not yet been copied there yet.
      // This behaviour lets the Closure used in verify_pointers fail, as it checks
      // if the pointer is in the allocated part of the region - which it may not be.
      //
      // To simply ignore this check would be wrong, but I'll do the next best thing:
      // Check if the object fits into a region as a whole.
      /*G1CollectedHeap *g1h = G1CollectedHeap::heap();
      
      HeapRegion *reg = g1h->heap_region_containing(to);
      HeapWord *reg_end = reg->end();
      int size = oop(to)->size();
      assert(reg->is_in_reserved(to), err_msg(""PTR_FORMAT" does not fit in HeapRegion#%u", (uintptr_t) to, reg->hrm_index()));
      assert(((to + size) < reg_end),
              err_msg(""INTPTR_FORMAT" does not fit into HeapRegion#%u as a whole, obj=["PTR_FORMAT";"PTR_FORMAT"[ HeapRegion=["PTR_FORMAT";"PTR_FORMAT"[",
              (uintptr_t) to, reg->hrm_index(), (uintptr_t) to, (uintptr_t) (to + size), (uintptr_t) reg->bottom(), (uintptr_t) reg_end));
       */
    } else {
      verify_pointers(oop(to));
    }
#endif
    
    if(event == EVENTS_GC_MOVE_FAST){
        EventsRuntime::fire_gc_move_fast_ptr(from, to, to_space);
    } else {
        EventsRuntime::fire_gc_move_slow_ptr(event, from, to);
    }
#ifdef ASSERT
    if (TraceObjectsGCEnableParanoidAssertions) {
        add_handled_object((oop) from, (oop) to);
    }
#endif
}


inline void EventsGCRuntime::fire_gc_ptr(EventType event, uintptr_t obj, bool obj_is_inside_heap){
    EventsRuntime::fire_gc_obj_ptr(event, (HeapWord*) obj);        
}

class GCMove : public StackObj {
private:
    ParMarkBitMap* bitmap;
    jint count;
    HeapWord *first_from, *first_to;
    HeapWord *next_from, *next_to;
#ifdef ASSERT
    jint moves;
    jint calls;
#endif
    
    inline void flush() {
        if(count > 0) {
            if (TraceObjectsPointers && UseG1GC) {
              flush_g1_pointers(first_from, first_to, bitmap, count);
            } else {
              EventsGCRuntime::fire_gc_move_region(oop(first_from), oop(first_to), count, bitmap);
            }
#ifdef ASSERT
            moves += count;
#endif
            count = 0;
            first_from = NULL;
            first_to = NULL;
            next_from = NULL;
            next_to = NULL;
        }
    }
    
public:
    inline GCMove(ParMarkBitMap* bitmap = NULL) : bitmap(bitmap), count(0), first_from(NULL), first_to(NULL), next_from(NULL), next_to(NULL)
#ifdef ASSERT
    , moves(0), calls(0)
#endif
    {}
    
    inline void fire(oop from, oop to, size_t size) {
        fire((HeapWord*) from, (HeapWord*) to, size);
    }
    
    inline ~GCMove() {
        if(TraceObjectsGC) {
            flush();
            assert(count == 0, "just checking");
            assert(calls == moves, "just checking");
        }
    }
    
    inline void fire(HeapWord* from, HeapWord* to, size_t size) {
        if(TraceObjectsGC) {
            assert(from != NULL, "just checking");
            assert(to != NULL, "just checking");
            assert(size > 0, "just checking");
#ifdef ASSERT
            calls++;
#endif
            if(next_from == from && next_to == to) {
                //nothing to do, just move along ...
                assert(count > 0, "just checking");
            } else {
                // The following assumption does not seem right, therefor assert removed: a single thread can only traverse left to right, i.e, move objects left
                // assert(next_from < from || next_to < to, "heap may only be traversed left to right");
                flush();
                first_from = from;
                first_to = to;
                next_from = first_from;
                next_to = first_to;
            }
            next_from = next_from + size;
            next_to = next_to + size;
            count++;
            if(0 < TraceObjectsGCMoveRegionALot && TraceObjectsGCMoveRegionALot <= count) {
                flush();
            }
        }
    }
};

class SpaceRedefinition : public StackObj {
private:
    uint index;
    MutableSpace* space;
    HeapWord *bottom, *end;
public:
    inline  SpaceRedefinition(uint index, MutableSpace* space) : index(index), space(space), bottom(space->bottom()), end(space->end()) {}
    inline ~SpaceRedefinition() {
        if(TraceObjects && (bottom != space->bottom() || end != space->end())) {
            EventsGCRuntime::schedule_space_redefine(index, space->bottom(), space->end(), bottom, end);
        }
    }
};

class PostponeSpaceEventsBlock : public BlockControl {
public:
    inline PostponeSpaceEventsBlock(int dummy) {
        EventsGCRuntime::postpone_space = true;
    }
    
    inline ~PostponeSpaceEventsBlock() {
        EventsGCRuntime::postpone_space = false;
    }
};

#define postpone_space_events _block_(PostponeSpaceEventsBlock, 0)
#endif	/* EVENTSGCRUNTIME_HPP */

