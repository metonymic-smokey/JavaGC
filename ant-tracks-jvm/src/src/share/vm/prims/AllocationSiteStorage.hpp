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
 * File:   AllocationSiteStorage.hpp
 * Author: Philipp Lengauer
 *
 * Created on October 18, 2014, 11:28 AM
 */

#ifndef ALLOCATIONSITESTORAGE_HPP
#define	ALLOCATIONSITESTORAGE_HPP

#include "memory/allocation.hpp"
#include "oops/oop.hpp"
#include "oops/markOop.hpp"
#include "AllocationSites.hpp"
//#include <execinfo.h>

class AllocationSiteStorage : public AllStatic {
public:
    enum {
        allocation_site_bits_big = SIZE_OF_ALLOCATION_SITE_IDENTIFIER_BIG * 8,
        allocation_site_shift_big = markOopDesc::hash_bits - allocation_site_bits_big, //use high bits because low bits are more likely to make a difference in hash tables
        allocation_site_mask_big = (1 << allocation_site_bits_big) - 1,
        allocation_site_mask_in_place_big = allocation_site_mask_big << allocation_site_shift_big,
                
        allocation_site_bits_small = SIZE_OF_ALLOCATION_SITE_IDENTIFIER_SMALL * 8,
        allocation_site_shift_small = markOopDesc::hash_bits - allocation_site_bits_small, //use high bits because low bits are more likely to make a difference in hash tables
        allocation_site_mask_small = (1 << allocation_site_bits_small) - 1,
        allocation_site_mask_in_place_small = allocation_site_mask_small << allocation_site_shift_small
    };
    
    static inline unsigned int allocation_site_bits(AllocationSiteIdentifier allocation_site) {
        return is_big_allocation_site(allocation_site) ? allocation_site_bits_big : allocation_site_bits_small;
    }
    
    static inline unsigned int allocation_site_shift(AllocationSiteIdentifier allocation_site) {
        return markOopDesc::hash_bits - allocation_site_bits(allocation_site);
    }
    
    static inline unsigned int allocation_site_shift(intptr_t hash) {
        return allocation_site_shift((AllocationSiteIdentifier) ((hash >> allocation_site_shift_big) & allocation_site_mask_big));
    }
    
    static inline unsigned int allocation_site_mask(AllocationSiteIdentifier allocation_site) {
        return (1 << allocation_site_bits(allocation_site)) - 1;
    }
    
    static inline unsigned int allocation_site_mask(intptr_t hash) {
        return allocation_site_mask((AllocationSiteIdentifier) ((hash >> allocation_site_shift_big) & allocation_site_mask_big));
    }
    
    static inline unsigned int allocation_site_mask_in_place(AllocationSiteIdentifier allocation_site) {
        return allocation_site_mask(allocation_site) << allocation_site_shift(allocation_site);
    }
    
    static inline unsigned int allocation_site_mask_in_place(intptr_t hash) {
        return allocation_site_mask_in_place((AllocationSiteIdentifier) ((hash >> allocation_site_shift_big) & allocation_site_mask_big));
    }
    
    static void store(Thread* thread, oop obj, AllocationSiteIdentifier allocation_site);
    static AllocationSiteIdentifier load(Thread* thread, oop obj, bool is_mark_valid = true);
};

#endif	/* ALLOCATIONSITESTORAGE_HPP */

