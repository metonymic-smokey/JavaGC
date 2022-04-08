package at.jku.anttracks.heap;

import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.nodes.ListGroupingNode;
import at.jku.anttracks.classification.trees.ListClassificationTree;
import at.jku.anttracks.graph.Graph;
import at.jku.anttracks.heap.datastructures.dsl.DSLDataStructure;
import at.jku.anttracks.heap.datastructures.dsl.DataStructureUtil;
import at.jku.anttracks.heap.labs.IndexHeapObject;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.objects.ObjectInfoCache;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.heap.space.SpaceInfo;
import at.jku.anttracks.heap.space.SpaceMode;
import at.jku.anttracks.heap.space.SpaceType;
import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.heap.symbols.AllocationSite;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.parser.TraceSlaveParser;
import at.jku.anttracks.parser.heap.ThreadInfo;
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler;
import at.jku.anttracks.util.*;
import javafx.beans.property.BooleanProperty;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public abstract class IndexBasedHeap implements Heap {
    public ObjectInfoCache objectInfoCache;

    public static final int NULL_INDEX = (int) TraceSlaveParser.NULL_PTR;
    public static final int SUPER_ROOT_INDEX = -2;

    protected int objectCount = 0;
    protected int byteCount;

    protected List<RootPtr> rootPtrList;
    protected HashMap<Integer, List<RootPtr>> rootPtrs;
    protected Map<RootPtr.RootType, BitSet> directlyReachableFromRootType;
    protected Map<RootPtr.RootType, BitSet> indirectlyReachableFromRootType;

    public Map<Long, ThreadInfo> threadsById;
    public Map<String, ThreadInfo> threadsByInternalName;
    protected Symbols symbols;

    protected FastDominators dominators;

    protected DSLDataStructure[] dataStructures;
    protected Map<Integer, DSLDataStructure> dataStructuresByHeadObjectIndexMap;
    protected int[][] dataStructureComposition;
    protected int gcNo;

    public IndexBasedHeap(HprofToFastHeapHandler handler, boolean initDataStructures) {
        initialize(handler.getSymbols(),
                   handler.getAddr().length,
                   new ObjectInfoCache(),
                   handler.getAddr().length,
                   new HashMap<>(),
                   new HashMap<>()
        );

        SpaceInfo fakeSpace = new SpaceInfo("no space information");
        fakeSpace.setAddress(0);
        fakeSpace.setLength(Arrays.stream(handler.getAddr()).max().orElse(0) + 1);
        fakeSpace.setType(SpaceType.UNDEFINED);
        fakeSpace.setMode(SpaceMode.NORMAL);
        SpaceInfo[] spaceInfos = new SpaceInfo[]{fakeSpace};
        long[] spaceStartAddresses = new long[]{0};

        IndexHeapObject[] objectsArray = new IndexHeapObject[handler.getObjectInfos().length];
        for (int i = 0; i < handler.getObjectInfos().length; i++) {
            objectsArray[i] = new IndexHeapObject(handler.getAddr()[i],
                                                  i,
                                                  handler.getObjectInfos()[i]
            );
        }

        for (int i = 0; i < handler.getObjectInfos().length; i++) {
            objectsArray[i].pointsToIndices = handler.getToPtrs()[i];
            objectsArray[i].pointsTo = Arrays.stream(handler.getToPtrs()[i]).mapToObj(ptrI -> ptrI == NULL_INDEX ? null : objectsArray[ptrI]).toArray(IndexHeapObject[]::new);
            objectsArray[i].pointedFromIndices = handler.getFrmPtrs()[i];
            objectsArray[i].pointedFrom = Arrays.stream(handler.getFrmPtrs()[i]).mapToObj(ptrI -> ptrI == NULL_INDEX ? null : objectsArray[ptrI]).toArray(IndexHeapObject[]::new);
        }

        // This call ensures that everything is set up right in the child classes
        store(spaceInfos,
              spaceStartAddresses,
              objectsArray);

        // translate root pointers to indices
        handler.getGcRoots().forEach((address, rootPointerList) -> {
            int rootIdx = NULL_INDEX;
            if (address != NULL_INDEX) {
                IndexHeapObject obj = toObject(address, objectsArray);
                if (obj == null) {
                    System.err.println("Could not find object at " + address + " with GC roots " + rootPointerList.stream()
                                                                                                                  .map(RootPtr::toString)
                                                                                                                  .collect(Collectors.joining(",")));
                } else {
                    rootIdx = obj.getIndex();
                }
            }
            final int finalRootIdx = rootIdx;
            rootPtrList.addAll(rootPointerList);
            rootPtrs.put(rootIdx, rootPointerList);
            rootPointerList.forEach(rp -> rp.setIdx(finalRootIdx));
        });

        finalInit(initDataStructures, null);
    }

    public IndexBasedHeap(DetailedHeap heap) {
        this(heap, true, null);
    }

    public IndexBasedHeap(DetailedHeap heap, boolean initDataStructures, ProgressListener progressListener) {
        initialize(heap.getSymbols(),
                   Math.toIntExact(heap.getObjectCount()),
                   heap.getCache().clone(),
                   heap.latestGCId(),
                   heap.threadsById,
                   heap.threadsByInternalName);

        if (progressListener != null) {
            progressListener.fire(0.0, "Setting up data");
        }

        Space[] spaces = Arrays.stream(heap.getSpacesUncloned())
                               .filter(space -> space != null && space.getType() != null && space.getMode() != null)
                               .sorted(Comparator.comparingLong(Space::getAddress))
                               .toArray(Space[]::new);

        SpaceInfo[] spaceInfos = new SpaceInfo[spaces.length];
        long[] spaceStartAdresses = new long[spaces.length];
        IndexHeapObject[] objectArray = new IndexHeapObject[objectCount];

        long[][] addressToPointers = new long[objectCount][];

        if (progressListener != null) {
            progressListener.fire(0.1, "Build space info, address info, object info");
        }
        AtomicInteger objIndex = new AtomicInteger(0);  // only for sequential stream!
        for (int spaceIndex = 0; spaceIndex < spaces.length; spaceIndex++) {
            spaceInfos[spaceIndex] = spaces[spaceIndex].getInfo();
            spaceStartAdresses[spaceIndex] = spaceInfos[spaceIndex].getAddress();

            if (progressListener != null) {
                progressListener.fire(0.1 + 0.2 / spaces.length * spaceIndex, "Build space info, address info, and object info of space #" + spaceIndex + " of " + spaces.length);
            }

            // copy heap objects
            spaces[spaceIndex].iterate(heap, (address, obj, space, rootPtrs) -> {
                int objectIndexInt = objIndex.get();
                objectArray[objectIndexInt] = new IndexHeapObject(address, objectIndexInt, obj);

                int ptrCount = Math.max(obj.getPointerCount(), 0);
                addressToPointers[objectIndexInt] = new long[ptrCount];
                for (int ptrNr = 0; ptrNr < ptrCount; ptrNr++) {
                    addressToPointers[objectIndexInt][ptrNr] = obj.getPointer(ptrNr);
                }
                objIndex.incrementAndGet();

                byteCount += obj.getSize();
            }, new ObjectVisitor.Settings(false));
        }

        if (progressListener != null) {
            progressListener.fire(0.3, "Build to-pointer info");
        }

        int[] noPointersDummyArrayIndices = new int[0];
        IndexHeapObject[] noPointersDummyArray = new IndexHeapObject[0];
        for (int i = 0; i < addressToPointers.length; i++) {
            if (addressToPointers[i] != null) {
                objectArray[i].pointsToIndices = new int[addressToPointers[i].length];
                objectArray[i].pointsTo = new IndexHeapObject[addressToPointers[i].length];
                for (int j = 0; j < addressToPointers[i].length; j++) {
                    IndexHeapObject pointedObject = addressToPointers[i][j] != TraceSlaveParser.NULL_PTR ? toObject(addressToPointers[i][j], objectArray) : null;
                    if (pointedObject != null) {
                        objectArray[i].pointsToIndices[j] = pointedObject.getIndex();
                        objectArray[i].pointsTo[j] = pointedObject;
                    } else {
                        objectArray[i].pointsToIndices[j] = NULL_INDEX;
                        objectArray[i].pointsTo[j] = null;
                    }
                }
            } else {
                // All objects that do not have pointers receive the same empty array as pointer array
                objectArray[i].pointsToIndices = noPointersDummyArrayIndices;
                objectArray[i].pointsTo = noPointersDummyArray;
            }
        }

        if (progressListener != null) {
            progressListener.fire(0.5, "Build from-pointer info");
        }

        //LOGGER.info("Count from pointers");
        Counter[] fromPtrCounter = new Counter[objectCount];
        for (IndexHeapObject pointingObject : objectArray) {
            for (IndexHeapObject pointedObject : pointingObject.pointsTo) {
                if (pointedObject != null) {
                    if (fromPtrCounter[pointedObject.getIndex()] == null) {
                        fromPtrCounter[pointedObject.getIndex()] = new Counter();
                    }
                    fromPtrCounter[pointedObject.getIndex()].inc();
                }
            }
        }
        if (progressListener != null) {
            progressListener.fire(0.6, null);
        }
        //LOGGER.info("Set from pointer arrays");
        for (int idx = 0; idx < objectCount; idx++) {
            if (fromPtrCounter[idx] != null) {
                objectArray[idx].pointedFromIndices = new int[(int) fromPtrCounter[idx].get()];
                objectArray[idx].pointedFrom = new IndexHeapObject[(int) fromPtrCounter[idx].get()];
                fromPtrCounter[idx].reset();
            } else {
                // No from pointers
                objectArray[idx].pointedFromIndices = noPointersDummyArrayIndices;
                objectArray[idx].pointedFrom = noPointersDummyArray;
            }
        }
        if (progressListener != null) {
            progressListener.fire(0.7, null);
        }
        for (int idx = 0; idx < objectCount; idx++) {
            IndexHeapObject pointingObject = objectArray[idx];
            IndexHeapObject[] pointedObjects = pointingObject.pointsTo;
            for (IndexHeapObject pointedObject : pointedObjects) {
                if (pointedObject != null) {
                    pointedObject.pointedFromIndices[(int) fromPtrCounter[pointedObject.getIndex()].get()] = pointingObject.getIndex();
                    pointedObject.pointedFrom[(int) fromPtrCounter[pointedObject.getIndex()].get()] = pointingObject;
                    fromPtrCounter[pointedObject.getIndex()].inc();
                }
            }
        }

        if (progressListener != null) {
            progressListener.fire(0.8, "Build root pointers");
        }

        // This call ensures that everything is set up right in the child classes
        store(spaceInfos, spaceStartAdresses, objectArray);

        // translate root pointers to indices
        heap.rootPtrs.forEach((address, rootPointerList) -> {
            int rootIdx = NULL_INDEX;
            if (address != NULL_INDEX) {
                IndexHeapObject obj = toObject(address, objectArray);
                if (obj == null) {
                    System.err.println("Could not find object at " + address + " with GC roots " + rootPointerList.stream()
                                                                                                                  .map(RootPtr::toString)
                                                                                                                  .collect(Collectors.joining(",")));
                } else {
                    rootIdx = obj.getIndex();
                }
            }
            final int finalRootIdx = rootIdx;
            rootPtrList.addAll(rootPointerList);
            rootPtrs.put(rootIdx, rootPointerList);
            rootPointerList.forEach(rp -> rp.setIdx(finalRootIdx));
        });

        // "easyTravel"-Modus
        AllocationSite longLocationAllocSite = null;
        for (ObjectInfo info : objectInfoCache.getInfos()) {
            if (info.type.internalName.equals("Lcom/dynatrace/easytravel/jpa/business/Location;")) {
                if (info.allocationSite.getCallSites()[0].shortest.contains("Generated")) {
                    if (longLocationAllocSite == null || info.allocationSite.getCallSites().length > longLocationAllocSite.getCallSites().length) {
                        longLocationAllocSite = info.allocationSite;
                    }
                }
            }
        }
        for (ObjectInfo info : objectInfoCache.getInfos()) {
            if (info.type.internalName.equals("Lcom/dynatrace/easytravel/jpa/business/Location;")) {
                if (info.allocationSite.getCallSites()[0].shortest.contains("Generated")) {
                    info.allocationSite = longLocationAllocSite;
                }
            }
        }

        finalInit(initDataStructures, progressListener);
    }

    private void initialize(Symbols symbols,
                            int objectCount,
                            ObjectInfoCache cache,
                            int gcNo,
                            Map<Long, ThreadInfo> threadsById,
                            Map<String, ThreadInfo> threadsByInternalName) {
        this.symbols = symbols;
        this.objectCount = objectCount;
        this.objectInfoCache = cache;
        this.gcNo = gcNo;
        this.rootPtrs = new HashMap<>();
        this.rootPtrList = new ArrayList<>();
        this.threadsById = new HashMap<>(threadsById);
        this.threadsByInternalName = threadsByInternalName;

        this.directlyReachableFromRootType = new HashMap<>();
        this.indirectlyReachableFromRootType = new HashMap<>();
        for (RootPtr.RootType type : RootPtr.RootType.values()) {
            this.directlyReachableFromRootType.put(type, new BitSet());
            this.indirectlyReachableFromRootType.put(type, new BitSet());
        }
    }

    protected abstract void store(SpaceInfo[] spaceInfos, long[] spaceStartAdresses, IndexHeapObject[] objects);

    private void finalInit(boolean initDataStructures, ProgressListener progressListener) {
        // mark everything that is reachable from roots
        if (progressListener != null) {
            progressListener.fire(0.9, "Mark reachable objects");
        }
        for (Map.Entry<Integer, List<RootPtr>> entry : rootPtrs.entrySet()) {
            if (valid(entry.getKey())) {
                for (RootPtr ptr : entry.getValue()) {
                    directlyReachableFromRootType.get(ptr.getRootType()).set(entry.getKey());
                    markReachable(entry.getKey(), ptr.getRootType());
                }
            }
        }

        if (progressListener != null) {
            progressListener.fire(1.0, "Calculate data structures");
        }
        if (initDataStructures) {
            // detect datastructures in heap
            initDSLDataStructures(progressListener);
            //try {
            //    Files.write(Paths.get(System.getProperty("user.home"), "Desktop", "DataStructureModel.dot"),
            //                initAutoDetectedDataStructures(progressListener).toDotString().getBytes());
            //} catch (IOException e) {
            //    e.printStackTrace();
            //}
        }
    }

    private Graph initAutoDetectedDataStructures(ProgressListener progressListener) {
        Graph graph = new Graph();
        for (int idx = 0; idx < objectCount; idx++) {
            AllocatedType type = getType(idx);
            String typeName = type.getExternalName(true, false);
            boolean isRecursiveType = type.isRecursiveType();
            if (isRecursiveType) {
                graph.addEdge(typeName, typeName);

                int[] fromPtrs = getFromPointers(idx);
                if (fromPtrs != null) {
                    for (int fromPtr : fromPtrs) {
                        AllocatedType fromType = getType(fromPtr);
                        if (!fromType.isArray()) {
                            graph.addEdge(getType(fromPtr).getExternalName(true, false), typeName);
                        }
                    }
                }
            }
        }
        return graph;
    }

    private void initDSLDataStructures(ProgressListener progressListener) {
        //ApplicationStatistics.Measurement mCreateDataStructures = ApplicationStatistics.getInstance().createMeasurement("Create data structures");
        Counter counter = new Counter();
        List<DSLDataStructure> dataStructureList = new ArrayList<>();
        if (progressListener != null) {
            progressListener.fire(0.0, "Detect data structure heads");
        }
        stream().forEach(idx -> {
            DSLDataStructure dataStructure = DSLDataStructure.tryCreateDataStructure((int) counter.get(), idx, this);
            if (dataStructure != null) {
                dataStructureList.add(dataStructure);
                counter.inc();
            }
        });
        this.dataStructures = dataStructureList.toArray(new DSLDataStructure[0]);
        dataStructuresByHeadObjectIndexMap = new HashMap<Integer, DSLDataStructure>();
        dataStructureList.forEach(dsInstance -> dataStructuresByHeadObjectIndexMap.put(dsInstance.getHeadIdx(), dsInstance));
        //mCreateDataStructures.end();

        if (progressListener != null) {
            progressListener.fire(0.2, "Calculate data structure composition");
        }

        dataStructureComposition = DataStructureUtil.calculateDataStructureComposition(this, dataStructures, objectCount, progressListener);
        System.out.println("Creation: " + DataStructureUtil.getArrayCreationCounter());
        System.out.println("Increase: " + DataStructureUtil.getArrayIncreaseCounter());

        if (progressListener != null) {
            progressListener.fire(0.8, "Detect top-level data structures");
        }

        //mBuildDataStructureComposition.end();

        // Detect top-level data structures
        DataStructureUtil.detectTopLevelDataStructures(dataStructures, progressListener);

        System.out.println("# of top-level data structure instances: " + Arrays.stream(dataStructures).filter(DSLDataStructure::isTopLevelDataStructure).count());
        System.out.println("# of internal data structure instances: " + Arrays.stream(dataStructures).filter(x -> !x.isTopLevelDataStructure()).count());

        if (progressListener != null) {
            progressListener.fire(1.0, "");
        }
    }

    private void markReachable(int objectIndex, RootPtr.RootType rootType) {
        if (objectIndex < 0) {
            return;
        }
        BitSet indirectlyReachableFromRoot = indirectlyReachableFromRootType.get(rootType);
        if (indirectlyReachableFromRoot.get(objectIndex)) {
            return;
        }

        indirectlyReachableFromRoot.set(objectIndex);

        IntStack stack = new IntStack();
        stack.push(objectIndex);

        while (!stack.isEmpty()) {
            int idx = stack.pop();
            int[] ptrs = getToPointers(idx);
            if (ptrs != null) {
                for (int ptrIdx : ptrs) {
                    if (ptrIdx >= 0 && !indirectlyReachableFromRootType.get(rootType).get(ptrIdx)) {
                        stack.push(ptrIdx);
                        indirectlyReachableFromRoot.set(ptrIdx);
                    }
                }
            }
        }
    }

    //================================================================================
    // utilities
    //================================================================================

    public boolean valid(int objIndex) {
        return objIndex >= 0 && objIndex < objectCount;
    }

    protected IndexHeapObject toObject(long address, IndexHeapObject[] objects) {
        IndexHeapObject obj = binarySearch(objects, 0, objects.length - 1, address);
        assert obj != null : "Object was not found!";
        return obj;
    }

    private IndexHeapObject binarySearch(IndexHeapObject[] arr, int low, int high, long addr) {
        while (low <= high) {
            int mid = (low + high) / 2;
            if (arr[mid].getAddress() < addr) {
                low = mid + 1;
            } else if (arr[mid].getAddress() > addr) {
                high = mid - 1;
            } else if (arr[mid].getAddress() == addr) {
                return arr[mid];
            }
        }
        return null;
    }

    abstract public void clear();

    //================================================================================
    // iteration
    //================================================================================
    public IntStream stream() {
        return IntStream.rangeClosed(0, objectCount - 1);
    }

    public ListClassificationTree groupListParallel(
            @NotNull
                    Filter[] filters,
            @NotNull
                    ClassifierChain classifiers,
            boolean addFilterNodeInTree,
            boolean sample,
            ObjectStream.IterationListener listener,
            BooleanProperty cancellationToken) {
        ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance()
                                                                   .createMeasurement("fast heap: group list");
        Arrays.stream(filters).filter(Objects::nonNull).forEach(f -> f.setup(() -> symbols, () -> this));
        classifiers.getList().stream().filter(Objects::nonNull).forEach(c -> c.setup(() -> symbols, () -> this));

        ListGroupingNode combinedTree = new ListGroupingNode();
        ParallelizationUtil.temporaryExecutorServiceBlocking((threadId, threadCount) -> {
            ListGroupingNode threadLocalTree = new ListGroupingNode();
            Counter threadLocalCounter = new Counter();
            for (int i = threadId; i < objectCount; i += threadCount) {
                if (cancellationToken == null || !cancellationToken.get()) {
                    try {
                        threadLocalTree.classify(this, i, classifiers, filters, addFilterNodeInTree);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                if (listener != null) {
                    // progress indication...
                    threadLocalCounter.inc();
                    if (threadLocalCounter.get() % 10_000 == 0) {
                        listener.objectsIterated(threadLocalCounter.get());
                        threadLocalCounter.reset();
                    }
                }
            }
            if (listener != null) {
                listener.objectsIterated(threadLocalCounter.get());
            }
            synchronized (combinedTree) {
                combinedTree.merge(threadLocalTree);
            }
        });
        if (sample) {
            if (combinedTree.containsChild("Filtered")) {
                combinedTree.getChild("Filtered").sampleTopDown(this);
            } else {
                combinedTree.sampleTopDown(this);
            }
        }
        m.end();
        return new ListClassificationTree(combinedTree, filters, classifiers);

    }

    //================================================================================
    // Getters
    //================================================================================
    public int getObjectCount() {
        return objectCount;
    }

    public long getByteCount() {
        return byteCount;
    }

    public long getReachableFromRootByteCount() {
        BitSet allindirectlyReachableFromRoots = new BitSet();
        for (BitSet bitSet : indirectlyReachableFromRootType.values()) {
            allindirectlyReachableFromRoots.or(bitSet);
        }
        return ClosureUtil.getClosureByteCount(allindirectlyReachableFromRoots, this);
    }

    public AllocatedType getType(int objIndex) {
        return getObjectInfo(objIndex).type;
    }

    public int getSize(int objIndex) {
        assert objIndex < objectCount : "Object index may not be out of range";
        return objIndex == NULL_INDEX ? 0 : getObjectInfo(objIndex).size;
    }

    public boolean isArray(int objIndex) {
        return getObjectInfo(objIndex).isArray;
    }

    public int getArrayLength(int objIndex) {
        return getObjectInfo(objIndex).arrayLength;
    }

    public AllocationSite getAllocationSite(int objIndex) {
        if (!valid(objIndex)) {
            return null;
        }

        return getObjectInfo(objIndex).allocationSite;
    }

    public EventType getEventType(int objIndex) {
        if (!valid(objIndex)) {
            return null;
        }

        return getObjectInfo(objIndex).eventType;
    }

    public abstract SpaceInfo getSpace(int objIndex);

    public List<RootPtr> getRoot(int objIndex) {
        return rootPtrs.get(objIndex);
    }

    public Symbols getSymbols() {
        return symbols;
    }

    public List<RootPtr> getRootPointerList() {
        return rootPtrList;
    }

    public Map<Integer, List<RootPtr>> getRootPointerMap() {
        return rootPtrs;
    }

    public boolean isDirectlyReachable(int objIndex) {
        return directlyReachableFromRootType.values().stream().anyMatch(b -> b.get(objIndex));
    }

    public boolean isRootReachable(int objIndex) {
        if (valid(objIndex)) {
            return indirectlyReachableFromRootType.values().stream().anyMatch(b -> b.get(objIndex));
        } else {
            return false;
        }
    }

    public BitSet getIndirectlyReachableObjectsByTypes(RootPtr.RootType... rootTypes) {
        BitSet result = new BitSet();
        for (RootPtr.RootType type : rootTypes) {
            result.or(indirectlyReachableFromRootType.get(type));
        }
        return result;
    }

    public boolean isRootReachable(int objIndex, RootPtr.RootType... rootTypes) {
        return Arrays.stream(rootTypes).anyMatch(type -> indirectlyReachableFromRootType.get(type).get(objIndex));
    }

    //================================================================================
    // DSL data structures
    //================================================================================

    public DSLDataStructure[] getDataStructures() {
        return dataStructures;
    }

    public Map<Integer, DSLDataStructure> getDataStructuresByHeadObjectIndexMap() {
        return dataStructuresByHeadObjectIndexMap;
    }

    public Set<DSLDataStructure> getDataStructures(int objIndex, boolean includeIndirectDataStructures, boolean reduceToTopLevelDataStructures) {
        // TODO reduce flag is dependent on include flag which makes it weird to use...

        if (!valid(objIndex)) {
            return null;
        }

        if (dataStructureComposition == null || objIndex >= dataStructureComposition.length) {
            // May happen if dataStructureComposition has not been initialized because data strucutres should not be initialized...
            // Could write a warning here because accessing DS information without initialized data structures seems unintended...
            return new HashSet<>();
        }

        int[] dataStructureIndices = dataStructureComposition[objIndex];

        if (dataStructureIndices != null && dataStructureIndices.length > 0) {
            Set<DSLDataStructure> closedSet = new HashSet<>();
            for (int i = 0; i < dataStructureIndices.length; i++) {
                closedSet.add(dataStructures[dataStructureIndices[i]]);
            }

            Set<DSLDataStructure> topLevelDSs = null;
            if (reduceToTopLevelDataStructures) {
                topLevelDSs = new HashSet<>();
                // only add those ds to top-level set that are not dominated by any other ds in the closed set
                closedSet.stream()
                         .filter(ds -> closedSet.stream()
                                                .filter(ds2 -> ds2 != ds)
                                                .noneMatch(ds2 -> dominates(ds2.getHeadIdx(), ds.getHeadIdx())))
                         .forEach(topLevelDSs::add);
            }
            if (includeIndirectDataStructures) {
                // follow data structure heads recursively to collect also data structures that indirectly reference the object at the given index
                Stack<DSLDataStructure> stack = new Stack<>();
                stack.addAll(closedSet);
                while (!stack.isEmpty()) {
                    DSLDataStructure cur = stack.pop();
                    int[] curContainingDSIndices = dataStructureComposition[cur.getHeadIdx()];
                    for (int i = 0; i < curContainingDSIndices.length; i++) {
                        DSLDataStructure dsContainingCur = dataStructures[curContainingDSIndices[i]];
                        if (closedSet.add(dsContainingCur)) {
                            stack.push(dsContainingCur);

                            if (reduceToTopLevelDataStructures) {
                                topLevelDSs.add(dsContainingCur);
                                if (dsContainingCur != cur && dominates(dsContainingCur.getHeadIdx(), cur.getHeadIdx())) {   // TODO do really only head indices matter?
                                    topLevelDSs.remove(cur);
                                }
                            }
                        }
                    }
                }
            }

            return reduceToTopLevelDataStructures ? topLevelDSs : closedSet;
        }

        return null;
    }

    /**
     * Returns the DataStructure of which the object at the given index is the head
     *
     * @param headIndex
     * @return the DataStructure object or null if the given index is not a head index
     */
    public DSLDataStructure getHeadedDataStructure(int headIndex) {
        if (!valid(headIndex)) {
            return null;
        }
        if (dataStructureComposition == null || headIndex >= dataStructureComposition.length) {
            // May happen if dataStructureComposition has not been initialized because data strucutres should not be initialized...
            // Could write a warning here because accessing DS information without initialized data structures seems unintended...
            return null;
        }

        int[] dataStructureIndices = dataStructureComposition[headIndex];
        if (dataStructureIndices == null) {
            return null;
        }

        for (int i = 0; i < dataStructureIndices.length; i++) {
            DSLDataStructure ds = dataStructures[dataStructureIndices[i]];
            if (ds.getHeadIdx() == headIndex) {
                return ds;
            }
        }

        return null;
    }

    public boolean isContainedInDataStructure(int objIndex) {
        if (!valid(objIndex)) {
            throw new IllegalArgumentException("Invalid object index given!");
        }
        if (dataStructureComposition == null || objIndex >= dataStructureComposition.length) {
            // May happen if dataStructureComposition has not been initialized because data strucutres should not be initialized...
            // Could write a warning here because accessing DS information without initialized data structures seems unintended...
            return false;
        }

        return dataStructureComposition[objIndex] != null;
    }

    //================================================================================
    // Auto-detected data structures
    //================================================================================

    //================================================================================
    // Abstract Getters
    //================================================================================
    public abstract int toIndex(long address);

    public abstract long getAddress(int objIndex);

    public abstract int[] getToPointers(int objIndex);

    public BitSet getToPointers(BitSet objIndices) {
        BitSet pointedTo = new BitSet();
        int objIdx = objIndices.nextSetBit(0);
        while (objIdx != -1) {
            int[] pointers = getToPointers(objIdx);
            for (int p : pointers) {
                if (valid(p)) {
                    pointedTo.set(p);
                }
            }
            objIdx = objIndices.nextSetBit(objIdx + 1);
        }
        return pointedTo;
    }

    public abstract int[] getFromPointers(int objIndex);

    public BitSet getFromPointers(BitSet objIndices) {
        BitSet pointedFrom = new BitSet();
        int objIdx = objIndices.nextSetBit(0);
        while (objIdx != -1) {
            int[] pointers = getFromPointers(objIdx);
            for (int p : pointers) {
                if (valid(p)) {
                    pointedFrom.set(p);
                }
            }
            objIdx = objIndices.nextSetBit(objIdx + 1);
        }
        return pointedFrom;
    }

    public abstract ObjectInfo getObjectInfo(int objIndex);

    public abstract short getBorn(int objIndex);

    //================================================================================
    // Closures
    //================================================================================
    public Closures getClosures(boolean calculateTransitiveClosure,
                                boolean calculateGCClosure,
                                boolean calculateDataStructureClosure,
                                boolean calculateDeepDataStructureClosure,
                                int objIndex) {
        return getClosures(calculateTransitiveClosure,
                           calculateGCClosure,
                           calculateDataStructureClosure,
                           calculateDeepDataStructureClosure,
                           new int[]{objIndex},
                           null);
    }

    public Closures getClosures(boolean calculateTransitiveClosure,
                                boolean calculateGCClosure,
                                boolean calculateDataStructureClosure,
                                boolean calculateDeepDataStructureClosure,
                                int[] objIndices) {
        return getClosures(calculateTransitiveClosure,
                           calculateGCClosure,
                           calculateDataStructureClosure,
                           calculateDeepDataStructureClosure,
                           objIndices,
                           null);
    }

    public Closures getClosures(boolean calculateTransitiveClosure,
                                boolean calculateGCClosure,
                                boolean calculateDataStructureClosure,
                                boolean calculateDeepDataStructureClosure,
                                int[] objIndices,
                                BitSet assumeChildClosure) {
        BitSet transitiveClosure = calculateTransitiveClosure ? transitiveClosure(objIndices, assumeChildClosure) : new BitSet();
        BitSet gcClosure = calculateGCClosure ? gcClosure(objIndices, transitiveClosure) : new BitSet();

        // TODO data structure closures disabled at the moment
        /*
        ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Calculate DS closure");
        BitSet dsClosure = calculateDataStructureClosure ? getDataStructureClosure(objIndices) : new BitSet();
        m.end();

        m = ApplicationStatistics.getInstance().createMeasurement("Calculate deep DS closure");
        BitSet deepDSClosure = calculateDeepDataStructureClosure ? getDeepDSLDataStructureClosure(objIndices) : new BitSet();
        m.end();
        */

        return new Closures(this, objIndices, transitiveClosure, gcClosure, new BitSet(), new BitSet());
    }

    //================================================================================
    // Closure size
    //================================================================================
    public BitSet transitiveClosure(BitSet objIndices, BitSet assumeClosure) {
        int[] objIndicesAsArray = new int[objIndices.cardinality()];
        for (int i = 0, objId = objIndices.nextSetBit(0); objId != -1; i++, objId = objIndices.nextSetBit(objId + 1)) {
            objIndicesAsArray[i] = objId;
        }
        return transitiveClosure(objIndicesAsArray, assumeClosure);
    }

    public BitSet transitiveClosure(int[] objIndices, BitSet assumeClosure) {
        ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("anttracks.indexbasedheap.transitiveclosure");
        ApplicationStatistics.Measurement m2 = ApplicationStatistics.getInstance().createMeasurement("anttracks.indexbasedheap.transitiveclosure.preperation");
        BitSet closure = assumeClosure != null ? assumeClosure : new BitSet();
        IntStack toProcess = new IntStack(objIndices.length);

        // build closure of objIndices
        for (int objIndex : objIndices) {
            if (valid(objIndex)) {
                toProcess.push(objIndex);
                closure.set(objIndex);
            }
        }
        m2.end();
        m2 = ApplicationStatistics.getInstance().createMeasurement("anttracks.indexbasedheap.transitiveclosure.traversal");
        while (!toProcess.isEmpty()) {
            int idx = toProcess.pop();
            int[] pointers = getToPointers(idx);
            if (pointers != null) {
                for (int ptr : pointers) {
                    if (valid(ptr) && !closure.get(ptr)) {
                        closure.set(ptr);
                        toProcess.push(ptr);
                    }
                }
            }
        }

        m2.end();
        m.end();
        return closure;
    }

    //================================================================================
    // GC size
    //================================================================================
    public BitSet gcClosure(int[] objIndices, BitSet transitiveClosure) {
        // everything reachable from given indices (= upper limit of gc size)
        ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("anttracks.indexbasedheap.gcclosure");
        ApplicationStatistics.Measurement m2 = ApplicationStatistics.getInstance().createMeasurement("anttracks.indexbasedheap.gcclosure.preperation");
        BitSet gcClosure = (BitSet) transitiveClosure.clone();
        BitSet objIndicesSet = new BitSet();
        for (int i = 0; i < objIndices.length; i++) {
            if (valid(objIndices[i])) {
                objIndicesSet.set(objIndices[i]);
            }
        }
        m2.end();
        m2 = ApplicationStatistics.getInstance().createMeasurement("anttracks.indexbasedheap.gcclosure.traversal");
        // find all objects in transitiveClosure that are referenced from the outside (except transitiveClosure root objects)
        IntStack toRemoveWalker = new IntStack();
        for (int idx = gcClosure.nextSetBit(0); idx != -1; idx = gcClosure.nextSetBit(idx + 1)) {
            if (objIndicesSet.get(idx)) {
                continue;
            }

            // check direct root pointer for idx
            if (isDirectlyReachable(idx)) {
                toRemoveWalker.push(idx);
                gcClosure.clear(idx);
                while (!toRemoveWalker.isEmpty()) {
                    int removeIdx = toRemoveWalker.pop();
                    int[] pointers = getToPointers(removeIdx);
                    if (pointers != null) {
                        for (int j = 0; j < pointers.length; j++) {
                            int ptr = pointers[j];
                            if (valid(ptr) && gcClosure.get(ptr) && !objIndicesSet.get(ptr)) {
                                toRemoveWalker.push(ptr);
                                gcClosure.clear(ptr);
                            }
                        }
                    }
                }
            } else {
                // object itself is not directly root pointed, check if any referee is 1) outside of the transitiveClosure and 2) reachable from root
                int[] fromPointers = getFromPointers(idx);
                if (fromPointers != null) {
                    for (int i = 0; i < fromPointers.length; i++) {
                        int fromPtr = fromPointers[i];
                        if (!transitiveClosure.get(fromPtr) && isRootReachable(fromPtr)) {
                            // Object is kept alive from outside the transitiveClosure: Remove it and all its children from the transitiveClosure
                            toRemoveWalker.push(idx);
                            gcClosure.clear(idx);
                            while (!toRemoveWalker.isEmpty()) {
                                int removeIdx = toRemoveWalker.pop();
                                int[] pointers = getToPointers(removeIdx);
                                if (pointers != null) {
                                    for (int j = 0; j < pointers.length; j++) {
                                        int ptr = pointers[j];
                                        if (valid(ptr) && gcClosure.get(ptr) && !objIndicesSet.get(ptr)) {
                                            toRemoveWalker.push(ptr);
                                            gcClosure.clear(ptr);
                                        }
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
        m2.end();
        m.end();

        // TODO dominator tree
        //        if (objIndices.length == 1) {
        //            assert gcClosure.equals(getDominatedObjects(objIndices[0])) : "Dominator tree gc closure differs for index " + objIndices[0];
        //        }

        return gcClosure;
    }

    //================================================================================
    // Data structure size
    //================================================================================
//    public BitSet getDataStructureClosure(int headIndex) {
//        return getDataStructureClosure(new int[]{headIndex});
//    }
//
//    public BitSet getDataStructureClosure(int[] headIndices) {
//        BitSet closure = new BitSet(objectCount);
//
//        BitSet visited = new BitSet(objectCount);
//
//        for (int i = 0; i < headIndices.length; i++) {
//            if (valid(headIndices[i]) && dataStructureComposition[headIndices[i]] != null) {
//                for (int j = 0; j < dataStructureComposition[headIndices[i]].length; j++) {
//                    DSLDataStructure dataStructure = dataStructures[dataStructureComposition[headIndices[i]][j]];
//                    if (dataStructure.getHeadIdx() == headIndices[i]) {
//                        // ...headIndices[i] is the head of dataStructure (and not just contained)
//                        // ==> mark all objects in dataStructure in the closure
//                        dataStructure.visitAllObjects(this, visited, (parentObjIndex, objIndex) -> {
//                            closure.set(objIndex);
//                            return Unit.INSTANCE;
//                        });
//                        visited.clear();
//                    }
//                }
//            }
//        }
//
//        return closure;
//    }

//    private BitSet getDeepDSLDataStructureClosure(int headIndex) {
//        return getDeepDSLDataStructureClosure(new int[]{headIndex});
//    }
//
//    private BitSet getDeepDSLDataStructureClosure(int[] headIndices) {
//        // this set serves as a queue of head indices still to handle
//        BitSet dsHeadsToVisit = new BitSet();
//        BitSet visitedObject = new BitSet();
//        // ...initialized with the given set of head indices
//        for (int i = 0; i < headIndices.length; i++) {
//            if (valid(headIndices[i]) && DSLDataStructure.isDataStructureHead(headIndices[i], this)) {
//                dsHeadsToVisit.set(headIndices[i]);
//            }
//        }
//
//        // the final closure; also serves as closed set for already visited head indices
//        BitSet deepDataStructureClosure = new BitSet();
//        // the to visit queue is iterated from front to back until it is empty (new head indices might be set before the one that is currently handled)
//        while (!dsHeadsToVisit.isEmpty()) {
//            for (int headIndex = dsHeadsToVisit.nextSetBit(0); headIndex != -1; headIndex = dsHeadsToVisit.nextSetBit(headIndex + 1)) {
//                // visiting the head index of a data structure:
//                // 1) remove it from the to visit queue
//                dsHeadsToVisit.clear(headIndex);
//                // 2) if it was not yet handled (i.e. not yet in the final closure) ...
//                if (!deepDataStructureClosure.get(headIndex)) {
//                    // ... add it
//                    deepDataStructureClosure.set(headIndex);
//                    // 3) retrieve the data structure object of the head index
//                    DSLDataStructure ds = getHeadedDataStructure(headIndex);
//                    if (ds != null) {
//                        // 4) visit all objects part of the data structure...
//                        ds.visitAllObjects(this, visitedObject, (parentObjIndex, objIndex) -> {
//                            if (DSLDataStructure.isDataStructureHead(objIndex, this)) {
//                                // ... data structure heads encountered in the process are not added to final closure yet but only put in the to visit queue
//                                dsHeadsToVisit.set(objIndex);
//                            } else {
//                                deepDataStructureClosure.set(objIndex);
//                            }
//                            return Unit.INSTANCE;
//                        });
//                        visitedObject.clear();
//                    }
//                }
//            }
//        }
//
//        return deepDataStructureClosure;
//    }

    //================================================================================
    // Root pointers
    //================================================================================
    public List<RootPtr.RootInfo> traceClosestRootBFS(int objIndex, int maxDepth, boolean onlyVariables) {

        if (!valid(objIndex)) {
            return new ArrayList<>();
        }

        Set<Integer> closedSet = new HashSet<>();
        ArrayDeque<int[]> fifo = new ArrayDeque<>();
        List<RootPtr.RootInfo> ret = new ArrayList<>();

        int[] initialPath = new int[]{objIndex};
        fifo.add(initialPath);
        closedSet.add(objIndex);

        while (!fifo.isEmpty()) {
            int[] curPath = fifo.poll();
            int curObjIndex = curPath[curPath.length - 1];

            // Continue to check all from-pointers on the same level on which the first root-pointer was found.
            // We always take the one which's typeId is lower
            if (!ret.isEmpty() && curPath.length > ret.get(0).path.length) {
                return ret;
            }

            // is there a corresponding root ptr?
            List<RootPtr> matchingRoots = rootPtrs.get(curObjIndex);
            if (matchingRoots != null && onlyVariables) {
                matchingRoots = matchingRoots.stream()
                                             .filter(r -> r.getRootType().isVariable)
                                             .collect(Collectors.toList());
            }
            if (matchingRoots != null && matchingRoots.size() != 0) {
                // If we find multiple root pointers on the same depth, take the one with the lower type id
                if (ret.isEmpty() || ret.get(0).ptrs[0].getRootType().byteVal > matchingRoots.get(0)
                                                                                             .getRootType().byteVal) {
                    ret.clear();
                    for (RootPtr root : matchingRoots) {
                        ret.add(new RootPtr.RootInfo(new RootPtr[]{root}, curPath));
                    }
                }
            }

            // ... and keep searching
            int[] fromPointers = getFromPointers(curObjIndex);
            if (fromPointers != null && curPath.length < maxDepth) {
                for (int i = 0; i < fromPointers.length; i++) {
                    // skip already handled addresses
                    if (closedSet.add(fromPointers[i])) {
                        // extend path by next step
                        int[] newPath = Arrays.copyOf(curPath, curPath.length + 1);
                        newPath[newPath.length - 1] = fromPointers[i];
                        fifo.offer(newPath);
                    }
                }
            }
        }

        return ret;
    }

    public List<RootPtr.RootInfo> traceAllRootsDFS(int objIndex, int maxDepth, boolean onlyVariables) {
        return traceAllRootsDFS(new int[]{objIndex}, maxDepth, onlyVariables);
    }

    public List<RootPtr.RootInfo> traceAllRootsDFS(int[] objIndices, int maxDepth, boolean onlyVariables) {
        // the keys of this map are the object indices of all objects that have already been identified as part of a path to a root
        // the value of each key is an array of RootInfos i.e. the results (paths) that contain the key
        Map<Integer, List<RootPtr.RootInfo>> foundPathObjects = new HashMap<>();
        List<RootPtr.RootInfo> ret = new ArrayList<>();

        for (int objIndex : objIndices) {
            if (!valid(objIndex)) {
                return new ArrayList<>();
            }

            Stack<int[]> stack = new Stack<>();
            Set<Integer> closedSet = new HashSet<>();

            int[] initialPath = new int[]{objIndex};
            stack.add(initialPath);
            closedSet.add(objIndex);

            while (!stack.isEmpty()) {
                int[] curPath = stack.pop();
                int curObjIndex = curPath[curPath.length - 1];

                if (foundPathObjects.containsKey(curObjIndex)) {
                    // we reached an object index that was already identified as part of a path to a root
                    // retrieve this already existing result
                    List<RootPtr.RootInfo> existingPaths = foundPathObjects.get(curObjIndex);
                    // the current path can be completed with each of the already existing paths
                    for (RootPtr.RootInfo existingPath : existingPaths) {
                        List<Integer> completedPath = Arrays.stream(existingPath.path)
                                                            .boxed()
                                                            .collect(Collectors.toCollection(ArrayList::new));
                        completedPath = completedPath.subList(completedPath.indexOf(curObjIndex) + 1, completedPath.size());
                        for (int i = curPath.length - 1; i >= 0; i--) {
                            completedPath.add(0, curPath[i]);
                        }
                        // add the resulting path as a new result
                        RootPtr.RootInfo newResult = new RootPtr.RootInfo(existingPath.ptrs,
                                                                          completedPath.stream()
                                                                                       .mapToInt(i -> i)
                                                                                       .toArray());
                        ret.add(newResult);
                        for (int i = 0; i < curPath.length - 1; i++) {
                            List<RootPtr.RootInfo> rootInfos = foundPathObjects.get(curPath[i]);
                            if (rootInfos == null) {
                                rootInfos = new ArrayList<>();
                            }
                            rootInfos.add(newResult);
                            foundPathObjects.put(curPath[i], rootInfos);
                        }
                    }

                    // possible root paths from this node have already been discovered
                    continue;
                }

                // are there corresponding root ptrs?
                List<RootPtr> matchingRoots = rootPtrs.get(curObjIndex);
                if (matchingRoots != null) {
                    RootPtr.RootInfo newResult = new RootPtr.RootInfo(matchingRoots.stream()
                                                                                   .filter(root -> !onlyVariables || root.getRootType().isVariable)
                                                                                   .toArray(RootPtr[]::new), curPath);
                    ret.add(newResult);
                    for (Integer curPathObj : curPath) {
                        List<RootPtr.RootInfo> rootInfos = foundPathObjects.get(curPathObj);
                        if (rootInfos == null) {
                            rootInfos = new ArrayList<>();
                        }
                        rootInfos.add(newResult);
                        foundPathObjects.put(curPathObj, rootInfos);
                    }
                }

                // ... and keep searching
                int[] fromPointers = getFromPointers(curObjIndex);
                if (fromPointers != null && curPath.length < maxDepth) {
                    for (int i = 0; i < fromPointers.length; i++) {
                        // skip already handled addresses
                        if (closedSet.add(fromPointers[i])) {
                            // extend path by next step
                            int[] newPath = Arrays.copyOf(curPath, curPath.length + 1);
                            newPath[newPath.length - 1] = fromPointers[i];
                            stack.push(newPath);
                        }
                    }
                }
            }
        }

        ret.sort(Comparator.comparingInt(rootInfo -> rootInfo.path.length));
        return ret;
    }

    public RootPtr findClosestRoot(int objIndex, int maxDepth, boolean onlyVariables) {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("find closest Root BFS");
        if (!valid(objIndex)) {
            return null;
        }

        BitSet visited = new BitSet();
        BitSet currentlyToVisit = new BitSet();
        BitSet nextVisitRound = new BitSet();

        RootPtr ret = null;

        nextVisitRound.set(objIndex);
        visited.set(objIndex);

        for (int currentDepth = 0; currentDepth < maxDepth && ret == null; currentDepth++) {
            currentlyToVisit.clear();
            currentlyToVisit.or(nextVisitRound);
            nextVisitRound.clear();
            for (int idx = currentlyToVisit.nextSetBit(0); idx != -1; idx = currentlyToVisit.nextSetBit(idx + 1)) {
                // is there a corresponding root ptr?
                List<RootPtr> matchingRoots = rootPtrs.get(idx);
                if (onlyVariables && matchingRoots != null) {
                    matchingRoots = matchingRoots.stream()
                                                 .filter(r -> r.getRootType().isVariable)
                                                 .collect(Collectors.toList());
                }
                if (matchingRoots != null && matchingRoots.size() != 0) {
                    // If we find multiple root pointers on the same depth, take the one with the lower type id
                    if (ret == null || ret.getRootType().byteVal > matchingRoots.get(0)
                                                                                .getRootType().byteVal) {
                        ret = matchingRoots.get(0);
                    }
                }
                if (ret == null) {
                    // ... and keep searching
                    int[] fromPointers = getFromPointers(idx);
                    if (fromPointers != null) {
                        for (int i = 0; i < fromPointers.length; i++) {
                            if (!visited.get(fromPointers[i])) {
                                visited.set(fromPointers[i]);
                                // extend path by next step
                                nextVisitRound.set(fromPointers[i]);
                            }
                        }
                    }
                }
            }
        }
        //m.end();
        return ret;
    }

    public List<RootPtr> indirectGCRoots(int[] objIndices, int maxDepth) {
        return indirectGCRoots(objIndices, maxDepth, null);
    }

    public List<RootPtr> indirectGCRoots(int[] objIndices, int maxDepth, BiConsumer<Integer, Integer> visitor) {
        List<RootPtr> ret = new ArrayList<>();

        BitSet visited = new BitSet();
        BitSet currentlyToVisit = new BitSet();
        BitSet nextVisitRound = new BitSet();

        for (int i = 0; i < objIndices.length; i++) {
            if (valid(objIndices[i])) {
                nextVisitRound.set(objIndices[i]);
                visited.set(objIndices[i]);
            }
        }

        for (int currentDepth = 0; currentDepth < maxDepth && !nextVisitRound.isEmpty(); currentDepth++) {
            currentlyToVisit.clear();
            currentlyToVisit.or(nextVisitRound);
            nextVisitRound.clear();
            for (int idx = currentlyToVisit.nextSetBit(0); idx != -1; idx = currentlyToVisit.nextSetBit(idx + 1)) {

                // is there a corresponding root ptr?
                List<RootPtr> matchingRoots = rootPtrs.get(idx);
                if (matchingRoots != null && matchingRoots.size() != 0) {
                    ret.addAll(matchingRoots);
                }

                // ... and keep searching
                int[] fromPointers = getFromPointers(idx);
                if (fromPointers != null) {
                    for (int i = 0; i < fromPointers.length; i++) {
                        if (!visited.get(fromPointers[i])) {
                            visited.set(fromPointers[i]);
                            // extend path by next step
                            nextVisitRound.set(fromPointers[i]);

                            if (visitor != null) {
                                visitor.accept(idx, fromPointers[i]);
                            }
                        }
                    }
                }
            }
        }

        return ret;
    }

    public List<RootPtr> indirectGCRoots(int objIndex, int maxDepth) {
        return indirectGCRoots(new int[]{objIndex}, maxDepth);
    }

    public BitSet fromClosure(int objIndex) {
        ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance()
                                                                   .createMeasurement("From Closure");
        if (!valid(objIndex)) {
            return new BitSet();
        }

        BitSet fromClosure = new BitSet();

        int[] stack = new int[]{objIndex};
        int stackIndex = 0;
        int stackTop = 1;

        while (stackIndex < stackTop) {
            int curObjIndex = stack[stackIndex];
            stackIndex++;
            // Add current object to from-closure
            fromClosure.set(curObjIndex);

            // ... and keep following from-pointers
            int[] fromPointers = getFromPointers(curObjIndex);
            if (fromPointers != null) {
                for (int i = 0; i < fromPointers.length; i++) {
                    int pointedFromPtr = fromPointers[i];
                    if (!fromClosure.get(pointedFromPtr)) {
                        fromClosure.set(pointedFromPtr);
                        // extend path by next step
                        if (stackTop == stack.length) {
                            stack = Arrays.copyOf(stack, stack.length * 2);
                        }
                        stack[stackTop] = pointedFromPtr;
                        stackTop++;
                    }
                }
            }
        }
        m.end();
        return fromClosure;
    }

    //================================================================================
    // Dominator Tree (written by eg)
    // TODO: Check for correctness
    //================================================================================
    public void initDominators() {
        ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Calculate fast dominators");
        dominators = new FastDominators(this::getToPointers,
                                        this::getFromPointers,
                                        this::isRootReachable,
                                        rootPtrs.keySet().stream().mapToInt(i -> i).toArray(),
                                        objectCount);
        m.end();
    }

    /**
     * Checks whether object n dominates object m
     *
     * @param n
     * @param m
     * @return true if n is an ancestor of m in the dominator tree, false otherwise. Note that for n = m, this method returns false.
     */
    private boolean dominates(int n, int m) {
        if (dominators == null) {
            initDominators();
        }

        for (int d = dominators.getImmediateDominator(m); d >= 0; d = dominators.getImmediateDominator(d)) {
            if (d == n) {
                return true;
            }
        }

        return false;
    }

    public int getImmediateDominator(int objIndex) {
        if (dominators == null) {
            initDominators();
        }

        return dominators.getImmediateDominator(objIndex);
    }

    public BitSet getDominatedObjects(int objIndex) {
        if (dominators == null) {
            initDominators();
        }

        BitSet dominatedObjects = new BitSet();
        BitSet toProcess = new BitSet();

        toProcess.set(objIndex);

        for (int idx = toProcess.nextSetBit(0); idx != -1; idx = toProcess.nextSetBit(idx + 1)) {
            toProcess.clear(idx);
            dominatedObjects.set(idx);

            int[] immediatelyDominated = dominators.getImmediatelyDominated(idx);
            if (immediatelyDominated != null) {
                for (int i = 0; i < immediatelyDominated.length; i++) {
                    toProcess.set(immediatelyDominated[i]);
                }
            }
        }

        return dominatedObjects;
    }
}