package at.jku.anttracks.parser

import at.jku.anttracks.heap.objects.ObjectInfo
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.heap.space.SpaceMode
import at.jku.anttracks.heap.space.SpaceType
import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.heap.symbols.AllocationSite
import at.jku.anttracks.util.TraceException

interface TraceParsingEventHandler {
    val parsingInfo: ParsingInfo

    @Throws(TraceException::class)
    fun doCleanUp(threadLocalHeap: ThreadLocalHeap)

    fun doParseSpaceRedefine(index: Int, addr: Long, size: Long, threadLocalHeap: ThreadLocalHeap)

    fun doParseSpaceDestroy(firstIndex: Int, nRegions: Long, threadLocalHeap: ThreadLocalHeap)

    fun doParseSpaceRelease(index: Int, threadLocalHeap: ThreadLocalHeap)

    fun doParseSpaceAlloc(index: Int, spaceMode: SpaceMode, spaceType: SpaceType, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseSpaceCreate(index: Int, startAddr: Long, size: Long, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCInfo(index: Int, gcId: Int, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCFailed(index: Int, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCStart(gcInfo: ParserGCInfo, start: Long, end: Long, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCEnd(gcInfo: ParserGCInfo, start: Long, end: Long, failed: Boolean, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCInterrupt(id: Int, address: Long, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCContinue(id: Int, address: Long, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doGCMove(eventType: EventType, fromAddr: Long, toAddr: Long, toSpaceType: SpaceType?, threadLocalHeap: ThreadLocalHeap): Long

    @Throws(TraceException::class)
    fun doParseGCClassLoaderRootPtr(ptr: Long, loaderName: String, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCClassRootPtr(ptr: Long, classId: Int, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCStaticFieldRootPtr(ptr: Long, classId: Int, offset: Int, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCJNILocalRootPtr(ptr: Long, threadId: Long, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCLocalVariableRootPtr(ptr: Long, threadId: Long, classId: Int, methodId: Int, slot: Int, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCVMInternalThreadDataRootPtr(ptr: Long, threadId: Long, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCCodeBlobRootPtr(ptr: Long, classId: Int, methodId: Int, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCJNIGlobalRootPtr(ptr: Long, weak: Boolean, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCOtherRootPtr(ptr: Long, rootType: RootPtr.RootType, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCDebugRootPtr(ptr: Long, vmCall: String, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseGCMoveRegion(eventType: EventType,
                            fromAddr: Long,
                            toAddr: Long,
                            numOfObjects: Int,
                            threadLocalHeap: ThreadLocalHeap): List<ObjectInfo>

    fun doParseThreadAlive(header: Int, id: Long, name: String, threadLocalHeap: ThreadLocalHeap)

    fun doParseThreadDeath(id: Long, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseSyncObj(eventType: EventType,
                       allocationSiteId: Int,
                       allocatedType: AllocatedType,
                       fromAddr: Long,
                       toAddr: Long,
                       length: Int,
                       size: Int,
                       threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParseObjAllocFastCi(eventType: EventType,
                              allocationSite: AllocationSite,
                              allocatedType: AllocatedType,
                              isArray: Boolean,
                              arrayLength: Int,
                              threadLocalHeap: ThreadLocalHeap): Long

    @Throws(TraceException::class)
    fun doParseObjAllocFastC2DeviantType(eventType: EventType,
                                         header: Int,
                                         allocationSiteId: Int,
                                         allocatedType: AllocatedType,
                                         isArray: Boolean,
                                         arrayLength: Int,
                                         threadLocalHeap: ThreadLocalHeap): Long

    @Throws(TraceException::class)
    fun doParseObjAllocFastIr(eventType: EventType, allocationSite: AllocationSite, threadLocalHeap: ThreadLocalHeap): Long

    @Throws(TraceException::class)
    fun doParseObjAllocNormalCi(eventType: EventType,
                                allocationSite: AllocationSite,
                                allocatedType: AllocatedType,
                                addr: Long,
                                isArray: Boolean,
                                arrayLength: Int,
                                threadLocalHeap: ThreadLocalHeap): ObjectInfo

    @Throws(TraceException::class)
    fun doParseObjAllocNormalIr(eventType: EventType,
                                allocationSiteId: Int,
                                allocationSite: AllocationSite,
                                addr: Long,
                                threadLocalHeap: ThreadLocalHeap): ObjectInfo

    @Throws(TraceException::class)
    fun doParseObjAllocSlowCiIr_Deviant(eventType: EventType,
                                        allocatedType: AllocatedType,
                                        allocationSite: AllocationSite,
                                        addr: Long,
                                        isArray: Boolean,
                                        arrayLength: Int,
                                        realAllocatedTypeId: Int,
                                        threadLocalHeap: ThreadLocalHeap): ObjectInfo

    @Throws(TraceException::class)
    fun doParseObjAllocSlow(eventType: EventType,
                            allocationSite: AllocationSite,
                            addr: Long,
                            isArray: Boolean,
                            arrayLength: Int,
                            size: Int,
                            mayBeFiller: Boolean,
                            threadLocalHeap: ThreadLocalHeap): ObjectInfo

    @Throws(TraceException::class)
    fun doParseTlabAlloc(size: Int, addr: Long, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doParsePlabAlloc(size: Int, addr: Long, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun getMoveTarget(fromAddr: Long, toAddr: Long, toSpaceType: SpaceType, threadLocalHeap: ThreadLocalHeap): Long

    @Throws(TraceException::class)
    fun doPtrEvent(eventType: EventType, fromAddr: Long, toAddr: Long, ptrs: LongArray, threadLocalHeap: ThreadLocalHeap)

    fun doParseGCTag(tagText: String, threadLocalHeap: ThreadLocalHeap)

    @Throws(TraceException::class)
    fun doKeepAlive(eventType: EventType, addr: Long, threadLocalHeap: ThreadLocalHeap)
}
