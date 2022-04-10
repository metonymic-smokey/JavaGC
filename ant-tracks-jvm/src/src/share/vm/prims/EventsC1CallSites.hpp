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
 * File:   EventsC1CallSites.hpp
 * Author: Philipp Lengauer
 *
 * Created on September 15, 2014, 12:17 PM
 */

#ifndef EVENTSC1CALLSITES_HPP
#define	EVENTSC1CALLSITES_HPP

#include "AllocationSites.hpp"

class C1CallSiteIterator : public CallSiteIterator {
private:
    ValueStack* start;
    ValueStack* current;
public:
    C1CallSiteIterator(ValueStack* stack) : start(stack), current(stack) {}

    int count() {
        return start == NULL ? 0 : (start->scope()->level() + 1); 
    }
    
    void reset() {
        current = start;
    }
    
    bool has_next() {
        return current != NULL;
    }

    CallSite next() {
        CallSite site;
        site.method = (Method*) current->scope()->method()->constant_encoding();
        site.bytecode_index = current->bci();
        current = current->caller_state();
        return site;
    }
};

#endif	/* EVENTSC1CALLSITES_HPP */

