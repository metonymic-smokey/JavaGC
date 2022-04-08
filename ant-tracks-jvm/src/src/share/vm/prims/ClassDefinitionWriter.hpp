/*
 * Copyright (c) 2016, 2017 dynatrace and/or its affiliates. All rights reserved.
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
 * File:   ClassDefinitionWriter.hpp
 * Author: Peter Feichtinger
 *
 * Created on September 26, 2016, 3:08 PM
 */

#ifndef CLASSDEFINITIONWRITER_HPP
#define CLASSDEFINITIONWRITER_HPP

#include "AllocationTracingIO.hpp"
#include "AllocatedTypes.hpp"

#define MAGIC_BYTE_CLASS_DEFINITION 14

struct SymbolsClassDefinition
{
    AllocatedTypeIdentifier allocated_type_id;
    u1* buffer;
    int length;
};

class ClassDefinitionWriter : public AllocationTracingIO
{
public:
    ClassDefinitionWriter();
    void write_symbol(SymbolsClassDefinition symbol);
protected:
    virtual void write_header();
    virtual int get_file_type();
};

#endif /* CLASSDEFINITIONWRITER_HPP */

