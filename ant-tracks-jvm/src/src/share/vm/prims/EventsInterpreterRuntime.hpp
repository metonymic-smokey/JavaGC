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
 * File:   EventsInterpreterRuntime.hpp
 * Author: vmb
 *
 * Created on December 9, 2013, 10:22 AM
 */

#ifndef EVENTSINTERPRETERRUNTIME_HPP
#define	EVENTSINTERPRETERRUNTIME_HPP

#include "jni.h"
#include "EventsRuntime.hpp"

class Thread;
class Method;

class EventsInterpreterRuntime : public AllStatic {
public:
    static void fire_obj_alloc_no_arrays_naked(JavaThread* thread, oopDesc* obj, Method* method, intptr_t bcp);
    static void fire_obj_alloc_no_arrays(JavaThread* thread, oopDesc* obj, Method* method, intptr_t bcp);
};

#endif	/* EVENTSINTERPRETERRUNTIME_HPP */

