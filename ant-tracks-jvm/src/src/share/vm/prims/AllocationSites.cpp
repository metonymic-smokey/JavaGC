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
 * File:   AllocationSites.cpp
 * Author: Verena Bitto
 * 
 * Created on November 26, 2013, 10:36 AM
 */
 
#include "precompiled.hpp"
#include "AllocationSites.hpp"
#include "AllocationTracingDefinitions.hpp"
#include "EventsRuntime.hpp"
#include "../oops/method.hpp"
#include "../ci/ciMethod.hpp"
#include "../memory/resourceArea.hpp"
#include "../runtime/thread.hpp"
#include "SymbolsWriter.hpp"
#include "AllocationTracingSynchronization.hpp"
#include "AllocationTracing.hpp"
#include "HashMap.hpp"
#include "PrefixTreeMap.hpp"    
#include "utilities/quickSort.hpp"

Monitor* AllocationSites::lock = NULL;
Arena* AllocationSites::arena = NULL;
Arena* AllocationSites::arena_temp = NULL;
Arena* AllocationSites::arena_dictionary = NULL;
NavigableMap<AllocationSiteString, AllocationSiteIdentifier>* AllocationSites::dictionary = NULL;
Arena* AllocationSites::arena_elements = NULL;
Dict* AllocationSites::info_to_id = NULL;
Dict* AllocationSites::id_to_info = NULL;
AllocationSiteIdentifier AllocationSites::next_allocation_site_identifier_small = 0,
        AllocationSites::next_allocation_site_identifier_big = 0;
SymbolsWriter* AllocationSites::symbols_writer = NULL;
bool AllocationSites::is_in_yellow_zone = false;

// Functions for PrefixTree which is used to save unloaded allocation sites as string
    size_t getAllocationSiteStringLength(AllocationSiteString allocSiteStr) {
        return allocSiteStr.length;
    }
    char getAllocationSiteStringElement(AllocationSiteString allocSiteStr, int index) {
        return allocSiteStr.string[index];
    }
    bool equalsAllocationSiteStringElement(char c1, char c2) {
        return c1 == c2;
    }
    int hashAllocationSiteStringElement(char c) {
        return (int) c;
    }
    Map<char, PrefixTreeMapNodeID>* createElementMap(Arena* arena) {
        return new(arena) HashMap<char, PrefixTreeMapNodeID, hashAllocationSiteStringElement, equalsAllocationSiteStringElement>(arena, 8);
    }
// end functions

void AllocationSites::init(SymbolsWriter* writer) {
    lock = new Mutex(Mutex::native, "Allocation Sites Lock", true);
    arena = new (mtOther) Arena(mtOther);
    info_to_id = new (arena) Dict(compare_keys, hash_key, arena);
    id_to_info = new (arena) Dict(cmpkey, hashkey, arena);
    next_allocation_site_identifier_small = ALLOCATION_SITE_IDENTIFIER_FIRST_CUSTOM;
    next_allocation_site_identifier_big = ALLOCATION_SITE_IDENTIFIER_FIRST_CUSTOM;
    symbols_writer = writer;
    arena_temp = new(mtOther) Arena(mtOther, Chunk::size);
    arena_dictionary = new(mtOther) Arena(mtOther, Chunk::tiny_size);
    arena_elements = new(mtOther) Arena(mtOther, Chunk::size);
    dictionary = new(arena_dictionary) PrefixTreeMap<AllocationSiteString, char, AllocationSiteIdentifier, getAllocationSiteStringLength, getAllocationSiteStringElement, equalsAllocationSiteStringElement, createElementMap>(arena_dictionary, K * 4);
    dictionary->clear();
    arena_elements->destruct_contents();
    
    write_symbols(ALLOCATION_SITE_IDENTIFIER_VM_INTERNAL, (char*) "VM internal");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_VM_GC, (char*) "VM internal (GC)");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_VM_GC_DEAD_SPACE, (char*) "VM GC Dead Space");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_VM_GC_SCAVENGE_ZOMBIE, (char*) "VM GC Scavenge Zombie");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_OBJECT__CLONE, (char*) "java.lang.Object.clone()Ljava/lang/Object;");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_STRING__INTERN, (char*) "java.lang.String.intern()Ljava/lang/String;");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_IO_OBJECT_INPUT_STREAM__ALLOCATE_NEW_OBJECT, (char*) "java.io.ObjectInputStream.allocateNewObject(Ljava/lang/Class;Ljava/lang/Class;)Ljava/lang/Object;");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_IO_OBJECT_INPUT_STREAM__ALLOCATE_NEW_ARRAY, (char*) "java.io.ObjectInputStream.allocateNewArray(Ljava/lang/Class;I)Ljava/lang/Object;");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_REFLECT_ARRAY__NEW_ARRAY, (char*) "java.lang.reflect.Array.newArray(Ljava/lang/Class;I)Ljava/lang/Object;");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_REFLECT_ARRAY__NEW_MULTI_ARRAY, (char*) "java.lang.reflect.Array.newMultiArray(Ljava/lang/Class;[I)Ljava/lang/Object;");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_REFLECT_CONSTRUCTOR__NEW_INSTANCE, (char*) "java.lang.reflect.Constructor.newInstance([Ljava/lang/Object;)Ljava/lang/Object;");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_JVM__JAVA_LANG_REFLECT_METHOD__INVOKE, (char*) "java.lang.reflect.Method.invoke(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_TLAB_FILLER, (char*) "VM internal (TLAB filler)");  
    write_symbols(ALLOCATION_SITE_IDENTIFIER_ARRAY_FILLER, (char*) "VM internal (Array filler)");
    write_symbols(ALLOCATION_SITE_IDENTIFIER_OBJECT_FILLER, (char*) "VM internal (Object filler)");
 
}

void AllocationSites::destroy() {
    symbols_writer = NULL;
    info_to_id = NULL;
    id_to_info = NULL;
    delete arena; arena = NULL;
    delete lock; lock = NULL;
    dictionary->~NavigableMap<AllocationSiteString, AllocationSiteIdentifier>(); dictionary = NULL;
    delete arena_temp; arena_temp = NULL;
    delete arena_dictionary; arena_dictionary = NULL;
    delete arena_elements; arena_elements = NULL;
}

void AllocationSites::check_allocation_site(AllocationSiteIdentifier allocation_site_id, Klass* klass){
    synchronized(lock) {
        assert(allocation_site_id != ALLOCATION_SITE_IDENTIFIER_UNKNOWN, "Invalid allocation_site_id");
        assert(allocation_site_id != ALLOCATION_SITE_IDENTIFIER_VM_INTERNAL, "Invalid allocation_site_id");
        assert(allocation_site_id != ALLOCATION_SITE_IDENTIFIER_VM_GC, "Invalid allocation_site_id");
        assert(is_big_allocation_site(allocation_site_id) ? 
            (next_allocation_site_identifier_big == 0 || (allocation_site_id & 0x7FFFFF) < next_allocation_site_identifier_big) :
            (next_allocation_site_identifier_small == 0 || allocation_site_id < next_allocation_site_identifier_small), 
                "Invalid allocation_site_id");
        AllocationSiteInfo* allocation_site_info = allocation_site_info_from_void_ptr((*id_to_info)[allocation_site_id_to_void_ptr(allocation_site_id)]);
        assert(allocation_site_info != NULL, "cannot find allocation site");
        AllocatedTypeIdentifier allocated_type_id = AllocatedTypes::to_allocated_type(klass);

        if(allocation_site_info->allocated_type_id == ALLOCATED_TYPE_IDENTIFIER_UNKNOWN && allocated_type_id != ALLOCATED_TYPE_IDENTIFIER_UNKNOWN){
            allocation_site_info->allocated_type_id = allocated_type_id;
            write_symbols(allocation_site_id, allocation_site_info);

            if(PrintTraceObjects || PrintTraceObjectsSymbols) {
                log(allocation_site_id, allocation_site_info, klass, true);
            }
        }
        
        if((PrintTraceObjects || PrintTraceObjectsSymbols) && klass != NULL && allocation_site_info->allocated_type_id != allocated_type_id) {
            AllocationTracing_log("error: expected new %i @ allocation site %i (%s):%i  but observed new %i (%s)\n", allocation_site_info->allocated_type_id, allocation_site_id, allocation_site_info->sites.elements[0].method->name_and_sig_as_C_string(), allocation_site_info->sites.elements[0].bytecode_index, allocated_type_id, klass->internal_name());
        }
        
        assert(klass == NULL || allocation_site_info->allocated_type_id == allocated_type_id, "Stored allocated type id must be equal to the passed one");
    }
}

AllocationSiteIdentifier AllocationSites::method_to_allocation_site(CallSiteIterator* call_sites, Klass* allocated_type, bool relax_expected_allocated_type, bool C2_ALLOC_FAST_DEVIANT){
    //Start GC if we are running out of allocation site ids and full GC was never active (unloaded classes --> allocSite reuse)
    if(is_in_yellow_zone && Universe::heap()->total_full_collections() == 0) Universe::heap()->collect(GCCause::_jvmti_force_gc);
    
    synchronized(lock) {
        AllocationSiteIdentifier allocation_site_id = ALLOCATION_SITE_IDENTIFIER_UNKNOWN;
        AllocatedTypeIdentifier allocated_type_id = AllocatedTypes::to_allocated_type(allocated_type);
        
        int depth = call_sites->count();
        if(TraceObjectsMaxStackTraceDepth > 0) {
            depth = MIN2((int) TraceObjectsMaxStackTraceDepth, depth);
        }
        if(depth == 0) {
            return ALLOCATION_SITE_IDENTIFIER_UNKNOWN;
        }
        
        //check if allocation site already exists
        {
            AllocationSiteInfo info;
            info.sites.length = depth;
#ifndef _WINDOWS
            CallSite sites[depth];
            info.sites.elements = &sites[0];
#else
            info.sites.elements = (CallSite*) malloc(sizeof(CallSite) * depth);
#endif
            size_t index = 0;
            while(index < info.sites.length && call_sites->has_next()) {
                info.sites.elements[index++] = call_sites->next();
            }
            call_sites->reset();
            info.sites.length = index;
            info.allocated_type_id = allocated_type_id;
            
            allocation_site_id = (allocation_site_id_from_void_ptr((*info_to_id)[allocation_site_info_to_void_ptr(&info)]));
            
            //Check if an allocation site can be reused (from unloaded classes)
            if(allocation_site_id == ALLOCATION_SITE_IDENTIFIER_UNKNOWN && dictionary->get_size() > 0) {
                struct AllocationSiteString allocSiteStr = CallSitesToString(call_sites, arena_temp);

                if(allocSiteStr.string != NULL){
                    Maybe<AllocationSiteIdentifier> maybeId = dictionary->get(allocSiteStr);
                    if(maybeId.has_value()){
                        AllocationSiteIdentifier allocId = maybeId.get_value();
                        AllocationSiteInfo* info = allocation_site_info_from_void_ptr((*id_to_info)[allocation_site_id_to_void_ptr(allocId)]); 

                        if(info->allocated_type_id == allocated_type_id) {
                            allocation_site_id = allocId;
                            assert(call_sites->count() == ((int)info->sites.length), "length must be equal");

                            for(size_t index = 0; index < info->sites.length; index++) {
                                CallSite callSite = info->sites.elements[index];
                                callSite.method = call_sites->next().method;
                            }
                            call_sites->reset();
                        }
                    }
                }
                arena_temp->destruct_contents();
            }
            
            {
                //if we are running out of allocation site ids, reduce depth and try to find allocation site
                const AllocationSiteIdentifier last_id_small = (AllocationSiteIdentifier) ((1u << (SIZE_OF_ALLOCATION_SITE_IDENTIFIER_SMALL * 8 - 1)) - 1);
                const AllocationSiteIdentifier last_id_big = (AllocationSiteIdentifier) ((1u << (SIZE_OF_ALLOCATION_SITE_IDENTIFIER_BIG * 8 - 1)) - 1);
                const double yellow_zone = 0.10;
                const double red_zone = 0.05;
                is_in_yellow_zone = next_allocation_site_identifier_small > last_id_small * (1 - yellow_zone) ||
                                            next_allocation_site_identifier_big > last_id_big * (1 - yellow_zone);
                if(is_in_yellow_zone) {
                    while(info.sites.length > 0 && allocation_site_id == ALLOCATION_SITE_IDENTIFIER_UNKNOWN) {
                        allocation_site_id = (allocation_site_id_from_void_ptr((*info_to_id)[allocation_site_info_to_void_ptr(&info)]));
                        info.sites.length--;
                    }
                    if(allocation_site_id == ALLOCATION_SITE_IDENTIFIER_UNKNOWN) {
                        warning_once("Running out of allocation site identifiers (entered yellow zone) - reducing depth for new allocation sites");
                        depth = MIN2(3, depth);
                        bool is_in_red_zone = is_in_yellow_zone && (next_allocation_site_identifier_small > last_id_small * (1 - red_zone) ||
                                                next_allocation_site_identifier_big > last_id_big * (1 - red_zone));
                        if(is_in_red_zone) {
                            warning_once("Running out of allocation site identifiers (entered red zone) - drastically reducing depth for new allocation sites");
                            depth = 1;
                        }
                    }
                }
            }

#ifndef _WINDOWS
#else
            free(info.sites.elements);
#endif
        }
        
        if(relax_expected_allocated_type && allocation_site_id != ALLOCATION_SITE_IDENTIFIER_UNKNOWN) {
            AllocationSiteInfo* stored = (AllocationSiteInfo*) (*id_to_info)[allocation_site_id_to_void_ptr(allocation_site_id)];
            if(stored->allocated_type_id != allocated_type_id) {
                allocation_site_id = ALLOCATION_SITE_IDENTIFIER_UNKNOWN;
            }
        }
        
        //add allocation site if no matching has been found
        if(allocation_site_id == ALLOCATION_SITE_IDENTIFIER_UNKNOWN) {
            assert(sizeof(void*) >= SIZE_OF_ALLOCATION_SITE_IDENTIFIER_BIG, "void* must be bigger than AllocationSiteIdentifier in order to the dictionary to work properly");
            
            if(!ExtendAllocationSiteIdRange || allocated_type == NULL || C2_ALLOC_FAST_DEVIANT || allocated_type->oop_is_array()){                
                //Is an array or allocated_type was undefined (NULL) or EVENTS_C2_ALLOC_FAST_DEVIANT_TYPE --> get small allocSite
                allocation_site_id = next_allocation_site_identifier_small;
                next_allocation_site_identifier_small = (next_allocation_site_identifier_small + 1) & 0x7FFF;

                if(allocation_site_id < ALLOCATION_SITE_IDENTIFIER_FIRST_CUSTOM) {
                    fatal("Ran out of small allocation site identifiers");
                }
            }
            else { //is an instance --> get big allocSite
                allocation_site_id = 0x800000 | next_allocation_site_identifier_big;
                next_allocation_site_identifier_big = (next_allocation_site_identifier_big + 1) & 0x7FFFFF;
                
                if((allocation_site_id & 0x7FFFFF) < ALLOCATION_SITE_IDENTIFIER_FIRST_CUSTOM) {
                    fatal("Ran out of big allocation site identifiers");
                }
            }

            AllocationSiteInfo* info = (AllocationSiteInfo*) arena->Amalloc(sizeof(AllocationSiteInfo)); 
            info->sites.length = depth;
            info->sites.elements = (CallSite*) arena->Amalloc_4(sizeof(CallSite) * info->sites.length);
            bool sameClassLoader = true;
            ClassLoaderData* classLoader = NULL;
            size_t index = 0;
            
            while(index < info->sites.length && call_sites->has_next()) {
                CallSite callSite = call_sites->next();
                info->sites.elements[index++] = callSite;
                   
                if(sameClassLoader && callSite.method != NULL) {
                    if(classLoader == NULL) {
                        classLoader = callSite.method->method_holder()->class_loader_data();
                    } else {
                        sameClassLoader &= classLoader == callSite.method->method_holder()->class_loader_data();
                    }
                }
            }
            info->sites.length = index;
            info->allocated_type_id = allocated_type_id;
            info->savable = sameClassLoader;
            
            info_to_id->Insert(allocation_site_info_to_void_ptr(info), allocation_site_id_to_void_ptr(allocation_site_id));
            id_to_info->Insert(allocation_site_id_to_void_ptr(allocation_site_id), allocation_site_info_to_void_ptr(info));
            
            self_monitoring(1) {
                AllocationTracingSelfMonitoring::report_new_allocation_site();
            }
            
            if(info->allocated_type_id != ALLOCATED_TYPE_IDENTIFIER_UNKNOWN) {
                write_symbols(allocation_site_id, info);
            }
            
            if(PrintTraceObjects || PrintTraceObjectsSymbols) {
                log(allocation_site_id, info, allocated_type);
            }
        } else {
            AllocationSites::check_allocation_site(allocation_site_id, allocated_type);
        }
        
        assert(allocation_site_id >= ALLOCATION_SITE_IDENTIFIER_FIRST_CUSTOM, "invalid allocation site identifier");
        return allocation_site_id;
    }
    HERE_BE_DRAGONS(ALLOCATION_SITE_IDENTIFIER_UNKNOWN);
}

class CallSiteArrayIterator : public CallSiteIterator {
private:
    CallSiteArray* sites;
    int index;
public:
    CallSiteArrayIterator(CallSiteArray* sites) : sites(sites) {
        reset();
    }

    int count() {
        return (int) sites->length;
    }
    
    void reset() {
        index = 0;
    }
    
    bool has_next() {
        return index < count();
    }
    
    CallSite next() {
        return sites->elements[index++];
    }
};

AllocationSiteIdentifier AllocationSites::allocation_site_to_allocation_site_with_alternate_type(AllocationSiteIdentifier allocation_site, Klass* allocated_type) {
    synchronized(lock) {
        AllocationSiteInfo* info = allocation_site_info_from_void_ptr((*id_to_info)[allocation_site_id_to_void_ptr(allocation_site)]);
        assert(info != NULL, "?");
        AllocatedTypeIdentifier allocated_type_id = AllocatedTypes::to_allocated_type(allocated_type);
        if(info->allocated_type_id == allocated_type_id) {
            return allocation_site;
        } else {
            CallSiteArrayIterator call_sites = CallSiteArrayIterator(&info->sites);
            return method_to_allocation_site(&call_sites, allocated_type, true);
        }
    }
    HERE_BE_DRAGONS(ALLOCATION_SITE_IDENTIFIER_UNKNOWN);
}

void AllocationSites::remove_allocation_site(Method* method) {
    synchronized(lock) {
        self_monitoring_measure_time(1, AllocationTracingSelfMonitoring::get_cleanup_time()) {
            ResourceMark _(Thread::current());
            GrowableArray<AllocationSiteIdentifier> removed_ids = GrowableArray<AllocationSiteIdentifier>(0);
            
            for(DictI i(id_to_info); i.test(); ++i) {
                AllocationSiteIdentifier id = allocation_site_id_from_void_ptr((void*) i._key);
                AllocationSiteInfo* info = allocation_site_info_from_void_ptr((void*) i._value);
                bool contains = false;
                for(size_t index = 0; !contains && index < info->sites.length; index++) {
                    contains |= info->sites.elements[index].method == method;
                }
                if(contains) {
                    removed_ids.append(id);
                }
            }
            
            for(int index = 0; index < removed_ids.length(); index++) {
                remove_allocation_site(removed_ids.at(index));
            }
        }
    }
}

static int methodp_comparator(Method **a, Method **b) {
    return (*a) == (*b) ? 0 : (*a) < (*b) ? -1 : 1;
}

static bool binary_search(GrowableArray<Method*>* methods, Method* searchedMeth) {
  int len = methods->length();
  // methods are sorted, so do binary search
  int l = 0;
  int h = len - 1;
  while (l <= h) {
    int mid = (l + h) >> 1;
    Method* m = methods->at(mid);
    int res = methodp_comparator(&m, &searchedMeth);
    if (res == 0) {
      return true;
    } else if (res < 0) {
      l = mid + 1;
    } else {
      h = mid - 1;
    }
  }
  return false;
}

void AllocationSites::save_unloaded_allocation_sites(Klass* unloadedKlasses) {
    ResourceMark _(Thread::current());
    GrowableArray<Method*> methodArray = GrowableArray<Method*>(0);

    //Add method pointers to a growable array (for binary search algorithm)
    for (Klass* k = unloadedKlasses; k != NULL; k = k->next_link()) {
        if (k->oop_is_instance()) {
            InstanceKlass *ik = InstanceKlass::cast(k);

            Array<Method*>* methods = ik->methods();
            for (int i = 0; i < methods->length(); i++) {
                methodArray.append(methods->at(i));
            }
        }
        assert(k != k->next_link(), "no loops!");
    }

    // sort array (for binary search)
    methodArray.sort(methodp_comparator);
        
    synchronized(lock) {
        for(DictI i(id_to_info); i.test(); ++i) {
            AllocationSiteIdentifier id = allocation_site_id_from_void_ptr((void*) i._key);
            AllocationSiteInfo* info = allocation_site_info_from_void_ptr((void*) i._value);

            if(info->savable) {
                for(size_t index = 0; index < info->sites.length; index++) {
                    Method *meth = info->sites.elements[index].method;

                    if(meth != NULL && binary_search(&methodArray, info->sites.elements[index].method)) {
                        struct AllocationSiteString allocSiteStr;
                        CallSiteArrayIterator call_sites = CallSiteArrayIterator(&info->sites);
                        allocSiteStr = CallSitesToString(&call_sites, arena_elements, true);

                        if(allocSiteStr.string != NULL) {
                            dictionary->put(allocSiteStr, id);
                        }
                    }
                }
            }
        }
    }
}

void AllocationSites::remove_allocation_site(AllocationSiteIdentifier id) {
    synchronized(lock) {
        info_to_id->Delete(id_to_info->Delete(allocation_site_id_to_void_ptr(id)));
    }
}

bool AllocationSites::is_consistent(AllocationSiteIdentifier id) {
    if(id >= ALLOCATION_SITE_IDENTIFIER_FIRST_CUSTOM) {
        synchronized(lock) {
            AllocationSiteInfo* info = allocation_site_info_from_void_ptr((*id_to_info)[allocation_site_id_to_void_ptr(id)]);
            return info != NULL && info->allocated_type_id != ALLOCATION_SITE_IDENTIFIER_UNKNOWN;
        }
        HERE_BE_DRAGONS(false);
    } else {
        return true;
    }
}

void AllocationSites::check_consistency() {
    ResourceMark rm(Thread::current());
    synchronized(lock) {
        for(DictI i(info_to_id); i.test(); ++i) {
            AllocationSiteInfo* info = allocation_site_info_from_void_ptr((void*) i._key);
            AllocationSiteIdentifier id = allocation_site_id_from_void_ptr((void*) i._value);
            if(id >= ALLOCATION_SITE_IDENTIFIER_FIRST_CUSTOM && info->allocated_type_id == ALLOCATED_TYPE_IDENTIFIER_UNKNOWN) {
                CallSite* call_site = &info->sites.elements[0];
                warning("Allocation site %i (%s:%i) has not been written to the symbols file because no type could be determined.", id, call_site->method->name_and_sig_as_C_string(), call_site->bytecode_index);
            }
        }
    }
}

size_t AllocationSites::get_depth(AllocationSiteIdentifier id) {
    synchronized(lock) {
        AllocationSiteInfo* info = allocation_site_info_from_void_ptr((*id_to_info)[allocation_site_id_to_void_ptr(id)]);
        if(info != NULL) {
            return info->sites.length;
        } else {
            return 0;
        }
    }
    HERE_BE_DRAGONS(0);
}

bool AllocationSites::equals(AllocationSiteIdentifier id1, AllocationSiteIdentifier id2, bool ignore_allocated_types) {
    if(id1 == id2) {
        return true;
    } else {
        synchronized(lock) {
            AllocationSiteInfo* info1 = allocation_site_info_from_void_ptr((*id_to_info)[allocation_site_id_to_void_ptr(id1)]);
            AllocationSiteInfo* info2 = allocation_site_info_from_void_ptr((*id_to_info)[allocation_site_id_to_void_ptr(id2)]);
            return equals(info1, info2, ignore_allocated_types);
        }
        HERE_BE_DRAGONS(false);
    }
}

bool AllocationSites::equals(AllocationSiteInfo* info1, AllocationSiteInfo* info2, bool ignore_allocated_types) {
    if(info1 == info2) {
        return true;
    } else if(info1 != NULL
            && info2 != NULL
            && (ignore_allocated_types
                || info1->allocated_type_id == info2->allocated_type_id
                || info1->allocated_type_id == ALLOCATED_TYPE_IDENTIFIER_UNKNOWN
                || info2->allocated_type_id == ALLOCATED_TYPE_IDENTIFIER_UNKNOWN)
            && info1->sites.length == info2->sites.length) {
        for(size_t index = 0; index < info1->sites.length; index++) {
            CallSite* site1 = info1->sites.elements + index;
            CallSite* site2 = info2->sites.elements + index;
            if(site1->method != site2->method || site1->bytecode_index != site2->bytecode_index) {
                return false;
            }
        }
        return true;
    } else {
        return false;
    }
}

int32 AllocationSites::compare_keys(const void *key1, const void *key2) {
    AllocationSiteInfo* info1 = (AllocationSiteInfo*) key1;
    AllocationSiteInfo* info2 = (AllocationSiteInfo*) key2;
    return equals(info1, info2, false) ? 0 : -1;
}

int32 AllocationSites::hash_key(const void* key) {
    //DO NOT consider allocated types here (this field is mutable)
    AllocationSiteInfo* info = (AllocationSiteInfo*) key;
    int32 hash = 17;
    for(size_t index = 0; index < info->sites.length; index++) {
        CallSite* site = info->sites.elements + index;
        hash += 19 * ((intptr_t) (site->method) >> LogHeapWordSize);
        hash += 23 * (site->bytecode_index ^ (site->bytecode_index << 16));
    }
    return hash;
}

AllocationSiteIdentifier AllocationSites::allocation_site_id_from_void_ptr(void* value){
    return (AllocationSiteIdentifier) ((intptr_t) value);
}

AllocationSiteInfo* AllocationSites::allocation_site_info_from_void_ptr(void* value){
    return (AllocationSiteInfo*) value;
}

void* AllocationSites::allocation_site_info_to_void_ptr(AllocationSiteInfo* site){
    return (void*) site;
}

void* AllocationSites::allocation_site_id_to_void_ptr(AllocationSiteIdentifier id){
    return (void*) (intptr_t) id;
}

void AllocationSites::write_symbols(AllocationSiteIdentifier allocation_site_id, char* signature) {
    SymbolsSimpleSite symbol;
    symbol.allocation_site_id = allocation_site_id;
    symbol.signature = signature;
    symbol.bci = 0;
    symbol.allocated_type_id = ALLOCATED_TYPE_IDENTIFIER_UNKNOWN;
    symbols_writer->write_symbols(symbol);
}

void AllocationSites::write_symbols(AllocationSiteIdentifier allocation_site_id, AllocationSiteInfo* allocation_site_info){
    SymbolsSite symbol;
    symbol.allocation_site_id = allocation_site_id;
    symbol.call_sites = &allocation_site_info->sites;
    symbol.allocated_type_id = allocation_site_info->allocated_type_id;
    symbols_writer->write_symbols(symbol);
}

void AllocationSites::log(AllocationSiteIdentifier id, AllocationSiteInfo* info, Klass* allocated_type, bool patched) {
    ResourceMark rm(Thread::current());
    AllocationTracing_log_header();
    AllocationTracing_log_line("allocation site %i => new %s (id=%i) %s", id, allocated_type != NULL ? allocated_type->internal_name() : "<unknown>", info->allocated_type_id, patched ? "(patched)" : "");
    for(size_t index = 0; index < info->sites.length; index++) {
        CallSite* site = info->sites.elements + index;
        AllocationTracing_log_part("\tat ");
        if(site->method != NULL) {
            AllocationTracing_log_line("%i %s:%i", (int) index, site->method->name_and_sig_as_C_string(), site->bytecode_index);            
        } else {
            AllocationTracing_log_line("$$ recursion: repeat %i last frame(s)", site->bytecode_index);            
        }
    }
    AllocationTracing_log_footer();
}

//optimized strcat, no need for rescanning the string each time --> O(n) instead of O(n^2)
char* AllocationSites::mystrcat(char* dest, char* src) {
    while (*dest) dest++;
    while (*dest++ = *src++);
    return --dest;
}

AllocationSiteString AllocationSites::CallSitesToString(CallSiteIterator* call_sites, Arena *arena, bool fittingBuff){
    struct AllocationSiteString allocSiteStr;
    size_t buffer_size = sizeof(char) * call_sites->count() * 256; //init
    char *buff = (char*) arena->Amalloc(buffer_size);
    char *p = buff;
    buff[0] = '\0';
    bool isInvalid = false;
    char bci[15];

    //Build string representation of allocation site 
    while(!isInvalid && call_sites->has_next()) {
        CallSite callSite = call_sites->next();

        if(callSite.method != NULL){
            if(callSite.method->is_valid_method()) { 
                InstanceKlass* ik = callSite.method->method_holder();

                Symbol *klassName = ik->name();
                Symbol *methName = callSite.method->name();
                Symbol *signature = callSite.method->signature();
                sprintf(bci, "%d", callSite.bytecode_index);
                int len = klassName->utf8_length() + methName->utf8_length() + signature->utf8_length() + ((int)strlen(bci)) + 1;
#ifndef _WINDOWS
                char name_and_sig[len + 1];
#else
                char *name_and_sig = (char*) malloc(sizeof(char) * (len + 1));
#endif       
                callSite.method->name_and_sig_as_C_string(ik, methName, signature, name_and_sig, len + 1);
                strcat(name_and_sig, bci);          

                if((allocSiteStr.length + len + 1) >= buffer_size) {
                    buff = (char*) arena->Arealloc(buff, buffer_size, buffer_size * 2);
                    p = buff;
                    buffer_size = buffer_size * 2;
                }

                p = mystrcat(p, name_and_sig);
#ifdef _WINDOWS
                free(name_and_sig);
#endif
                allocSiteStr.length += len;
            } else isInvalid = true;
        } else {
            sprintf(bci, "%d", callSite.bytecode_index);
            int len = ((int)strlen(bci)) + 1;
            if((allocSiteStr.length + len + 1) >= buffer_size) {
                buff = (char*) arena->Arealloc(buff, buffer_size, buffer_size * 2);
                p = buff;
                buffer_size = buffer_size * 2;
            }

            p = mystrcat(p, bci);
            allocSiteStr.length += len;
        }
    }
    call_sites->reset();
    
    //Reallocate buff to get perfectly fitting buffer
    if(fittingBuff){
        size_t size = sizeof(char) * allocSiteStr.length + 1;
        buff = (char*) arena->Arealloc(buff, buffer_size, size);
    }
    
    allocSiteStr.string = isInvalid ? NULL : buff;
    return allocSiteStr;
}
