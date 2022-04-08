
package at.jku.anttracks.parser.heap

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.objects.ObjectInfo
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.heap.space.SpaceMode
import at.jku.anttracks.heap.space.SpaceType
import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.heap.symbols.AllocationSite
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.parser.*
import at.jku.anttracks.util.TraceException

open class HeapEventHandler(val heap: DetailedHeap,
                            val symbols: Symbols,
                            final override val parsingInfo: ParsingInfo,
                            private val heapTraceParser: HeapTraceParser?) : TraceParsingEventHandler {
    private val heapConstructor = HeapBuilder(this.heap, symbols, parsingInfo)

    @Throws(TraceException::class)
    override fun doCleanUp(
            threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doCleanUp(threadLocalHeap)
    }

    @Throws(TraceException::class)
    override fun doParseGCStart(
            gcInfo: ParserGCInfo,
            start: Long,
            end: Long,
            threadLocalHeap: ThreadLocalHeap) {
        val gcStartBeginTime = System.currentTimeMillis()
        // Does not take into account the very first allocations before the first GC
        if (heapTraceParser != null && heapTraceParser.latestGCEndTime >= 0) {
            heapTraceParser.allocTime.add(gcStartBeginTime - heapTraceParser.latestGCEndTime)
        }

        heapConstructor.doParseGCStart(gcInfo, start, end)

        if (heapTraceParser != null) {
            heapTraceParser.latestGCStartTime = System.currentTimeMillis()
            heapTraceParser.gcStartCompleteProcessingTime.add(heapTraceParser.latestGCStartTime - gcStartBeginTime)
        }
    }

    @Throws(TraceException::class)
    override fun doParseGCEnd(
            gcInfo: ParserGCInfo,
            start: Long,
            end: Long,
            failed: Boolean,
            threadLocalHeap: ThreadLocalHeap) {
        val gcEndBeginTime = System.currentTimeMillis()
        //val m = ApplicationStatistics.getInstance().createMeasurement("HeapEventHandler.doParseGCEnd")
        if (heapTraceParser != null && heapTraceParser.latestGCStartTime >= 0) {
            heapTraceParser.moveTime.add(gcEndBeginTime - heapTraceParser.latestGCStartTime)
        }

        heapConstructor.doParseGCEnd(gcInfo, threadLocalHeap, start, end, failed)

        if (heapTraceParser != null) {
            heapTraceParser.latestGCEndTime = System.currentTimeMillis()
            heapTraceParser.gcEndCompleteProcessingTime.add(heapTraceParser.latestGCEndTime - gcEndBeginTime)
        }
        //m.end()
    }

    override fun doParseThreadAlive(header: Int,
                                    id: Long,
                                    name: String,
                                    threadLocalHeap: ThreadLocalHeap) {
        threadLocalHeap.name = name
        threadLocalHeap.id = id
        threadLocalHeap.header = header
        heapConstructor.doParseThreadAlive(header, id, threadLocalHeap.internalThreadName, name)
    }

    override fun doParseThreadDeath(id: Long,
                                    threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseThreadDead(id)
    }

    // --------------------------------------------------------
    // ------------------- Move events ------------------------
    // --------------------------------------------------------

    @Throws(TraceException::class)
    override fun doParseGCMoveRegion(
            eventType: EventType,
            fromAddr: Long,
            toAddr: Long,
            numOfObjects: Int,
            threadLocalHeap: ThreadLocalHeap): List<ObjectInfo> {
        return heapConstructor.doParseGCMoveRegion(eventType, fromAddr, toAddr, numOfObjects, threadLocalHeap)
    }

    @Throws(TraceException::class)
    override fun doParseSyncObj(eventType: EventType,
                                allocationSiteId: Int,
                                allocatedType: AllocatedType,
                                fromAddr: Long,
                                toAddr: Long,
                                length: Int,
                                size: Int,
                                threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseSyncObj(eventType, allocationSiteId, allocatedType, fromAddr, toAddr, length, size, threadLocalHeap)
    }

    // --------------------------------------------------------
    // ------------------- Allocations ------------------------
    // --------------------------------------------------------

    @Throws(TraceException::class)
    override fun doParseObjAllocFastCi(eventType: EventType,
                                       allocationSite: AllocationSite,
                                       allocatedType: AllocatedType,
                                       isArray: Boolean,
                                       arrayLength: Int,
                                       threadLocalHeap: ThreadLocalHeap): Long {
        return heapConstructor.doParseObjAllocFastCi(eventType, allocationSite, allocatedType, isArray, arrayLength, threadLocalHeap)
    }

    @Throws(TraceException::class)
    override fun doParseObjAllocFastC2DeviantType(eventType: EventType,
                                                  header: Int,
                                                  allocationSiteId: Int,
                                                  allocatedType: AllocatedType,
                                                  isArray: Boolean,
                                                  arrayLength: Int,
                                                  threadLocalHeap: ThreadLocalHeap): Long {
        return heapConstructor.doParseObjAllocFastC2DeviantType(eventType, header, allocationSiteId, allocatedType, isArray, arrayLength, threadLocalHeap)
    }

    @Throws(TraceException::class)
    override fun doParseObjAllocFastIr(eventType: EventType, allocationSite: AllocationSite, threadLocalHeap: ThreadLocalHeap): Long {
        return heapConstructor.doParseObjAllocFastIr(eventType, allocationSite, threadLocalHeap)
    }

    @Throws(TraceException::class)
    override fun doParseObjAllocNormalCi(eventType: EventType,
                                         allocationSite: AllocationSite,
                                         allocatedType: AllocatedType,
                                         addr: Long,
                                         isArray: Boolean,
                                         arrayLength: Int,
                                         threadLocalHeap: ThreadLocalHeap): ObjectInfo {
        return heapConstructor.doParseObjAllocNormalCi(eventType, allocationSite, allocatedType, addr, isArray, arrayLength, threadLocalHeap)
    }

    @Throws(TraceException::class)
    override fun doParseObjAllocNormalIr(eventType: EventType,
                                         allocationSiteId: Int,
                                         allocationSite: AllocationSite,
                                         addr: Long,
                                         threadLocalHeap: ThreadLocalHeap): ObjectInfo {
        return heapConstructor.doParseObjAllocNormalIr(eventType, allocationSiteId, allocationSite, addr, threadLocalHeap)
    }

    @Throws(TraceException::class)
    override fun doParseObjAllocSlowCiIr_Deviant(eventType: EventType,
                                                 allocatedType: AllocatedType,
                                                 allocationSite: AllocationSite,
                                                 addr: Long,
                                                 isArray: Boolean,
                                                 arrayLength: Int,
                                                 realAllocatedTypeId: Int,
                                                 threadLocalHeap: ThreadLocalHeap): ObjectInfo {
        return heapConstructor.doParseObjAllocSlowCiIr_Deviant(eventType,
                                                               allocatedType,
                                                               allocationSite,
                                                               addr,
                                                               isArray,
                                                               arrayLength,
                                                               realAllocatedTypeId,
                                                               threadLocalHeap)
    }

    @Throws(TraceException::class)
    override fun doParseObjAllocSlow(eventType: EventType,
                                     allocationSite: AllocationSite,
                                     addr: Long,
                                     isArray: Boolean,
                                     arrayLength: Int,
                                     size: Int,
                                     mayBeFiller: Boolean,
                                     threadLocalHeap: ThreadLocalHeap): ObjectInfo {
        return heapConstructor.doParseObjAllocSlow(eventType, allocationSite, addr, isArray, arrayLength, size, mayBeFiller, threadLocalHeap)
    }

    @Throws(TraceException::class)
    override fun doParseTlabAlloc(size: Int, addr: Long, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseTlabAlloc(size, addr, threadLocalHeap)
    }

    @Throws(TraceException::class)
    override fun doParsePlabAlloc(size: Int, addr: Long, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParsePlabAlloc(size, addr, threadLocalHeap)
    }

    // --------------------------------------------------------
    // ----------------------- Spaces -------------------------
    // --------------------------------------------------------

    override fun doParseSpaceRedefine(index: Int, addr: Long, size: Long, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseSpaceRedefine(index, addr, size)
    }

    override fun doParseSpaceDestroy(firstIndex: Int, nRegions: Long, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseSpaceDestroy(firstIndex, nRegions)
    }

    override fun doParseSpaceRelease(index: Int, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseSpaceRelease(index)
    }

    override fun doParseSpaceAlloc(index: Int, spaceMode: SpaceMode, spaceType: SpaceType, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseSpaceAlloc(index, spaceMode, spaceType)
    }

    @Throws(TraceException::class)
    override fun doParseSpaceCreate(index: Int, startAddr: Long, size: Long, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseSpaceCreate(index, startAddr, size)
    }

    // --------------------------------------------------------
    // ----------------------- GC -----------------------------
    // --------------------------------------------------------

    @Throws(TraceException::class)
    override fun doParseGCInfo(index: Int, gcId: Int, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCInfo(index, gcId)
    }

    @Throws(TraceException::class)
    override fun doParseGCFailed(index: Int, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCFailed(index)
    }

    @Throws(TraceException::class)
    override fun doParseGCInterrupt(id: Int, address: Long, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCInterrupt(id, address)
    }

    @Throws(TraceException::class)
    override fun doParseGCContinue(id: Int, address: Long, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCContinue(id, address)
    }

    // --------------------------------------------------------
    // ----------------------- Moves --------------------------
    // --------------------------------------------------------

    @Throws(TraceException::class)
    override fun getMoveTarget(fromAddr: Long, toAddr: Long, toSpaceType: SpaceType, threadLocalHeap: ThreadLocalHeap): Long {
        return heapConstructor.getMoveTarget(fromAddr, toAddr, toSpaceType, threadLocalHeap)
    }

    @Throws(TraceException::class)
    override fun doGCMove(eventType: EventType, fromAddr: Long, toAddr: Long, toSpaceType: SpaceType?, threadLocalHeap: ThreadLocalHeap): Long {
        return heapConstructor.doGCMove(eventType, threadLocalHeap, fromAddr, toAddr, toSpaceType).tag
    }

    /*
    @Override
	public long doGCMove(EventType type, ThreadLocalHeap threadLocalHeap, ObjectInfoAge objAge, long fromAddr, long toAddr,
			SpaceType toSpaceType) throws TraceException {
		return heapConstructor.doGCMove(type, threadLocalHeap, objAge, fromAddr, toAddr, toSpaceType);
	}
	*/

    @Throws(TraceException::class)
    override fun doPtrEvent(eventType: EventType, fromAddr: Long, toAddr: Long, ptrs: LongArray, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doPtrEvent(eventType, threadLocalHeap, fromAddr, toAddr, ptrs)
    }

    override fun doParseGCTag(tagText: String, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.addTag(tagText)
    }

    @Throws(TraceException::class)
    override fun doKeepAlive(eventType: EventType, addr: Long, threadLocalHeap: ThreadLocalHeap) {
        doGCMove(eventType, addr, addr, null, threadLocalHeap)
    }

    // --------------------------------------------------------
    // ----------------------- Ptr ----------------------------
    // --------------------------------------------------------

    @Throws(TraceException::class)
    override fun doParseGCClassLoaderRootPtr(ptr: Long, loaderName: String, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCClassLoaderRootPtr(ptr, loaderName)
    }

    @Throws(TraceException::class)
    override fun doParseGCClassRootPtr(ptr: Long, classId: Int, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCClassRootPtr(ptr, classId)
    }

    @Throws(TraceException::class)
    override fun doParseGCStaticFieldRootPtr(ptr: Long, classId: Int, offset: Int, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCStaticFieldRootPtr(ptr, classId, offset)
    }

    @Throws(TraceException::class)
    override fun doParseGCLocalVariableRootPtr(ptr: Long, threadId: Long, classId: Int, methodId: Int, slot: Int, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCLocalVariableRootPtr(ptr, threadId, classId, methodId, slot)
    }

    @Throws(TraceException::class)
    override fun doParseGCVMInternalThreadDataRootPtr(ptr: Long, threadId: Long, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCVMInternalThreadDataRootPtr(ptr, threadId)
    }

    @Throws(TraceException::class)
    override fun doParseGCCodeBlobRootPtr(ptr: Long, classId: Int, methodId: Int, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCCodeBlobRootPtr(ptr, classId, methodId)
    }

    @Throws(TraceException::class)
    override fun doParseGCJNILocalRootPtr(ptr: Long, threadId: Long, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCJNILocalRootPtr(ptr, threadId)
    }

    @Throws(TraceException::class)
    override fun doParseGCJNIGlobalRootPtr(ptr: Long, weak: Boolean, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCJNIGlobalRootPtr(ptr, weak)
    }

    @Throws(TraceException::class)
    override fun doParseGCOtherRootPtr(ptr: Long, rootType: RootPtr.RootType, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCOtherRootPtr(ptr, rootType)
    }

    @Throws(TraceException::class)
    override fun doParseGCDebugRootPtr(ptr: Long, vmCall: String, threadLocalHeap: ThreadLocalHeap) {
        heapConstructor.doParseGCDebugRootPtr(ptr, vmCall)
    }
}
