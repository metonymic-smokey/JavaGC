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

#ifndef CLOSURECOLLECTION_H
#define CLOSURECOLLECTION_H

// ----------------------------------------------
// ----- ROOT POINTER CLOSURES ------------------
// ----------------------------------------------

// 10+ Others
class OtherOopClosure : public OopClosure {
    private:
        EventRootPointerList* roots;
        RootType rootType;
    public:       
        OtherOopClosure(EventRootPointerList* roots, RootType rootType);
        virtual void do_oop(oop* obj_p);
        virtual void do_oop(narrowOop* obj_p);
};

// 1 + 2: ClassLoaderDataGraph classes + static fields
class ClassesAndStaticFieldsKlassClosure : public KlassClosure {
    private:
        EventRootPointerList* roots;
    public:
        ClassesAndStaticFieldsKlassClosure(EventRootPointerList* roots);
        virtual void do_klass(Klass* k);
};
// 0 + 9
class ClassLoaderDataGraphClosure : public CLDClosure {
    private:
        OtherOopClosure* oopClosure;
        ClassesAndStaticFieldsKlassClosure* klassClosure;
        EventRootPointerList* roots;
    public:
        ClassLoaderDataGraphClosure(EventRootPointerList* roots, OtherOopClosure* oopClosure, ClassesAndStaticFieldsKlassClosure* klassClosure);
        virtual void do_cld(ClassLoaderData* cld);
};

// 5
class VMInternalThreadDataOopClosure : public OopClosure {
    private:
        EventRootPointerList* roots;
        long threadId;
    public:       
        VMInternalThreadDataOopClosure(EventRootPointerList* roots);
        virtual void do_oop(oop* obj_p);
        virtual void do_oop(narrowOop* obj_p);
        virtual void set_meta(long threadId);
};

// 6
class CodeBlobOopClosure : public OopClosure {
    private:
        EventRootPointerList* roots;
        int class_id;
        int method_id;
    public:
        CodeBlobOopClosure(EventRootPointerList* roots);
        virtual void do_oop(oop* obj_p);
        virtual void do_oop(narrowOop* obj_p);
        virtual void set_meta(int class_id, int method_id);
};

// 7: JNI local
class JNILocalsOopClosure : public OopClosure {
    private:
        EventRootPointerList* roots;
        char* thread;
        long threadId;
    public:
        JNILocalsOopClosure(EventRootPointerList* roots);
        virtual void do_oop(oop* obj_p);
        virtual void do_oop(narrowOop* obj_p);
        virtual void set_meta(char* thread, long threadId);
};



// 8: JNI globals
class JNIGlobalsOopClosure : public OopClosure {
    private:
        EventRootPointerList* roots;
    public:
        JNIGlobalsOopClosure(EventRootPointerList* roots);
        virtual void do_oop(oop* obj_p);
        virtual void do_oop(narrowOop* obj_p);
};
class JNIWeakGlobalsOopClosure : public OopClosure {
    private:
        EventRootPointerList* roots;
    public:       
        JNIWeakGlobalsOopClosure(EventRootPointerList* roots);
        virtual void do_oop(oop* obj_p);
        virtual void do_oop(narrowOop* obj_p);
};
class AlwaysTrueBoolObjectClosure : public BoolObjectClosure {
    public:
        virtual bool do_object_b(oop p);
};


// debug
class DebugOopClosure : public OopClosure {
    private:
        EventRootPointerList* roots;
        char* name;
    public:       
        DebugOopClosure(EventRootPointerList* roots, char* name);
        virtual void do_oop(oop* obj_p);
        virtual void do_oop(narrowOop* obj_p);
};

class DebugKlassClosure: public KlassClosure {
    private:
        DebugOopClosure closure;
    public:
        DebugKlassClosure(EventRootPointerList* roots, char* name);
        virtual void do_klass(Klass* klass);
};

class DebugCodeBlobClosure : public CodeBlobClosure {
    private:
        DebugOopClosure closure;    
    public:
        DebugCodeBlobClosure(EventRootPointerList* roots, char* name);
        virtual void do_code_blob(CodeBlob* cb);
};



#endif