/*
 * Copyright 2014 Edward Aftandilian. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.jku.anttracks.parser.hprof.handler

import at.jku.anttracks.parser.hprof.datastructures.*

/**
 * Base class to be used with the hprof parser.  For each record the parser encounters, it parses
 * the record and calls the matching function in its RecordHandler class.  The RecordHandler
 * handles each record, performing some function such as printing the record or building a graph.
 *
 * The default behavior is to do nothing for any record.
 */
class NullRecordHandler : RecordHandler {

    /* handler for file header */

    override fun header(format: String, idSize: Int, time: Long) {}

    /* handlers for top-level records */

    override fun stringInUTF8(id: Long, data: String) {}

    override fun loadClass(classSerialNum: Int, classObjId: Long, stackTraceSerialNum: Int, classNameStringId: Long) {}

    override fun unloadClass(classSerialNum: Int) {}

    override fun stackFrame(stackFrameId: Long, methodNameStringId: Long, methodSigStringId: Long, sourceFileNameStringId: Long, classSerialNum: Int, location: Int) {}

    override fun stackTrace(stackTraceSerialNum: Int, threadSerialNum: Int, numFrames: Int, stackFrameIds: LongArray) {}

    override fun allocSites(bitMaskFlags: Short,
                            cutoffRatio: Float,
                            totalLiveBytes: Int,
                            totalLiveInstances: Int,
                            totalBytesAllocated: Long,
                            totalInstancesAllocated: Long,
                            sites: Array<AllocSite>) {
    }

    override fun heapSummary(totalLiveBytes: Int, totalLiveInstances: Int, totalBytesAllocated: Long, totalInstancesAllocated: Long) {}

    override fun startThread(threadSerialNum: Int,
                             threadObjectId: Long,
                             stackTraceSerialNum: Int,
                             threadNameStringId: Long,
                             threadGroupNameId: Long,
                             threadParentGroupNameId: Long) {
    }

    override fun endThread(threadSerialNum: Int) {}

    override fun heapDump() {}

    override fun heapDumpEnd() {}

    override fun heapDumpSegment() {}

    override fun cpuSamples(totalNumOfSamples: Int, samples: Array<CPUSample>) {}

    override fun controlSettings(bitMaskFlags: Int, stackTraceDepth: Short) {}

    /* handlers for heap dump records */

    override fun rootUnknown(objId: Long) {}

    override fun rootJNIGlobal(objId: Long, JNIGlobalRefId: Long) {}

    override fun rootJNILocal(objId: Long, threadSerialNum: Int, frameNum: Int) {}

    override fun rootJavaFrame(objId: Long, threadSerialNum: Int, frameNum: Int) {}

    override fun rootNativeStack(objId: Long, threadSerialNum: Int) {}

    override fun rootStickyClass(objId: Long) {}

    override fun rootThreadBlock(objId: Long, threadSerialNum: Int) {}

    override fun rootMonitorUsed(objId: Long) {}

    override fun rootThreadObj(objId: Long, threadSerialNum: Int, stackTraceSerialNum: Int) {}

    override fun classDump(classObjId: Long,
                           stackTraceSerialNum: Int,
                           superClassObjId: Long,
                           classLoaderObjId: Long,
                           signersObjId: Long,
                           protectionDomainObjId: Long,
                           reserved1: Long,
                           reserved2: Long,
                           instanceSize: Int,
                           constants: Array<Constant>,
                           statics: Array<Static>,
                           instanceFields: Array<InstanceField>) {

    }

    override fun instanceDump(objId: Long, stackTraceSerialNum: Int, classObjId: Long, instanceFieldValues: Array<Value<*>>) {}

    override fun objArrayDump(objId: Long, stackTraceSerialNum: Int, elemClassObjId: Long, elems: LongArray) {}

    override fun primArrayDump(objId: Long, stackTraceSerialNum: Int, elemType: Byte, elems: Array<Value<*>>) {}

    /* handler for end of file */

    override fun finished() {}

}
