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
 * File:   EventsGCRuntime.cpp
 * Author: Philipp Lengauer
 * 
 * Created on April 2, 2014, 1:43 PM
 */

#include "precompiled.hpp"
#include "EventsGCRuntime.hpp"
#include "gc_implementation/parallelScavenge/parallelScavengeHeap.hpp"
#include "gc_implementation/parallelScavenge/parMarkBitMap.hpp"
#include "EventSynchronization.hpp"
#include "AllocationTracingSelfMonitoring.hpp"
#include "AllocationTracing.hpp"
#include "memory/allocation.hpp"
#include "gc_implementation/parNew/parNewGeneration.hpp"
#include "services/threadService.hpp"
#include "EventRootPointerList.hpp"
#include "ClosureCollection.hpp"
#include "../runtime/fprofiler.hpp"
#include "../runtime/jniHandles.hpp"
#ifdef ASSERT
#include "AllocationTracingSynchronization.hpp"
#include "AllocationSiteStorage.hpp"
#include "utilities/growableArray.hpp"
#endif

jlong EventsGCRuntime::start = 0;
Arena* EventsGCRuntime::postponed_space_redefine_arena = NULL;
Arena* EventsGCRuntime::postponed_space_destroy_arena = NULL;
GrowableArray<SpaceRedefinitionInfo*>* EventsGCRuntime::postponed_space_redefines = NULL;
GrowableArray<SpaceDestroyInfo*>* EventsGCRuntime::postponed_space_destroys = NULL;
EventRootPointerList* EventsGCRuntime::roots = NULL;
bool EventsGCRuntime::postpone_space = false;
size_t EventsGCRuntime::objects_in_eden = 0;

void flush_g1_pointers(HeapWord *first_from, HeapWord *first_to, ParMarkBitMap* bitmap, int count){
    HeapWord* from_addr = (HeapWord*) oop(first_from);
    HeapWord* to_addr = (HeapWord*) oop(first_to);
    
    for(int i = 0; i < count; i++) {
        size_t size = bitmap != NULL ? bitmap->obj_size(from_addr) : oop(to_addr)->size();
        Thread::current()->get_event_obj_ptrs()->set_meta(EVENTS_GC_MOVE_SLOW, (uintptr_t) (to_addr), true, (uintptr_t) (from_addr), -1, false);
        add_obj_pointers(to_addr);
        Thread::current()->get_event_obj_ptrs()->flush();
        from_addr += size;
        to_addr += size;
    }
}

void add_obj_pointers(HeapWord* addr){
    if(oop(addr)->is_array()) {
        if(oop(addr)->is_objArray()) {
            if(UseCompressedOops) {
                narrowOop* first = (narrowOop*) objArrayOop(addr)->base();
                narrowOop* end = first + objArrayOop(addr)->length();
                for(narrowOop* element = first; element < end; element++) {
                    Thread::current()->add_event_obj_ptr(oopDesc::decode_heap_oop(*element));
                }
            } else {
                oop* first = (oop*) objArrayOop(addr)->base();
                oop* end = first + objArrayOop(addr)->length();
                for(oop* element = first; element < end; element++) {
                    Thread::current()->add_event_obj_ptr(oopDesc::decode_heap_oop(*element));
                }
            }
        }
    } else {
        InstanceKlass* klass = (InstanceKlass*)oop(addr)->klass();
        //printf("Adding object pointers, pointer count for type %s: %d\n", klass->name()->as_C_string(), (int)klass->nonstatic_oop_map_count());
        OopMapBlock* map           = klass->start_of_nonstatic_oop_maps();          
        OopMapBlock* const end_map = map + klass->nonstatic_oop_map_count();
        if (UseCompressedOops) {
            while (map < end_map) {
                narrowOop* p = (narrowOop*)oop(addr)->obj_field_addr<narrowOop>(map->offset());
                narrowOop* const end = p + (map->count());
                while (p < end) {
                    //printf("add pointer (%p < %p): %p\n", p, end, oopDesc::decode_heap_oop(*p));
                    Thread::current()->add_event_obj_ptr(oopDesc::decode_heap_oop(*p));
                    ++p;
                }
                ++map;
            }
        } else {
            while (map < end_map) {
                oop* p = (oop*)oop(addr)->obj_field_addr<oop>(map->offset());
                oop* const end = p + (map->count());
                while (p < end) {
                    Thread::current()->add_event_obj_ptr(oopDesc::decode_heap_oop(*p));
                    ++p;
                }
                ++map;
            }
        }
    }
}


class HandlerObjectClosure : public ObjectClosure {
private:
    void (*handler)(oop obj);
public:
    HandlerObjectClosure(void (*handler)(oop obj)) : handler(handler) {}
    
    void do_object(oop obj) {
        handler(obj);
    }
};

void EventsGCRuntime::init() {
    start = 0;
    postponed_space_redefine_arena = new (mtTracing) Arena(mtTracing);
    postponed_space_redefines = new (postponed_space_redefine_arena) GrowableArray<SpaceRedefinitionInfo*>(postponed_space_redefine_arena, 4, 0, NULL);
    postponed_space_destroy_arena = new (mtTracing) Arena(mtTracing);
    postponed_space_destroys = new (postponed_space_destroy_arena) GrowableArray<SpaceDestroyInfo*>(postponed_space_destroy_arena, 4, 0, NULL);
    roots = new EventRootPointerList();
}

void EventsGCRuntime::destroy() {
    delete postponed_space_redefine_arena;
    postponed_space_redefine_arena = NULL;
    delete postponed_space_destroy_arena;
    postponed_space_destroy_arena = NULL;
    postponed_space_redefines = NULL;
    postponed_space_destroys = NULL;
    //assert(_collectioncounter == 0, "uneven number of gc_start_end events!");
}

void EventsGCRuntime::process_basic_data_type_arrays(Klass* klass) {
    while (klass != NULL) {
        roots->add_class((intptr_t)(HeapWord*)klass->java_mirror(), AllocatedTypes::get_allocated_type_id(klass));
        //printf("Universe basic data type arrays - %s\n", klass->signature_name());

        // get the array class for the next rank
        klass = klass->array_klass_or_null();
    }
}

// --------------------- 
// WRITING ROOT POINTERS 
// --------------------- 
void EventsGCRuntime::write_root_pointers() {
    roots->clear();
    
    // Define root pointer closures
    // 0 + 1 + 2 + 7
    OtherOopClosure classLoaderInternalsOopClosure(roots, CLASS_LOADER_INTERNAL_ROOT);
    ClassesAndStaticFieldsKlassClosure classesAndStaticFieldsKlassClosure(roots);
    ClassLoaderDataGraphClosure classLoaderDataGraphClosure(roots, &classLoaderInternalsOopClosure, &classesAndStaticFieldsKlassClosure);
    // 3 + 4 without closure
    // 5
    VMInternalThreadDataOopClosure vmInternalThreadDataOopClosure(roots);
    // 6
    CodeBlobOopClosure codeBlobOopClosure(roots);
    // 7
    JNILocalsOopClosure jniLocalsOopClosure(roots);
    // 8
    JNIGlobalsOopClosure jniGlobalsOopClosure(roots);
    JNIWeakGlobalsOopClosure jniWeakGlobalsOopClosure(roots);
    AlwaysTrueBoolObjectClosure always_true;
    // 9
    OtherOopClosure universeOopClosure(roots, UNIVERSE_ROOT);
    // 10
    OtherOopClosure systemDictionaryOopClosure(roots, SYSTEM_DICTIONARY_ROOT);
    // 11
    OtherOopClosure busyMonitorOopClosure(roots, BUSY_MONITOR_ROOT);
    // 12
    OtherOopClosure internedStringOopClosure(roots, INTERNED_STRING_ROOT);    
    // 13
    OtherOopClosure flatProfilerOopClosure(roots, FLAT_PROFILER_ROOT);
    // 14
    OtherOopClosure managementOopClosure(roots, MANAGEMENT_ROOT);
    // 15
    OtherOopClosure jvmtiExportOopClosure(roots, JVMTI_ROOT); 
    
    // apply root pointer closures
    // 0 + 1 + 2 + 9
    ClassLoaderDataGraph::cld_do(&classLoaderDataGraphClosure);
    SystemDictionary::always_strong_classes_do(&classesAndStaticFieldsKlassClosure);
    Universe::basic_type_classes_do(&process_basic_data_type_arrays);
    // 3 + 4 + 7 
    for (JavaThread* thread = Threads::first(); thread != NULL ; thread = thread->next()) {
        oop threadObj = thread->threadObj();            
        long threadId = (long)java_lang_Thread::thread_id(threadObj);
        roots->add_vm_internal_thread_data((intptr_t)(HeapWord*)threadObj, threadId);
        
        if (thread->has_last_Java_frame()) {
            if (threadObj != NULL && !thread->is_exiting() && !thread->is_hidden_from_external_view()) {
                // printf("Handling Java thread %s\n", thread->name());
                ThreadStackTrace* stack_trace = new ThreadStackTrace(thread, false);
                stack_trace->dump_stack_at_safepoint(-1);

                RegisterMap reg_map(thread);
                frame f = thread->last_frame();
                vframe* vf = vframe::new_vframe(&f, &reg_map, thread);
                frame* last_entry_frame = NULL;

                int stack_depth = stack_trace->get_stack_depth();                
                for(int i = 0; i < stack_depth; i++, vf = vf->sender()) {
                    StackFrameInfo* stack_frame = stack_trace->stack_frame_at(i);
                    Method* method = stack_frame->method();

                    if (vf->is_java_frame()) {
                        // printf("  Java frame\n");
                        // java frame (interpreted, compiled, ...)
                        javaVFrame *jvf = javaVFrame::cast(vf);
                        if (!(jvf->method()->is_native())) {
                            // printf("    Non-native frame\n");
                            StackValueCollection* locals = jvf->locals();
                            for (int slot=0; slot<locals->size(); slot++) {
                                if (locals->at(slot)->type() == T_OBJECT || locals->at(slot)->type() == T_ARRAY) {
                                    oop o = locals->obj_at(slot)();
                                    if (o != NULL) {  
                                        roots->add_local_variable((intptr_t)(HeapWord*)o, threadId, AllocatedTypes::get_allocated_type_id(method->method_holder()), method->method_idnum(), slot);
                                    }
                                }
                            }
                        } else {
                            // printf("    Native frame\n");
                            // native frame  
                            if (i == 0) {
                                // printf("      Top frame on thread %s with id %ld, write JNI locals\n", thread->name(), threadId);
                                // JNI locals for the top frame.
                                jniLocalsOopClosure.set_meta(thread->name(), threadId);
                                thread->active_handles()->oops_do(&jniLocalsOopClosure);
                            } else {
                              // printf("      Not top frame on thread %s with id %ld, write JNI locals\n", thread->name(), threadId);
                              if (last_entry_frame != NULL) {
                                //printf("        Using entry frame\n");
                                // JNI locals for the entry frame
                                assert(last_entry_frame->is_entry_frame(), "checking");
                                jniLocalsOopClosure.set_meta(thread->name(), threadId);
                                last_entry_frame->entry_frame_call_wrapper()->handles()->oops_do(&jniLocalsOopClosure);
                              }
                            }
                        }
                      
                    } else {
                        // printf("  External frame\n");
                        // externalVFrame - if it's an entry frame then report any JNI locals
                        // as roots when we find the corresponding native javaVFrame
                        frame* fr = vf->frame_pointer();
                        assert(fr != NULL, "sanity check");
                        if (fr->is_entry_frame()) {
                          last_entry_frame = fr;
                        }
                    }
                }
            } else {                
                // printf("Not handling thread %s\n", thread->name());
            }
       } else {
            // printf("Handle JNI local of thread %ld - %s\n", threadId, thread->name());
            // no last java frame but there may be JNI locals
            jniLocalsOopClosure.set_meta(thread->name(), (long)java_lang_Thread::thread_id(threadObj));
            thread->active_handles()->oops_do(&jniLocalsOopClosure);
       }
    }
    // 5
    // taken from Threads::oops_do
    for (JavaThread* thread = Threads::first(); thread != NULL; thread = thread->next()) {
        oop threadObj = thread->threadObj();            
        long threadId = (long)java_lang_Thread::thread_id(threadObj);
        vmInternalThreadDataOopClosure.set_meta(threadId);
        thread->oops_do(&vmInternalThreadDataOopClosure, NULL, NULL);
    }
    vmInternalThreadDataOopClosure.set_meta((long)VMThread::vm_thread()->self_raw_id());
    VMThread::vm_thread()->oops_do(&vmInternalThreadDataOopClosure, NULL, NULL);
    // 6
    // taken from CodeCache::blobs_do
    for (CodeBlob *blob = CodeCache::alive(CodeCache::first()); blob != NULL; blob = CodeCache::alive(CodeCache::next(blob)))  {
        nmethod* nmethod = blob->as_nmethod_or_null();
        if(nmethod != NULL && nmethod->method() != NULL) {
            codeBlobOopClosure.set_meta(AllocatedTypes::get_allocated_type_id(nmethod->method()->method_holder()), nmethod->method()->method_idnum());
            nmethod->oops_do(&codeBlobOopClosure);
        }
    }
    // 7
    JNIHandles::oops_do(&jniGlobalsOopClosure);
    JNIHandles::weak_oops_do(&always_true, &jniWeakGlobalsOopClosure);
    // 8
    Universe::oops_do(&universeOopClosure);
    // 10
    SystemDictionary::always_strong_oops_do(&systemDictionaryOopClosure);
    // 11
    ObjectSynchronizer::oops_do(&busyMonitorOopClosure);
    // 12
    StringTable::oops_do(&internedStringOopClosure);    
    // 13
    FlatProfiler::oops_do(&flatProfilerOopClosure);
    // 14
    Management::oops_do(&managementOopClosure);
    // 15
    JvmtiExport::oops_do(&jvmtiExportOopClosure);
    
     // ref_processor()->weak_oops_do(adjust_pointer_closure());
     // PSScavenge::reference_processor()->weak_oops_do(adjust_pointer_closure())

    roots->flush();
}

jint EventsGCRuntime::fire_gc_start(GCType type, GCCause::Cause cause, bool concurrent, bool gc_only, bool allow_rotation) {
    static jint id = 0;
    assert(!(type == GCType_Major || type == GCType_Major_Sync) || !is_gc_active(), "can't start major GC while other GC is active");
        
    if(allow_rotation && EventSynchronization::should_rotate()) {
        EventSynchronizationType sync_type;
        if(type == GCType_Major) {
            type = GCType_Major_Sync;
            sync_type = OnMajor;
        } else if(TraceObjectsMaxTraceSizeSynchronizeOnMinor && type == GCType_Minor) {
            type = GCType_Minor_Sync;
            sync_type = OnMinor;
        } else {
            sync_type = None;
        }
        if(sync_type != None) {
            EventSynchronization::start_synchronization(sync_type);
        } else {
            EventBuffersFlushAll::flush_all();
        }
        
        if(UseParallelGC) {
            ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
            EventsRuntime::fire_space_creation(PARALLEL_GC_OLD_ID, heap->old_gen()->object_space()->bottom(), heap->old_gen()->object_space()->end());
            EventsRuntime::fire_space_creation(PARALLEL_GC_EDEN_ID, heap->young_gen()->eden_space()->bottom(), heap->young_gen()->eden_space()->end());
            EventsRuntime::fire_space_creation(PARALLEL_GC_SURVIVOR_1_ID, heap->young_gen()->survivor_1_space()->bottom(), heap->young_gen()->survivor_1_space()->end());
            EventsRuntime::fire_space_creation(PARALLEL_GC_SURVIVOR_2_ID, heap->young_gen()->survivor_2_space()->bottom(), heap->young_gen()->survivor_2_space()->end());
            EventsRuntime::fire_space_alloc(PARALLEL_GC_OLD_ID, SPACE_MODE_NORMAL, OLD_SPACE);
            EventsRuntime::fire_space_alloc(PARALLEL_GC_EDEN_ID, SPACE_MODE_NORMAL, EDEN_SPACE);
            EventsRuntime::fire_space_alloc(PARALLEL_GC_SURVIVOR_1_ID, SPACE_MODE_NORMAL, SURVIVOR_SPACE);
            EventsRuntime::fire_space_alloc(PARALLEL_GC_SURVIVOR_2_ID, SPACE_MODE_NORMAL, SURVIVOR_SPACE);
        } else if(UseG1GC) {
            G1CollectedHeap* heap = G1CollectedHeap::heap();
            for(uint index = 0; index < heap->num_regions(); index++) {
                HeapRegion* region = heap->region_at(index);
                EventsRuntime::fire_space_creation(index, region->bottom(), region->end());
                SpaceType type;
                SpaceMode mode;
                if(region->is_eden()) {
                    type = EDEN_SPACE;
                    mode = SPACE_MODE_NORMAL;
                } else if(region->is_survivor()) {
                    type = SURVIVOR_SPACE;
                    mode = SPACE_MODE_NORMAL;
                } else if(region->is_old()) {
                    type = OLD_SPACE;
                    mode = SPACE_MODE_NORMAL;
                } else if(region->is_free()) {
                    continue;
                } else if(region->startsHumongous()) {
                    type = OLD_SPACE;
                    mode = SPACE_MODE_HUMONGOUS_START;
                } else if(region->continuesHumongous()) {
                    type = OLD_SPACE;
                    mode = SPACE_MODE_HUMONGOUS_CONTINUES;
                } else {
                    assert(false, "here be dragons");
                }
                EventsRuntime::fire_space_alloc(index, mode, type);
                assert(region->sendPostEvents == false, "assumption during collection");
                assert(region->firstAddrEvacuatedTo == NULL, "assumption during collection");
            }
        } else if(UseConcMarkSweepGC) {
            GenCollectedHeap* heap = GenCollectedHeap::heap();
            DefNewGeneration* young = (DefNewGeneration*) heap->get_gen(0);
            ConcurrentMarkSweepGeneration* old = (ConcurrentMarkSweepGeneration*) heap->get_gen(1);
            ContiguousSpace* survivor_1 = young->from()->bottom() < young->to()->bottom() ? young->from() : young->to();
            ContiguousSpace* survivor_2 = young->from()->bottom() < young->to()->bottom() ? young->to() : young->from();
            EventsRuntime::fire_space_creation(GENERATIONAL_GC_GEN_0_EDEN_ID, young->eden()->bottom(), young->eden()->end());
            EventsRuntime::fire_space_creation(GENERATIONAL_GC_GEN_0_SURVIVOR_1_ID, survivor_1->bottom(), survivor_1->end());
            EventsRuntime::fire_space_creation(GENERATIONAL_GC_GEN_0_SURVIVOR_2_ID, survivor_2->bottom(), survivor_2->end());
            EventsRuntime::fire_space_creation(GENERATIONAL_GC_GEN_1_OLD_ID, old->cmsSpace()->bottom(),old->cmsSpace()->end());
            EventsRuntime::fire_space_alloc(GENERATIONAL_GC_GEN_0_EDEN_ID, SPACE_MODE_NORMAL, EDEN_SPACE);
            EventsRuntime::fire_space_alloc(GENERATIONAL_GC_GEN_0_SURVIVOR_1_ID, SPACE_MODE_NORMAL, SURVIVOR_SPACE);
            EventsRuntime::fire_space_alloc(GENERATIONAL_GC_GEN_0_SURVIVOR_2_ID, SPACE_MODE_NORMAL, SURVIVOR_SPACE);
            EventsRuntime::fire_space_alloc(GENERATIONAL_GC_GEN_1_OLD_ID, SPACE_MODE_NORMAL, OLD_SPACE);
        } else if(UseSerialGC) {
            GenCollectedHeap* heap = GenCollectedHeap::heap();
            DefNewGeneration* young = (DefNewGeneration*) heap->get_gen(0);
            OneContigSpaceCardGeneration* old = (OneContigSpaceCardGeneration*) heap->get_gen(1);
            ContiguousSpace* survivor_1 = young->from()->bottom() < young->to()->bottom() ? young->from() : young->to();
            ContiguousSpace* survivor_2 = young->from()->bottom() < young->to()->bottom() ? young->to() : young->from();
            EventsRuntime::fire_space_creation(GENERATIONAL_GC_GEN_0_EDEN_ID, young->eden()->bottom(), young->eden()->end());
            EventsRuntime::fire_space_creation(GENERATIONAL_GC_GEN_0_SURVIVOR_1_ID, survivor_1->bottom(), survivor_1->end());
            EventsRuntime::fire_space_creation(GENERATIONAL_GC_GEN_0_SURVIVOR_2_ID, survivor_2->bottom(), survivor_2->end());
            EventsRuntime::fire_space_creation(GENERATIONAL_GC_GEN_1_OLD_ID, old->bottom_mark().point(), old->bottom_mark().point() + old->capacity());
            EventsRuntime::fire_space_alloc(GENERATIONAL_GC_GEN_0_EDEN_ID, SPACE_MODE_NORMAL, EDEN_SPACE);
            EventsRuntime::fire_space_alloc(GENERATIONAL_GC_GEN_0_SURVIVOR_1_ID, SPACE_MODE_NORMAL, SURVIVOR_SPACE);
            EventsRuntime::fire_space_alloc(GENERATIONAL_GC_GEN_0_SURVIVOR_2_ID, SPACE_MODE_NORMAL, SURVIVOR_SPACE);
            EventsRuntime::fire_space_alloc(GENERATIONAL_GC_GEN_1_OLD_ID, SPACE_MODE_NORMAL, OLD_SPACE);
        } else {
            assert(false, "wtf");
        }
    }
    EventsRuntime::fire_gc_start_end(EVENTS_GC_START, ++id, type, cause, concurrent, false, gc_only);
    fire_postponed_space_events();
    
    if(TraceObjectsGCRoots) {
        write_root_pointers();
    }
    
    self_monitoring(1) {
        start = AllocationTracing::get_trace_writer()->size();
    }
    self_monitoring(3) {
        objects_in_eden = count_eden_objects();
        if(EventSynchronization::is_synchronizing()) {
            AllocationTracingSelfMonitoring::prepare_for_report_sync_quality();
        } else {
            AllocationTracingSelfMonitoring::report_sync_quality();
        }
    }
#ifdef ASSERT
    if(TraceObjectsGCEnableParanoidAssertions) {
        clear_handled_objects(!UseConcMarkSweepGC);
        synchronized(lock) { // with concurrent collections this may be accessed by multiple threads
            ++_collectioncounter;
            if(type == GCType_Minor || type == GCType_Minor_Sync) {
                if(UseSerialGC) {
                    old_watermark = ((OneContigSpaceCardGeneration*) ((GenCollectedHeap*) Universe::heap())->get_gen(1))->top_mark().point();
                } else if (UseParallelGC) {
                    old_watermark = ((ParallelScavengeHeap*) Universe::heap())->old_gen()->object_space()->top();
                }
            }
        }
        if(TraceObjectsSaveAllocationSites) {
            verify_allocation_sites();
        }
    }
#endif
    return id;
}

void EventsGCRuntime::fire_gc_end(GCType type, jint id, GCCause::Cause cause, bool failed, bool gc_only) {
#ifdef ASSERT
    assert(!TraceObjectsGCEnableParanoidAssertions || is_gc_active(), "has to be");
#endif
    EventsRuntime::fire_gc_start_end(EVENTS_GC_END, id, type, cause, false, failed, gc_only);
    fire_postponed_space_events();
    bool was_sync = EventSynchronization::is_synchronizing();
    if(was_sync) {
        EventSynchronization::stop_synchronization();
    }
    
    self_monitoring(1) {
        EventBuffersFlushAll::wait_for_all_serialized();
        jlong size = AllocationTracing::get_trace_writer()->size() - start;
        switch(type) {
            case GCType_Minor: if(was_sync) AllocationTracingSelfMonitoring::report_minor_sync(size); else AllocationTracingSelfMonitoring::report_minor_gc(size); break;
            case GCType_Major: if(was_sync) AllocationTracingSelfMonitoring::report_major_sync(size); else AllocationTracingSelfMonitoring::report_major_gc(size); break;
            default: assert(false, "here be dragons");
        }
    }
    self_monitoring(3) {
        if(objects_in_eden > 0) {
            double survivor_ratio = 1.0 * count_eden_survivors() / objects_in_eden;
            AllocationTracingSelfMonitoring::report_survivor_ratio(survivor_ratio);
        }
    }
#ifdef ASSERT
    if(TraceObjectsGCEnableParanoidAssertions) {
        synchronized(lock) {
            --_collectioncounter;
            assert(_collectioncounter >= 0, "double gc_end events?");
        }
        if(!failed) {
            if(type == GCType_Major || type == GCType_Major_Sync) {
                verify_all_objects_handled_in(Universe::heap());
            } else {
                if (UseParallelGC && (UseParallelOldGC || !UseParallelOldGC)) {
                    ParallelScavengeHeap* heap = ParallelScavengeHeap::heap();
                    assert(heap->young_gen()->eden_space()->is_empty(), "eden must be empty");
                    verify_all_objects_handled_in(heap->young_gen()->from_space());
                    assert(heap->young_gen()->to_space()->is_empty(), "survivor to must be empty");
                    verify_all_objects_handled_in(heap->old_gen()->object_space(), old_watermark);
                } else if(UseG1GC) {
                    G1CollectedHeap* heap = G1CollectedHeap::heap();
                  uint regionIdx = 0;
                  for(; regionIdx < heap->num_regions(); regionIdx++) {
                    HeapRegion* region = heap->region_at(regionIdx);
                    // only regions we evacuated into have address set!
                    bool isSurv = region->is_survivor();
                    bool isEden = region->is_eden();
                    bool isYoun = region->is_young();
                    bool isOld  = region->is_old();
                    bool isEmpt = region->is_empty();
                    bool isMark = region->is_marked();
                    bool isDirt = region->is_on_dirty_cards_region_list();
                    bool isHumo = region->isHumongous();
                    bool isCont = region->continuesHumongous();
                    bool sndPst = region->sendPostEvents;

                    if (!sndPst && region->firstAddrEvacuatedTo != NULL) {
                      verify_all_objects_handled_in(region, region->firstAddrEvacuatedTo);
                      clear_objects_in(region);
                    }
                    region->firstAddrEvacuatedTo = NULL;
                  }
                  assert(regionIdx != 0, "there must always be a lich ki- a region being evacuated into");
                  assert(handled_objects->Size() == 0, "we copied more objects than we expected?");
                } else if(UseConcMarkSweepGC && (UseParNewGC || !UseParNewGC)) {
                    GenCollectedHeap* gch = GenCollectedHeap::heap();
                    if(GCCause::_cms_initial_mark == cause || GCCause::_cms_final_remark == cause
                            || GCCause::_cms_concurrent_mark == cause || GCCause::_cms_sweeping == cause
                            || GCCause::_no_gc == cause) {
                        Generation* g1 = gch->get_gen(1);
                        assert(g1->kind() == Generation::ConcurrentMarkSweep || g1->kind() == Generation::ASConcurrentMarkSweep, "Wrong generation!");
                        ConcurrentMarkSweepGeneration* cmsgen = (ConcurrentMarkSweepGeneration*) g1;
                        /*assert(0 == nr_of_objects_traced(cmsgen->cmsSpace()), "sanity check");
                        synchronized(lock) {
                            assert(handled_objects->Size() == 0, "ParNew did not clean up properly");
                        }*/
                    } else {
                        assert(GCCause::_no_gc != cause, "invariant");
                        Generation* g0 = gch->get_gen(0);
                        assert(g0->kind() == Generation::ParNew || g0->kind() == Generation::ASParNew || g0->kind() == Generation::DefNew, "Wrong generation!");
                        DefNewGeneration* young = (DefNewGeneration*) g0;
                        Generation* g1 = gch->get_gen(1);
                        assert(g1->kind() == Generation::ConcurrentMarkSweep || g1->kind() == Generation::ASConcurrentMarkSweep, "Wrong generation!");
                        ConcurrentMarkSweepGeneration* old = (ConcurrentMarkSweepGeneration*) g1;
                        assert(gch->n_gens() == 2, "oops, missed a new generation");
                    
                        bool is_young = !gc_only;
                        if(is_young) {
                            assert(failed || young->eden()->is_empty(), "eden must be empty!");
                            assert(young->to()->is_empty(), "survivor must be emtpy!");
                            /* manually clear all affected spaces */
                            clear_objects_in(young->to());
                            /* now we can verify */
                            verify_all_objects_handled_in(young->eden());
                            verify_all_objects_handled_in(young->from());
                        } else {
                            //synchronized(Heap_lock) {
                                // TODO because this is not executed at a safepoints, several checks fail in space-iterate(...)
                                //verify_all_objects_handled_of(old->cmsSpace());
                            //}
                        }
                        if (is_gc_active()) {
                            // clear objects during ongoing CMS collection, to not interfere
                            // with with verification
                            handled_objects->Clear();
                        }
                    }
                } else if(UseSerialGC && (UseParNewGC || !UseParNewGC)) {
                    GenCollectedHeap* heap = GenCollectedHeap::heap();
                    assert(heap->n_gens() == 2, "will use index 0 and 1 below");
                    Generation* g0 = heap->get_gen(0);
                    assert(g0->kind() == Generation::DefNew, "wrong generation!");
                    DefNewGeneration* young = (DefNewGeneration*) g0;
                    Generation* g1 = heap->get_gen(1);
                    assert(g1->kind() == Generation::MarkSweepCompact, "wrong generation!");
                    TenuredGeneration* old = (TenuredGeneration*) g1;
                    assert(young->eden()->is_empty(), "eden must be empty");
                    assert(young->to()->is_empty(), "survivor to must be empty");
                    verify_all_objects_handled_in(young->from());
                    verify_all_objects_handled_in((Generation*) old, old_watermark);
                } else {
                    assert(false, "here be dragons");
                }
            }
            if(TraceObjectsSaveAllocationSites) {
                verify_allocation_sites();
            }
        }
        clear_handled_objects(false);
    }
#endif
}

void EventsGCRuntime::fire_postponed_space_events() {
    if(postponed_space_redefines->length() > 0 || postponed_space_destroys->length() > 0) {
        EventsRuntime::fire_sync(true);
    }
    for(int i = 0; i < postponed_space_redefines->length(); i++) {
        SpaceRedefinitionInfo* redef = postponed_space_redefines->at(i);
        EventsRuntime::fire_space_redefine(redef->index, redef->bottom, redef->end);
        free(redef);
    }
    postponed_space_redefines->clear();
    for(int i = 0; i < postponed_space_destroys->length(); i++) {
        SpaceDestroyInfo* info = postponed_space_destroys->at(i);
        EventsRuntime::fire_space_destroyed(info->index, info->count);
        free(info);
    }
    postponed_space_destroys->clear();
}

void EventsGCRuntime::fire_gc_info(jint space_id, jint gc_id) {
    EventsRuntime::fire_gc_info(space_id, gc_id);
#ifdef ASSERT
    if(TraceObjectsGCEnableParanoidAssertions && UseG1GC) {
        G1CollectedHeap* heap = G1CollectedHeap::heap();
        add_handled_objects(heap->region_at(space_id), false, true);
    }
#endif
}

void EventsGCRuntime::fire_gc_failed(jint space_id) {
    EventsRuntime::fire_gc_failed(space_id);
}

void EventsGCRuntime::fire_gc_interrupt(jint gc_id, HeapWord* addr) {
    EventsRuntime::fire_gc_interrupt_continue(EVENTS_GC_INTERRUPT, gc_id, addr);
}

void EventsGCRuntime::fire_gc_continue(jint gc_id, HeapWord* addr) {
    EventsRuntime::fire_gc_interrupt_continue(EVENTS_GC_CONTINUE, gc_id, addr);    
}

void EventsGCRuntime::fire_plab_alloc(HeapWord* addr, size_t size) {
    EventsRuntime::fire_plab_alloc(addr, size);
}

void EventsGCRuntime::fire_plab_flushed(HeapWord* addr, oop filler) { 
    //actually, this method doesn't fire an event but is only used for consistency checking
    EventsGCRuntime::fire_gc_filler_alloc(filler);
}

void EventsGCRuntime::fire_gc_move_region(oop from, oop to, jint num_of_objects, ParMarkBitMap* bitmap) {
    if(EventSynchronization::is_synchronizing()) {
        HeapWord* from_addr = (HeapWord*) from;
        HeapWord* to_addr = (HeapWord*) to;
        for(int i = 0; i < num_of_objects; i++) {
            size_t size;
            if (bitmap != NULL && bitmap->region_start() <= from_addr && from_addr < bitmap->region_end()) {
                size = bitmap->obj_size(from_addr);
            } else {
                size = oop(to_addr)->size();
            }
            EventsRuntime::fire_sync_obj(oop(from_addr), oop(to_addr), (int) size, true);
            from_addr += size;
            to_addr += size;
        }
    } else {
        EventsRuntime::fire_gc_move_region(from, to, num_of_objects);
    }
#ifdef ASSERT
    if(TraceObjectsGCEnableParanoidAssertions) {
        HeapWord* from_addr = (HeapWord*) from;
        HeapWord* to_addr = (HeapWord*) to;
        for(int i = 0; i < num_of_objects; i++) {
            size_t size;
            if (bitmap != NULL && bitmap->region_start() <= from_addr && from_addr < bitmap->region_end()) {
                size = bitmap->obj_size(from_addr);
            } else {
                size = oop(to_addr)->size();
            }
            add_handled_object(oop(from_addr), oop(to_addr));
            from_addr += size;
            to_addr += size;
        }
    }
#endif
}

void EventsGCRuntime::sync() {
    EventsRuntime::fire_sync();
}

size_t EventsGCRuntime::count_eden_objects() {
    size_t count = 0;
    if(UseParallelGC) {
        MutableSpace* space = ParallelScavengeHeap::heap()->young_gen()->eden_space();
        for(HeapWord* cursor = space->bottom(); cursor < space->top(); cursor += oop(cursor)->size()) {
            count++;
        }
    } else if(UseG1GC) {
        for(uint index = 0; index < G1CollectedHeap::heap()->num_regions(); index++) {
            HeapRegion* region = G1CollectedHeap::heap()->region_at(index);
            if(region->is_eden()) {
                for(HeapWord* cursor = region->bottom(); cursor < region->top(); cursor += oop(cursor)->size()) {
                    count++;
                }
            }
        }
    } else if (UseSerialGC || UseConcMarkSweepGC) {
        GenCollectedHeap* gch = GenCollectedHeap::heap();
        Generation* g0 = gch->get_gen(0);
        DefNewGeneration* young = (DefNewGeneration*) g0;
        for(HeapWord* cursor = young->eden()->bottom(); cursor < young->eden()->top(); cursor+= oop(cursor)->size()) {
            count++;
        }
    } else {
        assert(false, "wtf");
    }
    return count;
}

size_t EventsGCRuntime::count_eden_survivors() {
    size_t count = 0;
    if(UseParallelGC) {
        MutableSpace* space = ParallelScavengeHeap::heap()->young_gen()->from_space();
        for(HeapWord* cursor = space->bottom(); cursor < space->top(); cursor += oop(cursor)->size()) {
            if(oop(cursor)->age() == 1) {
                count++;
            }
        }
    } else if (UseG1GC) {
        for(uint index = 0; index < G1CollectedHeap::heap()->num_regions(); index++) {
            HeapRegion* region = G1CollectedHeap::heap()->region_at(index);
            if(region->is_survivor()) {
                for(HeapWord* cursor = region->bottom(); cursor < region->top(); cursor += oop(cursor)->size()) {
                    if(oop(cursor)->age() == 1) {
                        count++;
                    }
                }
            }
        }
    } else if (UseSerialGC || UseConcMarkSweepGC) {
        GenCollectedHeap* gch = GenCollectedHeap::heap();
        assert(gch->n_gens() >= 1, "Will use index 0 below");
        Generation* g0 = gch->get_gen(0);
        DefNewGeneration* young = (DefNewGeneration*) g0;
        for(HeapWord* cursor = young->from()->bottom(); cursor < young->from()->top(); cursor+= oop(cursor)->size()) {
            if(oop(cursor)->age() == 1) count++;
        }
    } else {
        assert(false, "wtf");        
    }
    return count;
}

#ifdef ASSERT
Monitor* EventsGCRuntime::lock = new Monitor(Mutex::native, "EventsGCRuntime Paranoid Assertions Lock", true);
Arena* EventsGCRuntime::arena = NULL;
Dict* EventsGCRuntime::handled_objects = NULL;
volatile int32 EventsGCRuntime::_collectioncounter = 0;
HeapWord* EventsGCRuntime::old_watermark = NULL;

#define ObjectHandledStatus void*
#define OBJECT_UNHANDLED (NULL)
#define OBJECT_HANDLED_MAY_AGAIN ((void*) 0x1)
#define OBJECT_HANDLED ((void*) 0x2)

void EventsGCRuntime::add_handled_object(oop from, oop to, bool allow_to_be_handled_again) {
    if (!is_gc_active()) return; // simply ignore, because we don't know if everything has been initialized yet
    oop obj = to;

    assert(TraceObjectsGCEnableParanoidAssertions, "who is calling?");
    assert(obj != NULL, "just checking");
    assert(is_gc_active(), "just checking");
    ObjectHandledStatus prev_status;
    synchronized(lock) {
        prev_status = handled_objects->Insert(obj, allow_to_be_handled_again ? OBJECT_HANDLED_MAY_AGAIN : OBJECT_HANDLED);
    }
        assert(prev_status == OBJECT_UNHANDLED || prev_status == OBJECT_HANDLED_MAY_AGAIN, "object already handled (event fired twice for same object?)");
    }

void EventsGCRuntime::add_handled_objects(HeapRegion* region, bool is_prepared, bool allow_to_be_handled_again) {
    if(region->continuesHumongous()) return;
    HeapWord* q = region->bottom();
    HeapWord* t = region->top();
    while(q < t) {
        if(oop(q)->is_gc_marked()) {
            add_handled_object(oop(q), oop(q), allow_to_be_handled_again);
            q = q + oop(q)->size();
        } else {
            if(is_prepared) {
                q = (HeapWord*) oop(q)->mark()->decode_pointer();
                if(q == NULL) return;
            } else {
                q = q + oop(q)->size();
            }
        }
    }
}

void EventsGCRuntime::assert_handled_object(oop obj) {
    assert(TraceObjectsGCEnableParanoidAssertions, "who is calling?");
    synchronized(lock) {
        assert((*handled_objects)[obj] != OBJECT_UNHANDLED, "object in collected space although no move event has been sent");
        handled_objects->Delete(obj);
    }
}

void EventsGCRuntime::assert_handled_object_space(oop obj) {
    assert(TraceObjectsGCEnableParanoidAssertions, "who is calling?");
    assert(obj->is_oop(), "expected oop");
    synchronized(lock) {
      // skip alignment allocations (parNew uses int[])
      if (obj->mark() != markOopDesc::prototype()) {
        ObjectHandledStatus prev_status = (*handled_objects)[obj];
        assert(prev_status != OBJECT_UNHANDLED, "object in collected space although no move event has been sent");
      }
      handled_objects->Delete(obj);
    }
}

bool EventsGCRuntime::clear_handled_objects(bool should_be_empty) {
    assert(TraceObjectsGCEnableParanoidAssertions, "who is calling?");
    bool reti = false;
    synchronized(lock) {
        if(arena == NULL) {
            arena = new(mtOther) Arena(mtOther);
        }
        if(handled_objects == NULL) {
            handled_objects = new(arena) Dict(cmpkey, hashptr, arena);
        }
        if (!is_gc_active()) {
            assert(!should_be_empty || handled_objects->Size() == 0, "?");
            handled_objects->Clear();
            reti = true;
        }
    }
    assert(is_gc_active() || reti, "handled_objects should have been cleared!");
    return reti;
}

uint32 EventsGCRuntime::nr_of_objects_traced(Space* space) {
  assert(TraceObjectsGCEnableParanoidAssertions, "who is calling?");
  uint32 reti = 0;
  
  synchronized(lock) {
    for(DictI i(handled_objects); i.test(); ++i) {
      if (space->is_in(i._key)) reti++;
    }
  }
  return reti;
}

class VerifyObjectHandledClosure : public ObjectClosure {
    private:
        void (*verify)(oop obj);
        HeapWord* first;
    public:
        VerifyObjectHandledClosure(void (*verify)(oop obj), HeapWord* first = NULL) : verify(verify), first(first) {}

        void do_object(oop obj) {
            if(first == NULL || first <= (HeapWord*) obj) {
                verify(obj);
            }
        }
};

void EventsGCRuntime::verify_all_objects_handled() {
    assert(TraceObjectsGCEnableParanoidAssertions, "who is calling?");
    jlong count = 0;
    for(DictI i(handled_objects); i.test(); ++i) {
        if(i._value != OBJECT_HANDLED) count++;
    }
    assert(count == 0, "object(s) unhandled");
}

void EventsGCRuntime::verify_all_objects_handled_in(Space* space, HeapWord* first) {
    assert(TraceObjectsGCEnableParanoidAssertions, "who is calling?");
    //this lock is actually not necessary but it speeds up things a lot because the assert_handled_object doesn't have to lock/unlock all the time
    synchronized(lock) {
        VerifyObjectHandledClosure closure = VerifyObjectHandledClosure(assert_handled_object_space, first);
        space->object_iterate(&closure);
        for(DictI i(handled_objects); i.test(); ++i) {
            assert(!space->is_in(i._key), "some objects are not in the heap although move events have been sent");
        }
    }
}

void EventsGCRuntime::verify_all_objects_handled_in(MutableSpace* space, HeapWord* first) {
    assert(TraceObjectsGCEnableParanoidAssertions, "who is calling?");
    //this lock is actually not necessary but it speeds up things a lot because the assert_handled_object doesn't have to lock/unlock all the time
    synchronized(lock) {
        VerifyObjectHandledClosure closure = VerifyObjectHandledClosure(assert_handled_object, first);
        space->object_iterate(&closure);
        for(DictI i(handled_objects); i.test(); ++i) {
            assert(!space->contains(i._key), "some objects are not in the heap although move events have been sent");
        }
    }
}

void EventsGCRuntime::verify_all_objects_handled_in(Generation* generation, HeapWord* first) {
    assert(TraceObjectsGCEnableParanoidAssertions, "who is calling?");
    //this lock is actually not necessary but it speeds up things a lot because the assert_handled_object doesn't have to lock/unlock all the time
    synchronized(lock) {
        VerifyObjectHandledClosure closure = VerifyObjectHandledClosure(assert_handled_object, first);
        generation->object_iterate(&closure);
        for(DictI i(handled_objects); i.test(); ++i) {
            assert(!generation->is_in_reserved(i._key), "some objects are not in the heap although move events have been sent");
        }
    }
}

void EventsGCRuntime::verify_all_objects_handled_in(CollectedHeap* heap) {
    assert(TraceObjectsGCEnableParanoidAssertions, "who is calling?");
    //this lock is actually not necessary but it speeds up things a lot because the assert_handled_object doesn't have to lock/unlock all the time
    synchronized(lock) {
        VerifyObjectHandledClosure closure = VerifyObjectHandledClosure(assert_handled_object);
        heap->object_iterate(&closure);
        assert(handled_objects->Size() == 0, "some objects are not in the heap although move events have been sent");
    }
}

class VerifyAllocationSiteClosure : public ObjectClosure {
public:
    VerifyAllocationSiteClosure() {}
    
    void do_object(oop obj) {
        assert(AllocationSiteStorage::load(Thread::current(), obj) != ALLOCATION_SITE_IDENTIFIER_UNKNOWN, "no valid allocation site stored");
    }
};

void EventsGCRuntime::verify_allocation_sites() {
    //TODO: we do not know whether the allocation site is valid here, so the check currently does not work...
    //VerifyAllocationSiteClosure closure;
    //Universe::heap()->object_iterate(&closure);
}

void EventsGCRuntime::dump_region(HeapWord* from, HeapWord* to) {
    ResourceMark rm(Thread::current());
    HeapWord* cur = from;
    while(cur < to) {
        oopDesc* obj = (oopDesc*) cur;
        assert(obj->is_oop(false), "wtf");
        const char* type = obj->klass()->internal_name();
        long addr = ((intptr_t) obj) - ((intptr_t) from);
        int size = obj->size() * HeapWordSize;
        tty->print("%s @ %li (%i b)\n", type, addr, size);
        cur += obj->size();
    }
}

void EventsGCRuntime::clear_objects_in(Space* space) {
    assert(TraceObjectsGCEnableParanoidAssertions, "who is calling?");
    Arena* todelArena = new(mtOther) Arena(mtOther);
    GrowableArray<HeapWord*>* todelete = new(todelArena) GrowableArray<HeapWord*>(todelArena, 2, 0, NULL);
    //this lock is actually not necessary but it speeds up things a lot because the assert_handled_object doesn't have to lock/unlock all the time
    synchronized(lock) {
      for(DictI i(handled_objects); i.test(); ++i) {
        if (space->is_in(i._key))
          todelete->push((HeapWord*) i._key);
      }
      
      GrowableArrayIterator<HeapWord*> end = todelete->end();
      for(GrowableArrayIterator<HeapWord*> i=todelete->begin(); i != end; ++i) {
          assert((*handled_objects)[*i] != OBJECT_UNHANDLED, "wtf?");
          handled_objects->Delete(*i);
      }
    }
    delete todelArena;
}

bool EventsGCRuntime::is_gc_active() {
    bool ret = false;
    synchronized(lock) {
        assert(_collectioncounter >= 0, "more gc_ends than gc_starts?");
        ret = _collectioncounter != 0;
    }
    return ret;
}

class CountOopsClosure : public ExtendedOopClosure {
public:
  int count;
  
  CountOopsClosure() : count(0) {}
    
  void do_oop(oop* o) {
    count++;
  }
  
  void do_oop(narrowOop* o) {
    count++;
  }
};

void EventsGCRuntime::verify_pointers(oop obj) {
    EventPointerList* ptr_list = Thread::current()->get_event_obj_ptrs()->get();
    jubyte ptr_count = ptr_list->get_size();
    CountOopsClosure cl = CountOopsClosure();
    Klass* klass = obj->klass();
    klass->oop_oop_iterate(obj, &cl);
    //if(ptr_count != MIN2(12, cl.count)) {
        //fprintf(stderr, "### %s expected %d, recorded %d\n", obj->klass()->signature_name(), cl.count, ptr_count);
    //}
    
    //MW: Removed assertion to fix build
    //assert(ptr_count == MIN2(12, cl.count), "something strange");
}

#endif

void EventsGCRuntime::schedule_space_redefine(uint index, HeapWord* bottom, HeapWord* end, bool just_expanded) {
    if(!postpone_space) {
        assert(just_expanded, "just checking");
        EventsRuntime::fire_space_redefine(index, bottom, end);
    } else {
        SpaceRedefinitionInfo* redef = (SpaceRedefinitionInfo*) malloc(sizeof(SpaceRedefinitionInfo));
        redef->index = index;
        redef->bottom = bottom;
        redef->end = end;
        postponed_space_redefines->append(redef);
    }
}

void EventsGCRuntime::schedule_space_destroyed(uint index, uint count) {
    if(!postpone_space) {
        EventsRuntime::fire_space_destroyed(index, count);
    } else {
        SpaceDestroyInfo* redef = (SpaceDestroyInfo*) malloc(sizeof(SpaceDestroyInfo));
        redef->index = index;
        redef->count = count;
        postponed_space_destroys->append(redef);
    }
}
