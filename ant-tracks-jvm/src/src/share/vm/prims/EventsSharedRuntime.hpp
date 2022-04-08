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
 * File:   EventsSharedRuntime.hpp
 * Author: Philipp Lengauer
 *
 * Created on March 20, 2014, 4:31 PM
 */

#ifndef EVENTSSHAREDRUNTIME_HPP
#define	EVENTSSHAREDRUNTIME_HPP

#include "jni.h"
#include "AllocationSites.hpp"
#include "EventsRuntime.hpp"

class EventsSharedRuntime : public AllStatic {
public:
    static void fire_filler_alloc(JavaThread* thread, oopDesc* obj);
    static void fire_tlab_alloc(JavaThread* thread);
private:

};

#endif	/* EVENTSSHAREDRUNTIME_HPP */

