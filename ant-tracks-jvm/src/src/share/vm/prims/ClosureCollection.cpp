/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/* 
 * File:   ClosureCollection.hpp
 * Author: mw
 *
 * Created on November 9, 2017, 5:28 PM
 */

#include "precompiled.hpp"
#include "ClosureCollection.hpp"

// ----------------------------------------------
// ----- ROOT POINTER CLOSURES ------------------
// ----------------------------------------------

// 0 + 9
ClassLoaderDataGraphClosure::ClassLoaderDataGraphClosure(EventRootPointerList* roots, OtherOopClosure* oopClosure, ClassesAndStaticFieldsKlassClosure* klassClosure) {
    this->oopClosure = oopClosure;
    this->klassClosure = klassClosure;
    this->roots = roots;
}

void ClassLoaderDataGraphClosure::do_cld(ClassLoaderData* cld) {
    roots->add_class_loader((intptr_t)(HeapWord*)(cld->class_loader()), (char*)cld->loader_name());
    
    cld->_dependencies.oops_do(oopClosure);
    cld->_handles.oops_do(oopClosure);
    cld->classes_do(klassClosure);    
}

// 1 + 2
ClassesAndStaticFieldsKlassClosure::ClassesAndStaticFieldsKlassClosure(EventRootPointerList* roots) {
    this->roots = roots;
};

void ClassesAndStaticFieldsKlassClosure::do_klass(Klass* k) {
    if(roots != NULL) {
        if (k->oop_is_instance()) {
            InstanceKlass* ik = InstanceKlass::cast(k);
            roots->add_class((intptr_t)(HeapWord*)(ik->java_mirror()), AllocatedTypes::get_allocated_type_id(ik));

            instanceKlassHandle ikh = instanceKlassHandle(k);

            for (FieldStream fld(ikh, true, false); !fld.eos(); fld.next()) {
                if (fld.access_flags().is_static() && (fld.field_descriptor().field_type() == T_OBJECT || fld.field_descriptor().field_type() == T_ARRAY)) {                                           
                    int offset = fld.offset();
                    address addrToFieldValue = (address)ikh->java_mirror() + offset;            
                    oop o;
                    if (UseCompressedOops) {
                        o = oopDesc::load_decode_heap_oop((narrowOop*)addrToFieldValue);
                    } else {
                        o = oopDesc::load_decode_heap_oop((oop*)addrToFieldValue);
                    }
                    assert(o->is_oop_or_null(), "should always be an oop");
                    roots->add_static_field((intptr_t)(HeapWord*)o, AllocatedTypes::get_allocated_type_id(k), offset);
              }
            }
        }   
    } 
};

// 3 + 4 are handled in EventsGCRuntime without closure at the moment

// 5
VMInternalThreadDataOopClosure::VMInternalThreadDataOopClosure(EventRootPointerList* roots) {
    this->roots = roots;
};
void VMInternalThreadDataOopClosure::do_oop(oop* obj_p) {
    oop o = *obj_p;
    if (o != NULL && o != JNIHandles::deleted_handle()) {
        roots->add_vm_internal_thread_data((intptr_t)(HeapWord*)(*obj_p), threadId);
    }
};
void VMInternalThreadDataOopClosure::do_oop(narrowOop* obj_p) { 
    oop o = oopDesc::load_decode_heap_oop((narrowOop*)obj_p);
    roots->add_vm_internal_thread_data((intptr_t)(HeapWord*)o, threadId);
};
void VMInternalThreadDataOopClosure::set_meta(long threadId) {
    this->threadId = threadId;
};

// 6
CodeBlobOopClosure::CodeBlobOopClosure(EventRootPointerList* roots) { this->roots = roots;}
void CodeBlobOopClosure::do_oop(oop* obj_p) {
    oop o = *obj_p;
    roots->add_code_blob((intptr_t)(HeapWord*)o, class_id, method_id);
};
void CodeBlobOopClosure::do_oop(narrowOop* obj_p) { 
    oop o = oopDesc::load_decode_heap_oop((narrowOop*)obj_p);
    roots->add_code_blob((intptr_t)(HeapWord*)o, class_id, method_id);
};
void CodeBlobOopClosure::set_meta(int class_id, int method_id) {
    this->class_id = class_id;
    this->method_id = method_id;
}

// 7: JNI local
JNILocalsOopClosure::JNILocalsOopClosure(EventRootPointerList* roots) {
    this->roots = roots;
};
void JNILocalsOopClosure::do_oop(oop* obj_p) {
    oop o = *obj_p;
    if (o != NULL && o != JNIHandles::deleted_handle()) {
        roots->add_jni_local((intptr_t)(HeapWord*)(*obj_p), threadId);
    }
};
void JNILocalsOopClosure::do_oop(narrowOop* obj_p) { 
    oop o = oopDesc::load_decode_heap_oop((narrowOop*)obj_p);
    roots->add_jni_local((intptr_t)(HeapWord*)o, threadId);
};
void JNILocalsOopClosure::set_meta(char* thread, long threadId) {
    this->thread = thread;
    this->threadId = threadId;
};



// 8: JNI globals
JNIGlobalsOopClosure::JNIGlobalsOopClosure(EventRootPointerList* roots) {
    this->roots = roots;
};
inline void JNIGlobalsOopClosure::do_oop(oop* obj_p) {
    oop o = *obj_p;

    // ignore these
    if (o == NULL || o == JNIHandles::deleted_handle()) return;

    // we ignore global ref to symbols and other internal objects
    if (o->is_instance() || o->is_objArray() || o->is_typeArray()) {
        roots->add_jni_global((intptr_t)(HeapWord*)o, false);
    }
};
void JNIGlobalsOopClosure::do_oop(narrowOop* obj_p) { 
    oop o = oopDesc::load_decode_heap_oop((narrowOop*)obj_p);
    roots->add_jni_global((intptr_t)(HeapWord*)o, false);
};

JNIWeakGlobalsOopClosure::JNIWeakGlobalsOopClosure(EventRootPointerList* roots) : roots(roots) { };

void JNIWeakGlobalsOopClosure::do_oop(oop* obj_p) {
    oop o = *obj_p;
    roots->add_jni_global((intptr_t)(HeapWord*)o, true);
};
void JNIWeakGlobalsOopClosure::do_oop(narrowOop* obj_p) { 
    oop o = oopDesc::load_decode_heap_oop((narrowOop*)obj_p);
    roots->add_jni_global((intptr_t)(HeapWord*)o, true);
};
bool AlwaysTrueBoolObjectClosure::do_object_b(oop p) { return true; }



// 10+: Others   
OtherOopClosure::OtherOopClosure(EventRootPointerList* roots, RootType rootType) : roots(roots), rootType(rootType) { };

void OtherOopClosure::do_oop(oop* obj_p) {
    oop o = *obj_p;
    roots->add_other((intptr_t)(HeapWord*)o, rootType);
};
void OtherOopClosure::do_oop(narrowOop* obj_p) { 
    oop o = oopDesc::load_decode_heap_oop((narrowOop*)obj_p);
    roots->add_other((intptr_t)(HeapWord*)o, rootType);
};




// debug
DebugOopClosure::DebugOopClosure(EventRootPointerList* roots, char* name) : roots(roots), name(name) { };

void DebugOopClosure::do_oop(oop* obj_p) {
    oop o = *obj_p;
    roots->add_debug((intptr_t)(HeapWord*)o, name);
};
void DebugOopClosure::do_oop(narrowOop* obj_p) { 
    oop o = oopDesc::load_decode_heap_oop((narrowOop*)obj_p);
    roots->add_debug((intptr_t)(HeapWord*)o, name);
};

DebugKlassClosure::DebugKlassClosure(EventRootPointerList* roots, char* name) : closure(roots, name) { }

void DebugKlassClosure::do_klass(Klass* klass) {
    klass->oops_do(&closure);
}

DebugCodeBlobClosure::DebugCodeBlobClosure(EventRootPointerList* roots, char* name) : closure(roots, name) { }

void DebugCodeBlobClosure::do_code_blob(CodeBlob* cb) {
    nmethod* nm = cb->as_nmethod_or_null();
    if (nm != NULL) {
        nm->oops_do(&closure);
    }
}


