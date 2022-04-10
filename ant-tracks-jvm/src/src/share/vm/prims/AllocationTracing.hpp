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
 * File:   AllocationTracing.hpp
 * Author: Philipp Lengauer
 */

#ifndef ALLOCATIONTRACING_HPP
#define	ALLOCATIONTRACING_HPP

#include "memory/allocation.hpp"
#include "ClassDefinitionWriter.hpp"

class SymbolsWriter;
class EventsWriter;
class EventsWorkerThread;

class AllocationTracing : public AllStatic {
private:
    static SymbolsWriter* out_symbols;
    static ClassDefinitionWriter* out_class_definitions;
    static EventsWriter* out_trace;
    static EventsWorkerThread* worker;
    static volatile bool active;
public:
    static void init();
    static void destroy();

    static void begin_init_when_running();
    static void begin_destroy_when_running();    
    static void init_when_running();
    static void destroy_when_running();

    static bool is_active() { return active; }
    static SymbolsWriter* get_symbols_writer() { return out_symbols; }
    static ClassDefinitionWriter* get_class_definitions_writer() { return out_class_definitions; }
    static EventsWriter* get_trace_writer() { return out_trace; }
    static EventsWorkerThread* get_worker() { return worker; }
    
    static void log_humongous_allocation(size_t bytes);
};

#define AllocationTracing_safe_log(format, ...) do { if(tty != NULL) tty->print(format, ##__VA_ARGS__); else fprintf(stderr, format, ##__VA_ARGS__); } while(0)
#define AllocationTracing_safe_log_cr(format, ...) do { if(tty != NULL) tty->print_cr(format, ##__VA_ARGS__); else { fprintf(stderr, format, ##__VA_ARGS__); fprintf(stderr, "\n"); } } while(0)

#define AllocationTracing_log_header() AllocationTracing_safe_log("[AntTracks ")
#define AllocationTracing_log_footer() AllocationTracing_safe_log_cr("]")
#define AllocationTracing_log_line(format, ...) AllocationTracing_safe_log_cr(format, ##__VA_ARGS__)
#define AllocationTracing_log_part(format, ...) AllocationTracing_safe_log(format, ##__VA_ARGS__)

#define AllocationTracing_log(format, ...) do { ResourceMark rm(Thread::current()); AllocationTracing_log_header(); AllocationTracing_log_part(format, ##__VA_ARGS__); AllocationTracing_log_footer(); } while(0)


#endif	/* ALLOCATIONTRACING_HPP */

