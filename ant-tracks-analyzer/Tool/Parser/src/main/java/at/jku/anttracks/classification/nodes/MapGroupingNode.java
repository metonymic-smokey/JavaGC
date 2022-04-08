
package at.jku.anttracks.classification.nodes;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.heap.Heap;
import at.jku.anttracks.util.Counter;
import javafx.beans.property.LongProperty;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MapGroupingNode extends DetailedHeapGroupingNode {
    protected static final Logger LOGGER = Logger.getLogger(MapGroupingNode.class.getSimpleName());

    /**
     * This member is set if this is a non-leaf node. If this
     * {@link GroupingNode} instance is a leaf node, this must be null.
     */
    private Class<? extends Classifier<?>> classifier;

    /**
     * This member is set if this is a non-leaf node. If this
     * {@link GroupingNode} instance is a leaf node, this must be null.
     */
    private final Map<Object, DetailedHeapGroupingNode> children;

    /**
     * This member contains all leafs (objects that are no further grouped). It
     * reflects the amount of objects that meet all grouping criterias on the
     * way from the root grouping to this group.
     */
    private final HeapObjectMapGroupingMap data;

    /**
     * This member is set for every {@link GroupingNode} instance,
     * except root. It gives the parent grouping.
     */
    private GroupingNode parent;

    private final Object[] fullKey;
    private final String fullKeyAsString;

    /**
     * This member is set for every {@link GroupingNode} instance. It
     * represents the level of this {@link GroupingNode} instance
     * within its grouping tree. The root grouping is on level 0, its children
     * on level 1, and so on.
     */
    private final int level;

    /**
     * A grouping instance may contain multiple subtrees. Such a subtree can be
     * detected, if the parents subTreeLevel is lower than this' subTreeLevel.
     */
    private int subTreeLevel;

    /**
     * This map contains an entry for each duplicate classification in children.
     * E.g., if a multi-classifier classifies an object in 3 children, this map
     * will contain two entries for that object.
     */
    private final HeapObjectMapGroupingMap duplicates;

    private long summedObjectsExact = -1;
    private double summedObjectsSampled = -1;
    private long summedBytesExact = -1;
    private long summedBytesIncludingSampled = -1;
    private boolean isCalculatedOnSampling = false;

    public MapGroupingNode() {
        this(null, 0, 0, null, "Overall");
    }

    public MapGroupingNode(Class<? extends Classifier<?>> classifier, int level, int subTreeLevel, GroupingNode parent, Object key) {
        children = new HashMap<>();
        data = new HeapObjectMapGroupingMap();
        this.duplicates = new HeapObjectMapGroupingMap();
        this.parent = parent;
        this.fullKey = new Object[level + 1];
        if (this.parent != null) {
            System.arraycopy(parent.getFullKey(), 0, this.fullKey, 0, level);
        }
        this.fullKey[level] = key;
        this.fullKeyAsString = Arrays.stream(fullKey).map(Object::toString).collect(Collectors.joining("#"));
        this.level = level;
        this.subTreeLevel = subTreeLevel;
        this.classifier = classifier;
    }

    /**
     * Copy constructur that only should be used when reading tree from file
     *
     * @param classifier
     * @param level
     * @param subTreeLevel
     * @param fullKey
     * @param data
     * @param duplicates
     * @param basedOnSampling
     * @param children
     */
    private MapGroupingNode(MapGroupingNode parent,
                            Class<? extends Classifier<?>> classifier,
                            int level,
                            int subTreeLevel,
                            Object[] fullKey,
                            HeapObjectMapGroupingMap data,
                            HeapObjectMapGroupingMap duplicates,
                            boolean basedOnSampling,
                            Map<Object, DetailedHeapGroupingNode> children) {
        this.parent = parent;
        this.classifier = classifier;
        this.level = level;
        this.subTreeLevel = subTreeLevel;
        this.fullKey = fullKey;
        this.fullKeyAsString = Arrays.stream(fullKey).map(Object::toString).collect(Collectors.joining("#"));
        this.data = data;
        this.duplicates = duplicates;
        this.isCalculatedOnSampling = basedOnSampling;
        this.children = children;
    }

    public static void writeTree(DataOutputStream writer, MapGroupingNode node) {
        try {
            // 1. Classifier
            if (node.classifier != null) {
                writer.writeUTF(node.classifier.newInstance().getName());
            } else {
                writer.writeUTF("");
            }
            // 2. Level
            writer.writeInt(node.level);
            // 3. SubTreeLevel
            writer.writeInt(node.subTreeLevel);
            // 4. Key
            writer.writeInt(node.fullKey.length);
            for (int i = 0; i < node.fullKey.length; i++) {
                // TODO: Key gets converted to String here, no real serialization
                writer.writeUTF(node.fullKey[i].toString());
            }
            // 5. Data
            writer.writeInt(node.data.entrySet().size());
            for (Entry<HeapObjectGroupingKey, GroupingCounter> entry : node.data.entrySet()) {
                writer.writeLong(entry.getKey().objectSizeInBytes);
                writer.writeLong(entry.getValue().exact.get());
                writer.writeDouble(entry.getValue().sampled.get());
            }
            // 6. Duplicates
            writer.writeInt(node.duplicates.entrySet().size());
            for (Entry<HeapObjectGroupingKey, GroupingCounter> entry : node.duplicates.entrySet()) {
                writer.writeLong(entry.getKey().objectSizeInBytes);
                writer.writeLong(entry.getValue().exact.get());
                writer.writeDouble(entry.getValue().sampled.get());
            }
            // 7. Sampling
            writer.writeBoolean(node.isCalculatedOnSampling);
            // 8. Children
            writer.writeInt(node.children.size());
            node.children.values().forEach(child -> writeTree(writer, (MapGroupingNode) child));
        } catch (IOException | IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static MapGroupingNode readTree(DataInputStream reader, MapGroupingNode parent, HashMap<String, Classifier<?>> availableClassifierInfo) {
        try {
            // 1. Classifier
            String classifierName = reader.readUTF();
            Classifier<?> classifier = null;
            if (!classifierName.equals("")) {
                classifier = availableClassifierInfo.get(classifierName);
            }

            // 2. Level
            int level = reader.readInt();

            // 3. SubTreeLevel
            int subTreeLevel = reader.readInt();

            // 4. Key
            int keyLength = reader.readInt();
            Object[] key = new Object[keyLength];
            for (int i = 0; i < keyLength; i++) {
                key[i] = reader.readUTF();
            }

            // 5. Data
            int dataSize = reader.readInt();
            HeapObjectMapGroupingMap data = new HeapObjectMapGroupingMap();
            for (int i = 0; i < dataSize; i++) {
                long size = reader.readLong();
                long exact = reader.readLong();
                double sampled = reader.readDouble();
                data.put(new HeapObjectGroupingKey(size), new GroupingCounter(exact, sampled, true));
            }

            // 6. Duplicates
            int duplicateSize = reader.readInt();
            HeapObjectMapGroupingMap duplicates = new HeapObjectMapGroupingMap();
            for (int i = 0; i < duplicateSize; i++) {
                long size = reader.readLong();
                long exact = reader.readLong();
                double sampled = reader.readDouble();
                duplicates.put(new HeapObjectGroupingKey(size), new GroupingCounter(exact, sampled, true));
            }

            // 7. Sampling
            boolean basedOnSampling = reader.readBoolean();

            // 8. Children
            int childCount = reader.readInt();
            Map<Object, DetailedHeapGroupingNode> children = new HashMap<>();
            MapGroupingNode node = new MapGroupingNode(parent,
                                                       classifier != null ? (Class<? extends Classifier<?>>) classifier.getClass() : null,
                                                       level,
                                                       subTreeLevel,
                                                       key,
                                                       data,
                                                       duplicates,
                                                       basedOnSampling,
                                                       children);

            for (int i = 0; i < childCount; i++) {
                MapGroupingNode child = readTree(reader, node, availableClassifierInfo);
                if (child == null) {
                    throw new IllegalStateException("Failed to parse a MapGroupingNode!");
                }
                children.put(child.getKey(), child);
            }

            return node;

        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    protected void addDuplicate(HeapObjectGroupingKey key, long n) {
        if (!duplicates.containsKey(key)) {
            duplicates.put(key, new GroupingCounter(0, 0, true));
        }
        duplicates.get(key).exact.add(n);
    }

    public GroupingCounter addLeaf(HeapObjectGroupingKey key, long n) {
        if (!data.containsKey(key)) {
            data.put(key, new GroupingCounter(0, 0, false));
        }
        data.get(key).exact.add(n);
        return data.get(key);
    }

    /**
     * Executes a depth-first-traversal to clear the whole tree, inculsive
     * destroying its structure.
     */
    public void clear() {
        for (GroupingNode g : children.values()) {
            g.clear();
        }
        children.clear();
        parent = null;
    }

    @Override
    public void setCalculatedOnSampling(boolean b) {
        this.isCalculatedOnSampling = b;
    }

    public boolean containsChild(Object key) {
        return children.containsKey(key);
    }

    public GroupingNode addChild(Class<? extends Classifier<?>> classifier, int level, int subTreeLevel, Object key) {
        children.put(key, new MapGroupingNode(classifier, level, subTreeLevel, this, key));
        return children.get(key);
    }

    public GroupingNode merge(GroupingNode other, int refSubTreeLevel) {
        if (!(other instanceof MapGroupingNode)) {
            throw new Error("Can only merge nodes of same type");
        }

        // General merging activities
        if (classifier == null && other.getClassifier() != null) {
            classifier = other.getClassifier();
        } else {
            if (other.getClassifier() != null) {
                assert classifier.getName() == other.getClassifier().getName() : String.format("Classifier must match on merge if set on both groupings (%s <-> %s)",
                                                                                               classifier,
                                                                                               other.getClassifier());
            }
        }
        this.summedObjectsExact = -1;
        this.summedBytesIncludingSampled = -1;

        // Sum up all leafs on this Grouping
        for (Entry<HeapObjectGroupingKey, GroupingCounter> otherLeaf : ((HeapObjectMapGroupingMap) other.getData(false)).entrySet()) {
            addLeaf(otherLeaf.getKey(), otherLeaf.getValue().exact.get());
        }
        // Sum up all duplicates on this Grouping
        for (Entry<HeapObjectGroupingKey, GroupingCounter> otherDuplicate : ((MapGroupingNode) other).duplicates.entrySet()) {
            addDuplicate(otherDuplicate.getKey(), otherDuplicate.getValue().exact.get());
        }

        // Merge classification groups, recursively repeat in children
        for (GroupingNode otherChild : other.getChildren()) {
            int relSubTreeLevel = refSubTreeLevel + otherChild.getSubTreeLevel();
            GroupingNode child = addChildIfNeeded(otherChild.getClassifier(), otherChild.getKey(), relSubTreeLevel);
            child.merge(otherChild, refSubTreeLevel);
        }
        return this;
    }

    @Override
    public void setObjectCount(double count) {
        this.summedObjectsExact = (long) count;
    }

    @Override
    public void setByteCount(long bytes) {
        this.summedBytesIncludingSampled = bytes;
    }

    @Override
    public List<GroupingNode> getChildren() {
        return new ArrayList<>(children.values());
    }

    @Override
    public Object[] getFullKey() {
        return fullKey;
    }

    @Override
    public String getFullKeyAsString() {
        return fullKeyAsString;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(");
        sb.append(getSubTreeLevel());
        sb.append(") ");
        sb.append(new String(new char[level]).replace("\0", "-"));
        if (level > 0) {
            sb.append(" ");
        }
        sb.append(this.getKey());
        sb.append(" -> ");
        sb.append(((HeapObjectMapGroupingMap) getData(true)).values().stream().mapToDouble(GroupingCounter::get).sum());
        sb.append(" (");
        sb.append(duplicates.values().stream().mapToLong(dup -> dup.exact.get()).sum());
        sb.append(" duplicates subtracted)");
        for (GroupingNode child : children.values()) {
            sb.append("\n");
            sb.append(child.toString());
        }
        sb.append(" ||| Classifier: ");
        sb.append(classifier == null ? "none" : classifier.getName());
        return sb.toString();
    }

    public int getLevel() {
        return level;
    }

    public HeapObjectMapGroupingMap getData(int refSubTreeLevel) {
        HeapObjectMapGroupingMap sumAmount = new HeapObjectMapGroupingMap();

        if (subTreeLevel == refSubTreeLevel) {
            for (Entry<HeapObjectGroupingKey, GroupingCounter> entry : data.entrySet()) {
                sumAmount.put(entry.getKey(), new GroupingCounter(entry.getValue().exact.get(), entry.getValue().sampled.get(), true));
            }
            for (Entry<HeapObjectGroupingKey, GroupingCounter> duplicateEntry : duplicates.entrySet()) {
                if (!sumAmount.containsKey(duplicateEntry)) {
                    sumAmount.put(duplicateEntry.getKey(), new GroupingCounter(0, 0, true));
                }
                sumAmount.get(duplicateEntry.getKey()).exact.sub(duplicateEntry.getValue().exact.get());
            }
        }

        for (GroupingNode child : children.values()) {
            if (!(child instanceof MapGroupingNode)) {
                throw new Error("Child type does not match own type, cannot get data");
            }

            Map<HeapObjectGroupingKey, GroupingCounter> childAmount = ((MapGroupingNode) child).getData(refSubTreeLevel);
            for (Entry<HeapObjectGroupingKey, GroupingCounter> entry : childAmount.entrySet()) {
                if (!sumAmount.containsKey(entry.getKey())) {
                    sumAmount.put(entry.getKey(), new GroupingCounter(0, 0, true));
                }
                sumAmount.get(entry.getKey()).add(entry.getValue());
            }
        }

        return sumAmount;
    }

    @Override
    public long getObjectCount() {
        if (summedObjectsExact < 0) {
            summedObjectsExact = ((HeapObjectMapGroupingMap) getData(true)).entrySet().stream().mapToLong(x -> x.getValue().exact.get()).sum();
        }
        if (summedObjectsSampled < 0) {
            summedObjectsSampled = ((HeapObjectMapGroupingMap) getData(true)).entrySet().stream().mapToDouble(x -> x.getValue().sampled.get()).sum();
        }
        return (long) (summedObjectsExact + summedObjectsSampled);
    }

    @Override
    public long getNonSampledObjectCount() {
        if (summedObjectsExact < 0) {
            summedObjectsExact = ((HeapObjectMapGroupingMap) getData(true)).entrySet().stream().mapToLong(x -> x.getValue().exact.get()).sum();
        }
        return summedObjectsExact;
    }

    @Override
    public long getNonSampledByteCount(Heap heap) {
        if (summedBytesExact < 0) {
            summedBytesExact = ((HeapObjectMapGroupingMap) getData(true)).getNonSampledBytes();
        }
        return summedBytesExact;
    }

    /**
     * @param key
     * @return The child grouping to which the specified key is mapped, or null
     * if this grouping contains no children for the key
     */
    @Override
    public DetailedHeapGroupingNode getChild(Object key) {
        return children.get(key);
    }

    @Override
    public DetailedHeapGroupingNode getChildBasedOnStringKey(String key) {
        for (Entry<Object, DetailedHeapGroupingNode> entry : children.entrySet()) {
            if (entry.getKey().toString().equals(key)) {
                return entry.getValue();
            }
        }
        return null;
    }

    @Override
    public void setSubTreeLevel(int subTreeLevel) {
        this.subTreeLevel = subTreeLevel;
    }

    @Override
    public void addDuplicate(int size, long n) {
        addDuplicate(new HeapObjectGroupingKey(size), n);
    }

    @Override
    public int getSubTreeLevel() {
        return subTreeLevel;
    }

    @Override
    public HeapObjectMapGroupingMap getData() {
        return data;
    }

    @Override
    public GroupingNode getParent() {
        return this.parent;
    }

    @Override
    public GroupingNode sampleTopDown(Heap heap) {
        if (children.size() == 0) {
            // No children, nowhere to push down
            return this;
        }
        if (data.size() > 0) {
            // Push down all our leafs into the children, key by key
            for (HeapObjectGroupingKey key : data.keySet()) {
                GroupingCounter toPush = data.get(key);
                Counter sum = new Counter();
                List<GroupingNode> children = getFirstChildrenOnSameSubtreeLevel();
                for (GroupingNode child : children) {
                    if (!(child instanceof MapGroupingNode)) {
                        throw new Error("Only can sample down into same node type");
                    }

                    GroupingCounter childCounter = ((HeapObjectMapGroupingMap) child.getData(true)).get(key);
                    if (childCounter != null) {
                        sum.add(childCounter.exact);
                    }
                }
                if (sum.get() == 0) {
                    // No child has an entry for the current key, split equally
                    double count = toPush.get() / children.size();
                    for (GroupingNode child : children) {
                        if (!(child instanceof MapGroupingNode)) {
                            throw new Error("Only can sample down into same node type");
                        }

                        GroupingCounter childCount = ((MapGroupingNode) child).addLeaf(key, 0);
                        childCount = ((HeapObjectMapGroupingMap) child.getData()).get(key);
                        childCount.sampled.add(count);
                    }
                } else {
                    for (GroupingNode child : children) {
                        if (!(child instanceof MapGroupingNode)) {
                            throw new Error("Only can sample down into same node type");
                        }

                        GroupingCounter childCount = ((HeapObjectMapGroupingMap) child.getData(true)).get(key);
                        if (childCount != null) {
                            GroupingCounter childCounter = ((HeapObjectMapGroupingMap) child.getData()).get(key);
                            if (childCounter == null) {
                                childCounter = ((MapGroupingNode) child).addLeaf(key, 0);
                            }
                            childCounter.sampled.add(toPush.get() * childCount.get() / sum.get());
                            child.setCalculatedOnSampling(true);
                        }
                    }
                }
            }
            if (getFirstChildrenOnSameSubtreeLevel().size() > 0) {
                // Check that leafs were pushed down (= there is at least one
                // child)
                data.clear();
            }
        }
        for (GroupingNode child : getChildren()) {
            child.sampleTopDown(heap);
        }
        return this;
    }

    @Override
    public boolean isCalculatedOnSampling() {
        return isCalculatedOnSampling;
    }

    @Override
    public Class<? extends Classifier<?>> getClassifier() {
        return classifier;
    }

    @Override
    public long getByteCount(Heap heap) {
        if (summedBytesIncludingSampled < 0) {
            summedBytesIncludingSampled = getData(true).getBytes(heap);
        }
        return (long) summedBytesIncludingSampled;
    }

    @Override
    public void fillClosureSizes(Heap heap,
                                 boolean calculateTransitiveClosure,
                                 boolean calculateGCClosure,
                                 boolean calculateDataStructureClosure,
                                 boolean calculateDeepDataStructureClosure) {
        // nothing to do
    }

    @Override
    public LongProperty transitiveClosureSizeProperty() {
        return null;   // can't calculate without object identity!
    }

    @Override
    public boolean isClosureSizeCalculated() {
        return false;
    }

    @Override
    public LongProperty retainedSizeProperty() {
        return null;   // can't calculate without object identity!
    }

    @Override
    public boolean isGCSizeCalculated() {
        return false;
    }

    @Override
    public LongProperty dataStructureSizeProperty() {
        return null;   // can't calculate without object identity!
    }

    @Override
    public boolean isDataStructureSizeCalculated() {
        return false;
    }

    @Override
    public LongProperty deepDataStructureSizeProperty() {
        return null;
    }

    @Override
    public boolean isDeepDataStructureSizeCalculated() {
        return false;
    }

    @Override
    public boolean getAndSetIsClosuresBeingCalculated(boolean newValue) {
        return false;
    }

    @Override
    public void setClassifier(Class<? extends Classifier<?>> classifier) {
        this.classifier = classifier;
    }

    @Override
    public void addLeaf(int size, long n) {
        addLeaf(new HeapObjectGroupingKey(size), n);
    }
}
