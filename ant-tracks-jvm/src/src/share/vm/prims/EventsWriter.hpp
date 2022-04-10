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
 * File:   TracingEventWriter.hpp
 * Author: Verena Bitto
 *
 * Created on December 2, 2013, 2:41 PM
 */

#ifndef EVENTSWRITER_HPP
#define	EVENTSWRITER_HPP

#include <stdio.h> 
#include "../prims/jni.h"
#include "EventBuffer.hpp"
#include "AllocationTracingIO.hpp"

class Thread;

class EventsWriter : public AllocationTracingIO{
private:
    elapsedTimer io_timer;
public:
    EventsWriter();
    void write_buffer(EventBuffer* buffer);
    elapsedTimer* get_io_timer() { return &io_timer; }
    virtual void flush();
private:
    void write_buffer_unlocked(EventBuffer* buffer);
protected:
    virtual void write_header();
    virtual int get_file_type();
};

#endif	/* EVENTSWRITER_HPP */

