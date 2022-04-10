/* 
 * Copyright (c) 2014, 2015, 2016, 2017 dynatrace and/or its affiliates. All rights reserved.
 * This file is part of the AntTracks extension for the Hotspot VM. 
 * 
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *figure:common_case_labs
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 * 
 * You should have received a copy of the GNU General Public License
 * along with with this work.  If not, see <http://www.gnu.org/licenses/>.
 * 
 * File:   AllocationTracingSelfMonitoring.cpp
 * Author: Philipp Lengauer
 * 
 * Created on February 12, 2014, 9:47 AM
 */

#include "precompiled.hpp"
#include "AllocationTracingSelfMonitoring.hpp"
#include "EventBuffers.hpp"
#include "AllocationTracing.hpp"

Monitor* AllocationTracingSelfMonitoring::lock = NULL;
elapsedTimer AllocationTracingSelfMonitoring::reset_time = elapsedTimer();
jlong AllocationTracingSelfMonitoring::size = 0;
jlong AllocationTracingSelfMonitoring::symbols_size = 0;
jlong AllocationTracingSelfMonitoring::class_definitions_size = 0;
jlong AllocationTracingSelfMonitoring::buffers_allocated = 0;
jlong AllocationTracingSelfMonitoring::buffers_freed = 0;
jlong AllocationTracingSelfMonitoring::buffers_active = 0;
jlong AllocationTracingSelfMonitoring::buffer_size_active = 0;
jlong AllocationTracingSelfMonitoring::buffer_size_stolen = 0;
jlong AllocationTracingSelfMonitoring::flush_queue_overflows = 0;
jlong AllocationTracingSelfMonitoring::allocation_sites = 0;
jlong AllocationTracingSelfMonitoring::allocated_types = 0;
ArithmeticMean AllocationTracingSelfMonitoring::minor_gc_size = ArithmeticMean();
ArithmeticMean AllocationTracingSelfMonitoring::major_gc_size = ArithmeticMean();
ArithmeticMean AllocationTracingSelfMonitoring::minor_sync_size = ArithmeticMean();
ArithmeticMean AllocationTracingSelfMonitoring::major_sync_size = ArithmeticMean();
jlong AllocationTracingSelfMonitoring::compiled_allocation_sites = 0;
jlong AllocationTracingSelfMonitoring::compiled_allocation_sites_total_depth = 0;
elapsedTimer AllocationTracingSelfMonitoring::cleanup_time = elapsedTimer();
AllocationTracingSelfMonitoringTLSData AllocationTracingSelfMonitoring::tls_data = AllocationTracingSelfMonitoring::create_TLS();
ArithmeticMean AllocationTracingSelfMonitoring::survivor_ratio = ArithmeticMean();
jlong AllocationTracingSelfMonitoring::sync_quality_objects_all[AllocationTracingSelfMonitoring::sync_quality_size];
jlong AllocationTracingSelfMonitoring::sync_quality_objects_before_sync[AllocationTracingSelfMonitoring::sync_quality_size];
jlong AllocationTracingSelfMonitoring::sync_distance = 0;

void AllocationTracingSelfMonitoring::init() {
    reset_time = elapsedTimer();
    lock = new Mutex(Mutex::native, "AllocationTracingSelfMonitoring Lock", true);
    reset_time.start();
}

void AllocationTracingSelfMonitoring::destroy() {
    delete lock; lock = NULL;
}

void AllocationTracingSelfMonitoring::reset() {
    if(TraceObjectsSelfMonitoringCutTraceOnReset) {
        warning("Resetting object trace because -XX:+TraceObjectsSelfMonitoringCutTraceOnReset has been set, the trace will not be parsable!");
        AllocationTracing::get_trace_writer()->reset();
    }
    {
        MutexLockerEx l(lock, true);
        collect_TLSs();

        size = 0;
        symbols_size = 0;
        class_definitions_size = 0;
        buffers_allocated = 0;
        buffers_freed = 0;
        // DO NOT RESET buffers_active!!!
        flush_queue_overflows = 0;
        AllocationTracing::get_trace_writer()->get_io_timer()->reset();
        if(TraceObjectsAsyncIO) {
            EventsWorkerThread* worker = AllocationTracing::get_worker();
            worker->get_self_monitoring_data()->lock_timer.reset();
            worker->get_self_monitoring_data()->wait_timer.reset();
            worker->get_work_timer()->reset();
        }
        allocation_sites = 0;
        allocated_types = 0;
        minor_gc_size.reset();
        major_gc_size.reset();
        minor_sync_size.reset();
        major_sync_size.reset();
        //compiled_allocation_sites = 0;
        //compiled_allocation_sites_total_depth = 0;
        cleanup_time.reset();
        
        reset_TLS(&tls_data);
        
        survivor_ratio.reset();
        for(int i = 0; i < sync_quality_size; i++) {
            sync_quality_objects_all[i] = 0;
            sync_quality_objects_before_sync[i] = 0;
        }
        sync_distance = 0;
        
        reset_time.stop();
        reset_time.reset();
        reset_time.start();
    }
}

void AllocationTracingSelfMonitoring::report_buffer_written(size_t size) {
    MutexLockerEx l(lock, true);
    AllocationTracingSelfMonitoring::size += size;
}

void AllocationTracingSelfMonitoring::report_symbol_written(size_t size) {
    MutexLockerEx l(lock, true);
    AllocationTracingSelfMonitoring::symbols_size += size;
}

void AllocationTracingSelfMonitoring::report_class_definition_written(size_t size) {
    MutexLockerEx l(lock, true);
    AllocationTracingSelfMonitoring::class_definitions_size += size;
}

void AllocationTracingSelfMonitoring::report_buffer_allocated(size_t capacity, size_t allocated_capacity) {
    MutexLockerEx l(lock, true);
    buffers_allocated++;
    buffers_active++;
    buffer_size_active += (capacity * sizeof(jint));
    buffer_size_stolen += (capacity - allocated_capacity) * sizeof(jint);
}

void AllocationTracingSelfMonitoring::report_buffer_freed(size_t capacity) {
    MutexLockerEx l(lock, true);
    buffers_freed++;
    buffers_active--;
    buffer_size_active -= (capacity * sizeof(jint));
}

void AllocationTracingSelfMonitoring::report_flush_queue_overflow() {
    MutexLockerEx l(lock, true);
    flush_queue_overflows++;
}

void AllocationTracingSelfMonitoring::report_new_allocation_site() {
    MutexLockerEx l(lock, true);
    allocation_sites++;
}

void AllocationTracingSelfMonitoring::report_new_allocated_type() {
    MutexLockerEx l(lock, true);
    allocated_types++;
}

void AllocationTracingSelfMonitoring::report_minor_gc(size_t size){
    MutexLockerEx l(lock, true);
    minor_gc_size.submit(size);
}

void AllocationTracingSelfMonitoring::report_major_gc(size_t size){
    MutexLockerEx l(lock, true);
    major_gc_size.submit(size);
}

void AllocationTracingSelfMonitoring::report_minor_sync(size_t size) {
    MutexLockerEx l(lock, true);
    minor_sync_size.submit(size);
}

void AllocationTracingSelfMonitoring::report_major_sync(size_t size) {
    MutexLockerEx l(lock, true);
    major_sync_size.submit(size);
}

void AllocationTracingSelfMonitoring::report_new_compiled_allocation_site(size_t depth) {
    MutexLockerEx l(lock, true);
    compiled_allocation_sites++;
    compiled_allocation_sites_total_depth += depth;
}

void AllocationTracingSelfMonitoring::report_TLS(Thread* thread) {
    MutexLockerEx l(lock, true);
    add_TLS(&tls_data, thread->get_self_monitoring_data());
    reset_TLS(thread->get_self_monitoring_data());
}

/*
 * convert to paranoid assertion when pointers are removed
 */

void AllocationTracingSelfMonitoring::report_ptr_offset(oop o, uintptr_t ptr_abs){
    ParallelScavengeHeap* heap = (ParallelScavengeHeap*) Universe::heap();
    HeapWord* ptr = (HeapWord*) ptr_abs;
    HeapWord* obj = (HeapWord*) o;
    int id = 0, oop_id = 0;

      
    if(heap->old_gen()->object_space()->contains(obj)){
        oop_id = 2;
    }  else if(heap->young_gen()->to_space()->contains(obj)) {
        oop_id = 3;
    } else {
        assert(false, "Obj has not been copied...");
    }

    if(heap->young_gen()->eden_space()->contains(ptr)){
        id = 1;
    } else if(heap->old_gen()->object_space()->contains(ptr)){
        id = 2;
    }  else if(heap->young_gen()->to_space()->contains(ptr)) {
        id = 3;
    }  else if(heap->young_gen()->from_space()->contains(ptr)) {
        id = 4;
    } else {
        assert(false, "Ptr to object is invalid");
    }
      
    if(id == 1 || id == 4){ // ptr_to_patch
    } else { }
      
        
}

void AllocationTracingSelfMonitoring::report_ptr_offset(oop o, uint ptr_rel){
    HeapWord* base = ParallelScavengeHeap::heap()->young_gen()->eden_space()->bottom();
    HeapWord* addr = (HeapWord*) (ptr_rel * sizeof(HeapWord));
    
    HeapWord* ptr_abs = (HeapWord*) (((uintptr_t) base) +((uintptr_t) addr));
    report_ptr_offset(o, (uintptr_t) ptr_abs);
}

void AllocationTracingSelfMonitoring::report_survivor_ratio(jdouble ratio) {
    MutexLockerEx l(lock, true);
    survivor_ratio.submit(ratio);
}

class SetAgeClosure : public ObjectClosure {
private:
    int age;
public:
    SetAgeClosure(int age) : age(age) {}
    void do_object(oop obj) {
        if(obj->has_displaced_mark()) {
            obj->set_displaced_mark(obj->displaced_mark()->set_age(age));
        } else {
            obj->set_mark(obj->mark()->set_age(age));
        }
    }
};

class CountAgeClosure : public ObjectClosure {
private:
    uint age;
public:
    jlong objects_all;
    jlong objects_matched;
public:
    CountAgeClosure(uint age) : age(age), objects_all(0), objects_matched(0) {}
    void do_object(oop obj) {
        objects_all++;
        uint obj_age = obj->age();
        if(obj_age == markOopDesc::max_age || obj_age > age) {
            objects_matched++;
        }
    }
};

void AllocationTracingSelfMonitoring::prepare_for_report_sync_quality() {
    MutexLockerEx l(lock, true);
    SetAgeClosure closure = SetAgeClosure(markOopDesc::max_age);
    ParallelScavengeHeap::heap()->old_gen()->object_iterate(&closure);
    sync_distance = 0;
}

void AllocationTracingSelfMonitoring::report_sync_quality() {
    MutexLockerEx l(lock, true);
    if((minor_sync_size.get_count() > 0 || major_sync_size.get_count()) && sync_distance < sync_quality_size) {
        //TODO currently, this implementation does not handle objects in the young gen during a synchronization correctly (they will be accounted as new ones if they survive)
        CountAgeClosure closure = CountAgeClosure(sync_distance + 1);
        Universe::heap()->object_iterate(&closure);
        assert(closure.objects_matched <= closure.objects_all, "should never happen");
        sync_quality_objects_all[sync_distance] += closure.objects_all;
        sync_quality_objects_before_sync[sync_distance] += closure.objects_matched;
        sync_distance++;
    }
}

void AllocationTracingSelfMonitoring::dump() {
    ResourceMark _(Thread::current());
    GrowableArray<char*> names = GrowableArray<char*>();
    GrowableArray<jdouble> values = GrowableArray<jdouble>();
    get(&names, &values);

    const char* file_name_format = "hs_object_tracing_self_monitoring_pid%i.log";
    char* filename = (char*) calloc(sizeof(char), strlen(file_name_format) * 2);
    sprintf(filename, file_name_format, NOT_WINDOWS(getpid()) WINDOWS_ONLY(0));
    FILE* file = fopen(filename, "w");
    free(filename); filename = NULL;

    AllocationTracing_log_header();
    AllocationTracing_log_line("self monitoring dump:");
    for(int i = 0; i < names.length(); i++) {
        char* name = names.at(i);
        double value = values.at(i);
        fprintf(stderr, "\t%s = %lf\n", name, value);
        fprintf(file, "%s = %lf\n", name, value);
    }
    AllocationTracing_log_footer();

    fclose(file);
}

void AllocationTracingSelfMonitoring::get(GrowableArray<char*>* names, GrowableArray<jdouble>* values) {
    #define define_metric(name, value) do { names->append((char*) name); values->append(value); } while(0)
    MutexLockerEx l(lock, true);
    self_monitoring(1) {
        collect_TLSs();
    }
    self_monitoring(1) {
        jdouble worker_thread_work_time, worker_thread_lock_time, worker_thread_idle_time;
        compute_worker_thread_times(&worker_thread_work_time, &worker_thread_lock_time, &worker_thread_idle_time);
        jdouble speed = compute_speed();
        define_metric("lock_time", tls_data.lock_timer.seconds());
        define_metric("wait_time", tls_data.wait_timer.seconds());
        define_metric("stack_walk_time", tls_data.stack_walk_timer.seconds());
        define_metric("flush_count", tls_data.flushes);
        define_metric("trace_size", size);
        define_metric("symbols_size", symbols_size);
        define_metric("class_definitions_size", class_definitions_size);
        define_metric("original_trace_size", tls_data.size);
        if(TraceObjectsCompressTrace || TraceObjectsCompressTraceAdaptively) {
            define_metric("compression_rate", 1 - (1.0 * size / MAX2((jlong) 1l, tls_data.size)));          
            define_metric("compression_time", tls_data.compressed_timer.seconds());    
            define_metric("compressed_buffers", tls_data.compressed_buffers);
        }
        define_metric("speed", speed);
        define_metric("buffers_allocated_count", buffers_allocated);
        define_metric("buffers_freed_count", buffers_freed);
        define_metric("buffers_active_count", buffers_active);
        define_metric("buffers_active_size", buffer_size_active);
        define_metric("buffers_stolen_size", buffer_size_stolen);
        define_metric("flush_queue_overflow_count", flush_queue_overflows);
        define_metric("worker_thread_work_time", worker_thread_work_time);
        define_metric("io_time", AllocationTracing::get_trace_writer()->get_io_timer()->seconds());
        define_metric("worker_thread_lock_time", worker_thread_lock_time);
        define_metric("worker_thread_idle_time", worker_thread_idle_time);
        define_metric("allocation_sites_registered", allocation_sites);
        define_metric("allocated_types_registered", allocated_types);
        define_metric("minor_gc_count", minor_gc_size.get_count());
        define_metric("major_gc_count", major_gc_size.get_count());
        define_metric("minor_gc_size", minor_gc_size.get());
        define_metric("major_gc_size", major_gc_size.get());
        if(TraceObjectsMaxTraceSize > 0) {
            define_metric("minor_sync_count", minor_sync_size.get_count());
            define_metric("major_sync_count", major_sync_size.get_count());
            define_metric("minor_sync_size", minor_sync_size.get());
            define_metric("major_sync_size", major_sync_size.get());
        }
        define_metric("compiled_allocation_sites_average_depth", 1.0 * compiled_allocation_sites_total_depth / MAX2((jlong) 1l, compiled_allocation_sites));
        define_metric("cleanup_time", cleanup_time.seconds());
    }
    self_monitoring(2) {
        jlong arrays = tls_data.small_arrays + tls_data.big_arrays;
        jlong objects = tls_data.instances + arrays;
        define_metric("allocated_objects", objects);
        if(TraceObjectsHashCodeElimination) {
            define_metric("hashes_eliminated", tls_data.hashes_eliminated);
        }
        define_metric("allocated_memory", tls_data.allocated_memory);
        define_metric("allocated_objects_average_size", 1.0 * tls_data.allocated_memory / MAX2((jlong) 1l, objects));
        define_metric("allocated_objects_average_depth", 1.0 * tls_data.total_allocation_depth / MAX2((jlong) 1l, objects));
        define_metric("allocated_arrays_average_length", 1.0 * tls_data.arrays_total_length / MAX2((jlong) 1l, arrays));
        define_metric("object_types_rate_instances", 1.0 * tls_data.instances / MAX2((jlong) 1l, objects));
        define_metric("object_types_rate_small_arrays", 1.0 * tls_data.small_arrays / MAX2((jlong) 1l, objects));
        define_metric("object_types_rate_big_arrays", 1.0 * tls_data.big_arrays / MAX2((jlong) 1l, objects));
        
        jlong events = 0;
        for(EventType type = 0; type < EVENT_COUNT; type++) {
            jlong count = get_event_count(1, type);
            define_metric(get_event_name(type), count);
            events += count;
        }
        define_metric("events", events);
        define_metric("average_event_size", 1.0 * tls_data.size / MAX2((jlong) 1l, events));
        
        jlong allocations = get_event_count(15,
                EVENTS_ALLOC_SLOW,
                EVENTS_IR_ALLOC_FAST, EVENTS_IR_ALLOC_NORMAL, EVENTS_IR_ALLOC_SLOW, EVENTS_IR_ALLOC_SLOW_DEVIANT_TYPE,
                EVENTS_C1_ALLOC_FAST, EVENTS_C1_ALLOC_NORMAL, EVENTS_C1_ALLOC_SLOW, EVENTS_C1_ALLOC_SLOW_DEVIANT_TYPE, EVENTS_C1_ALLOC_SLOW_DEVIANT_TYPE,
                EVENTS_C2_ALLOC_FAST, EVENTS_C2_ALLOC_NORMAL, EVENTS_C2_ALLOC_SLOW, EVENTS_C2_ALLOC_FAST_DEVIANT_TYPE, EVENTS_C2_ALLOC_SLOW_DEVIANT_TYPE);
        jlong moves = get_event_count(11,
                EVENTS_GC_MOVE_SLOW,
                EVENTS_GC_MOVE_FAST_WIDE, EVENTS_GC_MOVE_FAST, EVENTS_GC_MOVE_FAST_NARROW,
                EVENTS_GC_MOVE_REGION, EVENTS_GC_KEEP_ALIVE,
                EVENTS_SYNC_OBJ, EVENTS_SYNC_OBJ_NARROW,
                EVENTS_GC_MOVE_SLOW_PTR, EVENTS_GC_MOVE_FAST_PTR, EVENTS_GC_MOVE_FAST_WIDE_PTR);
        jlong deallocations = get_event_count(1, EVENTS_DEALLOCATION);
        jlong others = events - allocations - moves - deallocations;
        define_metric("event_types_rate_allocations", 1.0 * allocations / MAX2((jlong) 1l, events));
        define_metric("event_types_rate_GC_moves", 1.0 * moves / MAX2((jlong) 1l, events));
        define_metric("event_types_rate_GC_deallocations", 1.0 * deallocations / MAX2((jlong) 1l, events));
        define_metric("event_types_rate_others", 1.0 * others / MAX2((jlong) 1l, events));

        define_metric("allocations_rate_VM", 1.0 * get_event_count(1, EVENTS_ALLOC_SLOW) / MAX2((jlong) 1l, allocations));
        define_metric("allocations_rate_IR", 1.0 * get_event_count(4, EVENTS_IR_ALLOC_FAST, EVENTS_IR_ALLOC_NORMAL, EVENTS_IR_ALLOC_SLOW, EVENTS_IR_ALLOC_SLOW_DEVIANT_TYPE) / MAX2((jlong) 1l, allocations));
        define_metric("allocations_rate_C1", 1.0 * get_event_count(5, EVENTS_C1_ALLOC_FAST, EVENTS_C1_ALLOC_NORMAL, EVENTS_C1_ALLOC_SLOW, EVENTS_C1_ALLOC_SLOW_DEVIANT_TYPE, EVENTS_C1_ALLOC_FAST_DEVIANT_TYPE) / MAX2((jlong) 1l, allocations));
        define_metric("allocations_rate_C2", 1.0 * get_event_count(5, EVENTS_C2_ALLOC_FAST, EVENTS_C2_ALLOC_NORMAL, EVENTS_C2_ALLOC_SLOW, EVENTS_C2_ALLOC_SLOW_DEVIANT_TYPE, EVENTS_C2_ALLOC_FAST_DEVIANT_TYPE) / MAX2((jlong) 1l, allocations));
        
        define_metric("allocations_rate_slow", 1.0 * get_event_count(7, EVENTS_ALLOC_SLOW, EVENTS_IR_ALLOC_SLOW, EVENTS_C1_ALLOC_SLOW, EVENTS_C2_ALLOC_SLOW, EVENTS_IR_ALLOC_SLOW_DEVIANT_TYPE, EVENTS_C1_ALLOC_SLOW_DEVIANT_TYPE, EVENTS_C2_ALLOC_SLOW_DEVIANT_TYPE) / MAX2((jlong) 1l, allocations));
        define_metric("allocations_rate_normal", 1.0 * get_event_count(3, EVENTS_IR_ALLOC_NORMAL, EVENTS_C1_ALLOC_NORMAL, EVENTS_C2_ALLOC_NORMAL) / MAX2((jlong) 1l, allocations));
        define_metric("allocations_rate_fast", 1.0 * get_event_count(5, EVENTS_IR_ALLOC_FAST, EVENTS_C1_ALLOC_FAST, EVENTS_C2_ALLOC_FAST, EVENTS_C1_ALLOC_FAST_DEVIANT_TYPE, EVENTS_C2_ALLOC_FAST_DEVIANT_TYPE) / MAX2((jlong) 1l, allocations));        
        
        define_metric("GC_moves_rate_slow", 1.0 * get_event_count(2, EVENTS_GC_MOVE_SLOW, EVENTS_GC_MOVE_SLOW_PTR) / MAX2((jlong) 1l, moves));
        define_metric("GC_moves_rate_fast_wide", 1.0 * get_event_count(2, EVENTS_GC_MOVE_FAST_WIDE, EVENTS_GC_MOVE_FAST_WIDE_PTR) / MAX2((jlong) 1l, moves));
        define_metric("GC_moves_rate_fast", 1.0 * get_event_count(2, EVENTS_GC_MOVE_FAST, EVENTS_GC_MOVE_FAST_PTR) / MAX2((jlong) 1l, moves));
        define_metric("GC_moves_rate_fast_narrow", 1.0 * get_event_count(1, EVENTS_GC_MOVE_FAST_NARROW) / MAX2((jlong) 1l, moves));
        define_metric("GC_moves_rate_region", 1.0 * get_event_count(1, EVENTS_GC_MOVE_REGION) / MAX2((jlong) 1l, moves));
        define_metric("GC_moves_rate_keep_alive", 1.0 * get_event_count(1, EVENTS_GC_KEEP_ALIVE) / MAX2((jlong) 1l, moves));
        define_metric("GC_moves_rate_sync", 1.0 * get_event_count(1, EVENTS_SYNC_OBJ) / MAX2((jlong) 1l, moves));
        define_metric("GC_moves_rate_sync_narrow", 1.0 * get_event_count(1, EVENTS_SYNC_OBJ_NARROW) / MAX2((jlong) 1l, moves));
                
        define_metric("TLAB_allocations", 1.0 * get_event_count(5, EVENTS_IR_ALLOC_FAST, EVENTS_C1_ALLOC_FAST, EVENTS_C2_ALLOC_FAST, EVENTS_C1_ALLOC_FAST_DEVIANT_TYPE, EVENTS_C2_ALLOC_FAST_DEVIANT_TYPE) / MAX2((jlong) 1l, allocations));
        define_metric("PLAB_moves", 1.0 * get_event_count(3, EVENTS_GC_MOVE_FAST_WIDE, EVENTS_GC_MOVE_FAST, EVENTS_GC_MOVE_FAST_NARROW) / MAX2((jlong) 1l, moves - get_event_count(1, EVENTS_GC_MOVE_REGION) + tls_data.GC_move_region_object_count));
        define_metric("GC_move_region_average_object_count", 1.0 * tls_data.GC_move_region_object_count / MAX2((jlong) 1l, get_event_count(1, EVENTS_GC_MOVE_REGION)));
        
        if(TraceObjectsPointers || TraceObjectsPointersDirtyOnly) {
            define_metric("relative_ptrs", 1.0 * tls_data.relative_ptrs);
            define_metric("absolute_ptrs", 1.0 * tls_data.absolute_ptrs);
            define_metric("null_ptrs", 1.0 * tls_data.null_ptrs);
            define_metric("ptr_referrer", 1.0 * tls_data.ptr_referrer);
            jlong total_ptrs = tls_data.relative_ptrs + tls_data.absolute_ptrs + tls_data.null_ptrs;
            define_metric("relative_ptrs_ratio", 1.0 * tls_data.relative_ptrs / total_ptrs);
            define_metric("absolute_ptrs_ratio", 1.0 * tls_data.absolute_ptrs / total_ptrs);
            define_metric("null_ptrs_ratio", 1.0 * tls_data.null_ptrs / total_ptrs);
            define_metric("ptrs_per_referrer", 1.0 * total_ptrs / tls_data.ptr_referrer);
            define_metric("null_ptrs_per_referrer", 1.0 * tls_data.null_ptrs /tls_data.ptr_referrer);
            define_metric("clean_ptrs", 1.0 * tls_data.clean_ptrs);
            define_metric("dirty_ptrs", 1.0 * tls_data.dirty_ptrs);
            define_metric("dirty_ptrs_ratio", 1.0 * tls_data.dirty_ptrs / (tls_data.clean_ptrs + tls_data.dirty_ptrs));
            define_metric("ptrs_saved_bytes", 1.0 * tls_data.size_wptrs);
        }
    }
    self_monitoring(3) {
        define_metric("survivor_ratio", survivor_ratio.get());
        if(minor_sync_size.get_count() > 0 || major_sync_size.get_count() > 0) {
            for(int i = 0; i < sync_quality_size; i++) {
                if(sync_quality_objects_all[i] > 0) {
                    char* name = (char*) malloc(100 * sizeof(char));
                    sprintf(name, "sync_quality_%i", i);
                    jdouble quality = 1 - 1.0 * sync_quality_objects_before_sync[i] / sync_quality_objects_all[i];
                    define_metric(name, quality);
                }
            }
        }
    }
    #undef define_metric
}

void AllocationTracingSelfMonitoring::print_statistics(){
   /* MutexLockerEx l(lock, true);
    fprintf(stdout, "narrowedRoot=%ld, root=%ld, pointer=%ld \n", root_pointer_narrowed_young_gen, root_pointer_young_gen, pointer_young_gen);
    root_pointer_young_gen = 0;
    root_pointer_narrowed_young_gen = 0;
    pointer_young_gen = 0;
    */
    
}

void AllocationTracingSelfMonitoring::compute_worker_thread_times(jdouble* work_time_ptr, jdouble* lock_time_ptr, jdouble* idle_time_ptr) {
    if(TraceObjectsAsyncIO) {
        EventsWorkerThread* worker = AllocationTracing::get_worker();
        *work_time_ptr = worker->get_work_timer()->seconds();
        *lock_time_ptr = worker->get_self_monitoring_data()->lock_timer.seconds();
        *idle_time_ptr = worker->get_self_monitoring_data()->wait_timer.seconds();
    } else {
        *work_time_ptr = *lock_time_ptr = *idle_time_ptr = 0;
    }
}

jdouble AllocationTracingSelfMonitoring::compute_speed() {
    reset_time.stop();
    jdouble time = reset_time.seconds();
    reset_time.start();
    return size / time;
}

char* AllocationTracingSelfMonitoring::get_event_name(EventType type) {
    switch(type) {
        case EVENTS_C1_ALLOC_FAST: return (char*) "events_C1_alloc_fast";
        case EVENTS_IR_ALLOC_FAST: return (char*) "events_IR_alloc_fast";
        case EVENTS_C1_ALLOC_SLOW: return (char*) "events_C1_alloc_slow";
        case EVENTS_IR_ALLOC_SLOW: return (char*) "events_IR_alloc_slow";
        case EVENTS_TLAB_ALLOC: return (char*) "events_TLAB_alloc";
        case EVENTS_ALLOC_SLOW: return (char*) "events_VM_alloc_slow";
        case EVENTS_IR_ALLOC_NORMAL: return (char*) "events_IR_alloc_normal";
        case EVENTS_IR_ALLOC_SLOW_DEVIANT_TYPE: return (char*) "events_IR_alloc_slow_deviant_type";
        case EVENTS_C1_ALLOC_SLOW_DEVIANT_TYPE: return (char*) "events_C1_alloc_slow_deviant_type";
        case EVENTS_C1_ALLOC_NORMAL: return (char*) "events_C1_alloc_normal";
        case EVENTS_GC_START: return (char*) "events_GC_start";
        case EVENTS_GC_END: return (char*) "events_GC_end";
        case EVENTS_GC_MOVE_FAST: return (char*) "events_GC_move_fast";
        case EVENTS_GC_MOVE_SLOW: return (char*) "events_GC_move_slow";
        case EVENTS_PLAB_ALLOC: return (char*) "events_PLAB_alloc";
        case EVENTS_GC_KEEP_ALIVE: return (char*) "events_GC_keep_alive";
        case EVENTS_GC_MOVE_REGION: return (char*) "events_GC_move_region";
        case EVENTS_GC_MOVE_FAST_NARROW: return (char*) "events_GC_move_fast_narrow";
        case EVENTS_SYNC_OBJ: return (char*) "events_obj_sync";
        case EVENTS_NOP: return (char*) "events_nop";
        case EVENTS_C2_ALLOC_FAST: return (char*) "events_C2_alloc_fast";
        case EVENTS_C2_ALLOC_NORMAL: return (char*) "events_C2_alloc_normal";
        case EVENTS_C2_ALLOC_SLOW: return (char*) "events_C2_alloc_slow";
        case EVENTS_THREAD_ALIVE: return (char*) "events_thread_alive";
        case EVENTS_THREAD_DEATH: return (char*) "events_thread_death";
        case EVENTS_C2_ALLOC_FAST_DEVIANT_TYPE: return (char*) "events_C2_alloc_fast_deviant_type";
        case EVENTS_C2_ALLOC_SLOW_DEVIANT_TYPE: return (char*) "events_C2_alloc_slow_deviant_type";
        case EVENTS_SYNC_OBJ_NARROW: return (char*) "events_obj_sync_narrow";
        case EVENTS_DEALLOCATION: return (char*) "events_GC_deallocation";
        case EVENTS_GC_MOVE_FAST_WIDE: return (char*) "events_GC_move_fast_wide";
        case EVENTS_MARK: return (char*) "events_mark";
        case EVENTS_GC_MOVE_FAST_PTR: return (char*) "events_GC_move_fast_ptr";
        case EVENTS_GC_MOVE_FAST_WIDE_PTR: return (char*) "events_GC_move_fast_wide_ptr";
        case EVENTS_GC_MOVE_SLOW_PTR: return (char*) "events_GC_move_slow_ptr";
        case EVENTS_GC_KEEP_ALIVE_PTR: return (char*) "events_GC_keep_alive_ptr";
        case EVENTS_GC_ROOT_PTR: return (char*) "events_GC_root_ptr";
        case EVENTS_GC_PTR_EXTENSION: return (char*) "events_GC_ptr_extension";
        case EVENTS_GC_UPDATE_PTR_PREMOVE: return (char*) "events_GC_update_ptr_premove";
        case EVENTS_GC_PTR_MULTITHREADED: return (char*) "events_GC_ptr_multithreaded";
        case EVENTS_SPACE_CREATE: return (char*) "events_space_create";
        case EVENTS_SPACE_ALLOC: return (char*) "events_space_alloc";
        case EVENTS_SPACE_RELEASE: return (char*) "events_space_release";
        case EVENTS_SPACE_REDEFINE: return (char*) "events_space_redefine";
        case EVENTS_SPACE_DESTROY: return (char*) "events_space_destroy";
        case EVENTS_GC_INFO: return (char*) "events_GC_info";
        case EVENTS_GC_FAILED: return (char*) "events_GC_failed";
        case EVENTS_C1_ALLOC_FAST_DEVIANT_TYPE: return (char*) "events_C1_alloc_fast_deviant_type";
        case EVENTS_GC_INTERRUPT: return (char*) "events_GC_interrupt";
        case EVENTS_GC_CONTINUE: return (char*) "events_GC_continue";
        case EVENTS_GC_UPDATE_PTR_POSTMOVE: return (char*) "events_GC_update_ptr_postmove";
        default: assert(false, "no name for event"); return (char*) "event_unknown";
    }
}

jlong AllocationTracingSelfMonitoring::get_event_count(int n, ...) {
    jlong count = 0;
    va_list types;
    va_start(types, n);
    for(int i = 0; i < n; i++) {
        EventType type = (EventType) va_arg(types, int);
        count += tls_data.event_counts[type];
    }
    va_end(types);
    return count;
}

jdouble AllocationTracingSelfMonitoring::compute_average_event_size() {
    jlong count = 0;
    for(EventType event = 0; event < EVENT_COUNT; event++) {
        count += get_event_count(1, event);
    }
    return size * 1.0 / count;
}

class CollectTLSClosure: public ThreadClosure {
    public:
        AllocationTracingSelfMonitoringTLSData* data;
        void (*add)(AllocationTracingSelfMonitoringTLSData* dest, AllocationTracingSelfMonitoringTLSData* src);
        void (*reset)(AllocationTracingSelfMonitoringTLSData* data);

        CollectTLSClosure(AllocationTracingSelfMonitoringTLSData* data,void (*add)(AllocationTracingSelfMonitoringTLSData* dest, AllocationTracingSelfMonitoringTLSData* src), void (*reset)(AllocationTracingSelfMonitoringTLSData* data)) : data(data), add(add), reset(reset) {}

        void do_thread(Thread* thread) {
            if(thread != NULL) {
                AllocationTracingSelfMonitoringTLSData* tls = thread->get_self_monitoring_data();            
                add(data, tls);
                reset(tls);
            }
        }
};

void AllocationTracingSelfMonitoring::collect_TLSs() {
    Thread* self = Thread::current();
    bool do_lock = !SafepointSynchronize::is_at_safepoint() && Threads_lock->owner() != self;
    if(do_lock) {
        if(self->is_Java_thread()) {
            //this is a poor mans safepoint (if someone else triggered a safepoint, the Threads_lock will already be locked -> deadlock)
            while(!Threads_lock->try_lock()) {
                lock->unlock();
                { ThreadToNativeFromVM _((JavaThread*) self); }
                lock->lock();
            }
        } else {
            Threads_lock->lock_without_safepoint_check(self);
        }
    }
    CollectTLSClosure closure(&tls_data, add_TLS, reset_TLS);
    Threads::threads_do(&closure);
    for(int i = 0; i < CompressionThread::max_compression_threads; i++) {
        CompressionThread* thread = CompressionThread::get_CompressionThread(i);
        if(thread != NULL) {
            closure.do_thread(thread);
        }
    }
    if(do_lock) Threads_lock->unlock();
}

void AllocationTracingSelfMonitoring::reset_TLS(AllocationTracingSelfMonitoringTLSData* data) {
    data->lock_timer = elapsedTimer();
    data->wait_timer = elapsedTimer();
    data->stack_walk_timer = elapsedTimer();
    data->compressed_timer = elapsedTimer();
    
    for(EventType type = 0; type < EVENT_COUNT; type++) {
        data->event_counts[type] = 0;
    }
    data->instances = 0;
    data->small_arrays = 0;
    data->big_arrays = 0;
    data->allocated_memory = 0;
    data->arrays_total_length = 0;
    data->total_allocation_depth = 0;
    data->GC_move_region_object_count = 0;
    data->compressed_buffers = 0;
    data->flushes = 0;
    data->size = 0;
    data->hashes_eliminated = 0;
    data->relative_ptrs = 0;
    data->absolute_ptrs = 0;
    data->null_ptrs = 0;
    data->clean_ptrs = 0;
    data->dirty_ptrs = 0;
    data->ptr_referrer = 0;
    data->size_wptrs = 0;
}

AllocationTracingSelfMonitoringTLSData AllocationTracingSelfMonitoring::create_TLS() {
    AllocationTracingSelfMonitoringTLSData data;
    reset_TLS(&data);
    return data;
}

void AllocationTracingSelfMonitoring::add_TLS(AllocationTracingSelfMonitoringTLSData* dest, AllocationTracingSelfMonitoringTLSData* src) {
    dest->lock_timer.add(src->lock_timer);
    dest->wait_timer.add(src->wait_timer);
    dest->stack_walk_timer.add(src->stack_walk_timer);
    dest->compressed_timer.add(src->compressed_timer);
            
    for(EventType type = 0; type < EVENT_COUNT; type++) {
        dest->event_counts[type] += src->event_counts[type];
    }
    dest->instances += src->instances;
    dest->small_arrays += src->small_arrays;
    dest->big_arrays += src->big_arrays;
    dest->allocated_memory += src->allocated_memory;
    dest->arrays_total_length += src->arrays_total_length;
    dest->total_allocation_depth += src->total_allocation_depth;
    dest->GC_move_region_object_count += src->GC_move_region_object_count;
    dest->compressed_buffers += src->compressed_buffers;
    dest->flushes += src->flushes;
    dest->size += src->size;
    dest->hashes_eliminated += src->hashes_eliminated;
    dest->relative_ptrs += src->relative_ptrs;
    dest->absolute_ptrs += src->absolute_ptrs;
    dest->null_ptrs += src->null_ptrs;
    dest->clean_ptrs += src->clean_ptrs;
    dest->dirty_ptrs += src->dirty_ptrs;
    dest->ptr_referrer += src->ptr_referrer;
    dest->size_wptrs += src->size_wptrs;
}


