package at.jku.anttracks.classification.nodes;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.heap.Heap;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.util.Counter;
import at.jku.anttracks.util.ProgressListener;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This grouping node is a light variant of the ListGroupingNode.
 * It stores the same metrics but they cannot be recalculated because the objects they are based on are not stored anymore.
 */
public class DataGroupingNode implements GroupingNode {

    private final Object[] fullKey;
    private final String fullKeyAsString;
    private final Map<String, DataGroupingNode> children;  // TODO if containsChild(key) is not used often, children should be stored in a more compact way
    private DataGroupingNode parent;

    // TODO are these 3 fields needed? (save memory)
    private Class<? extends Classifier<?>> classifier;
    private int level;
    private int subTreeLevel;

    // metrics
    private long objectCount;
    private long byteCount;
    private long deepSize;
    private long retainedSize;
    private long dataStructureSize;
    private long deepDataStructureSize;

    /**
     * Use this constructor to transform a list grouping tree into a (less memory intensive) DataGroupingNode tree.
     *
     * @param listGroupingNode the root of an existing list grouping tree, if a non root node is given, only the subtree starting with this node is transformed.
     */
    public DataGroupingNode(IndexBasedHeap heap, ListGroupingNode listGroupingNode) {
        this.fullKey = listGroupingNode.getFullKey();
        this.fullKeyAsString = listGroupingNode.getFullKeyAsString();
        this.classifier = listGroupingNode.getClassifier();
        children = new HashMap<>();
        listGroupingNode.getChildren().forEach(listGroupingChild -> {
            DataGroupingNode child = new DataGroupingNode(heap, (ListGroupingNode) listGroupingChild);
            children.put(child.getKey().toString(), child);
            child.parent = this;
        });
        this.level = listGroupingNode.getLevel();
        this.subTreeLevel = listGroupingNode.getSubTreeLevel();

        // metrics
        this.objectCount = listGroupingNode.getObjectCount();
        this.byteCount = listGroupingNode.getByteCount(heap);
        this.deepSize = listGroupingNode.transitiveClosureSizeProperty().get();
        this.retainedSize = listGroupingNode.retainedSizeProperty().get();
        this.dataStructureSize = listGroupingNode.dataStructureSizeProperty().get();
        this.deepDataStructureSize = listGroupingNode.deepDataStructureSizeProperty().get();
    }

    public DataGroupingNode(MapGroupingNode mapGroupingNode) {
        this.fullKey = mapGroupingNode.getFullKey();
        this.fullKeyAsString = mapGroupingNode.getFullKeyAsString();
        this.classifier = mapGroupingNode.getClassifier();
        this.children = new HashMap<>();
        mapGroupingNode.getChildren().forEach(mapGroupingChild -> {
            DataGroupingNode child = new DataGroupingNode((MapGroupingNode) mapGroupingChild);
            children.put(child.getKey().toString(), child);
            child.parent = this;
        });
        this.level = mapGroupingNode.getLevel();
        this.subTreeLevel = mapGroupingNode.getSubTreeLevel();

        // metrics
        this.objectCount = mapGroupingNode.getObjectCount();
        this.byteCount = mapGroupingNode.getByteCount(null);
        this.deepSize = -1;
        this.retainedSize = -1;
        this.dataStructureSize = -1;
        this.deepDataStructureSize = -1;
    }

    public DataGroupingNode(
            DataGroupingNode parent,
            Object[] fullKey,
            Class<? extends Classifier<?>> classifier,
            int level,
            int subTreeLevel,
            long objectCount,
            long byteCount,
            long deepSize,
            long retainedSize,
            long dataStructureSize,
            long deepDataStructureSize) {
        this.parent = parent;
        this.fullKey = fullKey;
        this.fullKeyAsString = Arrays.stream(fullKey).map(Object::toString).collect(Collectors.joining("#"));
        this.classifier = classifier;

        children = new HashMap<>();
        this.level = level;
        this.subTreeLevel = subTreeLevel;

        // metrics
        this.objectCount = objectCount;
        this.byteCount = byteCount;
        this.deepSize = deepSize;
        this.retainedSize = retainedSize;
        this.dataStructureSize = dataStructureSize;
        this.deepDataStructureSize = deepDataStructureSize;

    }

    @Override
    public void setObjectCount(double count) {
        this.objectCount = (long) count;
    }

    @Override
    public void setByteCount(long bytes) {
        this.byteCount = bytes;
    }

    @Override
    public List<GroupingNode> getChildren() {
        return new ArrayList<>(children.values());
    }

    @Override
    public int getSubTreeLevel() {
        return subTreeLevel;
    }

    @Override
    public GroupingDataCollection getData() {
        throw new UnsupportedOperationException("DataGroupingNode does not store objects!");
    }

    @Override
    public GroupingDataCollection getData(int refSubTree) {
        throw new UnsupportedOperationException("DataGroupingNode does not store objects!");
    }

    @Override
    public GroupingNode getParent() {
        return parent;
    }

    @Override
    public Class<? extends Classifier<?>> getClassifier() {
        return classifier;
    }

    @Override
    public boolean isCalculatedOnSampling() {
        return false;
    }

    @Override
    public long getObjectCount() {
        return objectCount;
    }

    @Override
    public long getByteCount(Heap heap) {
        return byteCount;
    }

    @Override
    public void fillClosureSizes(Heap heap,
                                 boolean calculateTransitiveClosure,
                                 boolean calculateGCClosure,
                                 boolean calculateDataStructureClosure,
                                 boolean calculateDeepDataStructureClosure) {
        throw new UnsupportedOperationException("DataGroupingNode cannot be used for closure calculations!");
    }

    @Override
    public LongProperty transitiveClosureSizeProperty() {
        return new SimpleLongProperty(deepSize);
    }

    @Override
    public boolean isClosureSizeCalculated() {
        return deepSize > 0;
    }

    @Override
    public LongProperty retainedSizeProperty() {
        return new SimpleLongProperty(retainedSize);
    }

    @Override
    public boolean isGCSizeCalculated() {
        return retainedSize > 0;
    }

    @Override
    public LongProperty dataStructureSizeProperty() {
        return new SimpleLongProperty(dataStructureSize);
    }

    @Override
    public boolean isDataStructureSizeCalculated() {
        return dataStructureSize > 0;
    }

    @Override
    public LongProperty deepDataStructureSizeProperty() {
        return new SimpleLongProperty(deepDataStructureSize);
    }

    @Override
    public boolean isDeepDataStructureSizeCalculated() {
        return deepDataStructureSize > 0;
    }

    @Override
    public boolean getAndSetIsClosuresBeingCalculated(boolean newValue) {
        return false;
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
    public void setClassifier(Class<? extends Classifier<?>> classifier) {
        this.classifier = classifier;
    }

    @Override
    public GroupingNode merge(GroupingNode other, int refSubTreeLevel) {
        throw new UnsupportedOperationException("DataGroupingNode is not to be used for merging!");
    }

    @Override
    public boolean containsChild(Object key) {
        return children.containsKey(key);
    }

    @Override
    public int getLevel() {
        return level;
    }

    @Override
    public void clear() {
        for (GroupingNode g : children.values()) {
            g.clear();
        }
        children.clear();
        parent = null;
    }

    @Override
    public void setCalculatedOnSampling(boolean b) { }

    @Override
    public GroupingNode sampleTopDown(Heap heap) {
        throw new UnsupportedOperationException("ObjectMetricsNodes are not to be sampled!");
    }

    @Override
    public long getNonSampledObjectCount() {
        return objectCount;
    }

    @Override
    public long getNonSampledByteCount(Heap heap) {
        return byteCount;
    }

    @Override
    public DataGroupingNode getChild(Object key) {
        return children.get(key);
    }

    @Override
    public GroupingNode getChildBasedOnStringKey(String key) {
        return children.get(key);
    }

    @Override
    public void setSubTreeLevel(int subTreeLevel) {
        this.subTreeLevel = subTreeLevel;
    }

    @Override
    public GroupingNode addChild(Class<? extends Classifier<?>> classifier, int level, int subTreeLevel, Object key) {
        throw new UnsupportedOperationException("Use constructor to create DataGroupingNode trees!");
    }

    @Override
    public DataGroupingNode subtract(Heap myHeap, Heap otherHeap, GroupingNode other) {
        return null;
    }

    public static void writeTree(DataOutputStream writer, DataGroupingNode node, Counter current, long all, ProgressListener progressListener) {
        try {
            // 1. Classifier
            if (node.classifier != null) {
                writer.writeUTF(node.classifier.getName());
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
            // 5. Metrics
            writer.writeLong(node.objectCount);
            writer.writeLong(node.byteCount);
            writer.writeLong(node.deepSize);
            writer.writeLong(node.retainedSize);
            writer.writeLong(node.dataStructureSize);
            writer.writeLong(node.deepDataStructureSize);
            // 6. Children
            writer.writeInt(node.children.size());

            if (progressListener != null && current != null && all > 0) {
                progressListener.fire(1.0 * current.get() / all, null);
                current.inc();
            }

            for (DataGroupingNode child : node.children.values()) {
                writeTree(writer, child, current, all, progressListener);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static DataGroupingNode readTree(DataInputStream reader, DataGroupingNode parent, HashMap<String, Classifier<?>> classifiers) {
        try {
            // 1. Classifier
            String classifierName = reader.readUTF();
            Classifier<?> classifier = null;
            if (!classifierName.equals("")) {
                classifier = classifiers.get(classifierName);
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
            // 5. Metrics
            long objectCount = reader.readLong();
            long byteCount = reader.readLong();
            long deepSize = reader.readLong();
            long retainedSize = reader.readLong();
            long dataStructureSize = reader.readLong();
            long deepDataStructureSize = reader.readLong();
            // 6. Children
            int childCount = reader.readInt();
            Map<String, DataGroupingNode> children = new HashMap<>();
            DataGroupingNode node = new DataGroupingNode(parent,
                                                         (Class<? extends Classifier<?>>) classifier.getClass(),
                                                         level,
                                                         subTreeLevel,
                                                         key,
                                                         objectCount,
                                                         byteCount,
                                                         deepSize,
                                                         retainedSize,
                                                         dataStructureSize,
                                                         deepDataStructureSize,
                                                         children);
            for (int i = 0; i < childCount; i++) {
                DataGroupingNode child = readTree(reader, node, classifiers);
                children.put(child.getKey().toString(), child);

            }
            return node;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * should only be used by readTree(...)
     *
     * @param parent
     * @param classifier
     * @param level
     * @param subTreeLevel
     * @param fullKey
     */
    private DataGroupingNode(DataGroupingNode parent,
                             Class<? extends Classifier<?>> classifier,
                             int level,
                             int subTreeLevel,
                             Object[] fullKey,
                             long objectCount,
                             long byteCount,
                             long deepSize,
                             long retainedSize,
                             long dataStructureSize,
                             long deepDataStructureSize,
                             Map<String, DataGroupingNode> children) {
        this.parent = parent;
        this.classifier = classifier;
        this.level = level;
        this.subTreeLevel = subTreeLevel;
        this.fullKey = fullKey;
        this.fullKeyAsString = Arrays.stream(fullKey).map(Object::toString).collect(Collectors.joining("#"));
        this.objectCount = objectCount;
        this.byteCount = byteCount;
        this.deepSize = deepSize;
        this.retainedSize = retainedSize;
        this.dataStructureSize = dataStructureSize;
        this.deepDataStructureSize = deepDataStructureSize;
        this.children = children;
    }

    DataGroupingNode addChild(DataGroupingNode child) {
        child.parent = this;
        return children.put(child.getKey().toString(), child);
    }
}
