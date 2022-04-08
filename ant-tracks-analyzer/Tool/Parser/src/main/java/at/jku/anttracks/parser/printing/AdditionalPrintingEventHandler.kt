package at.jku.anttracks.parser.printing

import at.jku.anttracks.heap.objects.ObjectInfo
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.heap.space.SpaceMode
import at.jku.anttracks.heap.space.SpaceType
import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.heap.symbols.AllocationSite
import at.jku.anttracks.parser.*
import java.util.logging.Logger

class AdditionalPrintingEventHandler(override val parsingInfo: ParsingInfo) : TraceParsingEventHandler {
    companion object {
        private val PRINT_CLEANUP = false

        private val PRINT_SLOW_ALLOC = false
        private val PRINT_NORMAL_ALLOC = false
        private val PRINT_FAST_ALLOC = false

        private val PRINT_SPACE_REDEFINE = false
        private val PRINT_SPACE_DESTROY = false
        private val PRINT_SPACE_RELEASE = false
        private val PRINT_SPACE_ALLOC = false
        private val PRINT_SPACE_CREATE = false

        private val PRINT_KEEP_ALIVE = false
        private val PRINT_GC_MOVE = false
        private val PRINT_GC_MOVE_REGION = false

        private val PRINT_TAG = true

        private val PRINT_GC_START = true
        private val PRINT_GC_END = true
        private val PRINT_GC_INFO = true

        private val PRINT_THREAD_ALIVE = false
        private val PRINT_THREAD_DEATH = false

        private val PSEUDO_OBJECT_INFO_LIST = listOf<ObjectInfo>()
        private val PSEUDO_OBJECT_INFO = ObjectInfo.NULL()
    }

    private val LOGGER = Logger.getLogger(AdditionalPrintingEventHandler::class.java.simpleName)

    override fun doCleanUp(threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_CLEANUP) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + "Cleanup")
        }
    }

    override fun doParseSpaceRedefine(index: Int, addr: Long, size: Long, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_SPACE_REDEFINE) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + String.format("Redefine space %d (aka new address / size) by %s\nstart: %,d, size: %,d",
                                                                                  index,
                                                                                  Thread.currentThread().name,
                                                                                  addr,
                                                                                  size))
        }
    }

    override fun doParseSpaceDestroy(firstIndex: Int, nRegions: Long, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_SPACE_DESTROY) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + String.format("Destroy space %d (aka completely remove it) to %d by %s",
                                                                                  firstIndex,
                                                                                  firstIndex + nRegions - 1,
                                                                                  Thread.currentThread().name))
        }
    }

    override fun doParseSpaceRelease(index: Int, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_SPACE_RELEASE) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + String.format("Release space %d (aka clear mode and type) by %s", index, Thread.currentThread().name))
        }
    }

    override fun doParseSpaceAlloc(index: Int, spaceMode: SpaceMode, spaceType: SpaceType, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_SPACE_ALLOC) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + String.format("Alloc space %d (aka set mode and type) by %s\nmode: %s, type: %s",
                                                                                  index,
                                                                                  Thread.currentThread().name,
                                                                                  spaceMode,
                                                                                  spaceType))
        }
    }

    override fun doParseSpaceCreate(index: Int, startAddr: Long, size: Long, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_SPACE_CREATE) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + String.format("Create space %d (aka set initial addr and size) by %s\nstart: %,d, size: %,d",
                                                                                  index,
                                                                                  Thread.currentThread().name,
                                                                                  startAddr,
                                                                                  size))
        }
    }

    override fun doParseGCInfo(index: Int, id: Int, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_GC_INFO) {
            LOGGER.info("${threadLocalHeap.internalThreadName}: Space $index collected in GC $id")
        }
    }

    override fun doParseGCFailed(index: Int, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCStart(gcInfo: ParserGCInfo, start: Long, end: Long, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_GC_START) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + String.format("Start GC %d (%s, %s)", gcInfo.id, gcInfo.type, gcInfo.cause))
        }
    }

    override fun doParseGCEnd(info: ParserGCInfo, start: Long, end: Long, failed: Boolean, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_GC_END) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + "End GC " + info.id)
        }
    }

    override fun doParseGCInterrupt(id: Int, address: Long, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCContinue(id: Int, address: Long, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doGCMove(type: EventType,
                          fromAddr: Long,
                          toAddr: Long,
                          toSpaceType: SpaceType?,
                          threadLocalHeap: ThreadLocalHeap): Long {
        if (PRINT_GC_MOVE) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + String.format("Object moved from %,d to %,d", fromAddr, toAddr))
        }
        return 0
    }

    override fun doKeepAlive(type: EventType, addr: Long, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_KEEP_ALIVE) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + String.format("Object %,d kept alive", addr))
        }
    }

    override fun doParseGCClassLoaderRootPtr(ptr: Long, loaderName: String, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCClassRootPtr(ptr: Long, classId: Int, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCStaticFieldRootPtr(ptr: Long, classId: Int, offset: Int, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCJNILocalRootPtr(ptr: Long, threadId: Long, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCLocalVariableRootPtr(ptr: Long, threadId: Long, classId: Int, methodId: Int, slot: Int, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCVMInternalThreadDataRootPtr(ptr: Long, threadId: Long, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCCodeBlobRootPtr(ptr: Long, classId: Int, methodId: Int, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCJNIGlobalRootPtr(ptr: Long, weak: Boolean, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCOtherRootPtr(ptr: Long, rootType: RootPtr.RootType, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCDebugRootPtr(ptr: Long, vmCall: String, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCMoveRegion(type: EventType,
                                     fromAddr: Long,
                                     toAddr: Long,
                                     numOfObjects: Int,
                                     threadLocalHeap: ThreadLocalHeap): List<ObjectInfo> {
        if (PRINT_GC_MOVE_REGION) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + String.format("Object group (numOfObjects: %d) moved from %,d to %,d", numOfObjects, fromAddr, toAddr))
        }
        return PSEUDO_OBJECT_INFO_LIST
    }

    override fun doParseThreadAlive(header: Int, id: Long, name: String, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_THREAD_ALIVE) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + "Alive Thread[name=$name, id=$id, header=$header")
        }
    }

    override fun doParseThreadDeath(id: Long, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_THREAD_DEATH) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + "Deatch Thread[id=$id]")
        }
    }

    override fun doParseSyncObj(type: EventType,
                                allocationSiteId: Int,
                                allocatedType: AllocatedType,
                                fromAddr: Long,
                                toAddr: Long,
                                length: Int,
                                size: Int,
                                threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseObjAllocFastCi(type: EventType,
                                       allocationSite: AllocationSite,
                                       allocatedType: AllocatedType,
                                       isArray: Boolean,
                                       arrayLength: Int,
                                       threadLocalHeap: ThreadLocalHeap): Long {
        if (PRINT_FAST_ALLOC) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + "FAST ALLOC[eventtype=$type, allocSite=$allocationSite, type=$allocatedType, isArray=$isArray, arraylength=$arrayLength]")
        }

        return 0L
    }

    override fun doParseObjAllocFastC2DeviantType(type: EventType,
                                                  header: Int,
                                                  allocationSiteId: Int,
                                                  allocatedType: AllocatedType,
                                                  isArray: Boolean,
                                                  arrayLength: Int,
                                                  threadLocalHeap: ThreadLocalHeap): Long {
        if (PRINT_FAST_ALLOC) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + "FAST ALLOC[eventtype=$type, allocSiteId=$allocationSiteId, type=$allocatedType, isArray=$isArray, arraylength=$arrayLength]")
        }
        return 0L
    }

    override fun doParseObjAllocFastIr(type: EventType, allocationSite: AllocationSite, threadLocalHeap: ThreadLocalHeap): Long {
        if (PRINT_FAST_ALLOC) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + "FAST ALLOC[eventtype=$type, allocSite=$allocationSite]")
        }
        return 0L
    }

    override fun doParseObjAllocNormalCi(type: EventType,
                                         allocationSite: AllocationSite,
                                         allocatedType: AllocatedType,
                                         addr: Long,
                                         isArray: Boolean,
                                         arrayLength: Int,
                                         threadLocalHeap: ThreadLocalHeap): ObjectInfo {
        return PSEUDO_OBJECT_INFO
    }

    override fun doParseObjAllocNormalIr(type: EventType,
                                         allocationSiteId: Int,
                                         allocationSite: AllocationSite,
                                         addr: Long,
                                         threadLocalHeap: ThreadLocalHeap): ObjectInfo {
        if (PRINT_NORMAL_ALLOC) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + "FAST ALLOC[eventtype=$type, allocSite=$allocationSite]")
        }
        return PSEUDO_OBJECT_INFO
    }

    override fun doParseObjAllocSlowCiIr_Deviant(type: EventType,
                                                 allocatedType: AllocatedType,
                                                 allocationSite: AllocationSite,
                                                 addr: Long,
                                                 isArray: Boolean,
                                                 arrayLength: Int,
                                                 realAllocatedTypeId: Int,
                                                 threadLocalHeap: ThreadLocalHeap): ObjectInfo {
        if (PRINT_SLOW_ALLOC) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + "FAST ALLOC[eventtype=$type, allocSite=$allocationSite, type=$allocatedType, isArray=$isArray, arraylength=$arrayLength]")
        }
        return PSEUDO_OBJECT_INFO
    }

    override fun doParseObjAllocSlow(type: EventType,
                                     allocationSite: AllocationSite,
                                     addr: Long,
                                     isArray: Boolean,
                                     arrayLength: Int,
                                     size: Int,
                                     mayBeFiller: Boolean,
                                     threadLocalHeap: ThreadLocalHeap): ObjectInfo {
        if (PRINT_SLOW_ALLOC) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + "FAST ALLOC[eventtype=$type, allocSite=$allocationSite, isArray=$isArray, arraylength=$arrayLength]")
        }
        return PSEUDO_OBJECT_INFO
    }

    override fun doParseTlabAlloc(size: Int, addr: Long, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParsePlabAlloc(size: Int, addr: Long, threadLocalHeap: ThreadLocalHeap) {

    }

    override fun getMoveTarget(fromAddr: Long, toAddr: Long, toSpaceType: SpaceType, threadLocalHeap: ThreadLocalHeap): Long {
        return 0
    }

    override fun doPtrEvent(type: EventType,
                            fromAddr: Long,
                            toAddr: Long,
                            ptrs: LongArray,
                            threadLocalHeap: ThreadLocalHeap) {

    }

    override fun doParseGCTag(tagText: String, threadLocalHeap: ThreadLocalHeap) {
        if (PRINT_TAG) {
            LOGGER.info(threadLocalHeap.internalThreadName + ": " + String.format("Tag received: %s", tagText))
        }
    }
}
