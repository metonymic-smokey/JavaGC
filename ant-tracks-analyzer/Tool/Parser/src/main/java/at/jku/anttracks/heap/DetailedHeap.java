
package at.jku.anttracks.heap;

import at.jku.anttracks.heap.iteration.FakeHeapSpliterator;
import at.jku.anttracks.heap.iteration.HeapSpliterator;
import at.jku.anttracks.heap.iteration.IteratingObjectStream;
import at.jku.anttracks.heap.iteration.SpliteratorObjectStream;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.heap.labs.Lab;
import at.jku.anttracks.heap.objects.ObjectInfoCache;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.heap.space.SpaceInfo;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.*;
import at.jku.anttracks.parser.heap.ThreadInfo;
import at.jku.anttracks.parser.heap.pointer.IncompletePointerInfo;
import at.jku.anttracks.parser.heap.pointer.PointerHandling;
import at.jku.anttracks.util.ProgressListener;
import at.jku.anttracks.util.TraceException;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

public class DetailedHeap implements Heap, FrontBackObjectAccess {
    public static final ParserGCInfo INITIAL_PARSER_GC_INFO = new ParserGCInfo(EventType.GC_END,
                                                                               GarbageCollectionType.INITIALIZE,
                                                                               GarbageCollectionCauseKt.getARTIFICIAL_GARBAGE_COLLECTION(),
                                                                               (short) 0,
                                                                               0L,
                                                                               false);

    private final Symbols symbols;

    private final ObjectInfoCache cache;
    private Space[] spaces;
    private final Map<Short, short[]> collectedSpaces;
    private final Map<Short, GarbageCollectionCause> causes;

    private ParserGCInfo gc;
    private ParsingInfo parsingInfo;
    private final CopyOnWriteArrayList<HeapListener> listeners;

    public final Long2ObjectOpenHashMap<List<RootPtr>> rootPtrs;
    public final ConcurrentHashMap<Long, ThreadInfo> threadsById;
    public final ConcurrentHashMap<String, ThreadInfo> threadsByInternalName;

    public final CopyOnWriteArrayList<IncompletePointerInfo> notYetMatchedMultiThreadedPtrEventsToHandleAtGCEnd;
    public final ConcurrentHashMap<Long, IncompletePointerInfo> multiThreadedPtrEventsToHandleAtGCEnd;
    // public final Long2LongOpenHashMap movesSinceLastGCStart = new Long2LongOpenHashMap();

    public final CopyOnWriteArrayList<Long> addressesThatMayHaveInvalidPointersDueToNullingOnNonDirtyObjects = new CopyOnWriteArrayList<>();

    // root pointers must be written before GC start thus we need to signal when the current root pointer DS should be cleared
    // if they would be written after GC start, inspecting the heap at a GC start would not yield up to date root info
    private boolean clearRootPointers = false;

    private Logger LOGGER = Logger.getLogger(DetailedHeap.class.getSimpleName());
    private List<Tag> tags;

    public DetailedHeap(Symbols symbols, ParsingInfo parsingInfo) {
        this(symbols,
             new ObjectInfoCache(),
             new Space[0],
             INITIAL_PARSER_GC_INFO,
             new Long2ObjectOpenHashMap<>(),
             new ConcurrentHashMap<>(),
             parsingInfo);
    }

    public DetailedHeap(Symbols symbols,
                        ObjectInfoCache prototypes,
                        Space[] spaces,
                        ParserGCInfo currentGC,
                        Long2ObjectOpenHashMap<List<RootPtr>> rootPtrs,
                        ConcurrentHashMap<Long, ThreadInfo> threads,
                        ParsingInfo parsingInfo) {
        this.symbols = symbols;
        cache = prototypes;
        this.spaces = spaces.clone();
        collectedSpaces = new HashMap<>(7, 0.8f);
        causes = new HashMap<>(7, 0.8f);
        gc = currentGC;
        listeners = new CopyOnWriteArrayList<>();
        this.rootPtrs = rootPtrs;
        this.threadsById = threads;
        this.threadsByInternalName = new ConcurrentHashMap<>();
        for (ThreadInfo ti : threadsById.values()) {
            this.threadsByInternalName.put(ti.internalThreadName, ti);
        }
        notYetMatchedMultiThreadedPtrEventsToHandleAtGCEnd = new CopyOnWriteArrayList<>();
        multiThreadedPtrEventsToHandleAtGCEnd = new ConcurrentHashMap<>();
        tags = new ArrayList<>();
        this.parsingInfo = parsingInfo;
        // movesSinceLastGCStart.defaultReturnValue(-1);
    }

    public Symbols getSymbols() {
        return symbols;
    }

    public void addListener(HeapListener listener) {
        listeners.add(listener);
    }

    public void removeListener(HeapListener listener) {
        boolean removed = listeners.remove(listener);
        if (removed) {
            HeapListener.Companion.fireClose(listener, this, parsingInfo);
        }
    }

    public void removeAllListeners() {
        for (HeapListener listener : listeners) {
            removeListener(listener);
        }
    }

    public ObjectInfoCache getCache() {
        return cache;
    }

    public void addSpace(short index, Space space) throws TraceException {
        if (index < spaces.length) {
            if (spaces[index] != null) {
                throw new TraceException("There is already a space with id " + index + "!");
            }
        } else {
            Space[] spaces = new Space[index + 1];
            System.arraycopy(this.spaces, 0, spaces, 0, this.spaces.length);
            this.spaces = spaces;
        }
        spaces[index] = space;
        space.setId(index);

        if (gc.getEventType() == EventType.GC_START) {
            // We are currently in a garbage collection phase
            space.startTransition(SpaceInfo.TransitionType.Accumulative);
        }
    }

    public void removeSpace(int index) {
        spaces[index] = null;
    }

    public ParserGCInfo getGC() {
        return gc;
    }

    /**
     * @return Returns the latest GC's ID. For allocations at the very beginning, this is 0. The very first GC that occurs has ID 1.
     */
    public short latestGCId() {
        return gc.getId();
    }

    public void startGC(ParserGCInfo info, long beforeEventPosition) throws TraceException {
        ParserGCInfo fromGC = gc;
        ParserGCInfo toGC = info;
        /*
        // Prints out every address
        Arrays.stream(getSpacesCloned()).sorted(Comparator.comparingLong(Space::getAddress)).forEach(space -> {
            Arrays.stream(space.getLabs()).sorted(Comparator.comparingLong(Lab::bottom)).forEach(lab -> {
                lab.iterate(this, space.getInfo(), null, new ObjectVisitor() {
                    @Override
                    public void visit(                              int agelong address,
                              HO obj,
                              SpaceInfo space,
                              List<? extends RootPtr> rootPtrs
                                      int age) {
                        System.out.println(String.format("%d: %s @ %s (size: %d via %s)",
                                                         address,
                                                         type,
                                                         allocationSite.callSites[0],
                                                         size,
                                                         eventType.toString()));
                    }
                }, ObjectVisitor.Settings.ALL_INFOS);
            });
        });
        */

        if (info.getType().isFull() && collectedSpaces.size() != 0) {
            throw new TraceException("Can't start a full GC if another GC is still active!");
        }
        if (collectedSpaces.put(info.getId(), new short[0]) != null) {
            throw new TraceException("GC already active");
        }

        HeapListener.Companion.firePhaseChanging(listeners,
                                                 this,
                                                 fromGC,
                                                 toGC,
                                                 false,
                                                 beforeEventPosition,
                                                 parsingInfo,
                                                 parsingInfo.isWithinParseTimeWindow(info.getTime()));

        // Set all spaces to ReplaceAll if MAJOR_GC (every object that is not moved dies)
        // Set all spaces to Accumulative if MINOR_GC (keep non-moved objects if not overwritten
        // --> Following GC_INFO events sets collected spaces to ReplaceAll (i.e., spaces that must be evacuated) ...
        // See markSpaceForCollection()
        SpaceInfo.TransitionType type = info.getType().isFull() ? SpaceInfo.TransitionType.ReplaceAll : SpaceInfo.TransitionType.Accumulative;
        for (Space space : spaces) {
            if (space != null) {
                if (space.getType() == null) {
                    // Empty region
                    space.startTransition(SpaceInfo.TransitionType.ReplaceAll);
                    space.commitTransition();
                }
                if (!space.isBeingCollected()) {
                    space.startTransition(type);
                }
            }
        }

        clearRootPointers = true;
        gc = toGC;
        GarbageCollectionCause replaced = causes.put(info.getId(), info.getCause());
        if (replaced != null) {
            throw new TraceException("Cause already registered under this GCID");
        }
        // movesSinceLastGCStart.clear();

        HeapListener.Companion.firePhaseChanged(listeners,
                                                this,
                                                fromGC,
                                                toGC,
                                                false,
                                                beforeEventPosition,
                                                parsingInfo,
                                                parsingInfo.isWithinParseTimeWindow(info.getTime()));
    }

    public void markSpaceForCollection(short spaceID, short gcId) throws TraceException {
        errorOnAlreadyCollectedSpace(spaceID);
        short[] spaceIDs = collectedSpaces.get(gcId);
        if (spaceIDs == null) {
            throw new TraceException("Space=\"" + gcId + "\" can not be registered for GCID=\"" + gcId + "\", as the GCID is not registered! (gc_start missing?)");
        }
        short[] newSpaceIDs = new short[spaceIDs.length + 1];
        System.arraycopy(spaceIDs, 0, newSpaceIDs, 0, spaceIDs.length);
        newSpaceIDs[spaceIDs.length] = spaceID;
        collectedSpaces.put(gcId, newSpaceIDs);

        // This event tells us that a garbage collection happens in this space and that all living objects have to be moved
        // Objects that don't have get moved are assumed dead (i.e., Transition type is ReplaceAll)
        Space space = spaces[spaceID];
        if (space.getTransitionType() == SpaceInfo.TransitionType.Accumulative) {
            space.commitTransition();
        }
        space.startTransition(SpaceInfo.TransitionType.ReplaceAll);
    }

    public Space failGC(int spaceID) throws TraceException {
        LOGGER.info("Fail GC - " + spaces[spaceID].toShortString());
        Space space = spaces[spaceID];
        space.rollbackTransition();
        return space;
    }

    public void stopGC(ParserGCInfo info, boolean failed, long afterEventPosition) throws TraceException {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Heap.StopGC");
        //ApplicationStatistics.Measurement mInner = ApplicationStatistics.getInstance().createMeasurement("handlePointers");
        PointerHandling.handlePtrsOnGCEnd(this, failed);
        //mInner.end();

        ParserGCInfo fromGC = gc;
        ParserGCInfo toGC = info;

        if (gc.getEventType() == EventType.GC_END) {
            if (collectedSpaces.size() == 0) {
                throw new TraceException("no GC active");
            } else {
                throw new TraceException("internal state inconsistency");
            }
        }
        final short[] spaceIDs = collectedSpaces.remove(info.getId());
        GarbageCollectionCause replaced = causes.remove(info.getId());
        if (spaceIDs == null) {
            throw new TraceException(String.format("No GC active for ID=%d!", info.getId()));
        }

        if (replaced == null) {
            throw new TraceException(String.format("No cause found for GCID=%d!", info.getId()));
        }

        HeapListener.Companion.firePhaseChanging(listeners,
                                                 this,
                                                 fromGC,
                                                 toGC,
                                                 failed,
                                                 afterEventPosition,
                                                 parsingInfo,
                                                 parsingInfo.isWithinParseTimeWindow(info.getTime()));
        boolean anyGCActive = !collectedSpaces.isEmpty();
        // GarbageCollectionType gcType = gc;
        // gc = (anyGCActive) ? GarbageCollectionType.MINOR : GarbageCollectionType.MUTATOR;
        // TODO This change breaks CMS for sure!
        gc = toGC;

        // spaces handled by this GC
        for (int sID : spaceIDs) {
            Space space = spaces[sID];
            if (space != null) {
                if (space.getTransitionType() != SpaceInfo.TransitionType.None) {
                    space.commitTransition();
                    if (anyGCActive) {
                        space.startTransition(SpaceInfo.TransitionType.Accumulative);
                        // may be used in other GC
                    }
                }
                if (space.getType() == null) {
                    space.clear();
                }
            }
        }
        // spaces handled by other GCs (and current GC)
        if (!anyGCActive) {
            for (Space s : spaces) {
                if (s != null) {
                    if (s.getTransitionType() != SpaceInfo.TransitionType.None) {
                        s.commitTransition();
                    }
                    if (s.getType() == null) {
                        s.clear();
                    }
                }
            }
        } else {
            for (Space s : spaces) {
                if (s != null && s.isBeingCollected()) {
                    throw new TraceException("No concurrent GCs!");
                }
            }
        }

        // Empty labs can occur if, for example, a PLAB has been allocated, but the parser does not add a moved object to the PLAB but instead to another already existing
        // adjacent LAB due to faster insert
        for (Space space : spaces) {
            if (space != null) {
                space.removeEmptyLabs();
            }
        }

        // Pointers have to be validated before phase change.
        // Otherwise listerns could be notified with a heap that is in an inconsistant state
        if (TraceParser.CONSISTENCY_CHECK) {
            //mInner = ApplicationStatistics.getInstance().createMeasurement("heapValidation");
            validate(symbols.isHeapFragmented);
            //mInner.end();
            //mInner = ApplicationStatistics.getInstance().createMeasurement("pointerValidation");
            PointerHandling.validateAllPointers(this);
            //mInner.end();
        }

        // LOGGER.info(String.format("ObjectInfo cache size: %,d", cache.size()));
        if (gc.getType().isFull()) {
            shrinkObjectInfoCache();
        }

        HeapListener.Companion.firePhaseChanged(listeners,
                                                this,
                                                fromGC,
                                                toGC,
                                                failed,
                                                afterEventPosition,
                                                parsingInfo,
                                                parsingInfo.isWithinParseTimeWindow(info.getTime()));

        ObjectIterator<Long2ObjectMap.Entry<List<RootPtr>>> iter = rootPtrs.long2ObjectEntrySet().fastIterator();
        while (iter.hasNext()) {
            Long2ObjectMap.Entry<List<RootPtr>> rootPtrEntry = iter.next();
            if (rootPtrEntry.getLongKey() >= 0) {
                try {
                    // Throws an exception if object does not exist
                    this.getObjectInFront(rootPtrEntry.getLongKey());
                } catch (Exception ex) {
                    // Object was pointed by a weak JNI global root and has died -> Also remove root
                    // Or: Object was pointed by class loader internal stuff, seems like they can die too...
                    // Or still something else... oh man, just remove it, have a look at this in the future
                    // TODO
                    iter.remove();
                }
            }
        }

        /*
        // Prints out every address
        Arrays.stream(getSpacesCloned()).sorted(Comparator.comparingLong(Space::getAddress)).forEach(space -> {
            Arrays.stream(space.getLabs()).sorted(Comparator.comparingLong(Lab::bottom)).forEach(lab -> {
                lab.iterate(this, space.getInfo(), null, new ObjectVisitor() {
                    @Override
                    public void visit(long address,
                                      ObjectInfo objectInfo,
                                      SpaceInfo space,
                                      AllocatedType type,
                                      int size,
                                      boolean isArray,
                                      int arrayLength,
                                      AllocationSite allocationSite,
                                      long[] pointedFrom,
                                      long[] pointsTo,
                                      EventType eventType,
                                      List<RootPtr> rootPtrs) {
                        System.out.println(address + " -> " + objectInfo);
                    }
                }, ObjectVisitor.Settings.ALL_INFOS);
            });
        });
        */

        //m.end();
    }

    public void shrinkObjectInfoCache() {

        // TODO currently object info cache shrinking is disabled to check if this speeds up performance
        /*
        cache.clear();
        ObjectInfo proto = new ObjectInfo();

        toObjectStream().forEach(
                (address, object, space, rootPtrs) -> cache.get(object.getInfo(), proto, symbols),
                ObjectVisitor.Settings.Companion.getNO_INFOS());
        */
    }

    public void insertFillers(ThreadLocalHeap tlh) throws TraceException {
        for (Space space : tlh.getRetiredLabs().keySet()) {
            space.insertFillers(tlh.getRetiredLabs().get(space));
        }
    }

    public void assignLabsIncorrectlyAssumedToBeFillers(boolean allowUnusedFillers) throws TraceException {
        for (Space space : spaces) {
            if (space != null) {
                space.assignLabsIncorrectlyTreatedAsFillers(allowUnusedFillers);
            }
        }
    }

    @SuppressWarnings("unused")
    private void errorOnMajorGC(GarbageCollectionType type) throws TraceException {
        if (type == GarbageCollectionType.MAJOR || type == GarbageCollectionType.MAJOR_SYNC) {
            throw new TraceException("#" + type + ", GC MOVE Event to SURVIVOR_TO is only possible during a Minor GC. SURVIVOR_TO should be empty here. ");
        }

    }

    public boolean validate(boolean allowFragmentation) throws TraceException {
        for (Space space : spaces) {
            if (space != null) {
                space.validate(allowFragmentation);
            }
        }
        return true;
    }

    @Override
    public String toString() {
        StringBuilder result = new StringBuilder("Heap (" + Arrays.stream(spaces).filter(Objects::nonNull).count() + " spaces)\n");
        Arrays.stream(spaces).filter(Objects::nonNull).sorted(Comparator.comparingLong(Space::getAddress)).forEach(s -> result.append(s.toShortString() + "\n"));
        // Delete last new line
        result.deleteCharAt(result.length() - 1);
        return result.toString();
    }

    @Override
    public DetailedHeap clone() {
        return new DetailedHeap(symbols,
                                cache,
                                Stream.of(spaces).filter(Objects::nonNull).map(Space::clone).toArray(Space[]::new),
                                gc,
                                new Long2ObjectOpenHashMap<>(rootPtrs),
                                new ConcurrentHashMap<>(threadsById),
                                parsingInfo);
    }

    private void errorOnAlreadyCollectedSpace(short idx) throws TraceException {
        for (short[] arr : collectedSpaces.values()) {
            if (contains(arr, idx)) {
                throw new TraceException("Space#" + idx + "(" + spaces[idx].getType() + ") is already beeing collected!");
            }
        }
    }

    private static boolean contains(short[] array, short value) {
        for (int i : array) {
            if (i == value) {
                return true;
            }
        }
        return false;
    }

    /*
     * Returns a sequential stream
     */
    public Stream<AddressHO> stream() {
        return StreamSupport.stream(new HeapSpliterator(this, 0, 0, lastValidSpace(true) + 1, spaces[lastValidSpace(true)].getLabCount(), true, false), false);
    }

    /*
     * Returns a parallel stream
     */
    public Stream<AddressHO> parallelStream() {
        return StreamSupport.stream(new HeapSpliterator(this, 0, 0, lastValidSpace(true) + 1, spaces[lastValidSpace(true)].getLabCount(), true, false), true);
    }

    public GarbageCollectionCause getGarbageCollectionCause(int gcID) {
        return causes.get(gcID);
    }

    public long getLabCount() {
        long count = 0;
        for (Space s : spaces) {
            if (s != null) {
                count += s.getLabs().length;
            }
        }
        return count;
    }

    public long getObjectCount() {
        long count = 0;
        for (Space s : spaces) {
            if (s != null && s.getType() != null && s.getMode() != null) {
                for (Lab l : s.getLabs()) {
                    if (l != null) {
                        count += l.getObjectCount();
                    }
                }
            }
        }
        return count;
    }

    public long getMinimumAddress() {
        long min = Long.MAX_VALUE;
        for (Space s : spaces) {
            if (s != null && s.getType() != null) {
                long addr = s.getAddress();
                if (addr < min) {
                    min = addr;
                }
            }
        }
        return min;
    }

    private int lastValidSpace(boolean current) {
        int lastValidSpace = spaces.length - 1;
        while (lastValidSpace > 0 && (spaces[lastValidSpace] == null || spaces[lastValidSpace].getLabCount(current) == 0)) {
            // Search for last valid space (not null and contains at least one lab)
            lastValidSpace--;
        }
        return lastValidSpace;
    }

    public ObjectStream toSpliteratorObjectStream(long multithreadingThreshold) {
        return toSpliteratorObjectStream(true, multithreadingThreshold);
    }

    public ObjectStream toSpliteratorObjectStream(boolean current, long multithreadingThreshold) {
        return new SpliteratorObjectStream(this,
                                           new FakeHeapSpliterator(this,
                                                                   0,
                                                                   0,
                                                                   lastValidSpace(current) + 1,
                                                                   spaces[lastValidSpace(current)].getLabCount(current),
                                                                   current,
                                                                   false),
                                           multithreadingThreshold);
    }

    public ObjectStream toObjectStream() {
        return toObjectStream(true);
    }

    public ObjectStream toObjectStream(boolean current) {
        return new IteratingObjectStream(this, current);
    }

    public void complete() throws TraceException {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("completing heap");

        //ApplicationStatistics.Measurement m1 = ApplicationStatistics.getInstance().createMeasurement("completing heap: setting from pointers");
        setFromPointers();
        //m1.end();

        //ApplicationStatistics.Measurement m2 = ApplicationStatistics.getInstance().createMeasurement("completing heap: reducing size");
        reduceSize();
        //m2.end();

        resolveRootPtrs();

        // resolve thread callstacks
        threadsById.values().forEach(t -> {
            try {
                t.resolveCallStack(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        //m.end();
    }

    public void resolveRootPtrs() {
        //ApplicationStatistics.Measurement m5 = ApplicationStatistics.getInstance().createMeasurement("completing heap: resolve roots");
        // resolve roots

        // shrink root pointer ds by removing unnecessary elements
        ObjectIterator<Long2ObjectMap.Entry<List<RootPtr>>> entryIterator = rootPtrs.long2ObjectEntrySet().fastIterator();
        while (entryIterator.hasNext()) {
            Map.Entry<Long, List<RootPtr>> entry = entryIterator.next();
            Iterator<RootPtr> rootIterator = entry.getValue().iterator();
            while (rootIterator.hasNext()) {
                RootPtr rootPtr = rootIterator.next();

                // remove dead strings
                if (rootPtr.getRootType() == RootPtr.RootType.INTERNED_STRING) {
                    // must exist
                    AddressHO oi = null;
                    try {
                        oi = this.getObject(rootPtr.getAddr());

                        if (oi == null || !oi.getType().internalName.equals("Ljava/lang/String;")) {
                            rootIterator.remove();
                            //ApplicationStatistics.getInstance().inc("Removing dead string roots");
                        }
                    } catch (TraceException e) {
                        rootIterator.remove();
                        //ApplicationStatistics.getInstance().inc("Removing dead string roots");
                    }
                }

                // remove thread internal roots
                if (rootPtr.getRootType() == RootPtr.RootType.VM_INTERNAL_THREAD_DATA_ROOT || rootPtr.getRootType() == RootPtr.RootType.CODE_BLOB_ROOT) {
                    rootIterator.remove();
                }
            }

            // remove
            if (entry.getValue().isEmpty()) {
                entryIterator.remove();
            }
        }

        // actual resolving
        rootPtrs.values().stream().flatMap(Collection::stream).forEach(rootPtr -> {
            try {
                rootPtr.resolve(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        //m5.end();
    }

    private void reduceSize() {
        for (Space space : spaces) {
            if (space != null) {
                space.reduceSize();
            }
        }
    }

    private void setFromPointers() throws TraceException {
        if (symbols.expectPointers) {
            if (!multiThreadedPtrEventsToHandleAtGCEnd.isEmpty()) {
                throw new TraceException("All multithreaded pointer events must have been handled before from pointers get calculated!");
            }

            /*
            LOGGER.info("Set from-pointers");
            HashMap<Long, Counter> fromPointerCounters = new HashMap<>();

            LOGGER.info("Counting from-pointers");
            ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Counting from-pointers");
            ObjectVisitor fromPointerCounterClosure =
                    (address, objectInfo, space, type, size, isArray, arrayLength, allocationSite, pointedFrom, pointsTo, eventType, rootPtrs) -> {
                        for (int i = 0; pointsTo != null && i < pointsTo.length; i++) {
                            if (pointsTo[i] < 0) {
                                // Null pointer
                                continue;
                            }
                            Counter ptrCounter = fromPointerCounters.get(pointsTo[i]);
                            if (ptrCounter == null) {
                                fromPointerCounters.put(pointsTo[i], new Counter());
                                ptrCounter = fromPointerCounters.get(pointsTo[i]);
                            }
                            ptrCounter.inc();
                        }
                    };
            toObjectStream().forEach(fromPointerCounterClosure);
            m.end();

            LOGGER.info("Creating from-pointer arrays");
            m = ApplicationStatistics.getInstance().createMeasurement("Creating from-pointer arrays");
            HashMap<Long, long[]> fromPointers = new HashMap<>();
            Iterator<Map.Entry<Long, Counter>> fromPointerCounterIterator = fromPointerCounters.entrySet().iterator();
            while (fromPointerCounterIterator.hasNext()) {
                Map.Entry<Long, Counter> entry = fromPointerCounterIterator.next();
                if (entry.getValue().get() > 0) {
                    long[] from = new long[(int) entry.getValue().get()];
                    fromPointers.put(entry.getKey(), from);
                }
                fromPointerCounterIterator.remove();
            }
            m.end();

            LOGGER.info("Creating from-pointer current index counter");
            m = ApplicationStatistics.getInstance().createMeasurement("Creating from-pointer current index counter");
            HashMap<Long, Counter> fromPointerCurCounters = new HashMap<>();
            fromPointers.keySet().forEach(addr -> fromPointerCurCounters.put(addr, new Counter()));
            m.end();

            LOGGER.info("Set from-pointer addresses in arrays");
            m = ApplicationStatistics.getInstance().createMeasurement("Set from-pointer addresses in arrays");
            ObjectVisitor fromPointerCombiner =
                    (address, objectInfo, space, type, size, isArray, arrayLength, allocationSite, pointedFrom, pointsTo, eventType, rootPtrs) -> {
                        for (int i = 0; pointsTo != null && i < pointsTo.length; i++) {
                            if (pointsTo[i] < 0) {
                                // Null pointer
                                continue;
                            }
                            long[] ptrs = fromPointers.get(pointsTo[i]);
                            Counter curIndex = fromPointerCurCounters.get(pointsTo[i]);
                            ptrs[(int) curIndex.get()] = address;
                            curIndex.inc();
                            if (curIndex.get() == ptrs.length) {
                                fromPointerCurCounters.remove(pointsTo[i]);
                            }
                        }
                    };
            toObjectStream().forEach(fromPointerCombiner);
            m.end();

            LOGGER.info("Set from-pointer array in object");
            m = ApplicationStatistics.getInstance().createMeasurement("Set from-pointer array in object");
            Iterator<Map.Entry<Long, long[]>> pointerIterator = fromPointers.entrySet().iterator();
            while (pointerIterator.hasNext()) {
                Map.Entry<Long, long[]> entry = pointerIterator.next();
                getLabInFront(entry.getKey()).setFromPointers(entry.getKey(), entry.getValue());
                pointerIterator.remove();
            }
            m.end();
            LOGGER.info("Calculating from-pointers finished");
            */
        }
    }

    public void interruptGC(short id, long address) throws TraceException {
        Space space = getSpace(address);
        assert space.isBeingCollected();

        boolean isValidInterrupt = false;
        short[] spaceIDs = collectedSpaces.get(id);

        for (int s : spaceIDs) {
            if (spaces[s].equals(space)) {
                isValidInterrupt = true;
            }
        }
        if (!isValidInterrupt) {
            throw new TraceException("collection of space is interrupted by different GC!");
        }

        space.commitTransition(address);
        space.startTransition(SpaceInfo.TransitionType.Accumulative);
        // check if other Space is beeing collected
        // changes on the code here should also be made to
        // Heap::continueGC(int,long)!
        long spCnt = 0;
        for (short s : collectedSpaces.get(id)) {
            if (spaces[s].isBeingCollected()) {
                spCnt++;
            }
        }

        if (0 != spCnt) {
            for (Space s : spaces) {
                if (s != null && s.isBeingCollected()) {
                    throw new TraceException(s.toShortString() + " is beeing collected. Forgot an interrupt?");
                }
            }
        }
    }

    public void continueGC(int id, long address) throws TraceException {
        Space space = getSpace(address);
        assert !space.isBeingCollected();

        boolean isValidContinue = false;
        short[] spaceIDs = collectedSpaces.get(id);

        for (int s : spaceIDs) {
            if (spaces[s].equals(space)) {
                isValidContinue = true;
            }
        }

        if (!isValidContinue) {
            throw new TraceException("collection of space is continued by different GC!");
        }

        // check if other Space is beeing collected
        // changes on the code here should also be made to
        // Heap::interruptGC(int,long)!
        long spCnt = 0;
        for (short s : collectedSpaces.get(id)) {
            if (spaces[s].isBeingCollected()) {
                spCnt++;
            }
        }

        if (0 != spCnt) {
            for (Space s : spaces) {
                if (s != null && s.isBeingCollected()) {
                    throw new TraceException(s.toShortString() + " is being collected. Forgot an interrupt?");
                }
            }
        }
        space.startTransition(SpaceInfo.TransitionType.ReplaceAll, address);
    }

    public String getLabSizes() {

        StringBuilder sb = new StringBuilder(";"); // to later insert avg at the beginning (easier viewing)
        long objCount = 0;
        int labCount = 0;

        for (Space s : getSpacesCloned()) {
            if (s == null) {
                continue;
            }
            labCount += s.getLabCount();
            for (Lab l : s.getLabs()) {
                sb.append(l.getObjectCount());
                sb.append(";");
                objCount += l.getObjectCount();
            }
        }

        // write avg obj count per lab
        sb.insert(0, (labCount > 0 ? objCount / labCount : 0));
        sb.append("\n");

        return sb.toString();
    }

    public void addRoot(RootPtr newRoot) throws TraceException {
        // TODO: Learn how interned strings exactly work and if they have to be considered as strong roots
        if (newRoot.getRootType() == RootPtr.RootType.INTERNED_STRING) {
            return;
        }
        // IMPORTANT: root pointers must not be parsed simultaneously because heap root ds is NOT threadsafe
        if (clearRootPointers) {
            rootPtrs.clear();
            threadsById.values().forEach(ThreadInfo::clearCallstack);
            clearRootPointers = false;
        }

        if (TraceParser.CONSISTENCY_CHECK) {
            try {
                try {
                    newRoot.resolve(this);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
                if (newRoot.getAddr() != -1) {
                    if (this.getObject(newRoot.getAddr()) == null) {
                        throw new TraceException(String.format("Root pointed address %,d without object @ GC Start:\n%s", newRoot.getAddr(), newRoot));
                    }
                }
            } catch (TraceException e) {
                throw new TraceException(String.format("Root pointed address %,d without object @ GC Start:\n%s", newRoot.getAddr(), newRoot));
            }
        }

        List<RootPtr> existingRoots = rootPtrs.get(newRoot.getAddr());

        if (existingRoots != null) {
            // TODO mw: why do we need a duplicate check anyway? there should not be duplicates, and the contains call slows down parsing...
            // if (!existingRoots.contains(newRoot)) {
                existingRoots.add(newRoot);
                // TODO mw: I have no idea anymore why we added this sorting...
                //existingRoots.sort(Comparator.comparing(RootPtr::getRootType));
            //}
            existingRoots.add(newRoot);
        } else {
            existingRoots = new ArrayList<>();
            existingRoots.add(newRoot);
            rootPtrs.put(newRoot.getAddr(), existingRoots);
        }
    }

    // -------------------------------------
    // ------ Space access -----------------
    // -------------------------------------

    public Space[] getSpacesUncloned() {
        return spaces;
    }

    public Space[] getSpacesCloned() {
        return spaces.clone();
    }

    public Space getSpace(int id) {
        return 0 <= id && id < spaces.length ? spaces[id] : null;
    }

    /**
     * Used to find the corresponding space to a certain address.
     * Uses binary search instead of linear search for speed-up
     *
     * @param addr The address for which the corresponding space should be found
     * @return The found space, or null if not found
     */
    public Space getSpace(long addr) {
        Space result = getSpaceBinarySearch(addr);
        assert result != null || getSpaceLinearSearch(addr) == null : "binary search unable to find space, linear search is. what is " + "going on?";
        return result;
    }

    /**
     * Used to find the corresponding space to a certain address.
     * Uses binary search instead of linear search for speed-up
     *
     * @param addr The address for which the corresponding space should be found
     * @return The found space, or null if not found
     */
    private Space getSpaceBinarySearch(long addr) {
        int lo = 0, hi = spaces.length - 1;
        while (lo <= hi) {
            int mi = (lo + hi) / 2;
            Space space = getSpace(mi);

            while (space == null && mi < hi) {
                space = getSpace(++mi);
            }
            while (space == null && mi > lo) {
                space = getSpace(--mi);
            }
            if (space == null) {
                break;
            }

            if (space.contains(addr)) {
                return space;
            } else if (addr < space.getAddress()) {
                hi = mi - 1;
            } else if (space.getAddress() + space.getLength() <= addr) {
                lo = mi + 1;
            }
        }
        return null;
    }

    /**
     * DO NOT USE ANYMORE!
     * It is only used in assertion mode if the binary search does not find a certain space to check if also the linear search does not find
     * it, i.e., to verfy that the binary search is working correct.
     *
     * @param addr The address for which the corresponding space should be found
     * @return The found space, or null if not found
     */
    @Deprecated
    private Space getSpaceLinearSearch(long addr) {
        for (Space space : spaces) {
            if (space != null && space.contains(addr)) {
                return space;
            }
        }
        return null;
    }

    // -------------------------------------
    // ------ Lab access -------------------
    // -------------------------------------

    public Lab getLab(long objAddr, boolean inFront) throws TraceException {
        return inFront ? getLabInFront(objAddr) : getLabInBack(objAddr);
    }

    public Lab getCurrentLab(long objAddr) throws TraceException {
        Space space = getSpace(objAddr);
        if (space == null) {
            throw new TraceException(String.format("No space found containing address %d", objAddr));
        }
        Lab lab = space.getCurrentLab(objAddr);
        if (lab == null) {
            throw new TraceException(String.format("No lab found containing address %,d in space %s", objAddr, space));
        }
        return lab;
    }

    public Lab getLabInFront(long objAddr) throws TraceException {
        Space space = getSpace(objAddr);
        if (space == null) {
            throw new TraceException(String.format("No space found containing address %d", objAddr));
        }
        Lab lab = space.getLab(objAddr, true);
        if (lab == null) {
            throw new TraceException(String.format("No lab found containing address %,d in space %s", objAddr, space));
        }
        return lab;
    }

    public Lab getLabInBack(long objAddr) throws TraceException {
        Space space = getSpace(objAddr);
        if (space == null) {
            throw new TraceException(String.format("No space found containing address %d", objAddr));
        }
        Lab lab = space.getLab(objAddr, false);
        if (lab == null) {
            throw new TraceException(String.format("No lab found containing address %,d in space %s", objAddr, space));
        }
        return lab;
    }

    public IndexBasedHeap toIndexBasedHeap() {
        return toIndexBasedHeap(true, null);
    }

    public IndexBasedHeap toIndexBasedHeap(ProgressListener progressListener) {
        return toIndexBasedHeap(true, progressListener);
    }

    public IndexBasedHeap toIndexBasedHeap(boolean initDataStructures, ProgressListener progressListener) {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("build fast heap");
        LOGGER.info("create heap state representation");
        // build fast heap
        IndexBasedHeap fastHeap = IndexBasedHeapFactory.INSTANCE.create(this, initDataStructures, progressListener);
        //m.end();
        LOGGER.info("heap state representation created");
        return fastHeap;
    }

    public void addTag(String tagText) {
        this.tags.add(new Tag(gc,
                              new GarbageCollectionLookup(gc.getEventType(), gc.getType(), getGarbageCollectionCause(gc.getId()), gc.getId()),
                              tagText));
    }

    public List<Tag> getTags() {
        return tags;
    }

    @Override
    public AddressHO getObjectInFront(long objAddr) throws TraceException {
        return getLabInFront(objAddr).getObject(objAddr);
    }

    @Override
    public AddressHO getObjectInBack(long objAddr) throws TraceException {
        return getLabInBack(objAddr).getObject(objAddr);
    }

    @Override
    public AddressHO getObject(long objAddr) throws TraceException {
        return getCurrentLab(objAddr).getObject(objAddr);
    }

    public String getExternalThreadName(String internalThreadName) {
        ThreadInfo ti = threadsByInternalName.get(internalThreadName);
        if (ti == null) {
            return "unknown thread name";
        } else {
            return ti.threadName;
        }
    }

    public ParsingInfo getParsingInfo() {
        return parsingInfo;
    }
}