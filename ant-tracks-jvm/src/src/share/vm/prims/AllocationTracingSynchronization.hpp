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
 * File:   AllocationTracingSynchronization.hpp
 * Author: Philipp Lengauer
 *
 * Created on February 19, 2014, 9:40 AM
 */

#ifndef ALLOCATIONTRACINGSYNCHRONIZATION_HPP
#define	ALLOCATIONTRACINGSYNCHRONIZATION_HPP

#include "AllocationTracingUtil.hpp"
#include "AllocationTracingSelfMonitoring.hpp"
#include "runtime/mutex.hpp"

class SynchronizedBlockControl : public BlockControl {
private:
    Monitor* monitor;
public:
    inline SynchronizedBlockControl(Monitor* m, bool safepoint_check) {
        monitor = (m->owned_by_self() ? NULL : m);
        if(monitor != NULL) {
            assert(!monitor->owned_by_self(), "has to be");
            self_monitoring_measure_time(1, &Thread::current()->get_self_monitoring_data()->lock_timer) {
	        if(safepoint_check) {
	            monitor->lock();
	        } else {
	            monitor->lock_without_safepoint_check();
	        }
            }
        }
        assert(monitor == NULL || monitor->owned_by_self(), "has to be");
    }

    inline ~SynchronizedBlockControl() {
        assert(monitor == NULL || monitor->owned_by_self(), "has to be");
        if(monitor != NULL) {
            monitor->unlock();
        }
        assert(monitor == NULL || !monitor->owned_by_self(), "hast to be");
    }
};

#define synchronized(lock) _block_(SynchronizedBlockControl, lock, false)

#define synchronized_wait(lock) synchronized(lock) self_monitoring_measure_time(1, &Thread::current()->get_self_monitoring_data()->wait_timer) lock->wait(true, 0, false)

#define synchronized_notify_all(lock) synchronized(lock) lock->notify_all()

#define synchronized_wait_until(lock, condition) synchronized(lock) while(!(condition)) synchronized_wait(lock)

#define synchronized_if(lock, condition) if(condition) synchronized(lock) if(condition)

#define _declare_critical_section_lock(name) static Monitor* name = new Monitor(Mutex::native, "Critical Section Lock", true)

#define critical _declare_critical_section_lock(_csm_); synchronized(_csm_)

#define critical_if(condition) _declare_critical_section_lock(_csm_); synchronized_if(_csm_, condition)

#endif	/* ALLOCATIONTRACINGSYNCHRONIZATION_HPP */

