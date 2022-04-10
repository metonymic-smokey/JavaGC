/*
 * Copyright (c) 1999, 2010, Oracle and/or its affiliates. All rights reserved.
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

#ifndef CPU_X86_VM_C1_MACROASSEMBLER_X86_HPP
#define CPU_X86_VM_C1_MACROASSEMBLER_X86_HPP

// C1_MacroAssembler contains high-level macros for C1

 private:
  int _rsp_offset;    // track rsp changes
  // initialization
  void pd_init() { _rsp_offset = 0; }

 public:
  void try_allocate(
    Register obj,                      // result: pointer to object after successful allocation
    Register var_size_in_bytes,        // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,        // object size in bytes if   known at compile time
    Register t1,                       // temp register
    Register t2,                       // temp register
    Label&   slow_case                 // continuation point if fast allocation fails
  );

  void initialize_header(Register obj, Register klass, Register len, Register t1, Register t2);
  void initialize_body(Register obj, Register len_in_bytes, int hdr_size_in_bytes, Register t1);

  // locking
  // hdr     : must be rax, contents destroyed
  // obj     : must point to the object to lock, contents preserved
  // disp_hdr: must point to the displaced header location, contents preserved
  // scratch : scratch register, contents destroyed
  // returns code offset at which to add null check debug information
  int lock_object  (Register swap, Register obj, Register disp_hdr, Register scratch, Label& slow_case);

  // unlocking
  // hdr     : contents destroyed
  // obj     : must point to the object to lock, contents preserved
  // disp_hdr: must be eax & must point to the displaced header location, contents destroyed
  void unlock_object(Register swap, Register obj, Register lock, Label& slow_case);

  void initialize_object(
    Register obj,                      // result: pointer to object after successful allocation
    Register klass,                    // object klass
    Register var_size_in_bytes,        // object size in bytes if unknown at compile time; invalid otherwise
    int      con_size_in_bytes,        // object size in bytes if   known at compile time
    Register t1,                       // temp register
    Register t2                        // temp register
  );

  // allocation of fixed-size objects
  // (can also be used to allocate fixed-size arrays, by setting
  // hdr_size correctly and storing the array length afterwards)
  // obj        : must be rax, will contain pointer to allocated object
  // t1, t2     : scratch registers - contents destroyed
  // header_size: size of object header in words
  // object_size: total size of object in words
  // slow_case  : exit to slow case implementation if fast allocation fails
  void allocate_object(Register obj, Register t1, Register t2, Register t3, int header_size, int object_size, Register klass, Label& slow_case, Label* slow_case_trace, CallSiteIterator* call_sites, Klass* allocated_type);

  void incrementptr(Register reg, int val);
  void atomic_addptr(AddressLiteral addr, Register value, Register scr = rscratch1);
  Register emit_get_thread(Register t);
  bool emit_prepare_stub_param(Register dst, RegisterOrConstant src, Register t1 = noreg, Register t2 = noreg, Register t3 = noreg);
  void emit_fire_obj_alloc(Register obj, Register len, RegisterOrConstant size, Register t1, Register t2, Register t3, AllocationSiteIdentifier allocation_site, bool is_array, Label* slow);
  void emit_store_allocation_site(Register obj, Register t1, Register t2, Register t3, AllocationSiteIdentifier allocation_site, Klass* allocated_type);
  /*
   * This method tries to store the allocation site directly respecting every configuration option.
   * If it returns true, it needs a slow path (caller must generate it) and jumps to the passed slow_path label.
   * If assume_slow_path_is_next is true, it tries to avoid jumps and just continues execution afterwards. This label may not be bound if no slow path is needed.
   * The end label must be bound where normal execution (without a slow path being taken) would continue.
   * If ignore_shared_directive is true, the shared code stub will not be used and the according configuration option ignored.
   * The obj register is preserved, t1, t2, t3 will be destroyed, the caller must ensure that they are saved (and restored) if necessary.
   */
  bool emit_store_allocation_site_inlined(Register obj, Register t1, Register t2, Register t3, RegisterOrConstant allocation_site, Label* slow_pth, Label* end, bool assume_slow_path_is_next, bool ignore_shared_directive, Klass* allocated_type);
  /*
   * This method generates a new identity hash for the receiver object according to the configured hash code strategy, but without storing it!
   * The receiver register will be preserved, the generated hash will be stored in the hash register.
   * The register t1 and t2 will be destroyed and must be saved (and restored) by the caller if necessary.
   * The returned hash value will conform to all specifications of this virtual machine (0 < hash && hash <= 2^31, the only exception is that it may be zero if the allow_zero_hash parameter is true.
   * The caller may specify an additional mask which will be combined with the internal hash mask if a smaller hash is required.
   * This method returns true if the code could be emitted, false otherwise (which may be the case depending on hash code strategy and architecture)
   */
  bool emit_generate_next_hash(Register receiver, Register hash, Register t1, Register t2, bool allow_zero_hash = false, int32_t mask = ~0);
  
  enum {
    max_array_allocation_length = 0x00FFFFFF
  };

  // allocation of arrays
  // obj        : must be rax, will contain pointer to allocated object
  // len        : array length in number of elements
  // t          : scratch register - contents destroyed
  // header_size: size of object header in words
  // f          : element scale factor
  // slow_case  : exit to slow case implementation if fast allocation fails
  void allocate_array(Register obj, Register len, Register t, Register t2, Register t3, int header_size, Address::ScaleFactor f, Register klass, Label& slow_case, Label* slow_case_trace, CallSiteIterator* call_sites, Klass* allocated_type);

  int  rsp_offset() const { return _rsp_offset; }
  void set_rsp_offset(int n) { _rsp_offset = n; }

  // Note: NEVER push values directly, but only through following push_xxx functions;
  //       This helps us to track the rsp changes compared to the entry rsp (->_rsp_offset)

  void push_jint (jint i)     { _rsp_offset++; push(i); }
  void push_oop  (jobject o)  { _rsp_offset++; pushoop(o); }
  // Seems to always be in wordSize
  void push_addr (Address a)  { _rsp_offset++; pushptr(a); }
  void push_reg  (Register r) { _rsp_offset++; push(r); }
  void pop_reg   (Register r) { _rsp_offset--; pop(r); assert(_rsp_offset >= 0, "stack offset underflow"); }

  void dec_stack (int nof_words) {
    _rsp_offset -= nof_words;
    assert(_rsp_offset >= 0, "stack offset underflow");
    addptr(rsp, wordSize * nof_words);
  }

  void dec_stack_after_call (int nof_words) {
    _rsp_offset -= nof_words;
    assert(_rsp_offset >= 0, "stack offset underflow");
  }

  void invalidate_registers(bool inv_rax, bool inv_rbx, bool inv_rcx, bool inv_rdx, bool inv_rsi, bool inv_rdi) PRODUCT_RETURN;

#endif // CPU_X86_VM_C1_MACROASSEMBLER_X86_HPP
