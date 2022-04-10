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
 * File:   EventBuffersFlushAll.hpp
 * Author: Philipp Lengauer
 *
 * Created on February 18, 2014, 12:38 PM
 */

#ifndef FLUSHALLBUFFERS_HPP
#define	FLUSHALLBUFFERS_HPP

class EventBuffersFlushAll {
public:
    //schedules flushing of all buffers at the next safepoint
    static void begin_flush_all();
    //waits for all full buffers to be serialized (i.e., the flush queue is empty). (this method may block!)
    static void wait_for_all_serialized();
    //flushes all buffers and blocks until they are all serialized (this method waits for a safepoint, so calling thread must not be in a state in which it is required to block when a safepoint is necessary)
    static void flush_all_and_wait_for_all_serialized();
    //flushes all buffers (i.e., adds them to the flush queue), makes no guarantee on how many have been serialized on return of this method
    //may only be called at a safepoint (because all TLABs will be retired and doing this while java threads are working is definitely a bad idea)
    static void flush_all();
    // flushes all buffers of threads which fullfill the requirement: flush_gc_condition() (in EventBuffersFlushAll.cpp)
    // dose not require a safpoint, 
    static void flush_gc_threads();
};

class FlushAndSyncEventBufferOnLeave {
public:
    FlushAndSyncEventBufferOnLeave(bool flush_at_beginning = false);
    ~FlushAndSyncEventBufferOnLeave();
private:
    void do_it();
};

#endif	/* FLUSHALLBUFFERS_HPP */

