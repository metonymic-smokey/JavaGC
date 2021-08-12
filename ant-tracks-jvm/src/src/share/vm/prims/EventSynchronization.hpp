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
 * File:   AllocationTracingRotation.hpp
 * Author: Philipp Lengauer
 *
 * Created on July 7, 2014, 2:00 PM
 */

#ifndef ALLOCATIONTRACINGROTATION_HPP
#define	ALLOCATIONTRACINGROTATION_HPP

#include "memory/allocation.hpp"

enum EventSynchronizationType {
    None,
    OnMinor,
    OnMajor
};

class GCTaskQueue;
class FlexibleWorkGang;

class EventSynchronization : public AllStatic {
private:
    static bool forced;
    static EventSynchronizationType type;
public:
    static void force_rotate();
    static void check_trigger_rotate();
    static bool should_rotate(size_t epsilon = 0);
    static void start_synchronization(EventSynchronizationType type);
    static void stop_synchronization();
    inline static bool is_synchronizing() { return type != None; } // this function has very high traffic
    static void enqueue_synchronization_tasks_for_parallel_old_gc(GCTaskQueue* queue, int workers);
    static void enqueue_synchronization_tasks_for_g1_gc(FlexibleWorkGang* gang);
    static void do_synchronization_tasks_for_generation_n(int n);
    static void enqueue_synchronization_tasks_for_generation_n(int n, FlexibleWorkGang* gang);
};

#endif	/* ALLOCATIONTRACINGROTATION_HPP */

