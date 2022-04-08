
package at.jku.anttracks.classification.nodes;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.heap.Closures;
import at.jku.anttracks.heap.Heap;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.ObjectStream;
import at.jku.anttracks.util.Counter;
import at.jku.anttracks.util.ProgressListener;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleLongProperty;
import org.jetbrains.annotations.NotNull;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ListGroupingNode extends FastHeapGroupingNode {
    private static final Logger LOGGER = Logger.getLogger(ListGroupingNode.class.getSimpleName());

    /**
     * This member is set if this is a non-leaf node. If this {@link ListGroupingNode} instance is a leaf node, this must be null.
     */
    private final Map<Object, FastHeapGroupingNode> children;

    /**
     * This member contains all leafs (objects that are no further grouped). It reflects the amount of objects that meet all
     * HeapObjectGrouping criterias on the way from the root HeapObjectGrouping to this group.
     */
    //    private final AddressCollection data;
    private IndexCollection data;

    /**
     * This member is set for every {@link ListGroupingNode} instance, except root. It gives the parent HeapObjectGrouping.
     */
    private ListGroupingNode parent;

    /**
     * This member is set for every {@link ListGroupingNode} instance, except root. It gives the key with which this
     * HeapObjectGrouping is connected to the parent.
     */
    private final Object[] fullKey;
    private final String fullKeyAsString;

    /**
     * This member is set for every {@link ListGroupingNode} instance. It represents the level of this {@link ListGroupingNode}
     * instance within its HeapObjectGrouping tree. The root HeapObjectGrouping is on level 0, its children on level 1, and so on.
     */
    private final int level;

    /**
     * A HeapObjectGrouping instance may contain multiple subtrees. Such a subtree can be detected, if the parents subTreeLevel is lower
     * than this' subTreeLevel.
     */
    private int subTreeLevel;

    private boolean calculatedOnSampling = false;

    private boolean closuresBeingCalculated = false;

    /**
     * This member is set if this is a non-leaf node. If this {@link ListGroupingNode} instance is a leaf node, this must be null.
     */
    private Class<? extends Classifier<?>> classifier;
    private long summedBytes = -1;
    private long sampledBytes = -1;
    private long summedObjects = -1;
    private long sampledObjects = -1;
    private LongProperty closureSizeProperty = new SimpleLongProperty(-1);
    private LongProperty gcSizeProperty = new SimpleLongProperty(-1);
    private LongProperty dataStructureSizeProperty = new SimpleLongProperty(-1);
    private LongProperty deepDataStructureSizeProperty = new SimpleLongProperty(-1);

    public ListGroupingNode() {
        this(null, 0, 0, null, "Overall");
    }

    public ListGroupingNode(Class<? extends Classifier<?>> classifier, int level, int subTreeLevel, ListGroupingNode parent, Object key) {
        children = new HashMap<>();
        //        data = new AddressCollection("Leafs", 50);
        data = new IndexCollection();
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

    // constructor for when parsing tree from file
    private ListGroupingNode(ListGroupingNode parent,
                             Class<? extends Classifier<?>> classifier,
                             int level,
                             int subTreeLevel,
                             Object[] fullKey,
                             long summedObjects,
                             long sampledObjects,
                             long summedBytes,
                             long sampledBytes,
                             long closureSize,
                             long gcSize,
                             long dataStructureSize,
                             long deepDataStructureSize,
                             boolean calculatedOnSampling,
                             Map<Object, FastHeapGroupingNode> children) {
        this.parent = parent;
        this.classifier = classifier;
        this.level = level;
        this.subTreeLevel = subTreeLevel;
        this.fullKey = fullKey;
        this.fullKeyAsString = Arrays.stream(fullKey).map(Object::toString).collect(Collectors.joining("#"));
        this.summedObjects = summedObjects;
        this.sampledObjects = sampledObjects;
        this.summedBytes = summedBytes;
        this.sampledBytes = sampledBytes;
        this.closureSizeProperty.setValue(closureSize);
        this.gcSizeProperty.setValue(gcSize);
        this.dataStructureSizeProperty.setValue(dataStructureSize);
        this.deepDataStructureSizeProperty.setValue(deepDataStructureSize);
        this.calculatedOnSampling = calculatedOnSampling;
        this.children = children;
    }

    @Override
    public void addLeaf(int objIndex, long n) {
        if (n > 1) {
            throw new Error("HeapObjectListGrouping assumes that no address appears more than one time in one node");
        }

        //        data.add(objIndex);
        data.add(objIndex);
    }

    /**
     * Executes a depth-first-traversal to clear the whole tree, inculsive destroying its structure.
     */
    public void clear() {
        for (GroupingNode g : children.values()) {
            g.clear();
        }
        children.clear();
        data.clear();
        parent = null;
    }

    @Override
    public void setCalculatedOnSampling(boolean b) {
        this.calculatedOnSampling = b;
    }

    @Override
    public List<GroupingNode> getChildren() {
        return new ArrayList<>(children.values());
    }

    public Object[] getFullKey() {
        return fullKey;
    }

    @Override
    public String getFullKeyAsString() {
        return fullKeyAsString;
    }

    protected void setFullKey(Object newKey) {
        fullKey[level] = newKey;
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
        // TODO: Fix object count in toString() since old version could take very long
        // sb.append(getObjectCount())
        sb.append("?");
        sb.append(" (");
        sb.append("0"); // TODO: Duplicates in toString
        sb.append(" duplicates subtracted)");
        for (GroupingNode child : children.values()) {
            sb.append("\n");
            sb.append(child.toString());
        }
        return sb.toString();
    }

    public int getLevel() {
        return level;
    }

    /**
     * @param key
     * @return The child HeapObjectGrouping to which the specified key is mapped, or null if this HeapObjectGrouping contains no children
     * for the key
     */
    @Override
    public FastHeapGroupingNode getChild(Object key) {
        return children.get(key);
    }

    @Override
    public FastHeapGroupingNode getChildBasedOnStringKey(String key) {
        for (Map.Entry<Object, FastHeapGroupingNode> entry : children.entrySet()) {
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
    public int getSubTreeLevel() {
        return subTreeLevel;
    }

    @Override
    public IndexCollection getData() {
        return data;
    }

    @Override
    public IndexCollection getData(int refSubTreeLevel) {
        return getDataRec(refSubTreeLevel);
    }

    private IndexCollection getDataRec(int refSubTreeLevel) {
        IndexCollection ret = subTreeLevel == refSubTreeLevel ? (IndexCollection) getData(false) : new IndexCollection();

        List<GroupingNode> children = getFirstChildrenOnSubtreeLevel(refSubTreeLevel);
        if (children.isEmpty()) {
            return ret;
        }

        IndexCollection[] childData = new IndexCollection[children.size()];
        for (int i = 0; i < childData.length; i++) {
            childData[i] = ((ListGroupingNode) children.get(i)).getDataRec(refSubTreeLevel);
        }

        ret.unionWith(childData);

        return ret;
    }

    @Override
    public void setObjectCount(double count) {
        this.summedObjects = (long) count;
    }

    @Override
    public void setByteCount(long bytes) {
        this.summedBytes = bytes;
    }

    @Override
    public ListGroupingNode getParent() {
        return this.parent;
    }

    @Override
    public ListGroupingNode sampleTopDown(Heap heap) {
        if (children.size() == 0) {
            // No children, nowhere to push down
            return this;
        }
        if (data.getObjectCount() > 0 || sampledBytes > 0 || sampledObjects > 0) {
            List<? extends GroupingNode> children = getFirstChildrenOnSameSubtreeLevel();
            if (children.size() > 0) {
                // Push down all our leafs into the children
                long totalChildObjCounts = 0;
                long totalChildByteCounts = 0;

                for (GroupingNode child : children) {
                    if (!(child instanceof ListGroupingNode)) {
                        throw new Error("Only can sample down into same node type");
                    }
                    totalChildObjCounts += child.getObjectCount();
                    totalChildByteCounts += child.getByteCount(heap);
                }

                if (totalChildObjCounts < getObjectCount()) {
                    // distribute the bytes/objs that are missing in children
                    // retain relative portions of children on total bytes/objs
                    for (GroupingNode c : children) {
                        ListGroupingNode child = (ListGroupingNode) c;
                        child.sampledObjects = Math.round((getObjectCount() - totalChildObjCounts) * ((double) child.getObjectCount() / totalChildObjCounts));
                        child.sampledBytes = Math.round((getByteCount(heap) - totalChildByteCounts) * ((double) child.getByteCount(heap) / totalChildByteCounts));
                        child.calculatedOnSampling = true;
                    }
                }
            }
        }

        for (GroupingNode child : getChildren()) {
            child.sampleTopDown(heap);
        }

        return this;
    }

    @Override
    public boolean isCalculatedOnSampling() {
        return calculatedOnSampling;
    }

    // ---------------------------------------------------------
    // ---------------- CLASSIFICATION -------------------------
    // ---------------------------------------------------------

    @Override
    public GroupingNode addChild(Class<? extends Classifier<?>> classifier, int level, int subTreeLevel, Object key) {
        children.put(key, new ListGroupingNode(classifier, level, subTreeLevel, this, key));
        return children.get(key);
    }

    // --------------------------------------------
    // --------------------------------------------

    @Override
    public Class<? extends Classifier<?>> getClassifier() {
        return classifier;
    }

    @Override
    public long getObjectCount() {
        if (summedObjects < 0) {
            IndexCollection accumulatedData = (IndexCollection) getData(true);
            summedObjects = (long) accumulatedData.getObjectCount();
        }
        return summedObjects + (sampledObjects > 0 ? sampledObjects : 0);
    }

    @Override
    public long getByteCount(Heap heap) {
        if (summedBytes < 0) {
            IndexCollection accumulatedData = (IndexCollection) getData(true);
            summedBytes = accumulatedData.getBytes(heap);
        }
        return summedBytes + (sampledBytes > 0 ? sampledBytes : 0);
    }

    @Override
    public long getNonSampledObjectCount() {
        if (summedObjects < 0) {
            IndexCollection accumulatedData = (IndexCollection) getData(true);
            summedObjects = (long) accumulatedData.getObjectCount();
        }
        return summedObjects;
    }

    @Override
    public long getNonSampledByteCount(Heap heap) {
        if (summedBytes < 0) {
            IndexCollection accumulatedData = (IndexCollection) getData(true);
            summedBytes = accumulatedData.getBytes(heap);
        }
        return summedBytes;
    }

    @Override
    public synchronized void fillClosureSizes(Heap heap,
                                              boolean calculateTransitiveClosure,
                                              boolean calculateGCClosure,
                                              boolean calculateDataStructureClosure,
                                              boolean calculateDeepDataStructureClosure) {
        // note: closure is not calculated if only ds closures are unset (because for non-ds-head objects they cannot be set)
        if (gcSizeProperty.get() < 0 || closureSizeProperty.get() < 0) {
            IndexCollection data = (IndexCollection) getData(true);
            Closures closures = data.calculateClosures((IndexBasedHeap) heap,
                                                       calculateTransitiveClosure,
                                                       calculateGCClosure,
                                                       calculateDataStructureClosure,
                                                       calculateDeepDataStructureClosure);
            gcSizeProperty.set(closures.getGCClosureByteCount());
            closureSizeProperty.set(closures.getTransitiveClosureByteCount());
            dataStructureSizeProperty.set(closures.getDataStructureClosureByteCount());
            deepDataStructureSizeProperty.set(closures.getDeepDataStructureClosureByteCount());
        }
    }

    @Override
    public synchronized LongProperty transitiveClosureSizeProperty() {
        return closureSizeProperty;
    }

    @Override
    public boolean isClosureSizeCalculated() {
        return closureSizeProperty.get() > 0;
    }

    @Override
    public synchronized LongProperty retainedSizeProperty() {
        return gcSizeProperty;
    }

    @Override
    public boolean isGCSizeCalculated() {
        return gcSizeProperty.get() > 0;
    }

    @Override
    public LongProperty dataStructureSizeProperty() {
        return dataStructureSizeProperty;
    }

    @Override
    public boolean isDataStructureSizeCalculated() {
        return dataStructureSizeProperty.get() > 0;
    }

    @Override
    public LongProperty deepDataStructureSizeProperty() {
        return deepDataStructureSizeProperty;
    }

    @Override
    public boolean isDeepDataStructureSizeCalculated() {
        return deepDataStructureSizeProperty.get() > 0;
    }

    @Override
    public synchronized boolean getAndSetIsClosuresBeingCalculated(boolean newValue) {
        boolean ret = closuresBeingCalculated != newValue;
        closuresBeingCalculated = newValue;
        return ret;
    }

    @Override
    public void setClassifier(Class<? extends Classifier<?>> classifier) {
        this.classifier = classifier;
    }

    @Override
    public GroupingNode merge(GroupingNode other, int refSubTreeLevel) {
        this.summedObjects = -1;
        this.summedBytes = -1;
        this.gcSizeProperty.set(-1);
        this.closureSizeProperty.set(-1);
        this.dataStructureSizeProperty.set(-1);
        this.deepDataStructureSizeProperty.set(-1);

        // General merging activities
        if (classifier == null && other.getClassifier() != null) {
            classifier = other.getClassifier();
        } else {
            if (other.getClassifier() != null) {
                assert classifier.getName().equals(other.getClassifier().getName()) : String.format(
                        "Classifier must match on merge if set on both HeapObjectGroupings (%s <-> %s)",
                        classifier,
                        other.getClassifier());
            }
        }

        if (!(other.getData(false) instanceof IndexCollection)) {
            throw new Error("Can only merge nodes of same type");
        }

        // Sum up all leafs on this HeapObjectGrouping
        //long before = this.data.getObjectCount();
        this.data.unionWith(new IndexCollection[]{(IndexCollection) other.getData()});

        // This does not have to be the case for list groupings, since we merge
        // transformers into the main tree.
        // If two objects point to the same object, this object will only exist
        // once in the child node.
        // assert before + other.leafs.getObjectCount() ==
        // this.leafs.getObjectCount() : "Number of objects must match";

        // Merge classification groups, recursively repeat in children
        for (GroupingNode otherChild : other.getChildren()) {
            int relSubTreeLevel = refSubTreeLevel + otherChild.getSubTreeLevel();
            GroupingNode child = addChildIfNeeded(otherChild.getClassifier(), otherChild.getKey(), relSubTreeLevel);
            child.merge(otherChild, refSubTreeLevel);
        }
        return this;
    }

    @Override
    public boolean containsChild(Object key) {
        return children.containsKey(key);
    }

    @Override
    public void addDuplicate(int objIndex, long n) {
        // Nothing to do, list grouping does not have to remember duplicates since it removes them on UNION
    }

    public void setClosureSize(long closureSize) {
        this.closureSizeProperty.set(closureSize);
    }

    public void setGCSize(long gcSize) {
        this.gcSizeProperty.set(gcSize);
    }

    public void setDataStructureSize(long dataStructureSize) {
        this.dataStructureSizeProperty.set(dataStructureSize);
    }

    public void setDeepDataStructureSize(long deepDataStructureSize) {
        this.deepDataStructureSizeProperty.set(deepDataStructureSize);
    }

    public static void writeTree(DataOutputStream writer,
                                 Heap heap,
                                 @NotNull
                                         ListGroupingNode node,
                                 Counter current,
                                 long all,
                                 ProgressListener progressListener) {
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
            // 5. Data
            if (node.summedObjects < 0) {
                node.getObjectCount();
                node.getByteCount(heap);
            }
            writer.writeLong(node.summedObjects);
            writer.writeLong(node.sampledObjects);
            writer.writeLong(node.summedBytes);
            writer.writeLong(node.sampledBytes);
            writer.writeLong(node.closureSizeProperty.get());
            writer.writeLong(node.gcSizeProperty.get());
            writer.writeLong(node.dataStructureSizeProperty.get());
            writer.writeLong(node.deepDataStructureSizeProperty.get());
            // 7. Sampling
            writer.writeBoolean(node.calculatedOnSampling);
            // 8. Children
            writer.writeInt(node.children.size());

            if (progressListener != null && current != null && all > 0) {
                progressListener.fire(1.0 * current.get() / all, null);
                current.inc();
            }
            for (FastHeapGroupingNode child : node.children.values()) {
                writeTree(writer, heap, (ListGroupingNode) child, current, all, progressListener);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    public static ListGroupingNode readTree(DataInputStream reader, ListGroupingNode parent, HashMap<String, Classifier<?>> classifiers) {
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
            // 5. Data
            long summedObjects = reader.readLong();
            long sampledObjects = reader.readLong();
            long summedBytes = reader.readLong();
            long sampledBytes = reader.readLong();
            long closureSize = reader.readLong();
            long gcSize = reader.readLong();
            long dataStructureSize = reader.readLong();
            long deepDataStructureSize = reader.readLong();
            // 7. Sampling
            boolean basedOnSampling = reader.readBoolean();
            // 8. Children
            int childCount = reader.readInt();
            Map<Object, FastHeapGroupingNode> children = new HashMap<>();
            ListGroupingNode node = new ListGroupingNode(parent,
                                                         classifier != null ? (Class<? extends Classifier<?>>) classifier.getClass() : null,
                                                         level,
                                                         subTreeLevel,
                                                         key,
                                                         summedObjects,
                                                         sampledObjects,
                                                         summedBytes,
                                                         sampledBytes,
                                                         closureSize,
                                                         gcSize,
                                                         dataStructureSize,
                                                         deepDataStructureSize,
                                                         basedOnSampling,
                                                         children);
            for (int i = 0; i < childCount; i++) {
                ListGroupingNode child = readTree(reader, node, classifiers);
                children.put(child.getKey(), child);

            }
            return node;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public void locallyClassifyTransformerOnSameSubTreeLevel(IndexBasedHeap heap,
                                                             ClassifierChain classifiers,
                                                             ObjectStream.IterationListener iterationListener,
                                                             BooleanProperty cancellationToken) throws Exception {
        if (subTreeLevel <= 0) {
            throw new IllegalStateException("can only be called on transformer nodes!");
        }

        Map<ListGroupingNode, IndexCollection> dataBackups = new HashMap<>();
        dataBackups.put(this, ((IndexCollection) getData(false)).clone());

        // 1) save data of nodes with: node.STL < this.STL
        for (int stl = subTreeLevel - 1; stl >= 0; stl--) {
            IndexCollection dataToEvacuate = getData(stl);

            ListGroupingNode evacuationDestination = (ListGroupingNode) getFirstParentOnSubTreeLevel(stl);
            if (evacuationDestination == null) {
                // must never be the case since subtreelevel > 0
                throw new IllegalStateException("could not find parent with subtreelevel " + stl);
            }

            dataBackups.put(evacuationDestination, ((IndexCollection) evacuationDestination.getData(false)).clone());

            evacuationDestination.data.unionWith(dataToEvacuate);
        }

        // 2) retrieve data to (re-)classify in this subtree
        IndexCollection dataToClassify = getData(subTreeLevel);

        // 3) delete children
        // 4) classify data from 2) staying on current subtreelevel
        boolean success = locallyClassify(heap, dataToClassify, subTreeLevel, classifiers, iterationListener, cancellationToken);

        // if necessary reset evacuation nodes
        if (!success) {
            dataBackups.forEach((node, dataBackup) -> node.data = dataBackup);
        }
    }

    public void locallyClassifyTransformerOnLowerSubtreeLevel(IndexBasedHeap heap,
                                                              int targetSubTreeLevel,
                                                              ClassifierChain classifiers,
                                                              ObjectStream.IterationListener iterationListener,
                                                              BooleanProperty cancellationToken) throws Exception {
        if (subTreeLevel <= 0) {
            throw new IllegalStateException("can only be called on transformer nodes!");
        }

        if (targetSubTreeLevel >= subTreeLevel) {
            throw new IllegalArgumentException("target subtreelevel must be lower than this nodes subtreelevel");
        }

        Map<ListGroupingNode, IndexCollection> dataBackups = new HashMap<>();
        dataBackups.put(this, ((IndexCollection) getData(false)).clone());

        // 1) save data of nodes with: node.STL < this.STL && node.STL != targetSTL
        for (int stl = targetSubTreeLevel; stl >= 0; stl--) {
            if (stl != targetSubTreeLevel) {
                IndexCollection dataToEvacuate = getData(stl);
                ListGroupingNode evacuationDestination = (ListGroupingNode) getFirstParentOnSubTreeLevel(stl);
                if (evacuationDestination == null) {
                    // must never be the case since subtreelevel > 0
                    throw new IllegalStateException("could not find parent with subtreelevel " + stl);
                }

                dataBackups.put(evacuationDestination, ((IndexCollection) evacuationDestination.getData(false)).clone());

                evacuationDestination.data.unionWith(dataToEvacuate);
            }
        }

        // 2) retrieve data to (re-)classify out of this subtree
        IndexCollection dataToClassify = getData(targetSubTreeLevel);
        if (dataToClassify.isEmpty()) {
            dataToClassify = (IndexCollection) getFirstParentOnSubTreeLevel(targetSubTreeLevel).getData(false);
        }

        // 3) delete children
        // 4) classify data from 2) on parent subtreelevel
        boolean success = locallyClassify(heap, dataToClassify, targetSubTreeLevel, classifiers, iterationListener, cancellationToken);

        // if necessary reset evacuation nodes
        if (!success) {
            dataBackups.forEach((node, dataBackup) -> node.data = dataBackup);
        }
    }

    public void locallyClassify(IndexBasedHeap heap, ClassifierChain classifiers, ObjectStream.IterationListener iterationListener, BooleanProperty cancellationToken)
            throws Exception {
        if (subTreeLevel > 0) {
            throw new IllegalStateException("should only be called on non-transformer nodes!");
        }

        // local classification of regular nodes (subtreelevel 0)
        // (re-)classify the data of this node and of all its children
        IndexCollection dataToClassify = (IndexCollection) getData(true);
        locallyClassify(heap, dataToClassify, 0, classifiers, iterationListener, cancellationToken);
    }

    private boolean locallyClassify(IndexBasedHeap heap,
                                    IndexCollection dataToClassify,
                                    int targetSubTreeLevel,
                                    ClassifierChain classifiers,
                                    ObjectStream.IterationListener iterationListener,
                                    BooleanProperty cancellationToken) throws Exception {
        Map<Object, FastHeapGroupingNode> childrenBackup = new HashMap<>();
        childrenBackup.putAll(children);
        // TODO: must anything else be backed up to rollback state after classify has been called?
        children.clear();

        for (int i = 0; i < dataToClassify.getObjectCount(); i++) {
            if (cancellationToken.get()) {
                // on cancel restore previous children and return
                children.clear();
                children.putAll(childrenBackup);
                return false;
            }
            classify(heap, dataToClassify.get(i), classifiers, 1, targetSubTreeLevel);

            if (i % 1_000 == 0) {
                iterationListener.objectsIterated(1_000);
            }
        }

        return true;
    }
}
