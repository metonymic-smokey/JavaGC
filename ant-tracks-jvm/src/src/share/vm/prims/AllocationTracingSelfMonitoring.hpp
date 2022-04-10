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
 * File:   AllocationTracingSelfMonitoring.hpp
 * Author: Philipp Lengauer
 *
 * Created on February 12, 2014, 9:47 AM
 */

#ifndef ALLOCATIONTRACINGSELFMONITORING_HPP
#define	ALLOCATIONTRACINGSELFMONITORING_HPP

#include "precompiled/precompiled.hpp"
#include "AllocationTracingUtil.hpp"
#include "AllocationTracingSelfMonitoringTLS.hpp"

#ifndef ALLOCATION_TRACING_MAX_SELF_MONITORING_LEVEL
#define ALLOCATION_TRACING_MAX_SELF_MONITORING_LEVEL 3
#endif

#define self_monitoring(level) if(level <= MIN2(ALLOCATION_TRACING_MAX_SELF_MONITORING_LEVEL, (int) TraceObjectsSelfMonitoring))

class SelfMonitoringTimeMeasurementBlockControl : public BlockControl {
private:
    elapsedTimer* timer;
public:
    inline SelfMonitoringTimeMeasurementBlockControl(elapsedTimer* t) : timer(t) {
        if(timer != NULL) timer->start();
    }

    inline ~SelfMonitoringTimeMeasurementBlockControl() {
	if(timer != NULL) timer->stop();
    }
};
#define self_monitoring_measure_time(level, timer) _block_(SelfMonitoringTimeMeasurementBlockControl, (level <= MIN2(ALLOCATION_TRACING_MAX_SELF_MONITORING_LEVEL, (int) TraceObjectsSelfMonitoring)) ? (timer) : NULL)

class ArithmeticMean : public StackObj {
private:
    jlong count;
    jdouble total;
public:
    inline ArithmeticMean() {
        reset();
    }
    
    inline void reset() {
        count = 0;
        total = 0;
    }
    
    inline void submit(jdouble value) {
        count++;
        total += value;
    }
    
    inline jlong get_count() {
        return count;
    }
    
    inline jdouble get() {
        return count > 0 ? (1.0 * total / count) : 0;
    }
};



class AllocationTracingSelfMonitoring {
public:
    static void init();
    static void destroy();
    static void reset();
    /* global metrics */
    static void report_buffer_written(size_t size);
    static void report_symbol_written(size_t size);
    static void report_class_definition_written(size_t size);
    static void report_buffer_allocated(size_t capacity, size_t allocated_capacity);
    static void report_buffer_freed(size_t capacity);
    static void report_flush_queue_overflow();
    static void report_new_allocation_site();
    static void report_new_allocated_type();
    static void report_minor_sync(size_t size);
    static void report_major_sync(size_t size);
    static void report_new_compiled_allocation_site(size_t depth);
    static void report_ptr_offset(oop o, uint ptr_rel); // unused
    static void report_ptr_offset(oop o, uintptr_t ptr_abs); // unused
    static void report_minor_gc(size_t size);
    static void report_major_gc(size_t size);  
    static void print_statistics();
    /* event metrics */
    inline static void report_event_fired(Thread* thread, EventType type) {
        assert(Thread::current() == thread, "who is calling?");
        thread->get_self_monitoring_data()->event_counts[type] += 1;
    }
    inline static void report_instance_allocation(Thread* thread, size_t obj_size, size_t depth) {
        assert(Thread::current() == thread, "who is calling?");
        thread->get_self_monitoring_data()->instances += 1;
        thread->get_self_monitoring_data()->allocated_memory += obj_size;
        thread->get_self_monitoring_data()->total_allocation_depth += depth;
    }
    inline static void report_small_array_allocation(Thread* thread, size_t obj_size, size_t arr_length, size_t depth) {
        assert(Thread::current() == thread, "who is calling?");
        thread->get_self_monitoring_data()->small_arrays += 1;
        thread->get_self_monitoring_data()->allocated_memory += obj_size;
        thread->get_self_monitoring_data()->arrays_total_length += arr_length;
        thread->get_self_monitoring_data()->total_allocation_depth += depth;
    }
    inline static void report_big_array_allocation(Thread* thread, size_t obj_size, size_t arr_length, size_t depth) {
        assert(Thread::current() == thread, "who is calling?");
        thread->get_self_monitoring_data()->big_arrays += 1;
        thread->get_self_monitoring_data()->allocated_memory += obj_size;
        thread->get_self_monitoring_data()->arrays_total_length += arr_length;
        thread->get_self_monitoring_data()->total_allocation_depth += depth;
    }
    inline static void report_event_fired_GC_move_region(Thread* thread, jint objects) {
        assert(Thread::current() == thread, "who is calling?");
        thread->get_self_monitoring_data()->GC_move_region_object_count += objects;
    }
    inline static void report_compressed_buffer(Thread* thread) {
        thread->get_self_monitoring_data()->compressed_buffers += 1;
    }
    inline static void report_ptr_referrer(Thread* thread){
        thread->get_self_monitoring_data()->ptr_referrer += 1;
    }
    inline static void report_ptrs(Thread* thread, jubyte relative, jubyte absolute, jubyte null){
        thread->get_self_monitoring_data()->relative_ptrs += relative;
        thread->get_self_monitoring_data()->absolute_ptrs += absolute;
        thread->get_self_monitoring_data()->null_ptrs += null;
    }
    inline static void report_clean_ptr(Thread* thread, jubyte size){
        thread->get_self_monitoring_data()->clean_ptrs += 1;
        thread->get_self_monitoring_data()->size_wptrs += size;
    }
    inline static void report_dirty_ptr(Thread* thread){
        thread->get_self_monitoring_data()->dirty_ptrs += 1;
    }
    inline static elapsedTimer* get_cleanup_time() {
        return &cleanup_time;
    }
    /* TLS */
    static AllocationTracingSelfMonitoringTLSData create_TLS();
    static void report_TLS(Thread* thread);
    /* ??? */
    static void report_survivor_ratio(jdouble ratio);
    static void prepare_for_report_sync_quality();
    static void report_sync_quality();
    /* access */
    static void get(GrowableArray<char*>* names, GrowableArray<jdouble>* values);
    static void dump();
private:
    /* global metrics*/
    static Monitor* lock;
    static elapsedTimer reset_time;
    static jlong size;
    static jlong symbols_size;
    static jlong class_definitions_size;
    static jlong buffers_allocated;
    static jlong buffers_freed;
    static jlong buffers_active;
    static jlong buffer_size_active;
    static jlong buffer_size_stolen;
    static jlong flush_queue_overflows;
    static jlong allocation_sites;
    static jlong allocated_types;
    static ArithmeticMean minor_gc_size;
    static ArithmeticMean major_gc_size;
    static ArithmeticMean minor_sync_size;
    static ArithmeticMean major_sync_size;
    static jlong compiled_allocation_sites;
    static jlong compiled_allocation_sites_total_depth;
    static elapsedTimer cleanup_time;
    static jdouble compute_speed();
    static void compute_worker_thread_times(jdouble* work_time_ptr, jdouble* lock_time_ptr, jdouble* wait_time_ptr);
    /* event metrics */
    static char* get_event_name(EventType type);
    static jlong get_event_count(int n, ...);
    static jdouble compute_average_event_size();
    /* TLS */
    static AllocationTracingSelfMonitoringTLSData tls_data;
    static void collect_TLSs();
    static void reset_TLS(AllocationTracingSelfMonitoringTLSData* data);
    static void add_TLS(AllocationTracingSelfMonitoringTLSData* dest, AllocationTracingSelfMonitoringTLSData* src);
    /* ??? */
    static ArithmeticMean survivor_ratio;
    static const int sync_quality_size = 10;
    //static jdouble sync_quality[10]; //don't do this, floating point rounding errors mess up the results!
    static jlong sync_quality_objects_all[sync_quality_size];
    static jlong sync_quality_objects_before_sync[sync_quality_size];
    /* metrics that are NOT reported, but needed to compute others */
    static jlong sync_distance;
};

#endif	/* ALLOCATIONTRACINGSELFMONITORING_HPP */

