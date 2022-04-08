package at.jku.anttracks.classification.nodes;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.heap.Heap;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import javafx.beans.property.LongProperty;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public interface GroupingNode {

    void setObjectCount(double count);

    void setByteCount(long bytes);

    List<GroupingNode> getChildren();

    int getSubTreeLevel();

    /**
     * @return Returns only local data
     */
    GroupingDataCollection getData();

    /**
     * @param refSubTree
     * @return Returns data of children on given SubTreeLevel, including own data if this node is also on given subtreelevel.
     */
    GroupingDataCollection getData(int refSubTree);

    GroupingNode getParent();

    default GroupingNode getFirstParentOnSubTreeLevel(int subTreeLevel) {
        for (GroupingNode node = getParent(); node != null; node = node.getParent()) {
            if (node.getSubTreeLevel() == subTreeLevel) {
                return node;
            }
        }

        return null;
    }

    Class<? extends Classifier<?>> getClassifier();

    boolean isCalculatedOnSampling();

    long getObjectCount();

    long getByteCount(Heap heap);

    void fillClosureSizes(Heap heap,
                          boolean calculateTransitiveClosure,
                          boolean calculateGCClosure,
                          boolean calculateDataStructureClosure,
                          boolean calculateDeepDataStructureClosure);

    LongProperty transitiveClosureSizeProperty();

    boolean isClosureSizeCalculated();

    LongProperty retainedSizeProperty();

    boolean isGCSizeCalculated();

    LongProperty dataStructureSizeProperty();

    boolean isDataStructureSizeCalculated();

    LongProperty deepDataStructureSizeProperty();

    boolean isDeepDataStructureSizeCalculated();

    boolean getAndSetIsClosuresBeingCalculated(boolean newValue);

    Object[] getFullKey();

    String getFullKeyAsString();

    void setClassifier(Class<? extends Classifier<?>> classifier);

    GroupingNode merge(GroupingNode other, int refSubTreeLevel);

    boolean containsChild(Object key);

    int getLevel();

    void clear();

    void setCalculatedOnSampling(boolean b);

    GroupingNode sampleTopDown(Heap heap);

    long getNonSampledObjectCount();

    long getNonSampledByteCount(Heap heap);

    GroupingNode getChild(Object key);

    GroupingNode getChildBasedOnStringKey(String key);

    void setSubTreeLevel(int subTreeLevel);

    /**
     * FOR INTERNAL USE ONLY
     *
     * @param key
     * @return
     */
    GroupingNode addChild(Class<? extends Classifier<?>> classifier, int level, int subTreeLevel, Object key);

    default GroupingNode addSubTreeChildIfNeeded(Class<? extends Classifier<?>> classifier, Object key) {
        return addChildIfNeeded(classifier, key, this.getSubTreeLevel() + 1);
    }

    default GroupingNode addChildIfNeeded(Class<? extends Classifier<?>> classifier, Object key) {
        return addChildIfNeeded(classifier, key, getSubTreeLevel());
    }

    default GroupingNode addChildIfNeeded(Class<? extends Classifier<?>> classifier, Object key, int subTreeLevel) {
        GroupingNode child = getChild(key);
        if (child == null) {
            child = addChild(classifier, getLevel() + 1, subTreeLevel, key);
        } else {
            String childClassifierName = child.getClassifier() != null ? child.getClassifier().getName() : "";
            String classifierName = classifier != null ? classifier.getName() : "";

            assert childClassifierName.equals(classifierName) && child.getLevel() == getLevel() + 1 && child.getSubTreeLevel() == subTreeLevel :
                    String.format("Found child must match the classifier (%s <-> %s) and level (%d <-> %d) and subtreelevel (%d <-> %d",
                                  childClassifierName,
                                  classifierName,
                                  child.getLevel(),
                                  getLevel() + 1,
                                  child.getSubTreeLevel(),
                                  subTreeLevel);
        }
        return child;
    }

    default GroupingNode merge(GroupingNode other) {
        return merge(other, this.getSubTreeLevel() - other.getSubTreeLevel());
    }

    default List<GroupingNode> getFirstChildrenOnSameSubtreeLevel() {
        ArrayList<GroupingNode> children = new ArrayList<>();
        ArrayDeque<GroupingNode> toCheck = new ArrayDeque<>(getChildren());

        while (!toCheck.isEmpty()) {
            GroupingNode x = toCheck.poll();
            if (x.getSubTreeLevel() == getSubTreeLevel()) {
                children.add(x);
            } else {
                toCheck.addAll(x.getChildren());
            }
        }

        return children;
    }

    default List<GroupingNode> getFirstChildrenOnSubtreeLevel(int subTreeLevel) {
        ArrayList<GroupingNode> children = new ArrayList<>();
        ArrayDeque<GroupingNode> toCheck = new ArrayDeque<>(getChildren());

        while (!toCheck.isEmpty()) {
            GroupingNode x = toCheck.poll();
            if (x.getSubTreeLevel() == subTreeLevel) {
                children.add(x);
            } else {
                toCheck.addAll(x.getChildren());
            }
        }

        return children;
    }

    default GroupingDataCollection getData(boolean recursive) {
        if (recursive) {
            return getData(getSubTreeLevel());
        } else {
            return getData();
        }
    }

    default DataGroupingNode subtractRecursive(Heap myHeap, Heap otherHeap, GroupingNode other) {
        assert this.getFullKeyAsString().equals(other.getFullKeyAsString()) : "Two nodes may only be subtracted if they have the same key";
        DataGroupingNode result = subtract(myHeap, otherHeap, other);
        for (GroupingNode child : getChildren()) {
            GroupingNode otherChild = other.getChild(child.getKey());
            if (otherChild == null) {
                // add this child (and all successors) with unchanged values to the result grouping
                result.addChild(child.subtractRecursive(myHeap, otherHeap, new DataGroupingNode(null,
                                                                                                child.getFullKey(),
                                                                                                child.getClassifier(),
                                                                                                child.getLevel(),
                                                                                                child.getSubTreeLevel(),
                                                                                                0,
                                                                                                0,
                                                                                                0,
                                                                                                0,
                                                                                                0,
                                                                                                0)));
            } else {
                // subtract recursively
                result.addChild(child.subtractRecursive(myHeap, otherHeap, otherChild));
            }
        }

        // now all successors of this node are in the result tree
        // however, there might be successors of the other node which are not handled yet because there is no matching successor of this node
        // thus we also traverse the other node's successors and handle all those for which there is no matching successor of this node

        for (GroupingNode otherChild : other.getChildren()) {
            GroupingNode child = getChild(otherChild.getKey());
            if (child == null) {
                // add this child (and all successors) with negated values to the result grouping
                result.addChild(otherChild.subtractRecursive(myHeap, otherHeap, new DataGroupingNode(null,
                                                                                                     otherChild.getFullKey(),
                                                                                                     otherChild.getClassifier(),
                                                                                                     otherChild.getLevel(),
                                                                                                     otherChild.getSubTreeLevel(),
                                                                                                     otherChild.getObjectCount() * 2,
                                                                                                     otherChild.getByteCount(otherHeap) * 2,
                                                                                                     otherChild.transitiveClosureSizeProperty().get() * 2,
                                                                                                     otherChild.retainedSizeProperty().get() * 2,
                                                                                                     otherChild.dataStructureSizeProperty().get() * 2,
                                                                                                     otherChild.deepDataStructureSizeProperty().get() * 2)));
            }
        }

        return result;
    }

    default DataGroupingNode subtract(Heap myHeap, Heap otherHeap, GroupingNode other) {
        assert other == null || this.getFullKeyAsString().equals(other.getFullKeyAsString()) : "Nodes may only be subtracted if the share the same key";

        DataGroupingNode result;
        if (other != null) {
            result = new DataGroupingNode(null,
                                          getFullKey(),
                                          getClassifier(),
                                          getLevel(),
                                          getSubTreeLevel(),
                                          getObjectCount() - other.getObjectCount(),
                                          getByteCount(myHeap) - other.getByteCount(otherHeap),
                                          transitiveClosureSizeProperty().get() - other.transitiveClosureSizeProperty().get(),
                                          retainedSizeProperty().get() - other.retainedSizeProperty().get(),
                                          dataStructureSizeProperty().get() - other.dataStructureSizeProperty().get(),
                                          deepDataStructureSizeProperty().get() - other.deepDataStructureSizeProperty().get());
        } else {
            result = new DataGroupingNode(null,
                                          getFullKey(),
                                          getClassifier(),
                                          getLevel(),
                                          getSubTreeLevel(),
                                          getObjectCount(),
                                          getByteCount(myHeap),
                                          transitiveClosureSizeProperty().get(),
                                          retainedSizeProperty().get(),
                                          dataStructureSizeProperty().get(),
                                          deepDataStructureSizeProperty().get());
        }

        return result;
    }

    default Object getKey() {
        return getFullKey()[getLevel()];
    }

    default GroupingNode getDeepChild(Object[] keys) {
        GroupingNode cur = this;
        for (Object key : keys) {
            cur = cur.getChild(key);
            if (cur == null) {
                return null;
            }
        }
        return cur;
    }

    default GroupingNode getChildWithFullKey(Object[] key) {
        if (getLevel() + 1 == key.length) {
            if (this.getKey().toString().equals(key[getLevel()].toString())) {
                return this;
            } else {
                return null;
            }
        } else {
            GroupingNode child = getChildBasedOnStringKey(key[getLevel() + 1].toString());
            if (child == null) {
                return null;
            }
            return child.getChildWithFullKey(key);
        }
    }

    default boolean hasDeepChild(GroupingNode node) {
        return this == node || getChildren().stream().anyMatch(child -> child.hasDeepChild(node));
    }

    default JsonElement asAntTracksJSON(Heap heap, ClassifierChain chain, long time) {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("anttracks.groupingnode.export.json");
        HashMap<String, Integer> classifierTypeNameToId = new HashMap<>(chain.length());
        JsonArray jsonClassifiers = new JsonArray();
        for (int i = 0; i < chain.length(); i++) {
            Classifier<?> curClassifier = chain.get(i);
            classifierTypeNameToId.put(curClassifier.getClass().getTypeName(), i);
            JsonObject jsonClassifier = new JsonObject();
            jsonClassifier.addProperty("name", curClassifier.getName());
            jsonClassifier.addProperty("id", i);
            jsonClassifiers.add(jsonClassifier);
        }

        JsonObject rootObj = new JsonObject();
        rootObj.add("classifiers", jsonClassifiers);
        rootObj.addProperty("time", time);
        rootObj.add("root", createAntTracksJsonRecursive(heap, classifierTypeNameToId));

        return rootObj;
    }

    default JsonElement asDefaultJSON(Heap heap, ClassifierChain chain, long time) {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("anttracks.groupingnode.export.json");
        JsonObject rootObj = new JsonObject();
        rootObj.addProperty("time", time);
        rootObj.add("root", createDefaultJsonRecursive(heap, chain));

        return rootObj;
    }

    default void exportAsJSON(Heap heap, ClassifierChain chain, long time, File antTracksJsonFile, File defaultJsonFile) {
        Gson gson = new Gson();
        FileWriter fw = null;
        try {
            fw = new FileWriter(antTracksJsonFile);
            fw.write(gson.toJson(asAntTracksJSON(heap, chain, time)));
            fw.flush();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        try {
            fw = new FileWriter(defaultJsonFile);
            fw.write(gson.toJson(asDefaultJSON(heap, chain, time)));
            fw.flush();
            fw.close();
        } catch (IOException e) {
            e.printStackTrace();
            if (fw != null) {
                try {
                    fw.close();
                } catch (IOException ex) {
                    ex.printStackTrace();
                }
            }
        }
        //m.end();
    }

    default JsonObject createAntTracksJsonRecursive(Heap heap, HashMap<String, Integer> classifierIds) {
        // ["Overall", "TypeA", "AllocSiteX"]
        JsonArray keyArray = new JsonArray(getFullKey().length);
        for (Object keyPart : getFullKey()) {
            keyArray.add(keyPart.toString());
        }

        JsonObject element = new JsonObject();
        element.addProperty("key", getKey().toString());
        element.add("fullKey", keyArray);
        element.addProperty("fullKeyAsString", getFullKeyAsString());
        Class<? extends Classifier<?>> classifierClass = getClassifier();
        if (classifierClass != null) {
            element.addProperty("classifierId", classifierIds.get(classifierClass.getTypeName()));
        } else {
            element.addProperty("classifierId", -1);
        }
        element.addProperty("bytes", getByteCount(heap));
        element.addProperty("objects", getObjectCount());
        long transitiveClosureSize = transitiveClosureSizeProperty().longValue();
        if (transitiveClosureSize >= 1) {
            element.addProperty("transitiveClosureSize", transitiveClosureSize);
        } else {
            element.addProperty("transitiveClosureSize", -1);
        }
        long retainedSize = retainedSizeProperty().longValue();
        if (retainedSize >= 1) {
            element.addProperty("retainedSize", retainedSize);
        } else {
            element.addProperty("retainedSize", -1);
        }

        JsonArray jsonChildren = new JsonArray();
        for (GroupingNode child : getChildren()) {
            JsonObject childObj = child.createAntTracksJsonRecursive(heap, classifierIds);
            jsonChildren.add(childObj);
        }
        if (jsonChildren.size() != 0) {
            element.add("children", jsonChildren);
        }
        return element;
    }

    default JsonObject createDefaultJsonRecursive(Heap heap, ClassifierChain chain) {
        JsonObject element = new JsonObject();
        element.addProperty("key", getFullKeyAsString());
        element.addProperty("name", getKey().toString());

        Class<? extends Classifier<?>> classifierClass = getClassifier();
        if (classifierClass != null) {
            element.addProperty("role", ((Classifier<?>) chain.getList().stream().filter(x -> x.getClass() == classifierClass).findFirst().get()).getName());
        } else {
            element.addProperty("role", "");
        }

        element.addProperty("bytes", getByteCount(heap));
        element.addProperty("objects", getObjectCount());

        JsonArray jsonChildren = new JsonArray();
        for (GroupingNode child : getChildren()) {
            JsonObject childObj = child.createDefaultJsonRecursive(heap, chain);
            jsonChildren.add(childObj);
        }
        if (jsonChildren.size() != 0) {
            element.add("children", jsonChildren);
        }
        return element;
    }
}
