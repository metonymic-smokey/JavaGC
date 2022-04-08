
/**
 *
 */
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.PointersTask;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.bytecollection.ByteCollection;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.DetailedHeapInfo;
import at.jku.anttracks.gui.model.HeapVisualizationStatisticsInfo;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.heap.space.SpaceInfo;
import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.heap.symbols.AllocationSite;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.util.TraceException;
import javafx.concurrent.Task;

import java.awt.*;
import java.util.List;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * @author Christina Rammerstorfer
 */
public class ObjectVisualizationData implements ObjectVisitor, Iterable<BytesPixelDescription> {
    private final int heapWordSize;
    private static final String NO_CLASSIFIER = "no classifier selected";
    private final AppInfo appInfo;
    private final DetailedHeapInfo detailsInfo;
    private final Task<?> task;
    private final Logger logger;
    private final ConcurrentMap<Object, PixelDescription> classificationColors;
    private final Map<Object, Color> globalColorMap;
    private final ColorGenerator colorGenerator;
    private long objectCount;
    private final ClassifierChain selectedClassifiers;
    private final List<Filter> selectedFilters;

    private Object classifications;
    private long combinations;
    private long curObject;
    private long firstAddress;
    private long lastAddress;
    private int lastSize;
    private long gapCount;

    private int arrayLevel;
    private boolean saveMemory;

    private ByteCollection objects;
    private ByteCollection addresses;
    private ByteCollection gaps;

    public static final int MAX_POINTERS = Integer.MAX_VALUE;
    private Set<Long> pointers;
    private int pointersLevel;
    private Set<Long> fromPointers;
    private int fromPointersLevel;

    public ObjectVisualizationData(HeapVisualizationStatisticsInfo statisticsInfo, Task<?> task, Map<Object, Color> globalColorMap, ColorGenerator colorGenerator) {
        appInfo = statisticsInfo.getDetailsInfo().getAppInfo();
        detailsInfo = statisticsInfo.getDetailsInfo();
        this.task = task;
        logger = Logger.getLogger(getClass().getSimpleName() + " " + appInfo.getSymbols().root + " @ " + detailsInfo.getTime());
        classificationColors = new ConcurrentHashMap<>();
        objectCount = detailsInfo.getDetailedHeapSupplier().get().getObjectCount();
        selectedClassifiers = statisticsInfo.getSelectedClassifiers();
        selectedFilters = statisticsInfo.getSelectedFilters();
        heapWordSize = appInfo.getSymbols().heapWordSize;
        pointersLevel = -1;
        fromPointersLevel = -1;
        this.globalColorMap = globalColorMap;
        this.colorGenerator = colorGenerator;
    }

    public ObjectVisualizationData(HeapVisualizationStatisticsInfo statisticsInfo, Task<?> task) {
        appInfo = statisticsInfo.getDetailsInfo().getAppInfo();
        detailsInfo = statisticsInfo.getDetailsInfo();
        this.task = task;
        logger = Logger.getLogger(getClass().getSimpleName() + " " + appInfo.getSymbols().root + " @ " + detailsInfo.getTime());
        classificationColors = new ConcurrentHashMap<>();
        colorGenerator = new ColorGenerator(Color.BLUE);
        objectCount = detailsInfo.getDetailedHeapSupplier().get().getObjectCount();
        selectedClassifiers = statisticsInfo.getSelectedClassifiers();
        selectedFilters = statisticsInfo.getSelectedFilters();
        heapWordSize = appInfo.getSymbols().heapWordSize;
        pointersLevel = -1;
        fromPointersLevel = -1;
        globalColorMap = null;
    }

    public void generateData() {
        generateData(-1);
    }

    public void generateData(long firstAddress) {
        DetailedHeap heap = detailsInfo.getDetailedHeapSupplier().get();
        classifications = new Object[Byte.MAX_VALUE + 1];
        combinations = -1;
        arrayLevel = 1;
        saveMemory = true;
        curObject = 0;
        this.firstAddress = firstAddress;
        lastAddress = firstAddress;
        lastSize = 0;
        gapCount = 0;
        long estimatedMemory = objectCount * 2;
        objects = ByteCollection.create(estimatedMemory);
        addresses = ByteCollection.create(estimatedMemory += objectCount * 8);
        gaps = ByteCollection.create(estimatedMemory += 1024);
        heap.toObjectStream().forEach(this, Settings.Companion.getALL_INFOS());
        if (objectCount != curObject) {
            objectCount = curObject;
            logger.warning("heap.getObjectCount() gave a wrong result");
        }
    }

    @Override
    public void visit(long addr,
                      AddressHO obj,
                      SpaceInfo space,
                      List<? extends RootPtr> rootPtrs) {
        if (task.isCancelled()) {
            throw new CancellationException();
        }
        if (firstAddress < 0) {
            firstAddress = addr;
        } else if (addr - lastAddress != lastSize) {
            // Gap detected, store it
            gaps.writeUnknown((lastAddress + lastSize - firstAddress) / heapWordSize);
            gapCount++;
        }
        Object set;
        Iterator<Filter> iter = selectedFilters.iterator();
        boolean filterCurrentObject = false;
        while (iter.hasNext() && !filterCurrentObject) {
            Filter filter = iter.next();
            try {
                filterCurrentObject = !filter.classify(obj,
                                                       addr,
                                                       obj.getInfo(),
                                                       space,
                                                       obj.getType(),
                                                       obj.getSize(),
                                                       obj.isArray(),
                                                       obj.getArrayLength(),
                                                       obj.getSite(),
                                                       null, // TODO From pointers
                                                       null, // TODO Pointers
                                                       obj.getEventType(),
                                                       rootPtrs,
                                                       -1,// TODO Age
                                                       "",
                                                       ""); // TODO thread names

                if (filterCurrentObject) {
                    break;
                }
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        if (filterCurrentObject) {
            set = PixelDescription.FILTERED;
        } else {// Apply classifier(s)
            int classCount = selectedClassifiers.length();
            if (classCount == 0) {// No classifier selected
                set = NO_CLASSIFIER;
            } else if (classCount == 1) {// 1 classifier selected
                // TODO new heap layout does not support pointers as arrays, fix here
                // set = getClassification(selectedClassifiers.get(0), space, addr, pointedFrom, pointsTo, objectInfo);
                set = getClassification(selectedClassifiers.get(0), space, addr, null, null, obj.getInfo());
            } else {// > 1 classifier selected, wrap array
                Object[] classifications = new Object[classCount];
                int i = 0;
                for (Classifier<?> classifier : selectedClassifiers.getList()) {
                    // TODO new heap layout does not support pointers as arrays, fix here
                    // classifications[i] = getClassification(classifier, space, addr, pointedFrom, pointsTo, objectInfo);
                    classifications[i] = getClassification(classifier, space, addr, null, null, obj.getInfo());
                    i++;
                }
                set = new ArrayWrapper(classifications);
            }
        }
        // Store classification(s) for object and add new map entry if necessary
        writeClassification(set);
        curObject++;
        lastAddress = addr;
        lastSize = obj.getSize();
        // Store object address
        addresses.writeUnknown((lastAddress - firstAddress) / heapWordSize);
    }

    private void writeClassification(Object set) {
        PixelDescription px;
        synchronized (classificationColors) {
            if (!classificationColors.containsKey(set)) {
                combinations++;
                if (set == PixelDescription.FILTERED) {
                    px = new PixelDescription(Color.GRAY, set, combinations, true);
                } else if (globalColorMap != null && globalColorMap.containsKey(set)) {
                    px = new PixelDescription(globalColorMap.get(set), set, combinations);
                } else {
                    px = new PixelDescription(colorGenerator.getNextColor(), set, combinations);
                    if (globalColorMap != null) {
                        globalColorMap.put(set, px.color);
                    }
                }
                classificationColors.put(set, px);
                if (saveMemory) {
                    addNewCombination(set);
                }
            } else {
                px = classificationColors.get(set);
            }
        }
        if (saveMemory) {
            objects.writeUnknown(px.id);
        } else {
            writeClassification(classifications, curObject, px.classification);
        }
    }

    private Object getClassification(Classifier<?> classifier, SpaceInfo space, long addr, long[] pointedFrom, long[] pointsTo, ObjectInfo obj) {
        try {
            Object classification = classifier.classify(null,
                                                        addr,
                                                        obj,
                                                        space,
                                                        obj.type,
                                                        obj.size,
                                                        obj.isArray,
                                                        obj.arrayLength,
                                                        obj.allocationSite,
                                                        pointedFrom,
                                                        pointsTo,
                                                        obj.eventType,
                                                        detailsInfo.getDetailedHeapSupplier().get().rootPtrs.get(addr),
                                                        -1,
                                                        "",
                                                        ""); // TODO age and thread names

            switch (classifier.getType()) {
                case ONE:
                    break;
                case HIERARCHY:
                case MANY: {
                    Object[] keys = (Object[]) classification;
                    classification = new ArrayWrapper(keys);
                    break;
                }
                case MANY_HIERARCHY: {
                    Object[][] keys = (Object[][]) classification;
                    classification = new ArrayWrapper(keys);
                }
            }
            return classification;
        } catch (Exception e) {
            return null;
        }
    }

    private void addNewCombination(Object set) {
        if (arrayLevel == 1) {
            if (combinations < ((Object[]) classifications).length) {
                ((Object[]) classifications)[(int) combinations] = set;
            } else {
                int newLength;
                switch (((Object[]) classifications).length) {
                    case Byte.MAX_VALUE + 1:
                        newLength = Short.MAX_VALUE + 1;
                        classifications = Arrays.copyOf((Object[]) classifications, newLength);
                        ((Object[]) classifications)[(int) combinations] = set;
                        break;
                    case Short.MAX_VALUE + 1:
                        newLength = ByteCollection.POINTER_ARRAY_LENGTH;
                        classifications = Arrays.copyOf((Object[]) classifications, newLength);
                        ((Object[]) classifications)[(int) combinations] = set;
                        break;
                    default:
                        arrayLevel++;
                        Object[][] arr = new Object[ByteCollection.POINTER_ARRAY_LENGTH][];
                        arr[0] = ((Object[]) classifications);
                        arr[1] = new Object[ByteCollection.POINTER_ARRAY_LENGTH];
                        arr[1][0] = set;
                        classifications = arr;
                }
                logger.info("Resized classifications array");
            }
        } else if (arrayLevel == 2) {
            if (combinations <= Integer.MAX_VALUE) {
                int[] indices = ByteCollection.getIndicesForPosition(combinations, 2, ByteCollection.POINTER_ARRAY_LENGTH, ByteCollection.POINTER_ARRAY_LENGTH);
                if (((Object[][]) classifications)[indices[0]] == null) {
                    ((Object[][]) classifications)[indices[0]] = new Object[ByteCollection.POINTER_ARRAY_LENGTH];
                }
                ((Object[][]) classifications)[indices[0]][indices[1]] = set;
            } else {
                logger.info("Revert memory saving");
                revertMemorySaving();
            }
        }
    }

    private void writeClassification(Object arr, long idx, Object set) {
        int[] indices = ByteCollection.getIndicesForPosition(idx, arrayLevel, ByteCollection.POINTER_ARRAY_LENGTH, ByteCollection.POINTER_ARRAY_LENGTH);
        switch (arrayLevel) {
            case 1:
                ((Object[]) arr)[indices[0]] = set;
                break;
            case 2:
                ((Object[][]) arr)[indices[0]][indices[1]] = set;
                break;
            case 3:
                ((Object[][][]) arr)[indices[0]][indices[1]][indices[2]] = set;
                break;
            case 4:
                ((Object[][][][]) arr)[indices[0]][indices[1]][indices[2]][indices[3]] = set;
                break;
            default:
                break;
        }
    }

    private void revertMemorySaving() {
        saveMemory = false;
        // Compute needed array levels
        arrayLevel = 1;
        long cur = ByteCollection.POINTER_ARRAY_LENGTH;
        while (objectCount / cur > 0) {
            cur *= ByteCollection.POINTER_ARRAY_LENGTH;
            arrayLevel++;
        }
        Object arr;
        switch (arrayLevel) {
            case 2:
                arr = new Object[(int) (objectCount / ByteCollection.POINTER_ARRAY_LENGTH + (objectCount % ByteCollection.POINTER_ARRAY_LENGTH == 0 ?
                                                                                             0 :
                                                                                             1))][ByteCollection.POINTER_ARRAY_LENGTH];
                break;
            case 3:
                arr = new Object[(int) (objectCount / (ByteCollection.POINTER_ARRAY_LENGTH * (long) ByteCollection.POINTER_ARRAY_LENGTH) + (objectCount % (ByteCollection
                                                                                                                                                                   .POINTER_ARRAY_LENGTH * (long) ByteCollection.POINTER_ARRAY_LENGTH) == 0 ?
                                                                                                                                            0 :
                                                                                                                                            1))][ByteCollection
                        .POINTER_ARRAY_LENGTH][ByteCollection.POINTER_ARRAY_LENGTH];
                break;
            case 4:
                arr = new Object[(int) (objectCount / (ByteCollection.POINTER_ARRAY_LENGTH * (long) ByteCollection.POINTER_ARRAY_LENGTH * ByteCollection
                        .POINTER_ARRAY_LENGTH) + (
                        objectCount % (ByteCollection.POINTER_ARRAY_LENGTH * (long) ByteCollection.POINTER_ARRAY_LENGTH * ByteCollection.POINTER_ARRAY_LENGTH) == 0 ?
                        0 :
                        1))][ByteCollection.POINTER_ARRAY_LENGTH][ByteCollection.POINTER_ARRAY_LENGTH][ByteCollection.POINTER_ARRAY_LENGTH];
                break;
            default: // This should never happen
                arr = null;
                logger.warning("WTF?");
        }
        for (long i = 0; i < objects.size(); i++) {
            if (task.isCancelled()) {
                throw new CancellationException();
            }
            writeClassification(arr, i, getClassificationsForObject(i));
        }
        classifications = arr;
        objects = null;
    }

    public Object getClassificationsForByte(long idx) {
        long address = idx / heapWordSize;
        long addrIdx = addresses.binarySearch(address);
        long gapIdx = gaps.binarySearch(address);
        if (gapIdx < 0) {
            return getClassificationsForObject(addrIdx);
        }
        long addrAddr = addresses.readLong(addrIdx);
        long gapAddr = gaps.readLong(gapIdx);
        if (address - addrAddr < address - gapAddr) {
            return getClassificationsForObject(addrIdx);
        } else {
            return PixelDescription.GAP;
        }
    }

    public Object getClassificationsForObject(long idx) {
        if (idx < 0) {
            return null;
        }
        if (saveMemory) {
            int id = objects.readInt(idx);
            if (arrayLevel == 1) {
                return ((Object[]) classifications)[id];
            } else {
                int[] indices = ByteCollection.getIndicesForPosition(id, 2, ByteCollection.POINTER_ARRAY_LENGTH, ByteCollection.POINTER_ARRAY_LENGTH);
                return ((Object[][]) classifications)[indices[0]][indices[1]];
            }
        } else {
            int[] indices = ByteCollection.getIndicesForPosition(idx, arrayLevel, ByteCollection.POINTER_ARRAY_LENGTH, ByteCollection.POINTER_ARRAY_LENGTH);
            switch (arrayLevel) {
                case 1:
                    return ((Object[]) classifications)[indices[0]];
                case 2:
                    return ((Object[][]) classifications)[indices[0]][indices[1]];
                case 3:
                    return ((Object[][][]) classifications)[indices[0]][indices[1]][indices[2]];
                case 4:
                    return ((Object[][][][]) classifications)[indices[0]][indices[1]][indices[2]][indices[3]];
            }
            return null;
        }
    }

    public void generatePointers(long[] startingIndices, int level, boolean toPointers, PointersTask task) {
        long[] startingAddresses = new long[startingIndices.length];
        Set<Long> allAddresses = new HashSet<>();
        for (int i = 0; i < startingAddresses.length; i++) {
            if (task.isCancelled()) {
                throw new CancellationException();
            }
            long address = firstAddress + addresses.readLong(startingIndices[i]) * heapWordSize;
            startingAddresses[i] = address;
            allAddresses.add(address);
        }
        int curLevel = 0;
        boolean hasMorePointers = true;
        while (curLevel < level && hasMorePointers) {
            if (task.isCancelled()) {
                throw new CancellationException();
            }
            hasMorePointers = false;
            long[][] nextAddresses = new long[startingAddresses.length][];
            int count = 0;
            for (int i = 0; i < startingAddresses.length; i++) {
                if (task.isCancelled()) {
                    throw new CancellationException();
                }
                long address = startingAddresses[i];
                if (address >= 0) {// null ptrs are encoded with -1 (see at.jku.mevss.trace.parser.base.TraceSlaveParser)
                    // if(filteredObjects){//If objects were filtered, we'd like to exclude pointers to/from filtered objects
                    // long index = addresses.binarySearch(address);
                    // if(getClassificationsForObject(index) == PixelDescription.FILTERED){
                    // continue;
                    // }
                    // }
                    Space space = detailsInfo.getDetailedHeapSupplier().get().getSpace(address);
                    if (space != null) {
                        if (toPointers) {
                            // TODO New heap model does not support pointers as array, rework
                            //nextAddresses[i] = space.getObject(address).pointers;
                            nextAddresses[i] = new long[0];
                        } else {// fromPointers
                            // TODO New heap model does not support pointers as array, rework
                            //nextAddresses[i] = space.getObject(address).fromPointers;
                            nextAddresses[i] = new long[0];
                        }
                    } else {
                        logger.warning(String.format("Pointer adddress %,d has no space", address));
                    }
                    if (nextAddresses[i] != null) {
                        count += nextAddresses[i].length;
                    }
                }
            }
            long[] nextLevel = new long[count];
            int actualCount = 0;
            for (int i = 0; i < nextAddresses.length; i++) {
                if (task.isCancelled()) {
                    throw new CancellationException();
                }
                if (nextAddresses[i] != null) {
                    for (int j = 0; j < nextAddresses[i].length; j++) {
                        if (!allAddresses.contains(nextAddresses[i][j])) {
                            nextLevel[actualCount] = nextAddresses[i][j];
                            allAddresses.add(nextAddresses[i][j]);
                            actualCount++;
                        }
                    }
                }
            }
            hasMorePointers = actualCount > 0;
            startingAddresses = Arrays.copyOfRange(nextLevel, 0, actualCount);
            curLevel++;
        }
        if (toPointers) {
            pointers = allAddresses;
            pointersLevel = level;
        } else {
            fromPointers = allAddresses;
            fromPointersLevel = level;
        }
    }

    public boolean isPointerAddress(long objectIndex) {
        long address = firstAddress + addresses.readLong(objectIndex) * heapWordSize;
        return (pointers != null && pointers.contains(address)) || (fromPointers != null && fromPointers.contains(address));
    }

    // TODO Just for testing, remove me later
    public Set<Long> getPointers() {
        return pointers;
    }

    public HeapStateObjectInfo getDetailedObjInfoForByte(long idx) {
        long addr = idx / heapWordSize;
        long addrIdx = addresses.binarySearch(addr);
        long gapIdx = gaps.binarySearch(addr);
        long gapAddr;
        if (gapIdx < 0) {
            gapAddr = -1;
        } else {
            gapAddr = gaps.readLong(gapIdx);
        }
        long addrAddr = addresses.readLong(addrIdx);
        if (addr - addrAddr < addr - gapAddr) {
            long address = firstAddress + addrAddr * heapWordSize;
            Object classifications = getClassificationsForObject(addrIdx);
            ObjectInfo info = null;
            AllocationSite allocationSite = null;
            AllocatedType type = null;
            int size = -1;
            try {
                info = detailsInfo.getDetailedHeapSupplier().get().getSpace(address).getObject(address).getInfo();
                Symbols symbols = appInfo.getSymbols();
                allocationSite = info.allocationSite;
                type = info.type;
                size = info.size;
            } catch (TraceException e) {
                // TODO Maybe notify view?
            }
            return new HeapStateObjectInfo(address, classifications, allocationSite, type, size);
        } else {
            return null;
        }
    }

    public HeapStateObjectInfo getDetailedObjInfoForObj(long idx) {
        long address = firstAddress + addresses.readLong(idx) * heapWordSize;
        Object classifications = getClassificationsForObject(idx);
        ObjectInfo info = null;
        AllocationSite allocationSite = null;
        AllocatedType type = null;
        int size = -1;
        try {
            info = detailsInfo.getDetailedHeapSupplier().get().getSpace(address).getObject(address).getInfo();
            Symbols symbols = appInfo.getSymbols();
            allocationSite = info.allocationSite;
            type = info.type;
            size = info.size;
        } catch (TraceException e) {
            // TODO Maybe notify view?
        }
        return new HeapStateObjectInfo(address, classifications, allocationSite, type, size);
    }

    public long getObjectStartAddressForByte(long idx) {
        long address = idx / heapWordSize;
        long addrIdx = addresses.binarySearch(address);
        if (addrIdx < 0) {
            return 0;
        }
        long gapIdx = gaps.binarySearch(address);
        long addrAddr = addresses.readLong(addrIdx);
        if (gapIdx < 0) {
            return addrAddr * heapWordSize;
        }

        long gapAddr = gaps.readLong(gapIdx);
        if (address - addrAddr < address - gapAddr) {
            return addrAddr * heapWordSize;
        } else {
            return (address) * heapWordSize;
        }
    }

    public long getObjectEndAddressForByte(long idx) {
        long address = idx / heapWordSize;
        long addrIdx = addresses.binarySearch(address);
        long gapIdx = gaps.binarySearch(address);
        if (gapIdx >= 0 && gapIdx < gapCount) {
            long gapAddr = gaps.readLong(gapIdx);
            long addrAddr = addresses.readLong(addrIdx);
            if (gapAddr > addrAddr) {// We're within a gap, just return the original index
                return (address) * heapWordSize;
            }
        }
        addrIdx = addresses.binarySearch(address) + 1;
        if (addrIdx >= objectCount) {
            return getByteCount();
        }
        gapIdx = gaps.binarySearch(address) + 1;
        long addrAddr = addresses.readLong(addrIdx);
        if (gapIdx >= gapCount) {
            return addrAddr * heapWordSize - 1;
        }
        long gapAddr = gaps.readLong(gapIdx);
        if (addrAddr < gapAddr) {
            return addrAddr * heapWordSize - 1;
        } else {
            return gapAddr * heapWordSize - 1;
        }
    }

    public PixelDescription getPixelDescriptionForByte(long idx) {
        long address = idx / heapWordSize;
        long addrIdx = addresses.binarySearch(address);
        long gapIdx = gaps.binarySearch(address);
        if (gapIdx < 0) {
            return getPixelDescriptionForObject(addrIdx);
        } else if (addrIdx < 0) {
            return PixelDescription.GAP_PD;
        }
        long addrAddr = addresses.readLong(addrIdx);
        long gapAddr = gaps.readLong(gapIdx);
        if (address - addrAddr < address - gapAddr) {
            return getPixelDescriptionForObject(addrIdx);
        } else {
            return PixelDescription.GAP_PD;
        }
    }

    public Map<PixelDescription, Long> getPixelDescriptionsForBytes(long idx, long size, boolean showPointers, AtomicBoolean isInPointerSet) {
        Map<PixelDescription, Long> result = new HashMap<>();
        long address = idx / heapWordSize;
        long addrIdx = addresses.binarySearch(address);
        long gapIdx = gaps.binarySearch(address);
        long addrAddr = Long.MAX_VALUE;
        if (addrIdx >= 0) {
            addrAddr = addresses.readLong(addrIdx);
        }
        long gapAddr = Long.MAX_VALUE;
        if (gapIdx >= 0) {
            gapAddr = gaps.readLong(gapIdx);
        }
        long minAddr;
        long minNextAddr;
        long nextAddrAddr;
        long nextGapAddr;
        addrIdx++;
        if (addrIdx < addresses.size()) {
            nextAddrAddr = addresses.readLong(addrIdx);
        } else {
            nextAddrAddr = Long.MAX_VALUE;
        }
        gapIdx++;
        if (gapIdx < gaps.size()) {
            nextGapAddr = gaps.readLong(gapIdx);
        } else {
            nextGapAddr = Long.MAX_VALUE;
        }
        minNextAddr = nextAddrAddr < nextGapAddr ? nextAddrAddr : nextGapAddr;
        long bytes = lastSize;
        if (minNextAddr < Long.MAX_VALUE) {
            bytes = (minNextAddr * heapWordSize - idx);
        }
        size -= bytes;
        if (gapAddr == Long.MAX_VALUE || idx - addrAddr * heapWordSize < idx - gapAddr * heapWordSize) {
            result.put(getPixelDescriptionForObject(addrIdx - 1), bytes);
            if (showPointers) {
                isInPointerSet.set(isPointerAddress(addrIdx - 1));
            }
        } else {
            result.put(PixelDescription.GAP_PD, bytes);
        }
        while (size > 0 && addrIdx < addresses.size()) {
            PixelDescription px;
            if (nextAddrAddr < nextGapAddr) {
                addrAddr = nextAddrAddr;
                minAddr = addrAddr;
                px = getPixelDescriptionForObject(addrIdx);
                if (showPointers && !isInPointerSet.get()) {
                    isInPointerSet.set(isPointerAddress(addrIdx));
                }
                addrIdx++;
                if (addrIdx < addresses.size()) {
                    nextAddrAddr = addresses.readLong(addrIdx);
                } else {
                    nextGapAddr = Long.MAX_VALUE;
                }
            } else {
                gapAddr = nextGapAddr;
                minAddr = gapAddr;
                px = PixelDescription.GAP_PD;
                gapIdx++;
                if (gapIdx < gaps.size()) {
                    nextGapAddr = gaps.readLong(gapIdx);
                } else {
                    nextGapAddr = Long.MAX_VALUE;
                }
            }
            minNextAddr = nextAddrAddr < nextGapAddr ? nextAddrAddr : nextGapAddr;
            bytes = lastSize;
            if (minNextAddr < Long.MAX_VALUE) {
                bytes = (minNextAddr - minAddr) * heapWordSize;
            }
            long value = bytes < size ? bytes : size;
            Long res = result.get(px);
            if (res == null) {
                result.put(px, value);
            } else {
                result.put(px, value + res);
            }
            size -= bytes;
        }
        return result;
    }

    public long[] getObjectIndicesForBytes(long idx, long size) {
        List<Long> ids = new ArrayList<>();
        long address = idx / heapWordSize;
        long addrIdx = addresses.binarySearch(address);
        long gapIdx = gaps.binarySearch(address);
        long addrAddr = Long.MAX_VALUE;
        if (addrIdx >= 0) {
            addrAddr = addresses.readLong(addrIdx);
        }
        long gapAddr = Long.MAX_VALUE;
        if (gapIdx >= 0) {
            gapAddr = gaps.readLong(gapIdx);
        }
        long minAddr;
        long minNextAddr;
        long nextAddrAddr;
        long nextGapAddr;
        addrIdx++;
        if (addrIdx < addresses.size()) {
            nextAddrAddr = addresses.readLong(addrIdx);
        } else {
            nextAddrAddr = Long.MAX_VALUE;
        }
        gapIdx++;
        if (gapIdx < gaps.size()) {
            nextGapAddr = gaps.readLong(gapIdx);
        } else {
            nextGapAddr = Long.MAX_VALUE;
        }
        minNextAddr = nextAddrAddr < nextGapAddr ? nextAddrAddr : nextGapAddr;
        long bytes = lastSize;
        if (minNextAddr < Long.MAX_VALUE) {
            bytes = (minNextAddr * heapWordSize - idx);
        }
        size -= bytes;
        if (gapAddr == Long.MAX_VALUE || idx - addrAddr * heapWordSize < idx - gapAddr * heapWordSize) {
            ids.add(addrIdx - 1);
        } // else there's a gap
        while (size > 0 && addrIdx < addresses.size()) {
            if (nextAddrAddr < nextGapAddr) {
                addrAddr = nextAddrAddr;
                minAddr = addrAddr;
                ids.add(addrIdx);
                addrIdx++;
                if (addrIdx < addresses.size()) {
                    nextAddrAddr = addresses.readLong(addrIdx);
                } else {
                    nextGapAddr = Long.MAX_VALUE;
                }
            } else {
                gapAddr = nextGapAddr;
                minAddr = gapAddr;
                gapIdx++;
                if (gapIdx < gaps.size()) {
                    nextGapAddr = gaps.readLong(gapIdx);
                } else {
                    nextGapAddr = Long.MAX_VALUE;
                }
            }
            minNextAddr = nextAddrAddr < nextGapAddr ? nextAddrAddr : nextGapAddr;
            bytes = lastSize;
            if (minNextAddr < Long.MAX_VALUE) {
                bytes = (minNextAddr - minAddr) * heapWordSize;
            }
            size -= bytes;
        }
        Long[] boxed = ids.toArray(new Long[0]);
        return Stream.of(boxed).mapToLong(Long::longValue).toArray();
    }

    public PixelDescription getPixelDescriptionForObject(long idx) {
        return classificationColors.get(getClassificationsForObject(idx));
    }

    public PixelDescription getPixelDescriptionForClassification(Object key) {
        if (key == PixelDescription.GAP) {
            return PixelDescription.GAP_PD;
        }
        return classificationColors.get(key);
    }

    public long getObjectCount() {
        return objectCount;
    }

    public long getGapCount() {
        return gapCount;
    }

    public long getByteCount() {
        return lastAddress - firstAddress + lastSize;
    }

    private static class ArrayWrapper {
        private final Object[] array;

        public ArrayWrapper(Object[] array) {
            this.array = array;
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            if (array.length > 0) {
                sb.append(array[0].toString());
            }
            for (int i = 1; i < array.length; i++) {
                sb.append(", " + array[i].toString());
            }
            return sb.toString();
        }

        @Override
        public int hashCode() {
            return Arrays.hashCode(array);
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof ArrayWrapper) {
                return Arrays.deepEquals(array, ((ArrayWrapper) obj).array);
            } else if (obj instanceof Object[]) {
                return Arrays.deepEquals(array, (Object[]) obj);
            }
            return false;
        }

    }

    public ClassifierChain getSelectedClassifiers() {
        return selectedClassifiers;
    }

    @Override
    public Iterator<BytesPixelDescription> iterator() {
        return new BytesIterator();
    }

    public Iterator<BytesPixelDescription> iterator(long startIndex) {
        return new BytesIterator(startIndex, getByteCount());
    }

    public Iterator<BytesPixelDescription> iterator(long startIndex, long endIndex) {
        return new BytesIterator(startIndex, endIndex);
    }

    public class BytesIterator implements Iterator<BytesPixelDescription> {
        private long nextGapAddr;
        private long nextObjAddr;
        private long gapIdx;
        private long addrIdx;
        private long endIdx;
        private long endGapAddr;

        public BytesIterator() {
            if (gaps.size() > 0) {
                nextGapAddr = gaps.readLong(0);
            } else {
                nextGapAddr = Long.MAX_VALUE;
            }
            nextObjAddr = addresses.readLong(0);
            gapIdx = 0;
            addrIdx = 0;
            endIdx = objectCount;
            endGapAddr = Long.MAX_VALUE;
        }

        public BytesIterator(long startIndex, long endIndex) {
            endGapAddr = Long.MAX_VALUE;
            if (endIndex >= getByteCount()) {
                endIdx = objectCount;
            } else {
                long address = (endIndex) / heapWordSize;
                endIdx = addresses.binarySearch(address);
                long endIdxGap = gaps.binarySearch(address);
                if (endIdxGap >= 0) {
                    long endObjAddr = addresses.readLong(endIdx);
                    long endGapAddr = gaps.readLong(endIdxGap);
                    if (endGapAddr > endObjAddr) {
                        this.endGapAddr = address * heapWordSize;
                        endIdx = objectCount;
                    }
                }
            }
            long address = startIndex / heapWordSize;
            addrIdx = addresses.binarySearch(address);
            gapIdx = gaps.binarySearch(address);
            nextObjAddr = addresses.readLong(addrIdx);
            if (gapIdx < 0) {
                gapIdx = 0;
                if (gaps.size() > 0) {
                    nextGapAddr = gaps.readLong(0);
                } else {
                    nextGapAddr = Long.MAX_VALUE;
                }
            } else {
                nextGapAddr = gaps.readLong(gapIdx);
                if (nextGapAddr > nextObjAddr) {
                    addrIdx++;
                    nextObjAddr = addresses.readLong(addrIdx);
                    nextGapAddr = address;
                } else {
                    gapIdx++;
                    if (gapIdx < gapCount) {
                        nextGapAddr = gaps.readLong(gapIdx);
                    } else {
                        nextGapAddr = Long.MAX_VALUE;
                    }
                }
            }

        }

        @Override
        public boolean hasNext() {
            return addrIdx < endIdx;
        }

        @Override
        public BytesPixelDescription next() {
            long minAddr;
            long index;
            PixelDescription px;
            if (nextObjAddr < nextGapAddr) {
                minAddr = nextObjAddr;
                px = getPixelDescriptionForObject(addrIdx);
                index = addrIdx;
                addrIdx++;
                if (addrIdx < objectCount) {
                    nextObjAddr = addresses.readLong(addrIdx);
                } else {
                    return new BytesPixelDescription(px, lastSize, index);
                }
            } else {
                minAddr = nextGapAddr;
                gapIdx++;
                if (gapIdx < gapCount) {
                    nextGapAddr = gaps.readLong(gapIdx);
                } else {
                    nextGapAddr = Long.MAX_VALUE;
                }
                px = PixelDescription.GAP_PD;
                index = -1;
            }
            long minEnd;
            if (nextObjAddr < nextGapAddr) {
                minEnd = nextObjAddr;
            } else {
                minEnd = nextGapAddr;
            }
            if (endGapAddr <= minEnd) {
                minEnd = endGapAddr;
                addrIdx = endIdx; // hack
            }
            long size = (minEnd - minAddr) * heapWordSize;
            return new BytesPixelDescription(px, size, index);
        }

    }

    public long getAddressForObject(long i) {
        return addresses.readLong(i);
    }

    public int getPointersLevel() {
        return pointersLevel;
    }

    public int getFromPointersLevel() {
        return fromPointersLevel;
    }

    public List<Filter> getSelectedFilters() {
        return selectedFilters;
    }

    public Map<Object, PixelDescription> getClassificationColors() {
        return Collections.unmodifiableMap(classificationColors);
    }

    public void setColorForClassification(Object classification, Color color) {
        classificationColors.get(classification).color = color;
    }

    public Color getColorForClassification(Object classification) {
        return classificationColors.get(classification).color;
    }

    public long getFirstAddress() {
        return firstAddress;
    }

    // After calling this method, the methods generateData(), generatePointers(), getDetailedObjInfoForObj() and getDetailedObjInfoForByte()
    // won't work any more
    public void clearHeap() {
        detailsInfo.setHeap(null);
    }
}
