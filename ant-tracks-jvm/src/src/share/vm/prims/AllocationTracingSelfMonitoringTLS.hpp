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
 * File:   AllocationTracingSelfMonitoringTLS.hpp
 * Author: Philipp Lengauer
 *
 * Created on September 23, 2014, 12:59 PM
 */

#ifndef ALLOCATIONTRACINGSELFMONITORINGTLS_HPP
#define	ALLOCATIONTRACINGSELFMONITORINGTLS_HPP

struct AllocationTracingSelfMonitoringTLSData {
    /* global metrics */
    elapsedTimer lock_timer;
    elapsedTimer wait_timer;
    elapsedTimer stack_walk_timer;
    elapsedTimer compressed_timer;
    /* event metrics */
    jlong event_counts[EVENT_COUNT];
    jlong instances;
    jlong small_arrays;
    jlong big_arrays;
    jlong allocated_memory;
    jlong arrays_total_length;
    jlong total_allocation_depth;
    jlong GC_move_region_object_count;
    jlong compressed_buffers;
    jlong size;
    jlong flushes;
    jlong hashes_eliminated;
    // ptrs
    jlong relative_ptrs;
    jlong absolute_ptrs;
    jlong null_ptrs;
    jlong clean_ptrs;
    jlong dirty_ptrs;
    jlong ptr_referrer;
    jlong size_wptrs;
};

#endif	/* ALLOCATIONTRACINGSELFMONITORINGTLS_HPP */

