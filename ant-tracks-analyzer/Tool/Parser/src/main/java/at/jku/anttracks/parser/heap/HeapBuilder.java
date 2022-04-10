
package at.jku.anttracks.parser.heap;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.GarbageCollectionType;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.heap.labs.Lab;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.roots.*;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.heap.space.SpaceMode;
import at.jku.anttracks.heap.space.SpaceType;
import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.heap.symbols.AllocationSite;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.*;
import at.jku.anttracks.util.TraceException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

import static at.jku.anttracks.heap.labs.Lab.UNKNOWN_CAPACITY;
import static at.jku.anttracks.parser.TraceSlaveParser.NULL_PTR;
import static at.jku.anttracks.parser.heap.pointer.PtrEvent.MAX_PTRS_PER_EVENT;
import static at.jku.anttracks.util.Consts.UNDEFINED_ADDR;

public class HeapBuilder {

    public static final long[] DUMMY_NO_PTR_ARRAY = new long[0];

    // --------------------------------------------------------
    // ---------------------- Fields --------------------------
    // --------------------------------------------------------
    private final DetailedHeap heap;
    private final Symbols symbols;
    private final boolean useDynamicCallContext;
    private final ParsingInfo parsingInfo;
    private AllocationContext allocContext;
    private ThreadInfo currentThread; // build callstack
    private Logger LOGGER = Logger.getLogger(HeapBuilder.class.getSimpleName());

    // --------------------------------------------------------
    // ------------------- Constructor ------------------------
    // --------------------------------------------------------

    public HeapBuilder(DetailedHeap workspace, Symbols symbols, ParsingInfo parsingInfo) throws TraceException {
        heap = workspace;
        this.symbols = symbols;
        useDynamicCallContext = symbols.useDynamicCallContext();
        if (useDynamicCallContext) {
            allocContext = new AllocationContext();
        }

        this.parsingInfo = parsingInfo;
    }

    // --------------------------------------------------------
    // --------------------- Methods --------------------------
    // --------------------------------------------------------

    // --------------------------------------------------------
    // --------------------- Static ---------------------------
    // --------------------------------------------------------

    public static DetailedHeap constructHeap(Symbols symbols, ParsingInfo parsingInfo) {
        DetailedHeap heap = new DetailedHeap(symbols, parsingInfo);
        return heap;
    }

    // --------------------------------------------------------
    // --------------------- General ---------------------------
    // --------------------------------------------------------

    public void doCleanUp(ThreadLocalHeap tlh) throws TraceException {
        tlh.retireCurrentLabs(heap);
        heap.insertFillers(tlh);
        tlh.getRetiredLabs().clear();

        tlh.finishCurrentObjectPointers(heap);
        tlh.copyAndClearMultiThreadedEvents(heap.notYetMatchedMultiThreadedPtrEventsToHandleAtGCEnd);

        // tlh.copyAndClearMoves(heap.movesSinceLastGCStart);

        synchronized (heap.rootPtrs) {
            tlh.getRootPointedObjectMovesToHandleAtGCEnd().long2LongEntrySet().fastForEach(entry -> {
                long from = entry.getLongKey();
                long to = entry.getLongValue();

                List<RootPtr> rootPtrList = heap.rootPtrs.remove(from);
                rootPtrList.forEach(rp -> rp.setAddr(to));
                heap.rootPtrs.put(to, rootPtrList);
            });
        }
        tlh.getRootPointedObjectMovesToHandleAtGCEnd().clear();
    }

    public void doParseGCStart(ParserGCInfo info, long start, long end) throws TraceException {
        heap.assignLabsIncorrectlyAssumedToBeFillers(false);
        heap.startGC(info, start - Scanner.META_BUFFER_INFO);

        if (TraceParser.CONSISTENCY_CHECK) {
            heap.validate(symbols.isHeapFragmented);
        }
    }

    public void doParseGCEnd(ParserGCInfo info, ThreadLocalHeap threadLocalHeap, long start, long end, boolean failed) throws TraceException {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("HeapBuilder.GCEnd");

        // This is quick, no need to measure it
        heap.assignLabsIncorrectlyAssumedToBeFillers(false);
        threadLocalHeap.finishCurrentObjectPointers(heap);

        heap.stopGC(info, failed, end);

        //m.end();
    }

    public void doParseGCInterrupt(int id, long address) throws TraceException {
        heap.interruptGC((short) id, address);
    }

    public void doParseGCContinue(int id, long address) throws TraceException {
        heap.continueGC(id, address);
    }

    // --------------------------------------------------------
    // --------------------- Moving ---------------------------
    // --------------------------------------------------------

    public List<ObjectInfo> doParseGCMoveRegion(EventType type, long fromAddr, long toAddr, int numOfObjects, ThreadLocalHeap threadLocalHeap)
            throws TraceException {
        List<ObjectInfo> movedObjects = new ArrayList<>(numOfObjects);

        Space toSpace = heap.getSpace(toAddr);
        assert toSpace != null : "To-Space in doParseGCMoveRegion may not be null";

        Lab regionLab = toSpace.assignRealLab(threadLocalHeap.getInternalThreadName(), Lab.Kind.REGION_VIRTUAL, toAddr, UNKNOWN_CAPACITY);
        threadLocalHeap.getCurrentLabs().put(toSpace.getType(), regionLab);

        for (int i = 0; i < numOfObjects; i++) {
            // if next object is in another space
            if (!toSpace.contains(toAddr)) {
                // retire current lab
                regionLab.variableCapacity();
                regionLab.resetCapacity();
                threadLocalHeap.retireCurrentLab(heap, toSpace.getType());
                // create new lab
                toSpace = heap.getSpace(toAddr);
                regionLab = toSpace.assignRealLab(threadLocalHeap.getInternalThreadName(), Lab.Kind.REGION_VIRTUAL, toAddr, UNKNOWN_CAPACITY);
                threadLocalHeap.getCurrentLabs().put(toSpace.getType(), regionLab);
            }
            AddressHO obj = doGCMove(type, threadLocalHeap, fromAddr, toAddr, toSpace.getType());
            toAddr += obj.getSize();
            fromAddr += obj.getSize();
            movedObjects.add(obj.getInfo());
        }
        // Make sure size is correctly set for LAB
        regionLab.variableCapacity();
        regionLab.resetCapacity();

        threadLocalHeap.retireCurrentLab(heap, toSpace.getType());
        return movedObjects;
    }

    public Space getSpace(long addr) {
        return heap.getSpace(addr);
    }

    public AddressHO doGCMove(EventType type, ThreadLocalHeap threadLocalHeap, long fromAddr, long toAddr, SpaceType toSpaceType) throws TraceException {
        Space fromSpace = getSpace(fromAddr);
        AddressHO fillerObject = fromSpace.checkIfObjectIsFiller(fromAddr, heap.getGC().getType().isFull());

        boolean isFiller = fillerObject != null;
        threadLocalHeap.getLastMovedObjectWasFiller()[0] = isFiller;

        AddressHO obj;
        if (isFiller) {
            obj = fillerObject;
        } else {
            if (threadLocalHeap.getLatestsMoveFromLAB() == null || !threadLocalHeap.getLatestsMoveFromLAB().contains(fromAddr)) {
                threadLocalHeap.setLatestsMoveFromLAB(fromSpace.getLabInBack(fromAddr));
                // ApplicationStatistics.getInstance().inc("MOVE from recalc");
            }
            obj = threadLocalHeap.getLatestsMoveFromLAB().getObject(fromAddr);
        }

        long assignedAddr = -1;
        if (toAddr >= 0) {
            if (threadLocalHeap.getLatestsMoveToLAB() != null) {
                if (threadLocalHeap.getLatestsMoveToLAB().contains(fromAddr)) {
                    assignedAddr = assignToLab(threadLocalHeap.getInternalThreadName(), threadLocalHeap.getLatestsMoveToLAB(), obj, toAddr);
                    //ApplicationStatistics.getInstance().inc("MOVE slow into");
                } else if (threadLocalHeap.getLatestsMoveToLAB().top() == toAddr &&
                        (threadLocalHeap.getLatestsMoveToLAB().kind == Lab.Kind.VIRTUAL || threadLocalHeap.getLatestsMoveToLAB().kind == Lab.Kind.REGION_VIRTUAL)) {
                    Space toSpace = heap.getSpace(toAddr);
                    if (!toSpace.getFrontLabs().containsExact(toAddr)) {
                        toSpace.assignIntoAdjacentLab(threadLocalHeap.getLatestsMoveToLAB(), threadLocalHeap.getInternalThreadName(), toAddr, obj);
                        assignedAddr = toAddr;
                        //ApplicationStatistics.getInstance().inc("MOVE slow adjacent");
                    } else {
                        assignedAddr = assignToLab(threadLocalHeap.getInternalThreadName(),
                                                   toSpace.getLabInFront(toAddr),
                                                   obj,
                                                   toAddr);
                        threadLocalHeap.setLatestsMoveToLAB(toSpace.getLabInFront(toAddr));
                        //ApplicationStatistics.getInstance().inc("MOVE slow existing");
                    }
                }
            }
            if (assignedAddr == -1) {
                Space toSpace = heap.getSpace(toAddr);
                assignedAddr = assignToLabOrSpace(threadLocalHeap.getInternalThreadName(), threadLocalHeap, toSpace.getType(), toAddr, obj);
                threadLocalHeap.setLatestsMoveToLAB(toSpace.getLabInFront(assignedAddr));
                // ApplicationStatistics.getInstance().inc("MOVE slow new");
            }
        } else {
            assert toSpaceType != null : "If we do not know the to-address, we have to know the to-space-type";
            assignedAddr = assignToLab(threadLocalHeap.getInternalThreadName(),
                                       threadLocalHeap.getCurrentLabs().get(toSpaceType),
                                       obj,
                                       toAddr);
            // ApplicationStatistics.getInstance().inc("MOVE fast");
        }
        obj.setTag(assignedAddr);
        obj.setLastMovedAt(heap.latestGCId());
        // threadLocalHeap.recordMove(fromAddr, assignedAddr);

        if (symbols.expectPointers) {
            if (heap.rootPtrs.containsKey(fromAddr)) {
                // Object that is root pointed has been moved
                // Remember thread-local, so we do not need locking
                // Handling happens in cleanup()
                threadLocalHeap.getRootPointedObjectMovesToHandleAtGCEnd().put(fromAddr, assignedAddr);
            }
        }

        return obj;
    }

    public AddressHO doParseSyncObj(EventType eventType,
                                    int allocationSiteId,
                                    AllocatedType allocatedType,
                                    long fromAddr,
                                    long toAddr,
                                    int arrayLength,
                                    int size,
                                    ThreadLocalHeap threadLocalHeap) throws TraceException {
        boolean firstSyncGC = heap.latestGCId() == 0 && (heap.getGC().getType() == GarbageCollectionType.MAJOR_SYNC || heap.getGC().getType() == GarbageCollectionType.MINOR_SYNC);

        ObjectInfo objInfo = null;
        AllocationSite site = symbols.sites.getById(allocationSiteId);
        AddressHO object = null;
        if (firstSyncGC) {
            objInfo = heap.getCache().get(threadLocalHeap.getInternalThreadName(),
                                          site,
                                          allocatedType,
                                          eventType,
                                          size,
                                          arrayLength,
                                          threadLocalHeap.getPrototype(),
                                          symbols);

            object = AddressHO.Companion.createObject(objInfo, heap, false);
            long assignedAddr = assignToLabOrSpace(threadLocalHeap.getInternalThreadName(), threadLocalHeap, heap.getSpace(toAddr).getType(), toAddr, object);
        } else {
            if (heap.getGC().getType() != GarbageCollectionType.MINOR_SYNC || heap.getSpace(fromAddr).getType() != SpaceType.OLD) {
                object = doGCMove(eventType, threadLocalHeap, fromAddr, toAddr, getSpace(toAddr).getType());
            }
        }
        return object;
    }

    public void doPtrEvent(EventType eventType, ThreadLocalHeap threadLocalHeap, long fromAddr, long toAddr, long[] ptrs) throws TraceException {
        if (threadLocalHeap.getLastMovedObjectWasFiller()[0]) {
            // Ignore all filler objects
            return;
        }

        switch (eventType) {
            case GC_MOVE_SLOW_PTR:
            case GC_MOVE_FAST_WIDE_PTR:
            case GC_MOVE_FAST_PTR:
            case GC_KEEP_ALIVE_PTR:
                // This thread starts sending pointers for a new object, finish previous object and start processing new object

                assert fromAddr >= 0 : "From address must be known for \"move+pointer events\"";
                assert toAddr >= 0 : "To address must be known for \"move+pointer events\"";
                assert (eventType != EventType.GC_KEEP_ALIVE_PTR) || (fromAddr == toAddr) : "GC_KEEP_ALIVE_PTR must have matching from address and to address";
                startNewCurrentObjectPointers(threadLocalHeap, eventType, fromAddr, toAddr, ptrs);
                break;

            case GC_PTR_EXTENSION:
                // An extension event always has to belong to the object that is currently processed by the current thread
                assert fromAddr == -1 : "From address is not known for dedicated pointer events";
                assert toAddr != -1 : "To address must be known for dedicated pointer events";
                assert ptrs != null : "Dedicated ptr event must have ptrs";
                assert threadLocalHeap.isMatchingPtrEvent(toAddr) :
                        String.format("The received extension ptr event does not belong to the currently processed object!\n" +
                                              "Current object: %,d (%s)\n" +
                                              "Receive extension for: %,d (%s)",
                                      threadLocalHeap.getCurrentPtrToAddr(),
                                      heap.getObject(threadLocalHeap.getCurrentPtrToAddr()),
                                      toAddr,
                                      heap.getObject(toAddr));

                threadLocalHeap.addPtrToCurrentObject(toAddr, ptrs, heap);
                break;

            case GC_PTR_MULTITHREADED:
                // A multithreaded ptr event can be sent from any event for any object
                // The thread local lists will be merged after the GC and processed together
                assert fromAddr == -1 : "From address is not known for dedicated pointer events";
                assert toAddr != -1 : "To address must be known for dedicated pointer events";
                assert ptrs != null : "Dedicated ptr event must have ptrs";

                threadLocalHeap.addMultithreadedPtrEvent(heap, toAddr, ptrs);
                break;

            case GC_PTR_UPDATE_PREMOVE:
                // A pointer update event can either belong to the current object
                assert fromAddr == -1 : "From address is not known for dedicated pointer events";
                assert toAddr != -1 : "To address must be known for dedicated pointer events";
                assert ptrs != null : "Dedicated ptr event must have ptrs";

                if (threadLocalHeap.isMatchingPtrEvent(toAddr)) {
                    threadLocalHeap.addPtrToCurrentObject(toAddr, ptrs, heap);
                } else {
                    // or start a new object pointer handling
                    threadLocalHeap.finishCurrentObjectPointers(heap);
                    startNewCurrentObjectPointers(threadLocalHeap, eventType, toAddr, toAddr, ptrs);
                }
                break;

            case GC_PTR_UPDATE_POSTMOVE:
                assert fromAddr == -1 : "From address is not known for dedicated pointer events";
                assert toAddr != -1 : "To address must be known for dedicated pointer events";
                assert ptrs != null : "Dedicated ptr event must have ptrs";

                if (threadLocalHeap.isMatchingPtrEvent(toAddr)) {
                    threadLocalHeap.addPtrToCurrentObject(toAddr, ptrs, heap);
                } else {
                    threadLocalHeap.finishCurrentObjectPointers(heap);
                    startNewCurrentObjectPointers(threadLocalHeap, eventType, toAddr, toAddr, ptrs);
                }
                break;

            default:
                throw new TraceException("Cannot handle an event as pointer event that is not a pointer event");
        }
    }

    private void startNewCurrentObjectPointers(ThreadLocalHeap tlh, EventType eventType, long fromAddr, long toAddr, long[] ptrs) throws TraceException {
        long[] pointerArray = ptrs;
        AddressHO obj;
        if (tlh.getLatestsMoveFromLAB() != null && tlh.getLatestsMoveFromLAB().contains(fromAddr)) {
            //ApplicationStatistics.getInstance().inc("PointerObjectFoundInLatestMoveLAB");
            obj = tlh.getLatestsMoveFromLAB().getObject(fromAddr);
        } else {
            //ApplicationStatistics.getInstance().inc("PointerObjectSearchInHeap");
            obj = heap.getObjectInBack(fromAddr);
        }
        int pointerCount = obj.getPointerCount();
        int top = pointerArray.length;

        // Make the pointer array big enough to fit ptrs that will be added by following ptr events.
        // We do not need to copy arrays between size 1 and 12 here, because they will not be stored as arrays, but directly in HeapObject-instance fields.
        if (pointerArray.length <= pointerCount) {
            //ApplicationStatistics.getInstance().inc("PointerArrayCreation");
            long[] fullPointerArray = pointerCount == 0 ? DUMMY_NO_PTR_ARRAY : new long[pointerCount];
            Arrays.fill(fullPointerArray, NULL_PTR);
            System.arraycopy(pointerArray, 0, fullPointerArray, 0, pointerArray.length);
            pointerArray = fullPointerArray;
        } else if (pointerCount < 0) {
            //ApplicationStatistics.getInstance().inc("PointerArrayCreationUnknown");
            // TODO Handle objects with pointers that can arrive in wrong number...
            // This happens if we do not know the final pointer count (see AllocatedType.isKnownToHaveWrongPointerCount())

            int arraySize = pointerArray.length > obj.getType().pointersPerObject ? pointerArray.length : obj.getType().pointersPerObject;
            long[] fullPointerArray = arraySize == 0 ? DUMMY_NO_PTR_ARRAY : new long[arraySize];
            Arrays.fill(fullPointerArray, NULL_PTR);
            System.arraycopy(pointerArray, 0, fullPointerArray, 0, pointerArray.length);
            pointerArray = fullPointerArray;
        }

        tlh.finishCurrentObjectPointers(heap);
        tlh.startNewCurrentObjectPointers(eventType, fromAddr, toAddr, obj, pointerArray, top);

        if (pointerCount >= 0 && pointerCount <= MAX_PTRS_PER_EVENT) {
            //ApplicationStatistics.getInstance().inc("PointerArrayReuse");
            tlh.finishCurrentObjectPointers(heap);
        }
    }

    // --------------------------------------------------------
    // --------------------- Alloc ----------------------------
    // --------------------------------------------------------

    public long doParseObjAllocFastCi(EventType eventType,
                                      AllocationSite allocationSite,
                                      AllocatedType allocatedType,
                                      boolean isArray,
                                      int arrayLength,
                                      ThreadLocalHeap threadLocalHeap) throws TraceException {
        AllocationSite site = amendStackTrace(threadLocalHeap, allocationSite);

        ObjectInfo objInfo = heap.getCache().get(threadLocalHeap.getInternalThreadName(),
                                                 site,
                                                 allocatedType,
                                                 eventType,
                                                 -1,
                                                 arrayLength,
                                                 threadLocalHeap.getPrototype(),
                                                 symbols);

        AddressHO obj = AddressHO.Companion.createObject(objInfo, heap, false);
        long addr = assignToLab(threadLocalHeap.getInternalThreadName(),
                                threadLocalHeap.getCurrentLabs().get(SpaceType.EDEN),
                                obj,
                                UNDEFINED_ADDR);

        threadLocalHeap.recordAllocation(site, useDynamicCallContext);
        return addr;
    }

    public long doParseObjAllocFastC2DeviantType(EventType eventType,
                                                 int header,
                                                 int allocationSiteId,
                                                 AllocatedType allocatedType,
                                                 boolean isArray,
                                                 int arrayLength,
                                                 ThreadLocalHeap threadLocalHeap) throws TraceException {
        AllocationSite site = amendStackTrace(threadLocalHeap, symbols.sites.getById(allocationSiteId));

        ObjectInfo objInfo = heap.getCache().get(threadLocalHeap.getInternalThreadName(),
                                                 site,
                                                 allocatedType,
                                                 eventType,
                                                 -1,
                                                 arrayLength,
                                                 threadLocalHeap.getPrototype(),
                                                 symbols);

        AddressHO obj = AddressHO.Companion.createObject(objInfo, heap, false);
        long addr = assignToLab(threadLocalHeap.getInternalThreadName(), threadLocalHeap.getCurrentLabs().get(SpaceType.EDEN), obj, UNDEFINED_ADDR);

        threadLocalHeap.recordAllocation(site, useDynamicCallContext);
        return addr;
    }

    public long doParseObjAllocFastIr(EventType eventType, AllocationSite allocationSite, ThreadLocalHeap threadLocalHeap)
            throws TraceException {
        AllocationSite site = amendStackTrace(threadLocalHeap, allocationSite);

        ObjectInfo objInfo = heap.getCache().get(threadLocalHeap.getInternalThreadName(),
                                                 site,
                                                 symbols.types.getById(site.getAllocatedTypeId()),
                                                 eventType,
                                                 -1,
                                                 -1,
                                                 threadLocalHeap.getPrototype(),
                                                 symbols);
        AddressHO obj = AddressHO.Companion.createObject(objInfo, heap, false);
        long addr = assignToLab(threadLocalHeap.getInternalThreadName(), threadLocalHeap.getCurrentLabs().get(SpaceType.EDEN), obj, UNDEFINED_ADDR);

        threadLocalHeap.recordAllocation(site, useDynamicCallContext);

        return addr;
    }

    public ObjectInfo doParseObjAllocNormalCi(EventType eventType,
                                              AllocationSite allocationSite,
                                              AllocatedType allocatedType,
                                              long addr,
                                              boolean isArray,
                                              int arrayLength,
                                              ThreadLocalHeap threadLocalHeap) throws TraceException {
        AllocationSite site = amendStackTrace(threadLocalHeap, allocationSite);

        ObjectInfo objInfo = heap.getCache().get(threadLocalHeap.getInternalThreadName(),
                                                 site,
                                                 allocatedType,
                                                 eventType,
                                                 -1,
                                                 arrayLength,
                                                 threadLocalHeap.getPrototype(),
                                                 symbols);

        AddressHO obj = AddressHO.Companion.createObject(objInfo, heap, false);
        long assignedAddr = heap.getSpace(addr).assign(threadLocalHeap.getInternalThreadName(), obj, addr);

        threadLocalHeap.recordAllocation(site, useDynamicCallContext);
        return objInfo;
    }

    public ObjectInfo doParseObjAllocNormalIr(EventType eventType, int allocationSiteId, AllocationSite allocationSite, long addr, ThreadLocalHeap threadLocalHeap)
            throws TraceException {
        AllocationSite site = amendStackTrace(threadLocalHeap, allocationSite);

        ObjectInfo objInfo = heap.getCache().get(threadLocalHeap.getInternalThreadName(),
                                                 site,
                                                 symbols.types.getById(site.getAllocatedTypeId()),
                                                 eventType,
                                                 -1,
                                                 -1,
                                                 threadLocalHeap.getPrototype(),
                                                 symbols);

        AddressHO obj = AddressHO.Companion.createObject(objInfo, heap, false);
        long assignedAddr = heap.getSpace(addr).assign(threadLocalHeap.getInternalThreadName(), obj, addr);

        threadLocalHeap.recordAllocation(site, useDynamicCallContext);
        return objInfo;
    }

    public ObjectInfo doParseObjAllocSlowCiIr_Deviant(EventType eventType,
                                                      AllocatedType allocatedType,
                                                      AllocationSite allocationSite,
                                                      long addr,
                                                      boolean isArray,
                                                      int arrayLength,
                                                      int realAllocatedTypeId,
                                                      ThreadLocalHeap threadLocalHeap) throws TraceException {
        AllocationSite site = amendStackTrace(threadLocalHeap, allocationSite);

        SpaceType relAddrSpace = heap.getSpace(addr).getType();

        if (eventType == EventType.OBJ_ALLOC_SLOW_C1_DEVIANT_TYPE || eventType == EventType.OBJ_ALLOC_SLOW_IR_DEVIANT_TYPE || eventType == EventType
                .OBJ_ALLOC_SLOW_C2_DEVIANT_TYPE) {
            site = amendStackTrace(threadLocalHeap, site.copy(realAllocatedTypeId));
            allocatedType = symbols.types.getById(site.getAllocatedTypeId());
        }

        errorOnMirrorClass(allocatedType);

        ObjectInfo objInfo = heap.getCache().get(threadLocalHeap.getInternalThreadName(),
                                                 site,
                                                 allocatedType,
                                                 eventType,
                                                 -1,
                                                 arrayLength,
                                                 threadLocalHeap.getPrototype(),
                                                 symbols);

        AddressHO obj = AddressHO.Companion.createObject(objInfo, heap, false);
        long assignedAddr = assignToLabOrSpace(threadLocalHeap.getInternalThreadName(), threadLocalHeap, relAddrSpace, addr, obj);

        threadLocalHeap.recordAllocation(site, useDynamicCallContext);
        return objInfo;
    }

    public ObjectInfo doParseObjAllocSlow(EventType eventType,
                                          AllocationSite allocationSite,
                                          long addr,
                                          boolean isArray,
                                          int arrayLength,
                                          int size,
                                          boolean mayBeFiller,
                                          ThreadLocalHeap threadLocalHeap) throws TraceException {
        AllocationSite site = amendStackTrace(threadLocalHeap, allocationSite);
        Space space = heap.getSpace(addr);
        SpaceType spaceType = space.getType();

        ObjectInfo objInfo = heap.getCache().get(threadLocalHeap.getInternalThreadName(),
                                                 site,
                                                 symbols.types.getById(site.getAllocatedTypeId()),
                                                 eventType,
                                                 size,
                                                 arrayLength,
                                                 threadLocalHeap.getPrototype(),
                                                 symbols);

        AddressHO obj = AddressHO.Companion.createObject(objInfo, heap, false);
        long assignedAddr = assignToLabOrSpace(threadLocalHeap.getInternalThreadName(), threadLocalHeap, spaceType, addr, obj, mayBeFiller);

        threadLocalHeap.recordAllocation(site, useDynamicCallContext);
        return objInfo;
    }

    public void doParseTlabAlloc(int size, long addr, ThreadLocalHeap threadLocalHeap) throws TraceException {
        SpaceType relAddrSpace = heap.getSpace(addr).getType();
        errorOnTLABAlloc(relAddrSpace);
        setLab(threadLocalHeap, size, addr, Lab.Kind.TLAB);
    }

    public void doParsePlabAlloc(int size, long addr, ThreadLocalHeap threadLocalHeap) throws TraceException {
        Space space = heap.getSpace(addr);
        if (space == null) {
            throw new TraceException(String.format("PLAB should be allocated at address %,d, but no space found there.\nCurrent heap: %s", addr, heap.toString()));
        }
        SpaceType relAddrSpace = space.getType();
        errorOnPLABAlloc(relAddrSpace);
        setLab(threadLocalHeap, size > 0 ? size : UNKNOWN_CAPACITY, addr, size > 0 ? Lab.Kind.PLAB : Lab.Kind.REGION_VIRTUAL);
    }

    // --------------------------------------------------------
    // ---------------- Helper methods ------------------------
    // --------------------------------------------------------

    private long assignToLabOrSpace(String thread, ThreadLocalHeap tlh, SpaceType type, long addr, AddressHO obj, boolean mayBeFiller) throws TraceException {
        Lab currentLab = tlh.getCurrentLabs().get(type);
        long assignedAddr = Lab.OBJECT_NOT_ASSIGNED;
        if (currentLab != null) {
            if (mayBeFiller && addr + obj.getSize() == currentLab.end()) {
                // object can be for sure handled as filler, method "assign" takes care of everything
                heap.getSpace(addr).assign(thread, true, obj, addr);
                assignedAddr = addr;
            } else {
                // object is not a filler, current lab exists -> current object must be the next object in the LAB
                assignedAddr = assignToLab(thread, currentLab, obj, addr);
            }
        }
        if (assignedAddr == Lab.OBJECT_NOT_ASSIGNED) {
            heap.getSpace(addr).assign(thread, mayBeFiller, obj, addr);
        }

        return assignedAddr;
    }

    private long assignToLabOrSpace(String thread, ThreadLocalHeap tlh, SpaceType type, long addr, AddressHO obj) throws TraceException {
        long assignedAddr = assignToLab(thread, tlh.getCurrentLabs().get(type), obj, addr);
        if (assignedAddr == Lab.OBJECT_NOT_ASSIGNED) {
            assignedAddr = heap.getSpace(addr).assign(thread, obj, addr);
        }

        return assignedAddr;
    }

	/*
    private long assignToLabOrSpace(String thread, ThreadLocalHeap tlh, SpaceType type, long addr, ObjectInfoAge objAge)
			throws TraceException {
		long assignedAddr = assignToLab(thread, tlh.currentLabs.get(type), objAge.obj, addr);
		if (assignedAddr == Lab.OBJECT_NOT_ASSIGNED) {
			assignedAddr = heap.getSpace(addr).assign(thread, objAge, addr);
		} else {
			tlh.currentLabs.get(type).setAge(assignedAddr, objAge.age);
		}

		return assignedAddr;
	}
	*/

    private long assignToLab(String thread, Lab currentLab, AddressHO obj, long addr) throws TraceException {
        long assignedAddr = Lab.OBJECT_NOT_ASSIGNED;
        if (currentLab != null) {
            assignedAddr = currentLab.tryAllocate(addr, obj);
        } else {
            errorOnUndefinedAddr(addr);
        }

        return assignedAddr;
    }

    private void setLab(ThreadLocalHeap threadLocalHeap, int size, long addr, Lab.Kind kind) throws TraceException {
        SpaceType spaceType = heap.getSpace(addr).getType();
        Lab current = heap.getSpace(addr).assignRealLab(threadLocalHeap.getInternalThreadName(), kind, addr, size);
        threadLocalHeap.retireCurrentLab(heap, spaceType);
        threadLocalHeap.getCurrentLabs().put(spaceType, current);
    }

    // --------------------------------------------------------
    // ---------------- Stack trace extension -----------------
    // --------------------------------------------------------

    /**
     * Try to add missing methods to the stack trace of the specified allocation site using call context information.
     *
     * @param localHeap The {@link ThreadLocalHeap} to work with.
     * @param site      The allocation site.
     * @return A new allocation site with an amended stack trace, or {@code site} if no changes were made.
     */
    private AllocationSite amendStackTrace(ThreadLocalHeap localHeap, AllocationSite site) {
        // We can't do much without allocation context
        /*
         * Problem: if only second allocation decides, lookup stops at first allocation -> quick fix look through all nodes but does that
         * explode the tree?
         */
        if (useDynamicCallContext && symbols.isDynamicallyExtendableSite(site.getId()) && !localHeap.getLastAllocations().isEmpty()) {
            allocContext.assign(site, localHeap.getLastAllocations(), symbols);
            site = symbols.getOrCreateDynamicAllocationSite(site, allocContext);
        }
        return site;
    }

    // --------------------------------------------------------
    // ----------------- Error methods ------------------------
    // --------------------------------------------------------

    private static void errorOnUndefinedAddr(long addr) throws TraceException {
        if (addr == UNDEFINED_ADDR) {
            throw new TraceException("#CalculateAddrByTLAB, How can there be an allocation in a LAB without a LAB being allocated in the first place?");
        }
    }

    private static void errorOnTLABAlloc(SpaceType space) {
        // if (space != SpaceType.EDEN) {
        // throw new TraceException("#TLAB allocated in " + space);
        // }
        // Serial GC allocates into survivor sapces in rare cases ...
    }

    private static void errorOnPLABAlloc(SpaceType space) {
        // if (space != SpaceType.SURVIVOR_TO && space != SpaceType.OLD) { throw
        // new TraceException("#PLAB allocated in " + space); }
        // TODO: Discuss if further error handling has to be done here
    }

    private static void errorOnMirrorClass(AllocatedType allocatedType) throws TraceException {
        if (allocatedType.internalName.equals(ObjectInfo.MIRROR_CLASS)) {
            throw new TraceException("PARSE ERROR. Expected no MIRROR_CLASS here");
        }
    }

    // --------------------------------------------------------
    // ----------------------- Spaces -------------------------
    // --------------------------------------------------------

    public void doParseSpaceRedefine(int index, long addr, long size) {
        heap.getSpacesUncloned()[index].resetAddressAndLength(addr, size);
    }

    public void doParseSpaceDestroy(int firstIndex, long nRegions) {
        for (int i = 0; i < nRegions; i++) {
            heap.removeSpace(firstIndex + i);
        }
        // LOGGER.info(heap.toString());
    }

    public void doParseSpaceRelease(int index) {
        Space space = heap.getSpace(index);
        space.setMode(null);
        space.setType(null);
        // LOGGER.info(heap.toString());
    }

    public void doParseSpaceAlloc(int index, SpaceMode mode, SpaceType type) {
        Space space = heap.getSpace(index);
        space.setMode(mode);
        space.setType(type);
        // LOGGER.info(heap.toString());
    }

    public void doParseSpaceCreate(int index, long addr, long size) throws TraceException {
        Space space = heap.getSpace(index);
        if (space != null) {
            assert space.getAddress() == addr;
            // assert space.getLength() == size; //this may be valid due to rotation and postponed space events
        } else {
            space = new Space("Space #" + index);
            heap.addSpace((short) index, space);
            space.resetAddressAndLength(addr, size);
        }
        // LOGGER.info(heap.toString());
    }

    public void doParseGCInfo(int index, int gcId) throws TraceException {
        heap.markSpaceForCollection((short) index, (short) gcId);
    }

    public void doParseGCFailed(int index) throws TraceException {
        heap.failGC(index);
    }

    // Used in assertions to validate move location
    public long getMoveTarget(long fromAddr, long toAddr, SpaceType toSpaceType, ThreadLocalHeap threadLocalHeap) throws TraceException {
        Space space = heap.getSpace(fromAddr);
        ObjectInfo obj = space.getObjectInfo(fromAddr, heap.getGC().getType().isFull());
        assert obj != null;
        assert (toSpaceType != SpaceType.UNDEFINED);
        assert (toAddr >= 0 ? toSpaceType == heap.getSpace(toAddr).getType() : true);
        // SpaceType toSpace = toAddr < 0 ? toSpaceType :
        // RelAddrFactory.getSpaceFromAbsolute(workspace, toAddr).getType();
        Lab current = threadLocalHeap.getCurrentLabs().get(toSpaceType);
        return current.addr + (current.position() - obj.size);
    }

    protected void doParseGCClassLoaderRootPtr(long addr, String loaderName) throws TraceException {
        assert addr < 0 || heap.getObjectInBack(addr) != null : "Root pointed object not found in heap at address " + addr;
        heap.addRoot(new ClassLoaderRoot(addr, loaderName));
    }

    protected void doParseGCClassRootPtr(long addr, int classId) throws TraceException {
        assert addr < 0 || heap.getObjectInBack(addr) != null : "Root pointed object not found in heap at address " + addr;
        //assert addr < 0 || heap.getObjectInBack(addr).getType().id == -classId : "Root pointer type id and real type id do not match";
        heap.addRoot(new ClassRoot(addr, -classId));
    }

    protected void doParseGCStaticFieldRootPtr(long addr, int classId, int offset) throws TraceException {
        assert addr < 0 || heap.getObjectInBack(addr) != null : "Root pointed object not found in heap at address " + addr;
        heap.addRoot(new StaticFieldRoot(addr, classId, offset));
    }

    protected void doParseGCLocalVariableRootPtr(long addr, long threadId, int classId, int methodId, int slot) throws TraceException {
        assert addr < 0 || heap.getObjectInBack(addr) != null : "Root pointed object not found in heap at address " + addr;
        LocalVariableRoot localVariableRoot = new LocalVariableRoot(addr, threadId, classId, methodId, slot);
        heap.addRoot(localVariableRoot);

        ThreadInfo currentThread = heap.threadsById.get(threadId);

        if (currentThread != null) {
            assert currentThread.isAlive();

            if (currentThread.isNewFrame(classId, methodId)) {
                currentThread.addStackframe(classId, methodId);
            }

            localVariableRoot.setCallStackIndex(currentThread.getStackDepth() - 1);
        }
    }

    protected void doParseGCVMInternalThreadDataRootPtr(long addr, long threadId) throws TraceException {
        assert addr < 0 || heap.getObjectInBack(addr) != null : "Root pointed object not found in heap at address " + addr;
        heap.addRoot(new VMInternalThreadDataRoot(addr, threadId));
    }

    protected void doParseGCCodeBlobRootPtr(long addr, int classId, int methodId) throws TraceException {
        assert addr < 0 || heap.getObjectInBack(addr) != null : "Root pointed object not found in heap at address " + addr;
        heap.addRoot(new CodeBlobRoot(addr, classId, methodId));
    }

    protected void doParseGCJNILocalRootPtr(long addr, long threadId) throws TraceException {
        assert addr < 0 || heap.getObjectInBack(addr) != null : "Root pointed object not found in heap at address " + addr;
        heap.addRoot(new JNILocalRoot(addr, threadId));
    }

    protected void doParseGCJNIGlobalRootPtr(long addr, boolean weak) throws TraceException {
        assert addr < 0 || heap.getObjectInBack(addr) != null : "Root pointed object not found in heap at address " + addr;
        heap.addRoot(new JNIGlobalRoot(addr, weak));
    }

    protected void doParseGCOtherRootPtr(long addr, RootPtr.RootType rootType) throws TraceException {
        assert addr < 0 || heap.getObjectInBack(addr) != null : "Root pointed object not found in heap at address " + addr;
        heap.addRoot(new OtherRoot(addr, rootType));
    }

    protected void doParseGCDebugRootPtr(long addr, String vmCall) throws TraceException {
        assert addr < 0 || heap.getObjectInBack(addr) != null : "Root pointed object not found in heap at address " + addr;
        heap.addRoot(new DebugRoot(addr, vmCall));
    }

    public void doParseThreadAlive(int header, long id, String internalThreadName, String name) throws TraceException {
        ThreadInfo ti = new ThreadInfo(id, name, internalThreadName, true);
        if (heap.threadsById.containsKey(id)) {
            if (id == -1) {
                while (heap.threadsById.containsKey(id)) {
                    id--;
                }
            } else {
                throw new TraceException("There should not be multiple threads with the same positive id");
            }
        }
        heap.threadsById.put(id, ti);
        heap.threadsByInternalName.put(internalThreadName, ti);
    }

    public void doParseThreadDead(long id) {
        ThreadInfo dyingThread = heap.threadsById.get(id);
        if (dyingThread == null) {
            // just in case
            ThreadInfo ti = new ThreadInfo(id, "some dead thread", "some dead thread", false);
            heap.threadsById.put(id, ti);
        } else {
            dyingThread.setAlive(false);
        }
    }

    public void addTag(String tagText) {
        heap.addTag(tagText);
    }
}
