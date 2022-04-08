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

open class CountingRecordHandler : RecordHandler {
    var headerCount = 0
    var utf8Count = 0
    var loadClassCount = 0
    var unloadClassCount = 0
    var stackFrameCount = 0
    var stackTraceCount = 0
    var allocSitesCount = 0
    var heapSummaryCount = 0
    var startThreadCount = 0
    var endThreadCount = 0
    var heapDumpCount = 0
    var heapDumpEndCount = 0
    var heapDumpSegmentCount = 0
    var cpuSamplesCount = 0
    var controlSettingsCount = 0
    var rootUnknownCount = 0
    var rootJniGlobalCount = 0
    var rootJNILocalCount = 0
    var rootJavaFrameCount = 0
    var rootNativeStackCount = 0
    var rootStickyClassCount = 0
    var rootThreadBlockCount = 0
    var rootMonitorUsedCount = 0
    var rootThreadObjCount = 0
    var classDumpCount = 0
    var instanceDumpCount = 0
    var objArrayDumpCount = 0
    var primArrayDumpCount = 0

    var startTime = 0L
    var endTime = 0L

    /* handler for file header */

    override fun header(format: String, idSize: Int, time: Long) {
        headerCount++
        startTime = System.currentTimeMillis()
    }

    /* handlers for top-level records */

    override fun stringInUTF8(id: Long, data: String) {
        utf8Count++
    }

    override fun loadClass(classSerialNum: Int, classObjId: Long,
                           stackTraceSerialNum: Int, classNameStringId: Long) {
        loadClassCount++
    }

    override fun unloadClass(classSerialNum: Int) {
        unloadClassCount++
    }

    override fun stackFrame(stackFrameId: Long, methodNameStringId: Long,
                            methodSigStringId: Long, sourceFileNameStringId: Long,
                            classSerialNum: Int, location: Int) {
        stackFrameCount++
    }

    override fun stackTrace(stackTraceSerialNum: Int, threadSerialNum: Int,
                            numFrames: Int, stackFrameIds: LongArray) {
        stackTraceCount++
    }

    override fun allocSites(bitMaskFlags: Short, cutoffRatio: Float,
                            totalLiveBytes: Int, totalLiveInstances: Int, totalBytesAllocated: Long,
                            totalInstancesAllocated: Long, sites: Array<AllocSite>) {
        allocSitesCount++
    }

    override fun heapSummary(totalLiveBytes: Int, totalLiveInstances: Int,
                             totalBytesAllocated: Long, totalInstancesAllocated: Long) {
        heapSummaryCount++
    }

    override fun startThread(threadSerialNum: Int, threadObjectId: Long,
                             stackTraceSerialNum: Int, threadNameStringId: Long, threadGroupNameId: Long,
                             threadParentGroupNameId: Long) {
        startThreadCount++
    }

    override fun endThread(threadSerialNum: Int) {
        endThreadCount++
    }

    override fun heapDump() {
        heapDumpCount++
    }

    override fun heapDumpEnd() {
        heapDumpEndCount++
    }

    override fun heapDumpSegment() {
        heapDumpSegmentCount++
    }

    override fun cpuSamples(totalNumOfSamples: Int, samples: Array<CPUSample>) {
        cpuSamplesCount++
    }

    override fun controlSettings(bitMaskFlags: Int, stackTraceDepth: Short) {
        controlSettingsCount++
    }

    /* handlers for heap dump records */

    override fun rootUnknown(objId: Long) {
        rootUnknownCount++
    }

    override fun rootJNIGlobal(objId: Long, JNIGlobalRefId: Long) {
        rootJniGlobalCount++
    }

    override fun rootJNILocal(objId: Long, threadSerialNum: Int, frameNum: Int) {
        rootJNILocalCount++
    }

    override fun rootJavaFrame(objId: Long, threadSerialNum: Int, frameNum: Int) {
        rootJavaFrameCount++
    }

    override fun rootNativeStack(objId: Long, threadSerialNum: Int) {
        rootNativeStackCount++
    }

    override fun rootStickyClass(objId: Long) {
        rootStickyClassCount++
    }

    override fun rootThreadBlock(objId: Long, threadSerialNum: Int) {
        rootThreadBlockCount++
    }

    override fun rootMonitorUsed(objId: Long) {
        rootMonitorUsedCount++
    }

    override fun rootThreadObj(objId: Long, threadSerialNum: Int,
                               stackTraceSerialNum: Int) {
        rootThreadObjCount++
    }

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
        classDumpCount++
    }

    override fun instanceDump(objId: Long, stackTraceSerialNum: Int, classObjId: Long, instanceFieldValues: Array<Value<*>>) {
        instanceDumpCount++
    }

    override fun objArrayDump(objId: Long, stackTraceSerialNum: Int, elemClassObjId: Long, elems: LongArray) {
        objArrayDumpCount++
    }

    override fun primArrayDump(objId: Long, stackTraceSerialNum: Int, elemType: Byte, elems: Array<Value<*>>) {
        primArrayDumpCount++
    }

    /* handler for end of file */

    override fun finished() {
        endTime = System.currentTimeMillis()
    }

    open fun print() {
        println("### parsingTime ${endTime - startTime}")
        println("### headerCount $headerCount")
        println("### utf8Count $utf8Count")
        println("### loadClassCount $loadClassCount")
        println("### unloadClassCount $unloadClassCount")
        println("### stackFrameCount $stackFrameCount")
        println("### stackTraceCount $stackTraceCount")
        println("### allocSitesCount $allocSitesCount")
        println("### heapSummaryCount $heapSummaryCount")
        println("### startThreadCount $startThreadCount")
        println("### endThreadCount $endThreadCount")
        println("### heapDumpCount $heapDumpCount")
        println("### heapDumpEndCount $heapDumpEndCount")
        println("### heapDumpSegmentCount $heapDumpSegmentCount")
        println("### cpuSamplesCount $cpuSamplesCount")
        println("### controlSettingsCount $controlSettingsCount")
        println("### rootUnknownCount $rootUnknownCount")
        println("### rootJniGlobalCount $rootJniGlobalCount")
        println("### rootJNILocalCount $rootJNILocalCount")
        println("### rootJavaFrameCount $rootJavaFrameCount")
        println("### rootNativeStackCount $rootNativeStackCount")
        println("### rootStickyClassCount $rootStickyClassCount")
        println("### rootThreadBlockCount $rootThreadBlockCount")
        println("### rootMonitorUsedCount $rootMonitorUsedCount")
        println("### rootThreadObjCount $rootThreadObjCount")
        println("### classDumpCount $classDumpCount")
        println("### instanceDumpCount $instanceDumpCount")
        println("### objArrayDumpCount $objArrayDumpCount")
        println("### primArrayDumpCount $primArrayDumpCount")
    }
}
