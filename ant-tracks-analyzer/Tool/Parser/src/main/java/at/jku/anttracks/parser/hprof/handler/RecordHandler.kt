package at.jku.anttracks.parser.hprof.handler

import at.jku.anttracks.parser.hprof.datastructures.*

/**
 * Primary interface to be used with the hprof parser.  The parser takes an implementation of
 * this interface and calls the matching callback method on each record encountered.
 * Implementations of this interface can do things like printing the record or building a graph.
 *
 *
 * You may assume that all references passed into the handler methods are non-null.
 *
 *
 * Generally you want to subclass `NullRecordHandler` rather than implement this interface
 * directly.
 */
interface RecordHandler {

    fun header(format: String, idSize: Int, time: Long)

    fun stringInUTF8(id: Long, data: String)

    fun loadClass(classSerialNum: Int, classObjId: Long, stackTraceSerialNum: Int, classNameStringId: Long)

    fun unloadClass(classSerialNum: Int)

    fun stackFrame(stackFrameId: Long,
                   methodNameStringId: Long,
                   methodSigStringId: Long,
                   sourceFileNameStringId: Long,
                   classSerialNum: Int,
                   location: Int)

    fun stackTrace(stackTraceSerialNum: Int, threadSerialNum: Int, numFrames: Int, stackFrameIds: LongArray)

    fun allocSites(bitMaskFlags: Short,
                   cutoffRatio: Float,
                   totalLiveBytes: Int,
                   totalLiveInstances: Int,
                   totalBytesAllocated: Long,
                   totalInstancesAllocated: Long,
                   sites: Array<AllocSite>)

    fun heapSummary(totalLiveBytes: Int, totalLiveInstances: Int, totalBytesAllocated: Long, totalInstancesAllocated: Long)

    fun startThread(threadSerialNum: Int,
                    threadObjectId: Long,
                    stackTraceSerialNum: Int,
                    threadNameStringId: Long,
                    threadGroupNameId: Long,
                    threadParentGroupNameId: Long)

    fun endThread(threadSerialNum: Int)

    fun heapDump()

    fun heapDumpEnd()

    fun heapDumpSegment()

    fun cpuSamples(totalNumOfSamples: Int, samples: Array<CPUSample>)

    fun controlSettings(bitMaskFlags: Int, stackTraceDepth: Short)

    fun rootUnknown(objId: Long)

    fun rootJNIGlobal(objId: Long, JNIGlobalRefId: Long)

    fun rootJNILocal(objId: Long, threadSerialNum: Int, frameNum: Int)

    fun rootJavaFrame(objId: Long, threadSerialNum: Int, frameNum: Int)

    fun rootNativeStack(objId: Long, threadSerialNum: Int)

    fun rootStickyClass(objId: Long)

    fun rootThreadBlock(objId: Long, threadSerialNum: Int)

    fun rootMonitorUsed(objId: Long)

    fun rootThreadObj(objId: Long, threadSerialNum: Int, stackTraceSerialNum: Int)

    fun classDump(classObjId: Long,
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
                  instanceFields: Array<InstanceField>)

    fun instanceDump(objId: Long, stackTraceSerialNum: Int, classObjId: Long, instanceFieldValues: Array<Value<*>>)

    fun objArrayDump(objId: Long, stackTraceSerialNum: Int, elemClassObjId: Long, elems: LongArray)

    fun primArrayDump(objId: Long, stackTraceSerialNum: Int, elemType: Byte, elems: Array<Value<*>>)

    fun finished()

}
