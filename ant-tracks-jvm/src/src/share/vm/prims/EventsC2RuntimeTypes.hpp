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
 * File:   EventsC2RuntimeTypes.hpp
 * Author: Philipp Lengauer
 *
 * Created on August 22, 2014, 9:42 AM
 */

#ifndef EVENTSC2RUNTIMETYPES_HPP
#define	EVENTSC2RUNTIMETYPES_HPP

#include "../../vm/opto/type.hpp"

class EventsC2RuntimeTypes {
public:
    static const TypeFunc* fire_obj_alloc_fast_Type() {
        // create input type (domain)
        const Type **fields = TypeTuple::fields(2);
        fields[TypeFunc::Parms+0] = TypeRawPtr::BOTTOM;  // oop;    newly allocated object
        fields[TypeFunc::Parms+1] = TypeInt::SHORT;  // allocation site;

        const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2,fields);

        // create result type (range)
        fields = TypeTuple::fields(1);
        fields[TypeFunc::Parms+0] = TypeRawPtr::BOTTOM; // Returned oop

        const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

        return TypeFunc::make(domain, range);
    }
    
    static const TypeFunc* store_allocation_site_Type() {
        // create input type (domain)
        const Type **fields = TypeTuple::fields(2);
        fields[TypeFunc::Parms+0] = TypeRawPtr::BOTTOM;  // oop;    newly allocated object
        fields[TypeFunc::Parms+1] = TypeInt::SHORT;  // allocation site;

        const TypeTuple *domain = TypeTuple::make(TypeFunc::Parms+2,fields);

        // create result type (range)
        fields = TypeTuple::fields(1);
        fields[TypeFunc::Parms+0] = TypeRawPtr::BOTTOM; // Returned oop

        const TypeTuple *range = TypeTuple::make(TypeFunc::Parms+1, fields);

        return TypeFunc::make(domain, range);
    }
};

#endif	/* EVENTSC2RUNTIMETYPES_HPP */

