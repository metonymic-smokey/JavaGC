/*
 * Copyright (c) 1999, 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
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
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 *
 */

#include "precompiled.hpp"
#include "c1/c1_MacroAssembler.hpp"
#include "c1/c1_Runtime1.hpp"
#include "classfile/systemDictionary.hpp"
#include "gc_interface/collectedHeap.hpp"
#include "interpreter/interpreter.hpp"
#include "oops/arrayOop.hpp"
#include "oops/markOop.hpp"
#include "runtime/basicLock.hpp"
#include "runtime/biasedLocking.hpp"
#include "runtime/os.hpp"
#include "runtime/stubRoutines.hpp"
#include "prims/EventsC1Runtime.hpp"
#include "prims/EventsRuntime.hpp"
#include "prims/AllocationTracingSelfMonitoring.hpp"
#include "assembler_x86.hpp"

int C1_MacroAssembler::lock_object(Register hdr, Register obj, Register disp_hdr, Register scratch, Label& slow_case) {
  const int aligned_mask = BytesPerWord -1;
  const int hdr_offset = oopDesc::mark_offset_in_bytes();
  assert(hdr == rax, "hdr must be rax, for the cmpxchg instruction");
  assert(hdr != obj && hdr != disp_hdr && obj != disp_hdr, "registers must be different");
  Label done;
  int null_check_offset = -1;

  verify_oop(obj);

  // save object being locked into the BasicObjectLock
  movptr(Address(disp_hdr, BasicObjectLock::obj_offset_in_bytes()), obj);

  if (UseBiasedLocking) {
    assert(scratch != noreg, "should have scratch register at this point");
    null_check_offset = biased_locking_enter(disp_hdr, obj, hdr, scratch, false, done, &slow_case);
  } else {
    null_check_offset = offset();
  }

  // Load object header
  movptr(hdr, Address(obj, hdr_offset));
  // and mark it as unlocked
  orptr(hdr, markOopDesc::unlocked_value);
  // save unlocked object header into the displaced header location on the stack
  movptr(Address(disp_hdr, 0), hdr);
  // test if object header is still the same (i.e. unlocked), and if so, store the
  // displaced header address in the object header - if it is not the same, get the
  // object header instead
  if (os::is_MP()) MacroAssembler::lock(); // must be immediately before cmpxchg!
  cmpxchgptr(disp_hdr, Address(obj, hdr_offset));
  // if the object header was the same, we're done
  if (PrintBiasedLockingStatistics) {
    cond_inc32(Assembler::equal,
               ExternalAddress((address)BiasedLocking::fast_path_entry_count_addr()));
  }
  jcc(Assembler::equal, done);
  // if the object header was not the same, it is now in the hdr register
  // => test if it is a stack pointer into the same stack (recursive locking), i.e.:
  //
  // 1) (hdr & aligned_mask) == 0
  // 2) rsp <= hdr
  // 3) hdr <= rsp + page_size
  //
  // these 3 tests can be done by evaluating the following expression:
  //
  // (hdr - rsp) & (aligned_mask - page_size)
  //
  // assuming both the stack pointer and page_size have their least
  // significant 2 bits cleared and page_size is a power of 2
  subptr(hdr, rsp);
  andptr(hdr, aligned_mask - os::vm_page_size());
  // for recursive locking, the result is zero => save it in the displaced header
  // location (NULL in the displaced hdr location indicates recursive locking)
  movptr(Address(disp_hdr, 0), hdr);
  // otherwise we don't care about the result and handle locking via runtime call
  jcc(Assembler::notZero, slow_case);
  // done
  bind(done);
  return null_check_offset;
}


void C1_MacroAssembler::unlock_object(Register hdr, Register obj, Register disp_hdr, Label& slow_case) {
  const int aligned_mask = BytesPerWord -1;
  const int hdr_offset = oopDesc::mark_offset_in_bytes();
  assert(disp_hdr == rax, "disp_hdr must be rax, for the cmpxchg instruction");
  assert(hdr != obj && hdr != disp_hdr && obj != disp_hdr, "registers must be different");
  Label done;

  if (UseBiasedLocking) {
    // load object
    movptr(obj, Address(disp_hdr, BasicObjectLock::obj_offset_in_bytes()));
    biased_locking_exit(obj, hdr, done);
  }

  // load displaced header
  movptr(hdr, Address(disp_hdr, 0));
  // if the loaded hdr is NULL we had recursive locking
  testptr(hdr, hdr);
  // if we had recursive locking, we are done
  jcc(Assembler::zero, done);
  if (!UseBiasedLocking) {
    // load object
    movptr(obj, Address(disp_hdr, BasicObjectLock::obj_offset_in_bytes()));
  }
  verify_oop(obj);
  // test if object header is pointing to the displaced header, and if so, restore
  // the displaced header in the object - if the object header is not pointing to
  // the displaced header, get the object header instead
  if (os::is_MP()) MacroAssembler::lock(); // must be immediately before cmpxchg!
  cmpxchgptr(hdr, Address(obj, hdr_offset));
  // if the object header was not pointing to the displaced header,
  // we do unlocking via runtime call
  jcc(Assembler::notEqual, slow_case);
  // done
  bind(done);
}


// Defines obj, preserves var_size_in_bytes
void C1_MacroAssembler::try_allocate(Register obj, Register var_size_in_bytes, int con_size_in_bytes, Register t1, Register t2, Label& slow_case) {
  if (UseTLAB) {
    tlab_allocate(obj, var_size_in_bytes, con_size_in_bytes, t1, t2, slow_case);
  } else {
    eden_allocate(obj, var_size_in_bytes, con_size_in_bytes, t1, slow_case);
    incr_allocated_bytes(noreg, var_size_in_bytes, con_size_in_bytes, t1);
  }
}


void C1_MacroAssembler::initialize_header(Register obj, Register klass, Register len, Register t1, Register t2) {
  assert_different_registers(obj, klass, len);
  if (UseBiasedLocking && !len->is_valid()) {
    assert_different_registers(obj, klass, len, t1, t2);
    movptr(t1, Address(klass, Klass::prototype_header_offset()));
    movptr(Address(obj, oopDesc::mark_offset_in_bytes()), t1);
  } else {
    // This assumes that all prototype bits fit in an int32_t
    movptr(Address(obj, oopDesc::mark_offset_in_bytes ()), (int32_t)(intptr_t)markOopDesc::prototype());
  }
#ifdef _LP64
  if (UseCompressedClassPointers) { // Take care not to kill klass
    movptr(t1, klass);
    encode_klass_not_null(t1);
    movl(Address(obj, oopDesc::klass_offset_in_bytes()), t1);
  } else
#endif
  {
    movptr(Address(obj, oopDesc::klass_offset_in_bytes()), klass);
  }

  if (len->is_valid()) {
    movl(Address(obj, arrayOopDesc::length_offset_in_bytes()), len);
  }
#ifdef _LP64
  else if (UseCompressedClassPointers) {
    xorptr(t1, t1);
    store_klass_gap(obj, t1);
  }
#endif
}


// preserves obj, destroys len_in_bytes
void C1_MacroAssembler::initialize_body(Register obj, Register len_in_bytes, int hdr_size_in_bytes, Register t1) {
  Label done;
  assert(obj != len_in_bytes && obj != t1 && t1 != len_in_bytes, "registers must be different");
  assert((hdr_size_in_bytes & (BytesPerWord - 1)) == 0, "header size is not a multiple of BytesPerWord");
  Register index = len_in_bytes;
  // index is positive and ptr sized
  subptr(index, hdr_size_in_bytes);
  jcc(Assembler::zero, done);
  // initialize topmost word, divide index by 2, check if odd and test if zero
  // note: for the remaining code to work, index must be a multiple of BytesPerWord
#ifdef ASSERT
  { Label L;
    testptr(index, BytesPerWord - 1);
    jcc(Assembler::zero, L);
    stop("index is not a multiple of BytesPerWord");
    bind(L);
  }
#endif
  xorptr(t1, t1);    // use _zero reg to clear memory (shorter code)
  if (UseIncDec) {
    shrptr(index, 3);  // divide by 8/16 and set carry flag if bit 2 was set
  } else {
    shrptr(index, 2);  // use 2 instructions to avoid partial flag stall
    shrptr(index, 1);
  }
#ifndef _LP64
  // index could have been not a multiple of 8 (i.e., bit 2 was set)
  { Label even;
    // note: if index was a multiple of 8, than it cannot
    //       be 0 now otherwise it must have been 0 before
    //       => if it is even, we don't need to check for 0 again
    jcc(Assembler::carryClear, even);
    // clear topmost word (no jump needed if conditional assignment would work here)
    movptr(Address(obj, index, Address::times_8, hdr_size_in_bytes - 0*BytesPerWord), t1);
    // index could be 0 now, need to check again
    jcc(Assembler::zero, done);
    bind(even);
  }
#endif // !_LP64
  // initialize remaining object fields: rdx is a multiple of 2 now
  { Label loop;
    bind(loop);
    movptr(Address(obj, index, Address::times_8, hdr_size_in_bytes - 1*BytesPerWord), t1);
    NOT_LP64(movptr(Address(obj, index, Address::times_8, hdr_size_in_bytes - 2*BytesPerWord), t1);)
    decrement(index);
    jcc(Assembler::notZero, loop);
  }

  // done
  bind(done);
}

void C1_MacroAssembler::incrementptr(Register reg, int val) {
    LP64_ONLY(incrementq(reg, val)) NOT_LP64(incrementl(reg, val));
}

void C1_MacroAssembler::atomic_addptr(AddressLiteral addr, Register value, Register scr) {
    Address a;
    if(reachable(addr)) {
        a = as_Address(addr);
    } else {
        lea(scr, addr);
        a = Address(scr, 0);
    }
    if(os::is_MP()) lock();
    LP64_ONLY(addq(a, value)) NOT_LP64(addl(a, value));
}

Register C1_MacroAssembler::emit_get_thread(Register t){
#ifdef _LP64
    if(TraceObjectsC1UseThreadRegister){
        return r15_thread;
    } else {
        get_thread(t);
        return t;
    }
#else
    get_thread(t);
    return t;
#endif
}

bool C1_MacroAssembler::emit_prepare_stub_param(Register dst, RegisterOrConstant src, Register t1, Register t2, Register t3) {
    if(src.is_register() && src.as_register() == dst) {
        return false;
    } else {
        bool spill = dst != t1 && dst != t2 && dst != t3;
        if(spill) {
            push(dst);
        }
        if(src.is_register()) {
            movptr(dst, src.as_register());
        } else if (src.is_constant()) {
            movptr(dst, src.as_constant());
        } else {
            assert(false, "here be dragons");
        }
        return spill;
    }
}

void C1_MacroAssembler::emit_fire_obj_alloc(Register obj, Register len, RegisterOrConstant size, Register t1, Register t2, Register t3, AllocationSiteIdentifier allocation_site, bool is_array, Label* slow){
    guarantee(UseTLAB, "we rely on TLAB allocations here! (nothing will crash, but wrong events will be generated)");
    guarantee_different_registers(obj, len, t1, t2, t3, "Guarantee fails");

    assert(!TraceObjectsC1RelocateSlowPath || slow != NULL, "if we are relocating the slow path, we need a label to jump to");

    if(TraceObjectsC1Breakpoint && !is_array) {
        os_breakpoint();
    }

    if(TraceObjectsC1AlwaysSlowPath && TraceObjectsC1RelocateSlowPath) {
        jmp(*slow);
        return;
    }

    if(TraceObjectsC1ClearRegisters || TraceObjectsC1Breakpoint) {
        xorptr(t1, t1);
        xorptr(t2, t2);
        xorptr(t3, t3);
    }

    EventType event_type = EVENTS_C1_ALLOC_FAST;
    jint event_value = EventsRuntime::create_obj_alloc_fast_prototype(allocation_site, event_type);

    int num_words = 1 + (TraceObjectsInsertAnchors ? 1 : 0);

    Label slow_buffer_full;
    Label slow_big_array;

    movl(t3, event_value);

    if (is_array){                                            // adapt event_value
        cmpl(len, ARRAY_LENGTH_MAX_SMALL);
        jcc(Assembler::greaterEqual, TraceObjectsC1RelocateSlowPath ? *slow : slow_big_array);         // jmp slow path
        orl(t3, len);                                          // event_value | (array_len)
    }

    if(TraceObjectsC1AlwaysSlowPath) {
        jmp(TraceObjectsC1RelocateSlowPath ? *slow : slow_buffer_full);
    } else {
        Register thread;
        // if (top+4 - BOTTOM) > SIZE

        thread = emit_get_thread(t1);                             // top
        movptr(t1, Address(thread, Thread::offset_of_event_buffer_top()));

        thread = emit_get_thread(t2);
        if(TraceObjectsFuzzyBufferSizes || true) { //the else branch does not work if a dummy buffer is encountered
            movptr(t2, Address(thread, Thread::offset_of_event_buffer_end())); // end
            subptr(t2, num_words * sizeof(jint));                 // end - 4
        } else {
            movptr(t2, Address(thread, Thread::offset_of_event_buffer_bottom()));  // bottom
            addptr(t2, (TraceObjectsBufferSize) - (num_words * sizeof(jint))); // bottom + size - 4
        }
        //t2 = last position in the event buffer we allow someone to write to

        cmpptr(t1, t2);                                             // top > bottom + size - 4 <=> top + 4 - bottom > size
        jcc(Assembler::greater, TraceObjectsC1RelocateSlowPath ? *slow : slow_buffer_full);

        // t1 holds still top of buffer

        // then
#ifdef ASSERT
        Label assertion_failed, assertion_passed;
        for(int index = 0; index < num_words; index++) {
            cmpl(Address(t1, index * sizeof(jint)), EventBuffer_debug_uninitialized_content);
            jcc(Assembler::notEqual, assertion_failed);
        }
        jmp(assertion_passed);
        bind(assertion_failed);
        stop("space in buffer already initialized");
        bind(assertion_passed);
#endif

        int index = 0;
        movl(Address(t1, index++ * sizeof(jint)), t3);                                 // write event at top        
        if(TraceObjectsInsertAnchors) {
            movl(Address(t1, index++ * sizeof(jint)), get_debug_anchor(event_type));
        }
        assert(index == num_words, "illegal number of words written");

        addptr(t1, num_words * sizeof(jint));                     // top + 4

        thread = emit_get_thread(t2);
        movptr(Address(thread, Thread::offset_of_event_buffer_top()), t1);     // top = top + 4

        self_monitoring(2) {
            addptr(Address(thread, in_bytes(Thread::offset_of_self_monitoring_data() + byte_offset_of(AllocationTracingSelfMonitoringTLSData, event_counts[event_type]))), 1);
            addptr(Address(thread, in_bytes(Thread::offset_of_self_monitoring_data() + byte_offset_of(AllocationTracingSelfMonitoringTLSData, total_allocation_depth))), (int32_t) AllocationSites::get_depth(allocation_site));
            addptr(Address(thread, in_bytes(Thread::offset_of_self_monitoring_data() + (is_array ? byte_offset_of(AllocationTracingSelfMonitoringTLSData, small_arrays) : byte_offset_of(AllocationTracingSelfMonitoringTLSData, instances)))), 1);
            if(size.is_register()) {
                addptr(Address(thread, in_bytes(Thread::offset_of_self_monitoring_data() + byte_offset_of(AllocationTracingSelfMonitoringTLSData, allocated_memory))), size.as_register());
            } else {
                addptr(Address(thread, in_bytes(Thread::offset_of_self_monitoring_data() + byte_offset_of(AllocationTracingSelfMonitoringTLSData, allocated_memory))), size.as_constant());
            }
            if(is_array) {
                addptr(Address(thread, in_bytes(Thread::offset_of_self_monitoring_data() + byte_offset_of(AllocationTracingSelfMonitoringTLSData, arrays_total_length))), len);
            }
        }
    }

    if(!TraceObjectsC1RelocateSlowPath) {
        Label slow, end;
        jmp(end);

#ifdef _LP64
        // else max array length                            // additional word for array length
        if(is_array){
            bind(slow_big_array);                           // t3 holds event_value
            orl(t3, ARRAY_LENGTH_MAX_SMALL);                // event_value | (255)
            shlq(t3, 32);                                   // event_value << 32
            orq(t3, len);                                   // event_value | len
            jmp(slow);
        }

        // else obj
        bind(slow_buffer_full);
        shlq(t3, 32);                                       // event_value << 32

        // else
        bind(slow);
        assert(rax == obj, "obj must be in rax");
        const Register stub_event_reg = rdi; //choose a register that will be t3 often and most probably be one of the scratch registers
        bool save_stub_event_reg_spilled = emit_prepare_stub_param(stub_event_reg, RegisterOrConstant(t3), t1, t2);
        assert(!save_stub_event_reg_spilled, "this will probably work but fuck up the stack alignment and trigger a ton of assertions");
        call(RuntimeAddress(Runtime1::entry_for(Runtime1::fire_obj_alloc_fast_with_event_id))); //WARNING: no valid call because oop map is missing. therefore there must not be any safepoint here!
        if(save_stub_event_reg_spilled) {
            pop(stub_event_reg);
        }
#else
        bind(slow_big_array);
        bind(slow_buffer_full);
        assert(rax == obj, "obj must be in rax");
        push(allocation_site);
        call(RuntimeAddress(Runtime1::entry_for(Runtime1::fire_obj_alloc_fast_with_oop_id))); //WARNING: no valid call because oop map is missing. therefore there must not be any safepoint here!
        pop(t1);
#endif

        bind(end);
    }

}

void C1_MacroAssembler::emit_store_allocation_site(Register obj, Register t1, Register t2, Register t3, AllocationSiteIdentifier allocation_site, Klass* allocated_type) {
    assert(allocation_site != ALLOCATION_SITE_IDENTIFIER_UNKNOWN, "why are we storing an unknown allocation site");    
    Label slow_path, end;
    bool generate_slow_path = emit_store_allocation_site_inlined(obj, t1, t2, t3, RegisterOrConstant(allocation_site), &slow_path, &end, true, false, allocated_type);
    if(generate_slow_path) {
        bind(slow_path);
        assert(rax == obj, "");
        const Register alloc_site_reg = rdi;
        bool alloc_site_reg_spilled = emit_prepare_stub_param(alloc_site_reg, RegisterOrConstant(allocation_site), t1, t2, t3);
        assert(!alloc_site_reg_spilled, "this will probably work but fuck up the stack alignment and trigger a ton of assertions");    
        call(RuntimeAddress(Runtime1::entry_for(Runtime1::store_alloc_site_id)));
        if(alloc_site_reg_spilled) {
            pop(alloc_site_reg);
        }
    }
    bind(end);
}

bool C1_MacroAssembler::emit_store_allocation_site_inlined(Register obj, Register t1, Register t2, Register t3, RegisterOrConstant allocation_site, Label* slow_path, Label* end, bool assume_slow_path_is_next, bool ignore_shared_directive, Klass* allocated_type) {

    if(TraceObjectsC1SaveAllocationSitesBreakpoint) {
        os_breakpoint();
    }
    
    if(TraceObjectsC1ClearRegisters || TraceObjectsC1SaveAllocationSitesBreakpoint) {
        xorptr(t1, t1);
        xorptr(t2, t2);
        xorptr(t3, t3);
    }
    
    if(TraceObjectsC1SaveAllocationSitesShareGeneratedCode && !ignore_shared_directive) {
        assert(obj == rax, "necessary for stub");
        const Register alloc_site_reg = rdi;
        bool alloc_site_reg_spilled = emit_prepare_stub_param(alloc_site_reg, allocation_site, t1, t2, t3);
        call(RuntimeAddress(Runtime1::entry_for(Runtime1::store_alloc_site_inlined_id)));
        if(alloc_site_reg_spilled) {
            pop(alloc_site_reg);
        }
        return false;
    } else {
        bool generate_slow_path = false;

        if(TraceObjectsC1SaveAllocationSitesAlwaysSlowPath) {
            generate_slow_path = true;
            if(!assume_slow_path_is_next) {
                jmp(*slow_path);
            }
        } else {
            Register mark = t1;
            if(UseBiasedLocking) {
                movptr(mark, Address(obj, oopDesc::mark_offset_in_bytes()));
                //check if mark word is locked and/or biased locked
                //last three bits must be 001 in order for the header to be unlocked and unbiased
                movptr(t2, markOopDesc::biased_lock_pattern); //in theory, a new object can only be anonymously biased!
                andptr(mark, t2);
                cmpptr(mark, t2);
                jcc(Assembler::equal, *slow_path); generate_slow_path = true;
            }
#ifdef ASSERT
            //if mark is not biased (checked above) it must be neutral
            Label assertion_is_neutral_passed;
            movptr(mark, Address(obj, oopDesc::mark_offset_in_bytes()));
            andptr(mark, markOopDesc::biased_lock_mask_in_place);
            cmpptr(mark, markOopDesc::unlocked_value);
            jcc(Assembler::equal, assertion_is_neutral_passed);
            stop("object header is not neutral (<=> locked), how can this be right after allocation?");
            bind(assertion_is_neutral_passed);
#endif
            //generate hash
            Register hash = t2;
#ifdef ASSERT
            //there should not be a hash right after allocation
            Label assertion_has_hash_passed;
            movptr(hash, Address(obj, oopDesc::mark_offset_in_bytes()));
            andptr(hash, (int32_t) markOopDesc::hash_mask_in_place);
            shrptr(hash, markOopDesc::hash_shift);
            cmpptr(hash, markOopDesc::no_hash);
            jcc(Assembler::equal, assertion_has_hash_passed);
            stop("object already has a hash, how can this be right after allocation?");
            bind(assertion_has_hash_passed);
#endif
            bool generate_next_hash_inlined;
            if(TraceObjectsHashCodeElimination && AllocatedTypes::has_custom_hash_code(allocated_type)) { 
                xorptr(hash, hash);
                generate_next_hash_inlined = true;
                self_monitoring(2) {
                    Register thread = emit_get_thread(t1);
                    addptr(Address(thread, in_bytes(Thread::offset_of_self_monitoring_data() + byte_offset_of(AllocationTracingSelfMonitoringTLSData, hashes_eliminated))), 1);
                }
            } else {
                generate_next_hash_inlined = emit_generate_next_hash(obj, hash, t1, t3, true, ~AllocationSiteStorage::allocation_site_mask_in_place_big);
            }
            if(generate_next_hash_inlined) {
                //combine with allocation site
                //andptr(hash, ~AllocationSiteStorage::allocation_site_mask_in_place); // handled by emit_generate_next_hash
                if(allocation_site.is_constant()) {
                    orptr(hash, ((intptr_t) allocation_site.as_constant()) << AllocationSiteStorage::allocation_site_shift(allocation_site.as_constant()));
                } else if(allocation_site.is_register()) {
                    if(AllocationSiteStorage::allocation_site_shift_big > 0) {
                        shlptr(allocation_site.as_register(), AllocationSiteStorage::allocation_site_shift_big);
                    }
                    orptr(hash, allocation_site.as_register());
                } else {
                    assert(false, "here be dragons");
                }
#ifdef ASSERT
                //hash != no_hash
                Label assertion_not_no_hash_passed;
                cmpptr(hash, markOopDesc::no_hash);
                jcc(Assembler::notEqual, assertion_not_no_hash_passed);
                stop("generated hash (including allocation site) is equal to no_hash");
                bind(assertion_not_no_hash_passed);
#endif
                //store hash
                //andptr(hash, markOopDesc::hash_mask); // handled by emit_generate_next_hash
                shlptr(hash, markOopDesc::hash_shift);
                mark = hash;
                orptr(mark, (intptr_t) markOopDesc::prototype());
                //store mark
                movptr(Address(obj, oopDesc::mark_offset_in_bytes()), mark);
                //skip slow path
                if(generate_slow_path && assume_slow_path_is_next) {
                    jmp(*end);
                }
            } else {
                generate_slow_path = true;
                if(!assume_slow_path_is_next) {
                    jmp(*slow_path);
                }
            }
        }
        return generate_slow_path;
    }
}

bool C1_MacroAssembler::emit_generate_next_hash(Register receiver, Register hash, Register t1, Register t2, bool allow_zero_hash, int32_t mask) {
    bool may_generate_zero_hash;
    switch(hashCode) {
        case 0: {
                const long a = 16807;
                const unsigned long m = 2147483647;
                const long q = m / a; assert(q == 127773, "weird math");
                const long r = m % a; assert(r == 2836, "weird math");
                
                //do not use "just" random here because then this code an os::random will return the same sequence
                static long random_seed = os::random() * 21 + 37;
                assert(sizeof(random_seed) == sizeof(void*), "just checking whether we use the correct instructions");

                Register tmp = t1;
                Register lo = hash;
                Register hi = t2;
                
                lea(tmp, AddressLiteral((address) &random_seed, relocInfo::none));
                
                movptr(lo, Address(tmp, relocInfo::none));
                andptr(lo, 0xFFFF);
                imulptr(lo, lo, a);
                
                movptr(hi, Address(tmp, relocInfo::none));
                shrptr(hi, 16);
                imulptr(hi, hi, a);
                
                movptr(tmp, hi);
                andptr(tmp, 0x7FFF);
                shlptr(tmp, 16);
                addptr(lo, tmp);
                
                Label q_not_overflowed;
                cmpptr(lo, m);
                jcc(Assembler::lessEqual, q_not_overflowed);
                andptr(lo, m);
                incrementptr(lo, 1);
                bind(q_not_overflowed);
                
                shrptr(hi, 15);
                addptr(lo, hi);
                
                Label pq_not_overflowed;
                cmpptr(lo, m);
                jcc(Assembler::lessEqual, pq_not_overflowed);
                andptr(lo, m);
                incrementptr(lo, 1);
                bind(pq_not_overflowed);
                
                lea(tmp, AddressLiteral((address) &random_seed, relocInfo::none));
                movptr(Address(tmp, relocInfo::none), lo);
                
                if(hash != lo) {
                    movptr(hash, lo);
                }
            }
            may_generate_zero_hash = true;
            break;
        case 1: {
                Register addrBits = t1, stw_random_addr = t2;
                movptr(addrBits, receiver);
                shrptr(addrBits, 3);
            
                movptr(hash, addrBits);
                shrptr(addrBits, 5);
                xorptr(hash, addrBits);
                lea(stw_random_addr, AddressLiteral((address) ObjectSynchronizer::get_stw_random_addr(), relocInfo::none));
                xorl(hash, Address(stw_random_addr, 0));
            }
            may_generate_zero_hash = true; //very improbable, but possible :-(
            break;
        case 2:
            movptr(hash, 1);
            may_generate_zero_hash = false;
            break;
        case 3: {
                Register counter_addr = t1;
                lea(counter_addr, AddressLiteral((address) ObjectSynchronizer::get_hc_sequence_addr(), relocInfo::none));
                movl(hash, Address(counter_addr, 0));
                incrementl(hash, 1);
                movl(Address(counter_addr, 0), hash);
            }
            may_generate_zero_hash = true; //yes when counter wrapped around
            break;
        case 4:
            movptr(hash, receiver);
            //use last (always aligned bits)
            shrptr(hash, LogHeapWordSize);
            may_generate_zero_hash = false; //only if we call it on NULL
            break;
        case 5: {
                Register t = t1, v = hash;
                Register thread = emit_get_thread(t2);
                Register tmp;
                bool restore_tmp_register;
                if(thread == t2) {
                    tmp = receiver;
                    restore_tmp_register = true;
                    push(tmp);
                } else {
                    tmp = t2;
		    restore_tmp_register = false;
                }
            
#ifdef LINUX
                assert(sizeof(Thread::_hashStateW) == 4, "just checking whether we use the correct instructions");
#endif
            
                movl(t, Address(thread, byte_offset_of(Thread, _hashStateX)));
            
                movl(tmp, t);        

                shll(tmp, 11);
                xorl(t, tmp);
            
                movl(tmp, Address(thread, byte_offset_of(Thread, _hashStateY)));
                movl(Address(thread, byte_offset_of(Thread, _hashStateX)), tmp);

                movl(tmp, Address(thread, byte_offset_of(Thread, _hashStateZ)));
                movl(Address(thread, byte_offset_of(Thread, _hashStateY)), tmp);

                movl(tmp, Address(thread, byte_offset_of(Thread, _hashStateW)));
                movl(Address(thread, byte_offset_of(Thread, _hashStateZ)), tmp);

                movl(v, tmp); //tmp <=> Address(thread, byte_offset_of(Thread, _hashStateW))
            
                //movl(tmp, v); //tmp already v
                shrl(tmp, 19);
                xorl(v, tmp);
                movl(tmp, t);
                shrl(tmp, 8);
                xorl(t, tmp);
                xorl(v, t);

                movl(Address(thread, byte_offset_of(Thread, _hashStateW)), v);
            
                if(hash != v) {
                    movl(hash, v);
                }
            
                if(restore_tmp_register) {
                    pop(tmp);
                }            
            }
            may_generate_zero_hash = true; //don't know, have to assume so
            break;
        default:
            return false;
    }
    
    if(may_generate_zero_hash && !allow_zero_hash) {
        Label hash_not_zero;
        testptr(hash, hash); assert(markOopDesc::no_hash == 0, "otherwise this next check doesn't work");
        jcc(Assembler::notZero, hash_not_zero);
        movptr(hash, 0xBAD);
        bind(hash_not_zero);
    }

#ifdef ASSERT
    if(!allow_zero_hash) {
        //hash must not be no_hash
        testptr(hash, hash);assert(markOopDesc::no_hash == 0, "otherwise this next check doesn't work");
        Label assertion_hash_not_zero_passed;
        jcc(Assembler::notZero, assertion_hash_not_zero_passed);
        stop("generated hash was equal to markOopDesc::no_hash");
        bind(assertion_hash_not_zero_passed);
    }
#endif

    andptr(hash, markOopDesc::hash_mask & mask);
    
    return true;
}
                
void C1_MacroAssembler::allocate_object(Register obj, Register t1, Register t2, Register t3, int header_size, int object_size, Register klass, Label& slow_case, Label* slow_case_trace, CallSiteIterator* call_sites, Klass* allocated_type) {
  assert(obj == rax, "obj must be in rax, for cmpxchg");
  assert_different_registers(obj, t1, t2); // XXX really?
  assert(header_size >= 0 && object_size >= header_size, "illegal sizes");

  try_allocate(obj, noreg, object_size * BytesPerWord, t1, t2, slow_case);
  
  initialize_object(obj, klass, noreg, object_size * HeapWordSize, t1, t2);
 
  if(TraceObjectsAllocations){
      self_monitoring(1) AllocationTracingSelfMonitoring::report_new_compiled_allocation_site(call_sites->count());
      assert(!allocated_type->oop_is_array(), "?");
      AllocationSiteIdentifier allocation_site = AllocationSites::method_to_allocation_site(call_sites, allocated_type);
      if(TraceObjectsSaveAllocationSites) {
          emit_store_allocation_site(obj, t1, t2, t3, allocation_site, allocated_type);
      }
      emit_fire_obj_alloc(obj, noreg, RegisterOrConstant(object_size * BytesPerWord), t1, t2, t3, allocation_site, false, slow_case_trace);
  }
}

void C1_MacroAssembler::initialize_object(Register obj, Register klass, Register var_size_in_bytes, int con_size_in_bytes, Register t1, Register t2) {
  assert((con_size_in_bytes & MinObjAlignmentInBytesMask) == 0,
         "con_size_in_bytes is not multiple of alignment");
  const int hdr_size_in_bytes = instanceOopDesc::header_size() * HeapWordSize;

  initialize_header(obj, klass, noreg, t1, t2);

  // clear rest of allocated space
  const Register t1_zero = t1;
  const Register index = t2;
  const int threshold = 6 * BytesPerWord;   // approximate break even point for code size (see comments below)
  if (var_size_in_bytes != noreg) {
    mov(index, var_size_in_bytes);
    initialize_body(obj, index, hdr_size_in_bytes, t1_zero);
  } else if (con_size_in_bytes <= threshold) {
    // use explicit null stores
    // code size = 2 + 3*n bytes (n = number of fields to clear)
    xorptr(t1_zero, t1_zero); // use t1_zero reg to clear memory (shorter code)
    for (int i = hdr_size_in_bytes; i < con_size_in_bytes; i += BytesPerWord)
      movptr(Address(obj, i), t1_zero);
  } else if (con_size_in_bytes > hdr_size_in_bytes) {
    // use loop to null out the fields
    // code size = 16 bytes for even n (n = number of fields to clear)
    // initialize last object field first if odd number of fields
    xorptr(t1_zero, t1_zero); // use t1_zero reg to clear memory (shorter code)
    movptr(index, (con_size_in_bytes - hdr_size_in_bytes) >> 3);
    // initialize last object field if constant size is odd
    if (((con_size_in_bytes - hdr_size_in_bytes) & 4) != 0)
      movptr(Address(obj, con_size_in_bytes - (1*BytesPerWord)), t1_zero);
    // initialize remaining object fields: rdx is a multiple of 2
    { Label loop;
      bind(loop);
      movptr(Address(obj, index, Address::times_8, hdr_size_in_bytes - (1*BytesPerWord)),
             t1_zero);
      NOT_LP64(movptr(Address(obj, index, Address::times_8, hdr_size_in_bytes - (2*BytesPerWord)),
             t1_zero);)
      decrement(index);
      jcc(Assembler::notZero, loop);
    }
  }

  if (CURRENT_ENV->dtrace_alloc_probes()) {
    assert(obj == rax, "must be");
    call(RuntimeAddress(Runtime1::entry_for(Runtime1::dtrace_object_alloc_id)));
  }

  verify_oop(obj);
}

void C1_MacroAssembler::allocate_array(Register obj, Register len, Register t1, Register t2, Register t3, int header_size, Address::ScaleFactor f, Register klass, Label& slow_case, Label* slow_case_trace, CallSiteIterator* call_sites, Klass* allocated_type) {
  assert(obj == rax, "obj must be in rax, for cmpxchg");
  assert_different_registers(obj, len, t1, t2, klass);

  // determine alignment mask
  assert(!(BytesPerWord & 1), "must be a multiple of 2 for masking code to work");

  // check for negative or excessive length
  cmpptr(len, (int32_t)max_array_allocation_length);
  jcc(Assembler::above, slow_case);

  const Register arr_size = t2; // okay to be the same
  // align object end
  movptr(arr_size, (int32_t)header_size * BytesPerWord + MinObjAlignmentInBytesMask);
  lea(arr_size, Address(arr_size, len, f));
  andptr(arr_size, ~MinObjAlignmentInBytesMask);

  try_allocate(obj, arr_size, 0, t1, t2, slow_case);

  initialize_header(obj, klass, len, t1, t2);
  
  if(TraceObjectsAllocations && TraceObjectsSelfMonitoring >= 2) {
      movptr(klass, arr_size); //save size in a register which is not used otherwise (arr_size is destroyed in initialize_body)
  }
  
  // clear rest of allocated space
  const Register len_zero = len;
  initialize_body(obj, arr_size, header_size * BytesPerWord, len_zero);
  
  if (CURRENT_ENV->dtrace_alloc_probes()) {
    assert(obj == rax, "must be");
    call(RuntimeAddress(Runtime1::entry_for(Runtime1::dtrace_object_alloc_id)));
  }
  
  if(TraceObjectsAllocations) {
      self_monitoring(1) AllocationTracingSelfMonitoring::report_new_compiled_allocation_site(call_sites->count());
      AllocationSiteIdentifier allocation_site = AllocationSites::method_to_allocation_site(call_sites, allocated_type);
      if(TraceObjectsSaveAllocationSites) {
          emit_store_allocation_site(obj, t1, t2, t3, allocation_site, allocated_type);
      }
      movl(len, Address(obj, arrayOopDesc::length_offset_in_bytes())); //restore length because it has been destroyed
      emit_fire_obj_alloc(obj, len, RegisterOrConstant(klass), t1, t2, t3, allocation_site, true, slow_case_trace);
  }
  
  verify_oop(obj);
}



void C1_MacroAssembler::inline_cache_check(Register receiver, Register iCache) {
  verify_oop(receiver);
  // explicit NULL check not needed since load from [klass_offset] causes a trap
  // check against inline cache
  assert(!MacroAssembler::needs_explicit_null_check(oopDesc::klass_offset_in_bytes()), "must add explicit null check");
  int start_offset = offset();

  if (UseCompressedClassPointers) {
    load_klass(rscratch1, receiver);
    cmpptr(rscratch1, iCache);
  } else {
    cmpptr(iCache, Address(receiver, oopDesc::klass_offset_in_bytes()));
  }
  // if icache check fails, then jump to runtime routine
  // Note: RECEIVER must still contain the receiver!
  jump_cc(Assembler::notEqual,
          RuntimeAddress(SharedRuntime::get_ic_miss_stub()));
  const int ic_cmp_size = LP64_ONLY(10) NOT_LP64(9);
  assert(UseCompressedClassPointers || offset() - start_offset == ic_cmp_size, "check alignment in emit_method_entry");
}


void C1_MacroAssembler::build_frame(int frame_size_in_bytes, int bang_size_in_bytes) {
  assert(bang_size_in_bytes >= frame_size_in_bytes, "stack bang size incorrect");
  // Make sure there is enough stack space for this method's activation.
  // Note that we do this before doing an enter(). This matches the
  // ordering of C2's stack overflow check / rsp decrement and allows
  // the SharedRuntime stack overflow handling to be consistent
  // between the two compilers.
  generate_stack_overflow_check(bang_size_in_bytes);

  push(rbp);
  if (PreserveFramePointer) {
    mov(rbp, rsp);
  }
#ifdef TIERED
  // c2 leaves fpu stack dirty. Clean it on entry
  if (UseSSE < 2 ) {
    empty_FPU_stack();
  }
#endif // TIERED
  decrement(rsp, frame_size_in_bytes); // does not emit code for frame_size == 0
}


void C1_MacroAssembler::remove_frame(int frame_size_in_bytes) {
  increment(rsp, frame_size_in_bytes);  // Does not emit code for frame_size == 0
  pop(rbp);
}


void C1_MacroAssembler::unverified_entry(Register receiver, Register ic_klass) {
  if (C1Breakpoint) int3();
  inline_cache_check(receiver, ic_klass);
}


void C1_MacroAssembler::verified_entry() {
  if (C1Breakpoint || VerifyFPU || !UseStackBanging) {
    // Verified Entry first instruction should be 5 bytes long for correct
    // patching by patch_verified_entry().
    //
    // C1Breakpoint and VerifyFPU have one byte first instruction.
    // Also first instruction will be one byte "push(rbp)" if stack banging
    // code is not generated (see build_frame() above).
    // For all these cases generate long instruction first.
    fat_nop();
  }
  if (C1Breakpoint)int3();
  // build frame
  verify_FPU(0, "method_entry");
}


#ifndef PRODUCT

void C1_MacroAssembler::verify_stack_oop(int stack_offset) {
  if (!VerifyOops) return;
  verify_oop_addr(Address(rsp, stack_offset));
}

void C1_MacroAssembler::verify_not_null_oop(Register r) {
  if (!VerifyOops) return;
  Label not_null;
  testptr(r, r);
  jcc(Assembler::notZero, not_null);
  stop("non-null oop required");
  bind(not_null);
  verify_oop(r);
}

void C1_MacroAssembler::invalidate_registers(bool inv_rax, bool inv_rbx, bool inv_rcx, bool inv_rdx, bool inv_rsi, bool inv_rdi) {
#ifdef ASSERT
  if (inv_rax) movptr(rax, 0xDEAD);
  if (inv_rbx) movptr(rbx, 0xDEAD);
  if (inv_rcx) movptr(rcx, 0xDEAD);
  if (inv_rdx) movptr(rdx, 0xDEAD);
  if (inv_rsi) movptr(rsi, 0xDEAD);
  if (inv_rdi) movptr(rdi, 0xDEAD);
#endif
}

#endif // ifndef PRODUCT
