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
 * File:   EventsC1Runtime.hpp
 * Author: Verena Bitto
 *
 * Created on December 11, 2013, 10:43 AM
 */

#ifndef EVENTSC1RUNTIME_HPP
#define	EVENTSC1RUNTIME_HPP

#include "jni.h"
#include "AllocationSites.hpp"
#include "EventsRuntime.hpp"

class EventsC1Runtime : public AllStatic {
public:
   static void fire_obj_alloc_fast_with_event_naked(JavaThread* thread, oopDesc* obj, jlong event);
   static void fire_obj_alloc_fast_with_oop(JavaThread* thread, oopDesc* obj, AllocationSiteIdentifier allocation_site);
   static void fire_obj_alloc_fast_with_oop_naked(JavaThread* thread, oopDesc* obj, AllocationSiteIdentifier allocation_site);
   static void fire_obj_alloc_normal_with_oop(JavaThread* thread, oopDesc* obj, AllocationSiteIdentifier allocation_site);
   static void fire_obj_alloc_normal_with_oop_naked(JavaThread* thread, oopDesc* obj, AllocationSiteIdentifier allocation_site);
   
   static void store_alloc_site_naked(JavaThread* thread, oopDesc* obj, AllocationSiteIdentifier allocation_site);
private:

};

#endif	/* EVENTSC1RUNTIME_HPP */

