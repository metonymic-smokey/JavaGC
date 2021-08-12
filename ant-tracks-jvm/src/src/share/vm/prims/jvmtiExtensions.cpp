/*
 * Copyright (c) 2003, 2012, Oracle and/or its affiliates. All rights reserved.
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
#include "prims/jvmtiExport.hpp"
#include "prims/jvmtiExtensions.hpp"
#include "EventBuffersFlushAll.hpp"
#include "AllocationTracingDefinitions.hpp"
#include "AllocationTracingSelfMonitoring.hpp"
#include "EventBuffers.hpp"
#include "AllocationTracing.hpp"

// the list of extension functions
GrowableArray<jvmtiExtensionFunctionInfo*>* JvmtiExtensions::_ext_functions;

// the list of extension events
GrowableArray<jvmtiExtensionEventInfo*>* JvmtiExtensions::_ext_events;


// extension function
static jvmtiError JNICALL IsClassUnloadingEnabled(const jvmtiEnv* env, jboolean* enabled, ...) {
  if (enabled == NULL) {
    return JVMTI_ERROR_NULL_POINTER;
  }
  *enabled = (jboolean)ClassUnloading;
  return JVMTI_ERROR_NONE;
}

static jvmtiError JNICALL IsTracingEnabled(jvmtiEnv* env, jboolean* is_enabled_ptr) {
    ThreadInVMfromNative tiv(JavaThread::current());
    *is_enabled_ptr = TraceObjects;
    return JVMTI_ERROR_NONE;
}

static jvmtiError JNICALL SetTracing(jvmtiEnv* env, jboolean enabled) {
    ThreadInVMfromNative tiv(JavaThread::current());
    if(enabled) {
        AllocationTracing::begin_init_when_running();
    } else {
        AllocationTracing::begin_destroy_when_running();
    }
    return JVMTI_ERROR_NONE;
}

static jvmtiError JNICALL FlushAllEventBuffers(const jvmtiEnv* env, ...) {
    if(TraceObjects) {
        {
            ThreadInVMfromNative tiv(JavaThread::current());
            EventBuffersFlushAll::begin_flush_all();
        }
        //let a safepoint occur here, otherwise we will be in a deadlock because the vm needs this thread to be at a safepoint to flush all buffers
        {
            ThreadInVMfromNative tiv(JavaThread::current());
            EventBuffersFlushAll::wait_for_all_serialized();
        }
    }
    return JVMTI_ERROR_NONE;
}

static char* externalize(JvmtiEnv* jvmti_env, char* interned) {
    size_t length = strlen(interned);
    size_t raw_length = (length + 1) * sizeof(char);
    char* externalized = NULL;
    jvmti_env->Allocate(raw_length, (unsigned char**) &externalized);
    assert(externalized != NULL, "out of memory?");
    memcpy(externalized, interned, raw_length);
    return externalized;
}

static jvmtiError JNICALL GetProperties(jvmtiEnv* env, jboolean reset, jint* size_ptr, char*** names_ptr, jdouble** values_ptr, ...) {
    ThreadInVMfromNative tiv(JavaThread::current());
    if(TraceObjects && size_ptr != NULL && names_ptr != NULL && values_ptr != NULL) {
        ResourceMark rm(Thread::current());
        GrowableArray<char*> names = GrowableArray<char*>();
        GrowableArray<jdouble> values = GrowableArray<jdouble>();
        AllocationTracingSelfMonitoring::get(&names, &values);
        assert(names.length() == values.length(), "number of names and number of values do not match!");
        *size_ptr = names.length();
        JvmtiEnv* jvmti_env = JvmtiEnv::JvmtiEnv_from_jvmti_env(env);
        jvmti_env->Allocate(sizeof(char**) * (*size_ptr), (unsigned char**) names_ptr);
        jvmti_env->Allocate(sizeof(jdouble*) * (*size_ptr), (unsigned char**) values_ptr);
        for(int i = 0; i < *size_ptr; i++) {
            (*names_ptr)[i] = externalize(jvmti_env, names.at(i));
            (*values_ptr)[i] = values.at(i);
        }
    } else {
        if(size_ptr != NULL) *size_ptr = 0;
    }
    if(TraceObjects && reset) {
        AllocationTracingSelfMonitoring::reset();
    }
    return JVMTI_ERROR_NONE;
}

static jvmtiError JNICALL FireMark(jvmtiEnv* env, jlong id) {
    ThreadInVMfromNative tiv(JavaThread::current());
    if(TraceObjects) {
        EventsRuntime::fire_mark(id);
    }
    return JVMTI_ERROR_NONE;
}

static jvmtiError JNICALL AddTagToTrace(jvmtiEnv* env, char* tag) {
    ThreadInVMfromNative tiv(JavaThread::current());
    if(TraceObjects) {
        EventsRuntime::fire_tag(tag);
    }
    return JVMTI_ERROR_NONE;
}

// register extension functions and events. In this implementation we
// have a single extension function (to prove the API) that tests if class
// unloading is enabled or disabled. We also have a single extension event
// EXT_EVENT_CLASS_UNLOAD which is used to provide the JVMDI_EVENT_CLASS_UNLOAD
// event. The function and the event are registered here.
//
void JvmtiExtensions::register_extensions() {
  _ext_functions = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<jvmtiExtensionFunctionInfo*>(1,true);
  _ext_events = new (ResourceObj::C_HEAP, mtInternal) GrowableArray<jvmtiExtensionEventInfo*>(1,true);

  static jvmtiParamInfo is_params[] = {
    { (char*) "enabled", JVMTI_KIND_IN, JVMTI_TYPE_JBOOLEAN,  JNI_FALSE },
  };
  static jvmtiExtensionFunctionInfo is_func = {
      (jvmtiExtensionFunction) IsTracingEnabled,
      (char*) "at.jku.ssw.mevss.functions.IsObjectTracingEnabled",
      (char*) "Checks whether object tracing is enabled",
      sizeof(is_params) / sizeof(is_params[0]),
      is_params,
      0,
      NULL
  };
  _ext_functions->append(&is_func);
  
  if(TraceObjectsToggleAtRunTime) {
      static jvmtiParamInfo set_params[] = {
          { (char*) "enabled", JVMTI_KIND_IN, JVMTI_TYPE_JBOOLEAN,  JNI_FALSE },
      };
      static jvmtiExtensionFunctionInfo set_func = {
          (jvmtiExtensionFunction) SetTracing,
          (char*) "at.jku.ssw.mevss.functions.SetObjectTracing",
          (char*) "Enabling or disabled object tracing at run time",
          sizeof(set_params) / sizeof(set_params[0]),
          set_params,
          0,
          NULL
      };
      _ext_functions->append(&set_func);      
  }
  
  if(TraceObjects){
      //static jvmtiParamInfo flush_all_event_buffers_params[] = {};
      static jvmtiExtensionFunctionInfo flush_all_event_buffers_func = {
          (jvmtiExtensionFunction) FlushAllEventBuffers,
          (char*) "at.jku.ssw.mevss.functions.FlushAllEventBuffers",
          (char*) "Flush all event buffers of all threads",
          0, //sizeof(flush_all_event_buffers_params) / sizeof(jvmtiParamInfo),
          NULL, //flush_all_event_buffers_params,
          0,
          NULL
      };
      _ext_functions->append(&flush_all_event_buffers_func);
      
      static jvmtiParamInfo get_properties_params[] = {
          { (char*) "size_ptr",   JVMTI_KIND_OUT, JVMTI_TYPE_JINT,  JNI_FALSE },
          { (char*) "names_ptr",  JVMTI_KIND_OUT, JVMTI_TYPE_CVOID, JNI_FALSE },
          { (char*) "values_ptr", JVMTI_KIND_OUT, JVMTI_TYPE_CVOID, JNI_FALSE }
      };
      static jvmtiExtensionFunctionInfo get_allocation_tracing_properties_func = {
          (jvmtiExtensionFunction) GetProperties,
          (char*) "at.jku.ssw.mevss.functions.GetProperties",
          (char*) "Get properties describing tracing mechanism",
          sizeof(get_properties_params) / sizeof(get_properties_params[0]),
          get_properties_params,
          0,
          NULL
      };
      _ext_functions->append(&get_allocation_tracing_properties_func);
      
      static jvmtiParamInfo fire_mark_params[] = {
          { (char*) "id", JVMTI_KIND_IN, JVMTI_TYPE_JLONG, JNI_FALSE }
      };
      static jvmtiExtensionFunctionInfo fire_mark_func = {
          (jvmtiExtensionFunction) FireMark,
          (char*) "at.jku.ssw.mevss.functions.FireMark",
          (char*) "Fire a mark event for the calling thread",
          sizeof(fire_mark_params) / sizeof(fire_mark_params[0]),
          fire_mark_params,
          0,
          NULL
      };
      _ext_functions->append(&fire_mark_func);
  }
  
  // register our extension function
  static jvmtiParamInfo func_params[] = {
    { (char*)"IsClassUnloadingEnabled", JVMTI_KIND_OUT,  JVMTI_TYPE_JBOOLEAN, JNI_FALSE }
  };
  static jvmtiExtensionFunctionInfo ext_func = {
    (jvmtiExtensionFunction)IsClassUnloadingEnabled,
    (char*)"com.sun.hotspot.functions.IsClassUnloadingEnabled",
    (char*)"Tell if class unloading is enabled (-noclassgc)",
    sizeof(func_params)/sizeof(func_params[0]),
    func_params,
    0,              // no non-universal errors
    NULL
  };
  _ext_functions->append(&ext_func);

  // register our extension event

  static jvmtiParamInfo event_params[] = {
    { (char*)"JNI Environment", JVMTI_KIND_IN, JVMTI_TYPE_JNIENV, JNI_FALSE },
    { (char*)"Thread", JVMTI_KIND_IN, JVMTI_TYPE_JTHREAD, JNI_FALSE },
    { (char*)"Class", JVMTI_KIND_IN, JVMTI_TYPE_JCLASS, JNI_FALSE }
  };
  static jvmtiExtensionEventInfo ext_event = {
    EXT_EVENT_CLASS_UNLOAD,
    (char*)"com.sun.hotspot.events.ClassUnload",
    (char*)"CLASS_UNLOAD event",
    sizeof(event_params)/sizeof(event_params[0]),
    event_params
  };
  _ext_events->append(&ext_event);
  
    // register tagging function
  static jvmtiParamInfo tag_params[] = {
    { (char*)"AddTagToTrace", JVMTI_KIND_OUT, JVMTI_TYPE_CVOID, JNI_FALSE }
  };
  static jvmtiExtensionFunctionInfo tag_func = {
    (jvmtiExtensionFunction)AddTagToTrace,
    (char*)"at.jku.ssw.mevss.functions.AddTagToTrace",
    (char*)"Adds a given tag to the trace file",
    sizeof(tag_params)/sizeof(tag_params[0]),
    tag_params,
    0,              // no non-universal errors
    NULL
  };
  _ext_functions->append(&tag_func);
}


// return the list of extension functions

jvmtiError JvmtiExtensions::get_functions(JvmtiEnv* env,
                                          jint* extension_count_ptr,
                                          jvmtiExtensionFunctionInfo** extensions)
{
  guarantee(_ext_functions != NULL, "registration not done");

  ResourceTracker rt(env);

  jvmtiExtensionFunctionInfo* ext_funcs;
  jvmtiError err = rt.allocate(_ext_functions->length() *
                               sizeof(jvmtiExtensionFunctionInfo),
                               (unsigned char**)&ext_funcs);
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }

  for (int i=0; i<_ext_functions->length(); i++ ) {
    ext_funcs[i].func = _ext_functions->at(i)->func;

    char *id = _ext_functions->at(i)->id;
    err = rt.allocate(strlen(id)+1, (unsigned char**)&(ext_funcs[i].id));
    if (err != JVMTI_ERROR_NONE) {
      return err;
    }
    strcpy(ext_funcs[i].id, id);

    char *desc = _ext_functions->at(i)->short_description;
    err = rt.allocate(strlen(desc)+1,
                      (unsigned char**)&(ext_funcs[i].short_description));
    if (err != JVMTI_ERROR_NONE) {
      return err;
    }
    strcpy(ext_funcs[i].short_description, desc);

    // params

    jint param_count = _ext_functions->at(i)->param_count;

    ext_funcs[i].param_count = param_count;
    if (param_count == 0) {
      ext_funcs[i].params = NULL;
    } else {
      err = rt.allocate(param_count*sizeof(jvmtiParamInfo),
                        (unsigned char**)&(ext_funcs[i].params));
      if (err != JVMTI_ERROR_NONE) {
        return err;
      }
      jvmtiParamInfo* src_params = _ext_functions->at(i)->params;
      jvmtiParamInfo* dst_params = ext_funcs[i].params;

      for (int j=0; j<param_count; j++) {
        err = rt.allocate(strlen(src_params[j].name)+1,
                          (unsigned char**)&(dst_params[j].name));
        if (err != JVMTI_ERROR_NONE) {
          return err;
        }
        strcpy(dst_params[j].name, src_params[j].name);

        dst_params[j].kind = src_params[j].kind;
        dst_params[j].base_type = src_params[j].base_type;
        dst_params[j].null_ok = src_params[j].null_ok;
      }
    }

    // errors

    jint error_count = _ext_functions->at(i)->error_count;
    ext_funcs[i].error_count = error_count;
    if (error_count == 0) {
      ext_funcs[i].errors = NULL;
    } else {
      err = rt.allocate(error_count*sizeof(jvmtiError),
                        (unsigned char**)&(ext_funcs[i].errors));
      if (err != JVMTI_ERROR_NONE) {
        return err;
      }
      memcpy(ext_funcs[i].errors, _ext_functions->at(i)->errors,
             error_count*sizeof(jvmtiError));
    }
  }

  *extension_count_ptr = _ext_functions->length();
  *extensions = ext_funcs;
  return JVMTI_ERROR_NONE;
}


// return the list of extension events

jvmtiError JvmtiExtensions::get_events(JvmtiEnv* env,
                                       jint* extension_count_ptr,
                                       jvmtiExtensionEventInfo** extensions)
{
  guarantee(_ext_events != NULL, "registration not done");

  ResourceTracker rt(env);

  jvmtiExtensionEventInfo* ext_events;
  jvmtiError err = rt.allocate(_ext_events->length() * sizeof(jvmtiExtensionEventInfo),
                               (unsigned char**)&ext_events);
  if (err != JVMTI_ERROR_NONE) {
    return err;
  }

  for (int i=0; i<_ext_events->length(); i++ ) {
    ext_events[i].extension_event_index = _ext_events->at(i)->extension_event_index;

    char *id = _ext_events->at(i)->id;
    err = rt.allocate(strlen(id)+1, (unsigned char**)&(ext_events[i].id));
    if (err != JVMTI_ERROR_NONE) {
      return err;
    }
    strcpy(ext_events[i].id, id);

    char *desc = _ext_events->at(i)->short_description;
    err = rt.allocate(strlen(desc)+1,
                      (unsigned char**)&(ext_events[i].short_description));
    if (err != JVMTI_ERROR_NONE) {
      return err;
    }
    strcpy(ext_events[i].short_description, desc);

    // params

    jint param_count = _ext_events->at(i)->param_count;

    ext_events[i].param_count = param_count;
    if (param_count == 0) {
      ext_events[i].params = NULL;
    } else {
      err = rt.allocate(param_count*sizeof(jvmtiParamInfo),
                        (unsigned char**)&(ext_events[i].params));
      if (err != JVMTI_ERROR_NONE) {
        return err;
      }
      jvmtiParamInfo* src_params = _ext_events->at(i)->params;
      jvmtiParamInfo* dst_params = ext_events[i].params;

      for (int j=0; j<param_count; j++) {
        err = rt.allocate(strlen(src_params[j].name)+1,
                          (unsigned char**)&(dst_params[j].name));
        if (err != JVMTI_ERROR_NONE) {
          return err;
        }
        strcpy(dst_params[j].name, src_params[j].name);

        dst_params[j].kind = src_params[j].kind;
        dst_params[j].base_type = src_params[j].base_type;
        dst_params[j].null_ok = src_params[j].null_ok;
      }
    }
  }

  *extension_count_ptr = _ext_events->length();
  *extensions = ext_events;
  return JVMTI_ERROR_NONE;
}

// set callback for an extension event and enable/disable it.

jvmtiError JvmtiExtensions::set_event_callback(JvmtiEnv* env,
                                               jint extension_event_index,
                                               jvmtiExtensionEvent callback)
{
  guarantee(_ext_events != NULL, "registration not done");

  jvmtiExtensionEventInfo* event = NULL;

  // if there are extension events registered then validate that the
  // extension_event_index matches one of the registered events.
  if (_ext_events != NULL) {
    for (int i=0; i<_ext_events->length(); i++ ) {
      if (_ext_events->at(i)->extension_event_index == extension_event_index) {
         event = _ext_events->at(i);
         break;
      }
    }
  }

  // invalid event index
  if (event == NULL) {
    return JVMTI_ERROR_ILLEGAL_ARGUMENT;
  }

  JvmtiEventController::set_extension_event_callback(env, extension_event_index,
                                                     callback);

  return JVMTI_ERROR_NONE;
}
