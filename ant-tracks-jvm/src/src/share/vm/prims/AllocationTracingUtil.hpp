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
 * File:   AllocationTracingUtil.hpp
 * Author: Philipp Lengauer
 *
 * Created on February 19, 2014, 8:46 AM
 */

#ifndef ALLOCATIONTRACINGUTIL_HPP
#define	ALLOCATIONTRACINGUTIL_HPP

class BlockControl : public StackObj {
private:
    bool run;
public:
    inline BlockControl(bool enable) : run(enable) { }
    inline BlockControl() : run(true) { }
    inline bool condition() { return run; }
    inline void statement() { run = false; }
};

#define _block_(block_control_type, ...) for(block_control_type _bl_(__VA_ARGS__); _bl_.condition(); _bl_.statement())

#define _aware_lazy_block_(block_control_type, ...) for(block_control_type _bl_ = TraceObjectsAllocations ? block_control_type(__VA_ARGS__) : block_control_type(); _bl_.condition(); _bl_.statement())

#define HERE_BE_DRAGONS(result) do { assert(false, "here be dragons"); return result; } while(0)

class ObservableValue : public StackObj {
private:
    const char* name;
    jlong frequency;
    jlong _min, _max, down, high;
    jlong n;
public:
    inline ObservableValue(const char* name, jlong frequency) : name(name), frequency(frequency), _min(LONG_MAX), _max(LONG_MIN), down(~0L), high(~0L), n(0) {}
    
    inline void submit(jlong value) {
        _min = MIN2(_min, value);
        _max = MAX2(_max, value);
        down = down & ~value;
        high = high & value;
        if(n++ % frequency == 0) {
            print();
        }
    }
    
    inline void print() {
        tty->print("value \"%s\": min=%li, max=%li, down=0x%lx, high=0x%lx (%li)\n", name, _min, _max, down, high, n);
    }
};

#define observe_value(name, frequency, value) do { static ObservableValue _values = ObservableValue(name, frequency); _values.submit(value); } while(0)

#define warning_once(msg) do { static bool warned = false; if(!warned) { warned = true; warning((msg)); } } while(0) 

#endif	/* ALLOCATIONTRACINGUTIL_HPP */

