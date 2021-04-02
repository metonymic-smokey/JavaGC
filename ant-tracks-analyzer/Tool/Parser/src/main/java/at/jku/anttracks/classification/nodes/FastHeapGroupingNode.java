package at.jku.anttracks.classification.nodes;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.Transformer;
import at.jku.anttracks.heap.IndexBasedHeap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class FastHeapGroupingNode implements GroupingNode {
    @Override
    public FastHeapGroupingNode addChildIfNeeded(Class<? extends Classifier<?>> classifier, Object key) {
        return (FastHeapGroupingNode) GroupingNode.super.addChildIfNeeded(classifier, key);
    }

    @Override
    public FastHeapGroupingNode addChildIfNeeded(Class<? extends Classifier<?>> classifier, Object key, int subTreeLevel) {
        return (FastHeapGroupingNode) GroupingNode.super.addChildIfNeeded(classifier, key, subTreeLevel);
    }

    @Override
    public abstract FastHeapGroupingNode getChild(Object key);

    @Override
    public abstract FastHeapGroupingNode getChildBasedOnStringKey(String key);

    public FastHeapGroupingNode classify(IndexBasedHeap fastHeap,
                                         Integer objIndex,
                                         ClassifierChain classifiers) throws Exception {
        return classify(fastHeap, objIndex, classifiers, 1);
    }

    public FastHeapGroupingNode classify(IndexBasedHeap fastHeap,
                                         Integer objIndex,
                                         ClassifierChain classifiers,
                                         Filter[] filters,
                                         boolean addFilterNodeInTree) throws Exception {
        return classify(fastHeap, objIndex, classifiers, filters, addFilterNodeInTree, 1);
    }

    public FastHeapGroupingNode classify(IndexBasedHeap fastHeap,
                                         Integer objIndex,
                                         ClassifierChain classifiers,
                                         long n) throws Exception {
        return classify(fastHeap, objIndex, classifiers, n, 0);
    }

    public FastHeapGroupingNode classify(IndexBasedHeap fastHeap,
                                         Integer objIndex,
                                         ClassifierChain classifiers,
                                         Filter[] filters,
                                         boolean addFilterNodeInTree,
                                         long n) throws Exception {
        return classify(fastHeap, objIndex, classifiers, filters, addFilterNodeInTree, n, 0);
    }

    public FastHeapGroupingNode classify(IndexBasedHeap fastHeap,
                                         Integer objIndex,
                                         ClassifierChain classifiers,
                                         Filter[] filters,
                                         boolean addFilterNodeInTree,
                                         long n,
                                         int continueWithSubTreeLevel) throws Exception {
        if (filters == null || filters.length == 0) {
            // No filters -> nothing to do
            classify(fastHeap, objIndex, classifiers, n, continueWithSubTreeLevel);
        } else {
            for (int i = 0; i < filters.length; i++) {
                Filter f = filters[i];
                if (!f.classify(objIndex)) {
                    // Filter failed, do not continue with classification
                    addLeaf(objIndex, n);
                    return this;
                }
            }
            FastHeapGroupingNode group = this;
            if (addFilterNodeInTree) {
                group = addChildIfNeeded(null, "Filtered", continueWithSubTreeLevel);
            }
            group.classify(fastHeap, objIndex, classifiers, n);
        }

        return this;
    }

    @SuppressWarnings("unchecked")
    public FastHeapGroupingNode classify(IndexBasedHeap fastHeap,
                                         Integer objIndex,
                                         ClassifierChain classifiers,
                                         long n,
                                         int continueWithSubTreeLevel) throws Exception {
        // Recursion hook: No more classifiers to process
        if (classifiers.length() == 0) {
            addLeaf(objIndex, n);
            return this;
        }

        // Invariant: At least one classifier is left to process
        Classifier<?> classifierToUse = classifiers.get(0);
        ClassifierChain remainingClassifiers = classifiers.dropFirst();

        switch (classifierToUse.getType()) {
            case ONE: {
                Object key = classifierToUse.classify(objIndex);
                if (key == null) {
                    break;
                }

                if (classifierToUse instanceof Transformer) {
                    Transformer transfromer = (Transformer) classifierToUse;
                    // TODO: Heap currently is provided as null
                    GroupingNode transformed = transfromer.classify(objIndex);

                    // Merge the transformer into this HeapObjectGrouping
                    GroupingNode mergedTransfromer = addSubTreeChildIfNeeded((Class<? extends Classifier<?>>) classifierToUse.getClass(), transformed.getKey());
                    mergedTransfromer.merge(transformed);

                    if (remainingClassifiers.length() > 0) {
                        ArrayList<GroupingNode> toProcess = new ArrayList<>();
                        toProcess.add(transformed);
                        continueClassificationAfterTransformer(fastHeap,
                                                               this,
                                                               toProcess,
                                                               objIndex,
                                                               remainingClassifiers,
                                                               n,
                                                               new AtomicBoolean(true));
                    } else { // Transformer was last classifier, classify the
                        // object "before" the transformer
                        this.classify(fastHeap, objIndex, remainingClassifiers, n);
                    }
                } else {
                    FastHeapGroupingNode group = addChildIfNeeded((Class<? extends Classifier<?>>) classifierToUse.getClass(), key, continueWithSubTreeLevel);
                    group.classify(fastHeap, objIndex, remainingClassifiers, n);
                }
                break;
            }

            case MANY: {
                Object[] keys = (Object[]) classifierToUse.classify(objIndex);
                if (keys == null) {
                    break;
                }
                if (keys.length == 0) {
                    // 24 April 2018, mw: Let's try to not stop when empty key array is returned by to continue on same level?
                    classify(fastHeap, objIndex, remainingClassifiers, n);
                    break;
                }
                for (int i = 0; i < keys.length; i++) {
                    if (i > 0) {
                        addDuplicate(objIndex, n);
                    }
                    FastHeapGroupingNode group = addChildIfNeeded((Class<? extends Classifier<?>>) classifierToUse.getClass(), keys[i], continueWithSubTreeLevel);
                    group.classify(fastHeap, objIndex, remainingClassifiers, n);
                }
                break;
            }
            case HIERARCHY: {
                FastHeapGroupingNode currentGrouping = this;
                Object[] keys = (Object[]) classifierToUse.classify(objIndex);
                if (keys == null) {
                    break;
                }
                if (keys.length == 0) {
                    // 24 April 2018, mw: Let's try to not stop when empty key array is returned by to continue on same level?
                    classify(fastHeap, objIndex, remainingClassifiers, n);
                    break;
                }
                for (int i = 0; i < keys.length; i++) {
                    currentGrouping = currentGrouping.addChildIfNeeded((Class<? extends Classifier<?>>) classifierToUse.getClass(),
                                                                       keys[i],
                                                                       continueWithSubTreeLevel);
                }
                currentGrouping.classify(fastHeap, objIndex, remainingClassifiers, n);
                break;
            }
            case MANY_HIERARCHY: {
                FastHeapGroupingNode currentGrouping = this;
                Object[][] keys = (Object[][]) classifierToUse.classify(objIndex);
                if (keys == null) {
                    break;
                }
                if (keys.length == 0) {
                    // 24 April 2018, mw: Let's try to not stop when empty key array is returned by to continue on same level?
                    classify(fastHeap, objIndex, remainingClassifiers, n);
                    break;
                }
                for (int i = 0; i < keys.length; i++) {
                    currentGrouping = this;
                    for (int j = 0; j < keys[i].length; j++) {
                        currentGrouping = currentGrouping.addChildIfNeeded((Class<? extends Classifier<?>>) classifierToUse.getClass(),
                                                                           keys[i][j],
                                                                           continueWithSubTreeLevel);
                    }
                    if (i > 0) {
                        addDuplicate(objIndex, n);
                    }
                    currentGrouping.classify(fastHeap, objIndex, remainingClassifiers, n);
                }

                break;
            }
        }
        return this;
    }

    public void continueClassificationAfterTransformer(IndexBasedHeap fastHeap,
                                                       FastHeapGroupingNode transformerParent,
                                                       List<? extends GroupingNode> toProcess,
                                                       Integer objIndex,
                                                       ClassifierChain classifiers,
                                                       long n,
                                                       AtomicBoolean firstClassification) throws Exception {
        for (GroupingNode processGrouping : toProcess) {
            FastHeapGroupingNode child = getChild(processGrouping.getKey());
            if (processGrouping.getChildren().size() == 0) {
                // Leaf nodes in transformer -> Continue classification with
                // remaining classifiers on all leaf nodes
                if (!firstClassification.get()) {
                    transformerParent.addDuplicate(objIndex, n);
                }
                child.classify(fastHeap, objIndex, classifiers, n, transformerParent.getSubTreeLevel());
                firstClassification.set(false);
            } else {
                child.continueClassificationAfterTransformer(fastHeap,
                                                             transformerParent,
                                                             processGrouping.getChildren(),
                                                             objIndex,
                                                             classifiers,
                                                             n,
                                                             firstClassification);
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (other == null) {
            return false;
        }
        if (other instanceof FastHeapGroupingNode) {
            FastHeapGroupingNode otherNode = (FastHeapGroupingNode) other;
            return this.getFullKeyAsString().equals(otherNode.getFullKeyAsString());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return getFullKeyAsString().hashCode();
    }

    protected abstract void addLeaf(int objId, long n);

    protected abstract void addDuplicate(int objId, long n);
}
