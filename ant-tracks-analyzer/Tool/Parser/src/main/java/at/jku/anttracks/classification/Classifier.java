
package at.jku.anttracks.classification;

import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.features.FeatureMap;
import at.jku.anttracks.heap.Closures;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.heap.space.SpaceInfo;
import at.jku.anttracks.heap.symbols.*;
import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.parser.heap.ThreadInfo;
import at.jku.anttracks.util.AnnotationHelper;
import at.jku.anttracks.util.ImagePack;
import javafx.scene.image.ImageView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;
import java.util.logging.Logger;

public abstract class Classifier<T> implements Comparable<Classifier<T>> {
    public enum ClassificationMode {
        MAP,
        LIST
    }

    public static final ClassificationMode CLASSIFICATION_MODE = ClassificationMode.LIST;

    private Supplier<Symbols> symbolsSupplier = null;
    private Supplier<IndexBasedHeap> fastHeapSupplier = null;

    protected boolean iconsLoaded = false;
    protected ImagePack[] icons = null;
    protected ImageView[] iconNodes = null;
    private boolean isCustom = false;
    private String sourceCode = null;

    private static Logger LOGGER = Logger.getLogger(Classifier.class.getSimpleName());

    public void setup(Supplier<Symbols> symbolsSupplier) {
        this.symbolsSupplier = symbolsSupplier;
        this.fastHeapSupplier = null;
    }

    public void setup(Supplier<Symbols> symbolsSupplier, Supplier<IndexBasedHeap> fastHeapSupplier) {
        this.symbolsSupplier = symbolsSupplier;
        this.fastHeapSupplier = fastHeapSupplier;
    }

    public void uninstall() {
        this.symbolsSupplier = null;
        this.fastHeapSupplier = null;
    }

    @Override
    public String toString() {
        String s = getName();
        return s != null ? s : super.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((getName() == null) ? 0 : getName().hashCode());
        result = prime * result + ((getDesc() == null) ? 0 : getDesc().hashCode());
        result = prime * result + ((getExample() == null) ? 0 : getExample().hashCode());
        result = prime * result + ((getType() == null) ? 0 : getType().hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        Classifier<?> other = (Classifier<?>) obj;
        return other.getName().equals(getName()) && other.getDesc().equals(getDesc()) && other.getExample().equals(getExample()) && other.getType().equals(getType());
    }

    protected IndexBasedHeap fastHeap() {
        if (fastHeapSupplier == null) {
            LOGGER.warning("FastHeap supplier is null! Probably setup() hasn't been called.");
            return null;
        }
        IndexBasedHeap h = fastHeapSupplier.get();
        if (h == null) {
            LOGGER.warning("FastHeap is null! Supplier returned null.");
            return null;
        }
        return h;
    }

    protected Symbols symbols() {
        if (symbolsSupplier == null) {
            LOGGER.warning("Symbols supplier is null! Probably setup() hasn't been called.");
            return null;
        }
        Symbols s = symbolsSupplier.get();
        if (s == null) {
            LOGGER.warning("Symobls is null! Supplier returned null.");
            return null;
        }
        return s;
    }

    protected FeatureMap features() {
        Symbols s = symbols();
        if (s != null) {
            return s.features;
        }
        return null;
    }

    protected AllocationSites allocationSites() {
        Symbols s = symbols();
        if (s != null) {
            return s.sites;
        }
        return null;
    }

    protected AllocatedTypes types() {
        Symbols s = symbols();
        if (s != null) {
            return s.types;
        }
        return null;
    }

    public ImagePack getIcon(Object key) {
        if (!iconsLoaded) {
            // Load classifierIcon only once
            icons = loadIcons();
            loadIconNodes();
            iconsLoaded = true;
        }

        if (icons != null && icons.length > 0) {
            return icons[0];
        } else {
            return null;
        }
    }

    /**
     * Method that is invoked once to load the classifierIcon.
     *
     * @return Returns the classifier's classifierIcon, or null if no classifierIcon is available
     */
    protected ImagePack[] loadIcons() { return null; }

    public ImageView getIconNode(Object key) {
        if (!iconsLoaded) {
            icons = loadIcons();
            loadIconNodes();
            iconsLoaded = true;
        }

        if (iconNodes != null && iconNodes.length > 0) {
            return iconNodes[0];
        } else {
            return null;
        }
    }

    protected void loadIconNodes() {
        if (icons != null) {
            iconNodes = new ImageView[icons.length];
            for (int i = 0; i < icons.length; i++) {
                ImagePack icon = icons[i];
                iconNodes[i] = icon.getAsNewNode();
            }
        }
    }

    public boolean isCustom() {
        return isCustom;
    }

    public void setIsCustom(boolean b) {
        isCustom = b;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public void setSourceCode(String sc) {
        sourceCode = sc;
    }

    public boolean isOnTheFlyCompilable() {
        return isCustom && sourceCode != null && !sourceCode.equals("");
    }

    protected ClassifierType type = null;

    /**
     * Gets the classifier's type
     *
     * @return The classifier's type
     */
    public ClassifierType getType() {
        if (type == null) {
            C annotation = AnnotationHelper.getAnnotation(this,
                                                          C
                                                                  .class);
            type = annotation != null ? annotation.type() : ClassifierType.ONE;
        }
        return type;
    }

    protected String desc = null;

    /**
     * Gets the classifier's description
     *
     * @return The classifier's description
     */
    public String getDesc() {
        if (desc == null) {
            C annotation = AnnotationHelper.getAnnotation(this,
                                                          C
                                                                  .class);
            desc = annotation != null ? annotation.desc() : "No description";
        }
        return desc;
    }

    protected String example = desc;

    /**
     * Gets the classifier's example
     *
     * @return The classifier's example
     */
    public String getExample() {
        if (example == null) {
            C annotation = AnnotationHelper.getAnnotation(this,
                                                          C
                                                                  .class);
            example = annotation != null ? annotation.example() : "No example";
        }
        return example;
    }

    protected String name = null;

    /**
     * Gets the classifier's name
     *
     * @return The classifier's name
     */
    public String getName() {
        if (name == null) {
            C annotation = AnnotationHelper.getAnnotation(this,
                                                          C
                                                                  .class);
            name = annotation != null ? annotation.name() : "No name";
        }
        return name;
    }

    protected ClassifierSourceCollection sourceCollection;

    /**
     * Gets the classifier's name
     *
     * @return The classifier's name
     */
    public ClassifierSourceCollection getSourceCollection() {
        if (sourceCollection == null) {
            C annotation = AnnotationHelper.getAnnotation(this,
                                                          C
                                                                  .class);
            sourceCollection = annotation != null ? annotation.collection() : ClassifierSourceCollection.ALL;
        }
        return sourceCollection;
    }

    @SuppressWarnings("rawtypes")
    public ClassifierProperty<?>[] configurableProperties() {
        List<Field> fields = new ArrayList<Field>();
        for (Class<?> c = getClass(); c != null; c = c.getSuperclass()) {
            fields.addAll(Arrays.asList(c.getDeclaredFields()));
        }
        return fields.stream()
                     .filter(field -> field.getAnnotation(at.jku.anttracks.classification.annotations.ClassifierProperty.class) != null)
                     .map(field -> new ClassifierProperty<>(this, field))
                     .toArray(ClassifierProperty[]::new);
    }

    @Override
    public int compareTo(Classifier<T> other) {
        return getName().compareTo(other.getName());
    }

    ThreadLocal<AddressHO> currentObject = ThreadLocal.withInitial(() -> null);

    ThreadLocal<Integer> currentObjIndex = ThreadLocal.withInitial(() -> -1);

    ThreadLocal<Long> currentAddress = ThreadLocal.withInitial(() -> (long) -1);
    ThreadLocal<ObjectInfo> currentObjectInfo = ThreadLocal.withInitial(() -> null);
    ThreadLocal<SpaceInfo> currentSpace = ThreadLocal.withInitial(() -> null);
    ThreadLocal<AllocatedType> currentType = ThreadLocal.withInitial(() -> null);
    ThreadLocal<Integer> currentSize = ThreadLocal.withInitial(() -> -1);
    ThreadLocal<Boolean> currentIsArray = ThreadLocal.withInitial(() -> false);
    ThreadLocal<Integer> currentArrayLength = ThreadLocal.withInitial(() -> -1);
    ThreadLocal<AllocationSite> currentAllocationSite = ThreadLocal.withInitial(() -> null);
    ThreadLocal<long[]> currentPointedFrom = ThreadLocal.withInitial(() -> null);
    ThreadLocal<long[]> currentPointsTo = ThreadLocal.withInitial(() -> null);
    ThreadLocal<EventType> currentEventType = ThreadLocal.withInitial(() -> null);
    ThreadLocal<List<? extends RootPtr>> currentRootPointers = ThreadLocal.withInitial(() -> null);
    ThreadLocal<Integer> currentAge = ThreadLocal.withInitial(() -> -1);
    ThreadLocal<String> currentInternalThreadName = ThreadLocal.withInitial(() -> "unknown");
    ThreadLocal<String> currentExternalThreadName = ThreadLocal.withInitial(() -> "unknown");

    private boolean fastHeapUsed() {
        return currentObjIndex.get() >= 0;
    }

    protected AddressHO object() { return currentObject.get(); }

    protected int index() { return currentObjIndex.get(); }

    protected long address() {
        return fastHeapUsed() ? fastHeap().getAddress(currentObjIndex.get()) : currentAddress.get();
    }

    protected ObjectInfo objectInfo() {
        return fastHeapUsed() ? fastHeap().getObjectInfo(currentObjIndex.get()) : currentObjectInfo.get();
    }

    protected SpaceInfo space() {
        return fastHeapUsed() ? fastHeap().getSpace(currentObjIndex.get()) : currentSpace.get();
    }

    protected AllocatedType type() {
        return fastHeapUsed() ? fastHeap().getType(currentObjIndex.get()) : currentType.get();
    }

    protected int size() {
        return fastHeapUsed() ? fastHeap().getSize(currentObjIndex.get()) : currentSize.get();
    }

    protected boolean isArray() {
        return fastHeapUsed() ? fastHeap().isArray(currentObjIndex.get()) : currentIsArray.get();
    }

    protected int arrayLength() {
        return fastHeapUsed() ? fastHeap().getArrayLength(currentObjIndex.get()) : currentArrayLength.get();
    }

    protected AllocationSite allocationSite() {
        return fastHeapUsed() ? fastHeap().getAllocationSite(currentObjIndex.get()) : currentAllocationSite.get();
    }

    protected long[] pointedFrom() throws ClassifierException {
        if (fastHeapUsed()) {
            throw new ClassifierException("Pointed from addresses can only be obtained from Heap iteration, not FastHeap");
        } else {
            return currentPointedFrom.get();
        }
    }

    protected long[] pointsTo() throws ClassifierException {
        if (fastHeapUsed()) {
            throw new ClassifierException("Points to addresses can only be obtained from Heap iteration, not FastHeap");
        } else {
            return currentPointsTo.get();
        }
    }

    protected int[] pointedFromIndices() throws ClassifierException {
        if (!fastHeapUsed()) {
            throw new ClassifierException("Pointed from indices can only be obtained from FastHeap iteration, not DetailedHeap");
        } else {
            return fastHeap().getFromPointers(currentObjIndex.get());
        }
    }

    protected int[] pointsToIndices() throws ClassifierException {
        if (!fastHeapUsed()) {
            throw new ClassifierException("Points to indices can only be obtained from FastHeap iteration, not DetailedHeap");
        } else {
            return fastHeap().getToPointers(currentObjIndex.get());
        }
    }

    protected EventType eventType() {
        return fastHeapUsed() ? fastHeap().getEventType(currentObjIndex.get()) : currentEventType.get();
    }

    protected RootPtr closestGCRoot(int maxDepth, boolean onlyVariables) throws ClassifierException {
        if (!fastHeapUsed()) {
            throw new ClassifierException("Root pointer information can only be obtained from FastHeap iteration, not DetailedHeap");
        } else {
            return fastHeap().findClosestRoot(currentObjIndex.get(), maxDepth, onlyVariables);
        }
    }

    protected List<RootPtr> indirectGCRoots(int maxDepth) throws ClassifierException {
        if (!fastHeapUsed()) {
            throw new ClassifierException("Root pointer information can only be obtained from FastHeap iteration, not DetailedHeap");
        } else {
            //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("indirectGCRoots");
            //Version 1:
            List<RootPtr> x = fastHeap().indirectGCRoots(currentObjIndex.get(), maxDepth);
            //m.end();

            return x;

            // Version 2:
            /*
            ArrayList<RootPtr> rootPtrs = new ArrayList<>();
            BitSet fromClosure = fastHeap().fromClosure(currentObjIndex.get());
            for (int idx = fromClosure.nextSetBit(0); idx != -1; idx = fromClosure.nextSetBit(idx + 1)) {
                List<RootPtr> rootPtrAtAddress = fastHeap().getRootPointerBlocking().get(idx);
                if (rootPtrAtAddress != null) {
                    rootPtrs.addAll(rootPtrAtAddress);
                }
            }
            m.end();
            return rootPtrs;
            */

            // Version 3:
            /*
            int currentObjectIdx = currentObjIndex.get();
            List<RootPtr> rootPtrs = new ArrayList<>();
            fastHeap().getRootPointerBlocking().forEach((integer, rootPtrList) -> {
                if (rootPtrList.get(0).reachables.get(currentObjectIdx)) {
                    rootPtrs.addAll(rootPtrList);
                }
            });

            m.end();
            return rootPtrs;
            */

            // Version 4:
            /*
            int currentObjectIdx = currentObjIndex.get();
            RootPtr[] rootPtrs = fastHeap().getRootPtrListBlocking().parallelStream().filter(rootPtr -> rootPtr.reachables.get(currentObjectIdx)).toArray(RootPtr[]::new);

            m.end();
            return rootPtrs;
            */

            // Version 5:
            /*
            int currentObjectIdx = currentObjIndex.get();
            Stream<RootPtr> rootPtrs = fastHeap().getRootPtrListBlocking().parallelStream().filter(rootPtr -> rootPtr.reachables.get(currentObjectIdx));

            m.end();
            return rootPtrs;
            */
        }
    }

    protected List<RootPtr.RootInfo> traceIndirectRootPointers(int maxDepth, boolean onlyVariables) throws ClassifierException {
        if (!fastHeapUsed()) {
            throw new ClassifierException("Root pointer information can only be obtained from FastHeap iteration, not DetailedHeap");
        } else {
            return fastHeap().traceAllRootsDFS(currentObjIndex.get(), maxDepth, onlyVariables);
        }
    }

    protected List<RootPtr.RootInfo> traceClosestRoots(int maxDepth, boolean onlyVariables) throws ClassifierException {
        if (!fastHeapUsed()) {
            throw new ClassifierException("Root pointer information can only be obtained from FastHeap iteration, not DetailedHeap");
        } else {
            return fastHeap().traceClosestRootBFS(currentObjIndex.get(), maxDepth, onlyVariables);
        }
    }

    protected List<? extends RootPtr> rootPointers() {
        return fastHeapUsed() ? fastHeap().getRoot(currentObjIndex.get()) : currentRootPointers.get();
    }

    protected Closures closures() throws ClassifierException {
        if (!fastHeapUsed()) {
            throw new ClassifierException("Closure information can only be obtained from FastHeap iteration, not DetailedHeap");
        } else {
            return fastHeap().getClosures(true, true, true, true, currentObjIndex.get());
        }
    }

    protected int age() throws ClassifierException {
        if (!fastHeapUsed()) {
            return currentAge.get();
        } else {
            throw new ClassifierException("Age information can currently only be obtained from DetailedHeaps");
        }
    }

    protected String internalThreadName() {
        if (!fastHeapUsed()) {
            return currentInternalThreadName.get();
        } else {
            return fastHeap().getObjectInfo(index()).thread;
        }
    }

    protected String externalThreadName() {
        if (!fastHeapUsed()) {
            return currentExternalThreadName.get();
        } else {
            ThreadInfo ti = fastHeap().threadsByInternalName.get(fastHeap().getObjectInfo(index()).thread);
            if (ti == null) {
                return "unknown thread name";
            } else {
                return ti.threadName;
            }
        }
    }

    protected abstract T classify() throws Exception;

    public T classify(Integer objIndex) throws Exception {
        currentObjIndex.set(objIndex);
        return classify();
    }

    public T classify(
            AddressHO object,
            long address,
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
            List<? extends RootPtr> rootPointers,
            int age,
            String internalThreadName,
            String externalThreadName) throws Exception {
        currentObject.set(object);
        currentObjIndex.set(-1);
        currentObjectInfo.set(objectInfo);
        currentAddress.set(address);
        currentSpace.set(space);
        currentType.set(type);
        currentSize.set(size);
        currentIsArray.set(isArray);
        currentArrayLength.set(arrayLength);
        currentAllocationSite.set(allocationSite);
        currentPointedFrom.set(pointedFrom);
        currentPointsTo.set(pointsTo);
        currentEventType.set(eventType);
        currentRootPointers.set(rootPointers);
        currentAge.set(age);
        currentInternalThreadName.set(internalThreadName);
        currentExternalThreadName.set(externalThreadName);
        return classify();
    }
}
