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
 * File:   EventsC2Runtime.hpp
 * Author: Philipp Lengauer
 *
 * Created on August 22, 2014, 9:27 AM
 */

#ifndef EVENTSC2RUNTIME_HPP
#define	EVENTSC2RUNTIME_HPP
#include "jni.h"
#include "AllocationSites.hpp"
#include "EventsRuntime.hpp"

class EventsC2Runtime : public AllStatic {
public:
    static address fire_obj_alloc_fast_Java;
    static address fire_obj_alloc_fast_unknown_Java;
    
    static void fire_obj_alloc_fast(oopDesc* obj, AllocationSiteIdentifier allocation_site, JavaThread* thread);
    static void fire_obj_alloc_fast_naked(oopDesc* obj, AllocationSiteIdentifier allocation_site);

    static void fire_obj_alloc_fast_unknown(oopDesc* obj, AllocationSiteIdentifier allocation_site, JavaThread* thread);
    static void fire_obj_alloc_fast_unknown_naked(oopDesc* obj, AllocationSiteIdentifier allocation_site);
    
    static void store_allocation_site_naked(oopDesc* obj, AllocationSiteIdentifier allocation_site);
    
private:
    static void zero_object(oopDesc* obj);
};

#endif	/* EVENTSC2RUNTIME_HPP */

