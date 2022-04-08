
package at.jku.anttracks.parser

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.labs.AddressHO
import at.jku.anttracks.heap.labs.Lab
import at.jku.anttracks.heap.objects.ObjectInfo
import at.jku.anttracks.heap.space.Space
import at.jku.anttracks.heap.space.SpaceType
import at.jku.anttracks.heap.symbols.AllocationSite
import at.jku.anttracks.parser.heap.pointer.IncompletePointerInfo
import at.jku.anttracks.parser.heap.pointer.PointerHandling
import at.jku.anttracks.util.AntRingBuffer
import at.jku.anttracks.util.Assertion.assertion
import at.jku.anttracks.util.Counter
import at.jku.anttracks.util.TraceException
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.*
import java.util.concurrent.BlockingQueue
import java.util.concurrent.LinkedBlockingQueue

class ThreadLocalHeap(val internalThreadName: String,
                      var state: Int = STATE_IN_QUEUE,
                      val gcNr: Short = 0,
                      val gcStartOrEnd: EventType = EventType.GC_START) {
    companion object {
        const val STATE_PARKED = 0
        const val STATE_IN_QUEUE = 1
        const val STATE_IN_PROCESS = 2
        const val STATE_IN_QUEUE_FOR_CLEAN_UP = 3

        val test = 11

        private val NUM_LAST_ALLOCATIONS = 15

        private val EMPTY_PTR_ARRAY = LongArray(0)
    }

    val currentLabPos: MutableMap<SpaceType, Counter>

    val prototype = ObjectInfo()

    val queue: BlockingQueue<QueueEntry>
    val retiredLabs: MutableMap<Space, MutableList<Lab>>
    val currentLabs: MutableMap<SpaceType, Lab>

    var name: String? = "NoName"
    var id: Long = -1
    var header: Int = -1

    val lastAllocations: AntRingBuffer<AllocationSite>
    var objectsAllocated: Long = 0
    var extendedStackFramesStatic: Long = 0
    var extendedStackFramesDynamic: Long = 0

    var latestsMoveFromLAB: Lab? = null
    var latestsMoveToLAB: Lab? = null

    var lastMovedObjectWasFiller = BooleanArray(1) { false }

    // Ptr handling happens in two steps:
    // The first PTR event that is received for an object is stored in the fields currentPtrEvent, currentPtrFromAddr, currentPtrToAddr, currentPtrPointers and the
    // currentObjectPtrEvents list is null.
    // As soon as at least a second PTR event is received for the same object, the currentObjectPtrEvents list gets used to store pointer information.
    private var currentPtrEvent = EventType.NOP
    private var currentHeapObject: AddressHO? = null
    private var currentPtrFromAddr: Long = -1
    var currentPtrToAddr: Long = -1
    private var currentPtrPointers = EMPTY_PTR_ARRAY
    private var currentPtrTop = 0

    private val notYetMatchedMultiThreadedPtrEventsToHandleAtGCEnd: ArrayList<IncompletePointerInfo>
    val rootPointedObjectMovesToHandleAtGCEnd = Long2LongOpenHashMap()
    // private val movesSinceLastGCStart = Long2LongOpenHashMap()

    val fileName: String
        get() = "./${gcNr}_${gcStartOrEnd}_${internalThreadName}_${name}_${id}.threadlocaltrace"

    val file: File by lazy {
        val f = File(fileName)
        f.delete()
        f
    }
    val fileDataOutputStream: DataOutputStream by lazy {
        DataOutputStream(BufferedOutputStream(FileOutputStream(file, false), 100_000_000))
    }
    val outputData = ArrayList<Byte>()

    init {
        queue = LinkedBlockingQueue()
        retiredLabs = HashMap()
        currentLabs = HashMap()
        currentLabPos = HashMap()
        lastAllocations = AntRingBuffer(NUM_LAST_ALLOCATIONS)
        notYetMatchedMultiThreadedPtrEventsToHandleAtGCEnd = ArrayList()
    }

    fun retireCurrentLabs(heap: DetailedHeap) {
        for (type in currentLabs.keys.toTypedArray()) {
            retireCurrentLab(heap, type, true)
        }
    }

    private fun retireCurrentLab(heap: DetailedHeap, type: SpaceType?, clear: Boolean) {
        val current = currentLabs[type] ?: return
        val space = heap.getSpace(current.addr)
        var retireds: MutableList<Lab>? = retiredLabs[space]
        if (retireds == null) {
            retireds = ArrayList(SpaceType.values().size)
            if (space != null) {
                retiredLabs[space] = retireds
            }
        }
        retireds.add(current)
        if (clear && current.isFull) {
            currentLabs.remove(type)
        }
    }

    fun retireCurrentLab(heap: DetailedHeap, type: SpaceType?) {
        retireCurrentLab(heap, type, true)
    }

    /**
     * Record an allocation.
     *
     * @param site               The allocation site.
     * @param dynamicCallContext `true` to add the allocation to [.lastAllocations] and count dynamic stack frames, `false` to only count
     * the allocation and static stack frames.
     */
    fun recordAllocation(site: AllocationSite, dynamicCallContext: Boolean) {
        if (dynamicCallContext) {
            lastAllocations.add(site)
            extendedStackFramesDynamic += (site.callSites.size - site.stackSizeExtendedStatic).toLong()
        }
        objectsAllocated++
        extendedStackFramesStatic += (site.stackSizeExtendedStatic - site.stackSizeOriginal).toLong()
    }

    @Throws(TraceException::class)
    fun finishCurrentObjectPointers(heap: DetailedHeap) {
        if (currentPtrEvent != EventType.NOP) {
            // Objects must be filled completely (except they have been handled in a multi-threaded way by the VM
            if (currentPtrTop == currentPtrPointers.size) {
                PointerHandling.handleMovedObjectWithPointers(heap, currentPtrEvent, currentHeapObject, currentPtrToAddr, currentPtrPointers)
            } else {
                if (currentHeapObject!!.type.hasUnknownPointerCount) {
                    // weak references, etc.
                    PointerHandling.handleMovedObjectWithPointers(heap,
                                                                  currentPtrEvent,
                                                                  currentHeapObject!!,
                                                                  currentPtrToAddr,
                                                                  Arrays.copyOf(currentPtrPointers, currentPtrTop))
                } else {
                    // multi-threaded
                    val info = IncompletePointerInfo(currentPtrFromAddr,
                                                     currentPtrToAddr,
                                                     Arrays.copyOf(currentPtrPointers, currentPtrPointers.size),
                                                     currentPtrTop)
                    val alreadyExisting = heap.multiThreadedPtrEventsToHandleAtGCEnd.put(currentPtrToAddr, info)
                    assert(alreadyExisting == null) { "There must not have been an already existing incomplete pointer info" }
                }
            }
        }
        currentPtrEvent = EventType.NOP
        currentPtrFromAddr = -1
        currentPtrToAddr = -1
        currentHeapObject = null
        currentPtrPointers = EMPTY_PTR_ARRAY
        currentPtrTop = 0
    }

    fun isMatchingPtrEvent(toAddr: Long): Boolean {
        return currentPtrToAddr == toAddr
    }

    fun addPtrToCurrentObject(toAddr: Long, addPtrs: LongArray, heap: DetailedHeap) {
        assert(toAddr == currentPtrToAddr) { "All subsequent events must handle the same object" }
        assert(currentPtrTop + addPtrs.size <= currentPtrPointers.size) {
            "There must be enough space to hold the additional pointers." +
                    "CurrentPtrTop=$currentPtrTop, addPtrs.size=${addPtrs.size}, currentPtrsPointers.size=${currentPtrPointers.size}, object=${heap.getObjectInFront(toAddr)}"
        }
        if (currentPtrTop + addPtrs.size > currentPtrPointers.size)
            error("""Not enough space to merge pointer events!
                          |Initial pointer array must have been created with the wrong size
                          |current pointer top: $currentPtrTop, pointer array size: ${currentPtrPointers.size}, add size: ${addPtrs.size}, object=${heap.getObjectInFront(toAddr)}""".trimMargin()
            )
        System.arraycopy(addPtrs, 0, currentPtrPointers, currentPtrTop, addPtrs.size)
        currentPtrTop += addPtrs.size
    }

    fun startNewCurrentObjectPointers(event: EventType, fromAddr: Long, toAddr: Long, ho: AddressHO, ptrs: LongArray, top: Int) {
        assertion({ currentPtrTop == 0 }, { "Cannot start new object pointer handling if previous top is still set" })
        assertion({ currentPtrEvent == EventType.NOP }, { "Cannot start new object pointer handling if there are events currently set" })
        assertion({ currentPtrFromAddr == -1L }, { "Cannot start new object pointer handling if there are events currently set" })
        assertion({ currentPtrToAddr == -1L }, { "Cannot start new object pointer handling if there are events currently set" })
        assertion({ currentHeapObject == null }, { "Cannot start new object pointer handling if there are events currently set" })
        assertion({ currentPtrPointers contentEquals EMPTY_PTR_ARRAY }, { "Cannot start new object pointer handling if there are events currently set" })

        currentPtrEvent = event
        currentPtrFromAddr = fromAddr
        currentPtrToAddr = toAddr
        currentHeapObject = ho
        currentPtrPointers = ptrs
        currentPtrTop = top
    }

    fun addMultithreadedPtrEvent(heap: DetailedHeap, toAddr: Long, ptrs: LongArray) {
        var info: IncompletePointerInfo? = heap.multiThreadedPtrEventsToHandleAtGCEnd[toAddr]
        if (info != null) {
            info.addPointers(ptrs)
        } else {
            info = IncompletePointerInfo(-1, toAddr, Arrays.copyOf(ptrs, ptrs.size), ptrs.size)
            notYetMatchedMultiThreadedPtrEventsToHandleAtGCEnd.add(info)
        }
    }

    fun copyAndClearMultiThreadedEvents(dest: MutableList<IncompletePointerInfo>) {
        synchronized(dest) {
            dest.addAll(notYetMatchedMultiThreadedPtrEventsToHandleAtGCEnd)
        }
        notYetMatchedMultiThreadedPtrEventsToHandleAtGCEnd.clear()
    }

    /*
    fun recordMove(from: Long, to: Long) {
        movesSinceLastGCStart.put(from, to)
    }

    fun copyAndClearMoves(dest: Long2LongOpenHashMap) {
        synchronized(dest) {
            dest.putAll(movesSinceLastGCStart)
        }
        movesSinceLastGCStart.clear()
    }
    */
}
