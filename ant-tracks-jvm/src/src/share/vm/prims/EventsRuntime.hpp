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
 * File:   EventsRuntime.hpp
 * Author: Verena Bitto
 *
 * Created on November 27, 2013, 9:22 AM
 */

#ifndef EVENTSRUNTIME_HPP
#define	EVENTSRUNTIME_HPP

#include "../oops/oop.hpp"
#include "../oops/markOop.hpp"
#include "../gc_interface/gcCause.hpp"
#include "AllocationSites.hpp"
#include "AllocationTracingUtil.hpp"
#include "EventBuffersFlushAll.hpp"
#include "EventRootPointerList.hpp"

class EventPointers;

typedef jbyte EventType;
#define DEFINE_EVENT(type, name) const EventType name = type;

// Value in braces represents amount of bits
DEFINE_EVENT(0x00, EVENTS_NOP)
DEFINE_EVENT(0x01, EVENTS_MARK)
DEFINE_EVENT(0x02, EVENTS_GC_START)                                // [ EVENT_TYPE (6) | _ (9) | GC_TYPE (1) | _ (13) | NO_OF_SPACES (3) | ( ADDR (64) | LEN (32) )^NO_OF_SPACES  
DEFINE_EVENT(0x03, EVENTS_GC_END)                                  // [ EVENT_TYPE (6) | _ (9) | GC_TYPE (1) | _ (13) | NO_OF_SPACES (3) | ( ADDR (64) | LEN (32) )^NO_OF_SPACES  
DEFINE_EVENT(0x04, EVENTS_GC_INFO)
DEFINE_EVENT(0x05, EVENTS_GC_FAILED)
DEFINE_EVENT(0x06, EVENTS_SPACE_CREATE)                            // [ EVENT_TYPE (6) | SPACE_TYPE (4) | _ (22) | INDEX (32) | SIZE (64) | ADDR (64) ]
DEFINE_EVENT(0x07, EVENTS_SPACE_ALLOC)                             // [ EVENT_TYPE (6) | SPACE_TYPE (4) | _ (22) | INDEX (32) ]
DEFINE_EVENT(0x08, EVENTS_SPACE_RELEASE)                           // [ EVENT_TYPE (6) | _ (26) | INDEX (32) ]
DEFINE_EVENT(0x09, EVENTS_SPACE_REDEFINE)                          // [ EVENT_TYPE (6) | _ (26) | INDEX (32) | START (64) | SIZE (64) ]
DEFINE_EVENT(0x0A, EVENTS_SPACE_DESTROY)                           // [ EVENT_TYPE (6) | _ (26) | FIRST_INDEX (32) | NO_OF_REGIONS (64) ]
DEFINE_EVENT(0x0B, EVENTS_THREAD_ALIVE)
DEFINE_EVENT(0x0C, EVENTS_THREAD_DEATH)
DEFINE_EVENT(0x0D, EVENTS_TLAB_ALLOC)                               // [ EVENT_TYPE (6) | _ (26) | SIZE (64) || ADDR (64) ]
DEFINE_EVENT(0x0E, EVENTS_PLAB_ALLOC)                               // [ EVENT_TYPE (6) | _ (26) | SIZE (64) | ADDR (64) ]
DEFINE_EVENT(0x0F, EVENTS_ALLOC_SLOW)                               // [ EVENT_TYPE (6) | _ (2) | ALLOC_SITE (16) | LEN < ARRAY_LENGTH_MAX_SMALL (8)] || ADDR (64) { || LENGTH (32) } { || ALLOCATED_TYPE (32) } ]
DEFINE_EVENT(0x10, EVENTS_IR_ALLOC_SLOW)                            // [ EVENT_TYPE (6) | _ (2) | ALLOC_SITE (16) | LEN < ARRAY_LENGTH_MAX_SMALL (8)] || ADDR (64) { || LENGTH (32) } ]
DEFINE_EVENT(0x11, EVENTS_IR_ALLOC_SLOW_DEVIANT_TYPE)               // like EVENTS_IR_ALLOC_SLOW but with a type id appended
DEFINE_EVENT(0x12, EVENTS_IR_ALLOC_NORMAL)                          // [ EVENT_TYPE (6) | _ (2) | ALLOC_SITE (16) | _ (8) | ADDR (64) ]
DEFINE_EVENT(0x13, EVENTS_IR_ALLOC_FAST)                            // [ EVENT_TYPE (6) | _ (2) | ALLOC_SITE (16) | _ (8)]
DEFINE_EVENT(0x14, EVENTS_C1_ALLOC_SLOW)                            // [ EVENT_TYPE (6) | _ (2) | ALLOC_SITE (16) | LEN < ARRAY_LENGTH_MAX_SMALL (8)] || ADDR (64) { || LENGTH (32) } ]
DEFINE_EVENT(0x15, EVENTS_C1_ALLOC_SLOW_DEVIANT_TYPE)               // like EVENTS_C1_ALLOC_SLOW but with a type id appended 
DEFINE_EVENT(0x16, EVENTS_C1_ALLOC_NORMAL)                          // [ EVENT_TYPE (6) | _ (2) | ALLOC_SITE (16) | LEN < ARRAY_LENGTH_MAX_SMALL (8)] || ADDR (64) { || LENGTH (32) } ]
DEFINE_EVENT(0x17, EVENTS_C1_ALLOC_FAST)                            // [ EVENT_TYPE (6) | _ (2) | ALLOC_SITE (16) | _ (8) { || LENGTH (32) } ]
DEFINE_EVENT(0x18, EVENTS_C1_ALLOC_FAST_DEVIANT_TYPE)
DEFINE_EVENT(0x19, EVENTS_C2_ALLOC_SLOW)
DEFINE_EVENT(0x1A, EVENTS_C2_ALLOC_SLOW_DEVIANT_TYPE)
DEFINE_EVENT(0x1B, EVENTS_C2_ALLOC_NORMAL)
DEFINE_EVENT(0x1C, EVENTS_C2_ALLOC_FAST)
DEFINE_EVENT(0x1D, EVENTS_C2_ALLOC_FAST_DEVIANT_TYPE)
DEFINE_EVENT(0x1E, EVENTS_GC_MOVE_SLOW)                            // [ EVENT_TYPE (6) | _ (26) | FROM (64) | TO (64) ]
DEFINE_EVENT(0x1F, EVENTS_GC_MOVE_FAST_WIDE)
DEFINE_EVENT(0x20, EVENTS_GC_MOVE_FAST)                            // [ EVENT_TYPE (6) | _ (22) | DEST_SPACE_TYPE (2) | FROM (32) ]
DEFINE_EVENT(0x21, EVENTS_GC_MOVE_FAST_NARROW)                     // [ EVENT_TYPE (6) | _ (2) | FROM (22) | DEST_SPACE_TYPE (2) ]
DEFINE_EVENT(0x22, EVENTS_GC_MOVE_REGION)                          // [ EVENT_TYPE (6) | _ (2) | NO_OF_OBJECTS (24) | FROM (64) | TO (64) ]
DEFINE_EVENT(0x23, EVENTS_GC_KEEP_ALIVE)                           // [ EVENT_TYPE (6) | _ (26) | ADDR (64) ]
DEFINE_EVENT(0x24, EVENTS_SYNC_OBJ)                                // [ EVENT_TYPE (6) | _ (2) | TYPE (24) | ADDR (30) | SPACE_ID (2) ( | ARRAY_LENGTH )]
DEFINE_EVENT(0x25, EVENTS_SYNC_OBJ_NARROW)
DEFINE_EVENT(0x26, EVENTS_DEALLOCATION)

DEFINE_EVENT(0x27, EVENTS_GC_MOVE_SLOW_PTR)                        // [ EVENT_TYPE (6) | _ (2) | PTR_KINDS_ENCODING (24) | FROM (64) | TO (64) | PTRS (VAR up to 12 * 64) ]
DEFINE_EVENT(0x28, EVENTS_GC_MOVE_FAST_WIDE_PTR)                   // [ EVENT_TYPE (6) | DEST_SPACE_TYPE (2) | PTR_KINDS_ENCODING (24) | FROM (64) | PTRS (VAR up to 12 * 64) ]
DEFINE_EVENT(0x29, EVENTS_GC_MOVE_FAST_PTR)                        // [ EVENT_TYPE (6) | DEST_SPACE_TYPE (2) | PTR_KINDS_ENCODING (24) | FROM (32) | PTRS (VAR up to 12 * 64) ]
DEFINE_EVENT(0x2A, EVENTS_GC_KEEP_ALIVE_PTR)                       // [ EVENT_TYPE (6) | _ (2) | PTR_KINDS_ENCODING (24) | ADDR (64) | PTRS (VAR up to 12 * 64) ]
DEFINE_EVENT(0x2B, EVENTS_GC_PTR_EXTENSION)                        // [ EVENT_TYPE (6) | _ (2) | PTR_KINDS_ENCODING (24) | ROOT (64) | PTRS (VAR up to 12 * 64) ]
DEFINE_EVENT(0x2C, EVENTS_GC_PTR_MULTITHREADED)                    // [ EVENT_TYPE (6) | _ (2) | PTR_KINDS_ENCODING (24) | ROOT (64) | PTRS (VAR up to 12 * 64) ]
DEFINE_EVENT(0x2D, EVENTS_GC_ROOT_PTR)                             // [ EVENT_TYPE (6) | _ (2) | PTR_KINDS_ENCODING (24) | ROOT (64) | PTRS (VAR up to 12 * 64) ]
DEFINE_EVENT(0x2E, EVENTS_GC_UPDATE_PTR_PREMOVE)                   // [ EVENT_TYPE (6) | _ (2) | PTR_KINDS_ENCODING (24) | ROOT (64) | PTRS (VAR up to 12 * 64) ]

DEFINE_EVENT(0x2F, EVENTS_GC_INTERRUPT)
DEFINE_EVENT(0x30, EVENTS_GC_CONTINUE)

DEFINE_EVENT(0x31, EVENTS_GC_UPDATE_PTR_POSTMOVE)
DEFINE_EVENT(0x32, EVENTS_TAG)

#define EVENT_COUNT (0x32 + 1)
//#define MAX_EVENT_SIZE 33 // Should be EVENTS_GC_MOVE_SLOW_PTR with 12 absolute pointers + TraceObjectsInsertAnchors (6+2+24+64+64+12*64 bit) + 64 bit -> 33 words
#define MAX_EVENT_SIZE 200


#define NO_TO_SPACE 0
#define NO_REGION 0
#define NO_ENCODED_FROM_ADDR 0
        
#define GENERATIONAL_GC_GEN_0_EDEN_ID 0
#define GENERATIONAL_GC_GEN_0_SURVIVOR_1_ID 1
#define GENERATIONAL_GC_GEN_0_SURVIVOR_2_ID 2
#define GENERATIONAL_GC_GEN_1_OLD_ID 3

#define PARALLEL_GC_OLD_ID 0
#define PARALLEL_GC_EDEN_ID 1
#define PARALLEL_GC_SURVIVOR_1_ID 2
#define PARALLEL_GC_SURVIVOR_2_ID 3

#define ARRAY_LENGTH_MAX_SMALL 0xFF

typedef jbyte SpaceMode;
#define SPACE_MODE_NORMAL 0
#define SPACE_MODE_HUMONGOUS_START 1
#define SPACE_MODE_HUMONGOUS_CONTINUES 2

typedef jbyte SpaceType;
#define EDEN_SPACE 0
#define SURVIVOR_SPACE 1
#define OLD_SPACE 2

typedef jbyte GCType;
#define GCType_Minor 0
#define GCType_Major 1
#define GCType_Major_Sync 2
#define GCType_Minor_Sync 3

#define DEBUG_ANCHOR_PROTOTYPE 0xFFFF00FF
#define get_debug_anchor(event_type) (DEBUG_ANCHOR_PROTOTYPE | ((event_type) << 8))

class EventsRuntime : public AllStatic {
private:
    static HeapWord* rel_addr_base;
public:
    static jint create_obj_alloc_fast_prototype(AllocationSiteIdentifier allocation_site, EventType event_type);
    static void fire_obj_alloc_fast_no_arrays(JavaThread* thread, oop obj, AllocationSiteIdentifier allocation_site, EventType event_type);
    static void fire_obj_alloc_normal_no_arrays(JavaThread* thread, oop obj, AllocationSiteIdentifier allocation_site, EventType event_type);
    static void fire_obj_alloc_fast(JavaThread* thread, oop obj, jlong event);
    static void fire_obj_alloc_fast(JavaThread* thread, oop obj, AllocationSiteIdentifier allocation_site, EventType event_type);
    static void fire_obj_alloc_normal(JavaThread* thread, oop obj, AllocationSiteIdentifier allocation_site, EventType event_type);
    static void fire_obj_alloc_slow(Thread* thread, oop obj, AllocationSiteIdentifier allocation_site, EventType event_type, size_t size = 0);
    static void fire_mirror_obj_alloc(Thread* thread, oop obj, size_t size, Klass* class_klass, Klass* mirrored_klass);
    static void fire_tlab_alloc(HeapWord* addr, size_t size);
    static void fire_plab_alloc(HeapWord* addr, size_t size);
    static void fire_gc_start_end(EventType event_type, jint id, GCType gc_type, GCCause::Cause gc_cause, bool concurrent = false, bool failed = false, bool force_flush = false);
    static void fire_gc_interrupt_continue(EventType event_type, jint gc_id, HeapWord* addr);
    static void fire_gc_info(uint collected_region, jint id);
    static void fire_gc_failed(uint collected_region);
    static void fire_gc_move_slow(oop addr);
    static void fire_gc_move_slow(oop from_addr, oop to_addr);
    static void fire_gc_move_slow_ptr(EventType event_type, HeapWord* from_addr, HeapWord* to_addr);
    static void fire_gc_move_region(oop from_addr, oop to_addr, jint num_of_objects);
    static void fire_gc_move_fast(oop from_addr, SpaceType to_space);
    static void fire_gc_move_fast_ptr(HeapWord* from_addr, HeapWord* to_addr, SpaceType to_space);
    static void fire_gc_ptr(jint** local_buffer_top, EventPointerList* ptr_lists);
    static void fire_gc_root_ptrs(EventRootPointerList* roots);
    static void fire_gc_obj_ptr(EventType event_type, HeapWord* obj);
    static void fire_sync_obj(oop from, oop obj, int size, bool is_mark_valid);
    static void fire_sync_obj(oop obj, int size, bool is_mark_valid);
    static void fire_sync(bool full = false);
    static void fire_thread_alive(Thread* thread);
    static void fire_thread_death(Thread* thread);
    static void fire_mark(jlong id);
    static void fire_mirror_klass_write(oop java_class, Klass* k);
    
    static void fire_space_creation(uint id, HeapWord* bottom, HeapWord* end);
    static void fire_space_alloc(uint id, SpaceMode mode, SpaceType type);
    static void fire_space_release(uint id);
    static void fire_space_redefine(uint id, HeapWord* bottom, HeapWord* end);
    static void fire_space_destroyed(uint startId, size_t numOfRegions);
    
    static void fire_tag(char* tag);
    
    static bool get_rel_addr(HeapWord* addr, uint* addr_ptr, jint bits = 32);
    static jlong wo_pointer;
    
#ifdef ASSERT
    static bool meta_events_only;
#endif
private:
    static inline void write_obj_alloc_event(jint* local_buffer_top, AllocationSiteIdentifier allocation_site, EventType event_type, jint array_length);
    static inline void write_lab_alloc_event(jint* local_buffer_top, EventType event_type);
    static inline void write_gc_event(jint* local_buffer_top, EventType event_type, GCType gc_type, GCCause::Cause gc_cause, bool concurrent, bool failed);
    static inline void write_gc_move_event(jint* local_buffer_top, EventType event_type, jint num_of_objects);
    static inline void write_gc_move_fast_event(jint* local_buffer_top, EventType event_type, jint from_addr, SpaceType space);
    static inline void write_gc_ptr_event(jint* local_buffer_top, EventType event_type, SpaceType space, uint ptr_kinds);
    static inline void write_gc_ps_pointer(jint* local_buffer_top, jint addr);
    static inline void write_abs_obj_addr(jint* local_buffer_top, oop obj);
    static inline void write_abs_addr(jint* local_buffer_top, HeapWord* addr);
    static inline void write_time(jint* local_buffer_top, jlong time);
    static inline void write_length(jint* local_buffer_top, jint array_size);
    static inline void write_type(jint* local_buffer_top, jint id);
    static inline void write_size(jint* local_buffer_top, size_t size);
    static inline void write_anchor(jint* local_buffer_top, EventType event_type);
    static inline void write_wide_word(jint* local_buffer_top, jlong value);
    static inline void write_word(jint* local_buffer_top, jint value);
    static inline void write_string(jint** local_buffer_top, char* string);
    static jbyte get_thread_type(Thread* thread);
    static inline jint* allocate_event(Thread* thread, jint req_size);
    
    static bool is_allocation_event(EventType event_type);
    static bool is_allocation_fast_event(EventType event_type);
    static bool is_allocation_normal_event(EventType event_type);
    static bool is_allocation_slow_event(EventType event_type);
    static bool is_allocation_slow_with_deviant_type_event(EventType event_type);
    
    static HeapWord* compute_rel_addr_base();
};

class SlowAllocPatcher : public BlockControl {
public:
    SlowAllocPatcher();
    SlowAllocPatcher(Thread* thread, EventType event, Method* method, intptr_t bytecode_x, Klass* klass);
    SlowAllocPatcher(Thread* thread, EventType event, AllocationSiteIdentifier allocation_site, Klass* klass, bool relaxed);
    SlowAllocPatcher(Thread* thread, EventType event, AllocationSiteIdentifier allocation_site);
    ~SlowAllocPatcher();
private:
    Thread* thread;
    EventType prev_event_type;
    AllocationSiteIdentifier prev_allocation_site;
};

#define patch_alloc_slow(thread, event, method, bcx, klass) _aware_lazy_block_(SlowAllocPatcher, thread, event, method, (intptr_t) (bcx), klass)
#define patch_alloc_slow_relaxed(thread, event, allocation_site, klass) _aware_lazy_block_(SlowAllocPatcher, thread, event, allocation_site, klass, true)
#define patch_alloc_slow_with_allocation_site(thread, event, allocation_site, klass) _aware_lazy_block_(SlowAllocPatcher, thread, event, allocation_site, klass, false)

#endif	/* EVENTSRUNTIME_HPP */

