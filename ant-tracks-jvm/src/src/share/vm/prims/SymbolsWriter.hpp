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
 * File:   SymbolsWriter.hpp
 * Author: vmb
 *
 * Created on February 25, 2014, 11:51 AM
 */

#ifndef SYMBOLSWRITER_HPP
#define	SYMBOLSWRITER_HPP

#include "AllocationTracingIO.hpp"
#include "AllocationSites.hpp"
#include "AllocatedTypes.hpp"

#define MAGIC_BYTE_SITE 42
#define MAGIC_BYTE_TYPE 24
#define MAGIC_BYTE_TYPE_FIELD_INFO 56
#define MAGIC_BYTE_TYPE_SUPER_FIELD_INFO 57
#define MAGIC_BYTE_TYPE_METHOD_INFO 58
#define MAGIC_BYTE_SIMPLE_SITE 84
#define MAGIC_BYTE_GC_CAUSE 17

struct SymbolsSite {
    AllocationSiteIdentifier allocation_site_id;
    CallSiteArray* call_sites;
    AllocatedTypeIdentifier allocated_type_id;
};

struct SymbolsSimpleSite {
    AllocationSiteIdentifier allocation_site_id;
    char* signature;
    jshort bci;
    AllocatedTypeIdentifier allocated_type_id;
};

struct SymbolsType {
    AllocatedTypeIdentifier allocated_type_id;
    Klass* allocated_type;
    int allocated_type_size;
};


class SymbolsWriter : public AllocationTracingIO {
public:
    SymbolsWriter();
    void write_symbols(SymbolsSite symbol);
    void write_symbols(SymbolsSimpleSite symbol);
    void write_symbols(SymbolsType symbol);
protected:
    void write_fields(InstanceKlass* type, bool reverse);
    virtual void write_header();
    virtual int get_file_type();
};

#endif	/* SYMBOLSWRITER_HPP */

