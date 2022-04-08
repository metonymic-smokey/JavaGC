package at.jku.anttracks.classification.nodes;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.Transformer;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.heap.space.SpaceInfo;
import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.heap.symbols.AllocationSite;
import at.jku.anttracks.parser.EventType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class DetailedHeapGroupingNode implements GroupingNode {
    @Override
    public DetailedHeapGroupingNode addChildIfNeeded(Class<? extends Classifier<?>> classifier, Object key) {
        return (DetailedHeapGroupingNode) GroupingNode.super.addChildIfNeeded(classifier, key);
    }

    @Override
    public DetailedHeapGroupingNode addChildIfNeeded(Class<? extends Classifier<?>> classifier, Object key, int subTreeLevel) {
        return (DetailedHeapGroupingNode) GroupingNode.super.addChildIfNeeded(classifier, key, subTreeLevel);
    }

    public DetailedHeapGroupingNode classify(AddressHO object,
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
                                             List<? extends RootPtr> rootPtrs,
                                             int age,
                                             String internalThreadName,
                                             String externalThreadName,
                                             ClassifierChain classifiers,
                                             Filter[] filters,
                                             boolean addFilterNodeInTree) throws Exception {
        return classify(object,
                        address,
                        objectInfo,
                        space,
                        type,
                        size,
                        isArray,
                        arrayLength,
                        allocationSite,
                        pointedFrom,
                        pointsTo,
                        eventType,
                        rootPtrs,
                        age,
                        internalThreadName,
                        externalThreadName,
                        1,
                        classifiers,
                        filters,
                        addFilterNodeInTree);
    }

    public DetailedHeapGroupingNode classify(AddressHO object,
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
                                             List<? extends RootPtr> rootPtrs,
                                             int age,
                                             String internalThreadName,
                                             String externalThreadName,
                                             ClassifierChain classifiers) throws Exception {
        return classify(object,
                        address,
                        objectInfo,
                        space,
                        type,
                        size,
                        isArray,
                        arrayLength,
                        allocationSite,
                        pointedFrom,
                        pointsTo,
                        eventType,
                        rootPtrs,
                        age,
                        internalThreadName,
                        externalThreadName,
                        1,
                        classifiers);
    }

    public DetailedHeapGroupingNode classify(AddressHO object,
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
                                             List<? extends RootPtr> rootPtrs,
                                             int age,
                                             String internalThreadName,
                                             String externalThreadName,
                                             long n,
                                             ClassifierChain classifiers,
                                             Filter[] filters,
                                             boolean addFilterNodeInTree) throws Exception {
        return classify(object,
                        address,
                        objectInfo,
                        space,
                        type,
                        size,
                        isArray,
                        arrayLength,
                        allocationSite,
                        pointedFrom,
                        pointsTo,
                        eventType,
                        rootPtrs,
                        age,
                        internalThreadName,
                        externalThreadName,
                        n,
                        0,
                        classifiers,
                        filters,
                        addFilterNodeInTree);
    }

    public DetailedHeapGroupingNode classify(AddressHO object,
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
                                             List<? extends RootPtr> rootPtrs,
                                             int age,
                                             String internalThreadName,
                                             String externalThreadName,
                                             long n,
                                             ClassifierChain classifiers) throws Exception {
        return classify(object,
                        address,
                        objectInfo,
                        space,
                        type,
                        size,
                        isArray,
                        arrayLength,
                        allocationSite,
                        pointedFrom,
                        pointsTo,
                        eventType,
                        rootPtrs,
                        age,
                        internalThreadName,
                        externalThreadName,
                        n,
                        0,
                        classifiers);
    }

    public DetailedHeapGroupingNode classify(AddressHO object,
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
                                             List<? extends RootPtr> rootPtrs,
                                             int age,
                                             String internalThreadName,
                                             String externalThreadName,
                                             long n,
                                             int continueWithSubTreeLevel,
                                             ClassifierChain classifiers,
                                             Filter[] filters,
                                             boolean addFilterNodeInTree) throws Exception {

        if (filters == null || filters.length == 0) {
            // No filters -> nothing to do
            classify(object,
                     address,
                     objectInfo,
                     space,
                     type,
                     size,
                     isArray,
                     arrayLength,
                     allocationSite,
                     pointedFrom,
                     pointsTo,
                     eventType,
                     rootPtrs,
                     age,
                     internalThreadName,
                     externalThreadName,
                     n,
                     continueWithSubTreeLevel,
                     classifiers);
        } else {
            for (int i = 0; i < filters.length; i++) {
                Filter f = filters[i];
                if (!f.classify(object,
                                address,
                                objectInfo,
                                space,
                                type,
                                size,
                                isArray,
                                arrayLength,
                                allocationSite,
                                pointedFrom,
                                pointsTo,
                                eventType,
                                rootPtrs,
                                age,
                                internalThreadName,
                                externalThreadName)) {
                    // Filter failed, do not continue with classification
                    addLeaf(size, n);
                    return this;
                }
            }
            DetailedHeapGroupingNode group = this;
            if (addFilterNodeInTree) {
                group = addChildIfNeeded(null, "Filtered", continueWithSubTreeLevel);
            }
            group.classify(object,
                           address,
                           objectInfo,
                           space,
                           type,
                           size,
                           isArray,
                           arrayLength,
                           allocationSite,
                           pointedFrom,
                           pointsTo,
                           eventType,
                           rootPtrs,
                           age,
                           internalThreadName,
                           externalThreadName,
                           n,
                           classifiers);
        }
        return this;
    }

    @SuppressWarnings("unchecked")
    public DetailedHeapGroupingNode classify(AddressHO object,
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
                                             List<? extends RootPtr> rootPtrs,
                                             int age,
                                             String internalThreadName,
                                             String externalThreadName,
                                             long n,
                                             int continueWithSubTreeLevel,
                                             ClassifierChain classifiers) throws Exception {
        // Recursion hook: No more classifiers to process
        if (classifiers.length() == 0) {
            addLeaf(size, n);
            return this;
        }

        // Invariant: At least one classifier is left to process
        Classifier<?> classifierToUse = classifiers.get(0);
        ClassifierChain remainingClassifiers = classifiers.dropFirst();

        switch (classifierToUse.getType()) {
            case ONE: {
                Object key = classifierToUse.classify(object,
                                                      address,
                                                      objectInfo,
                                                      space,
                                                      type,
                                                      size,
                                                      isArray,
                                                      arrayLength,
                                                      allocationSite,
                                                      pointedFrom,
                                                      pointsTo,
                                                      eventType,
                                                      rootPtrs,
                                                      age,
                                                      internalThreadName,
                                                      externalThreadName);
                if (key == null) {
                    break;
                }

                if (classifierToUse instanceof Transformer) {
                    Transformer transfromer = (Transformer) classifierToUse;
                    // TODO: Heap currently is provided as null
                    GroupingNode transformed = transfromer.classify(object,
                                                                    address,
                                                                    objectInfo,
                                                                    space,
                                                                    type,
                                                                    size,
                                                                    isArray,
                                                                    arrayLength,
                                                                    allocationSite,
                                                                    pointedFrom,
                                                                    pointsTo,
                                                                    eventType,
                                                                    rootPtrs,
                                                                    age,
                                                                    internalThreadName,
                                                                    externalThreadName);

                    // Merge the transformer into this HeapObjectGrouping
                    GroupingNode mergedTransfromer = addSubTreeChildIfNeeded((Class<? extends Classifier<?>>) classifierToUse.getClass(), key);
                    mergedTransfromer.merge(transformed);

                    if (remainingClassifiers.length() > 0) {
                        ArrayList<GroupingNode> toProcess = new ArrayList<>();
                        toProcess.add(transformed);
                        continueClassificationAfterTransformer(object,
                                                               address,
                                                               objectInfo,
                                                               space,
                                                               type,
                                                               size,
                                                               isArray,
                                                               arrayLength,
                                                               allocationSite,
                                                               pointedFrom,
                                                               pointsTo,
                                                               eventType,
                                                               rootPtrs,
                                                               age,
                                                               internalThreadName,
                                                               externalThreadName,
                                                               this,
                                                               toProcess,
                                                               remainingClassifiers,
                                                               n,
                                                               new AtomicBoolean(true));
                    } else { // Transformer was last classifier, classify the
                        // object "before" the transformer
                        this.classify(object,
                                      address,
                                      objectInfo,
                                      space,
                                      type,
                                      size,
                                      isArray,
                                      arrayLength,
                                      allocationSite,
                                      pointedFrom,
                                      pointsTo,
                                      eventType,
                                      rootPtrs,
                                      age,
                                      internalThreadName,
                                      externalThreadName,
                                      n,
                                      remainingClassifiers);
                    }
                } else {
                    DetailedHeapGroupingNode group = addChildIfNeeded((Class<? extends Classifier<?>>) classifierToUse.getClass(), key, continueWithSubTreeLevel);
                    group.classify(object,
                                   address,
                                   objectInfo,
                                   space,
                                   type,
                                   size,
                                   isArray,
                                   arrayLength,
                                   allocationSite,
                                   pointedFrom,
                                   pointsTo,
                                   eventType,
                                   rootPtrs,
                                   age,
                                   internalThreadName,
                                   externalThreadName,
                                   n,
                                   remainingClassifiers);
                }
                break;
            }

            case MANY: {
                Object[] keys = (Object[]) classifierToUse.classify(object,
                                                                    address,
                                                                    objectInfo,
                                                                    space,
                                                                    type,
                                                                    size,
                                                                    isArray,
                                                                    arrayLength,
                                                                    allocationSite,
                                                                    pointedFrom,
                                                                    pointsTo,
                                                                    eventType,
                                                                    rootPtrs,
                                                                    age,
                                                                    internalThreadName,
                                                                    externalThreadName);
                if (keys == null) {
                    break;
                }
                if (keys.length == 0) {
                    // 24 April 2018, mw: Let's try to not stop when empty key array is returned by to continue on same level?
                    classify(object,
                             address,
                             objectInfo,
                             space,
                             type,
                             size,
                             isArray,
                             arrayLength,
                             allocationSite,
                             pointedFrom,
                             pointsTo,
                             eventType,
                             rootPtrs,
                             age,
                             internalThreadName,
                             externalThreadName,
                             n,
                             remainingClassifiers);
                }

                for (int i = 0; i < keys.length; i++) {
                    if (i > 0) {
                        addDuplicate(size, n);
                    }
                    DetailedHeapGroupingNode group = addChildIfNeeded((Class<? extends Classifier<?>>) classifierToUse.getClass(),
                                                                      keys[i],
                                                                      continueWithSubTreeLevel);
                    group.classify(object,
                                   address,
                                   objectInfo,
                                   space,
                                   type,
                                   size,
                                   isArray,
                                   arrayLength,
                                   allocationSite,
                                   pointedFrom,
                                   pointsTo,
                                   eventType,
                                   rootPtrs,
                                   age,
                                   internalThreadName,
                                   externalThreadName,
                                   n,
                                   remainingClassifiers);
                }
                break;
            }
            case HIERARCHY: {
                DetailedHeapGroupingNode currentGrouping = this;
                Object[] keys = (Object[]) classifierToUse.classify(object,
                                                                    address,
                                                                    objectInfo,
                                                                    space,
                                                                    type,
                                                                    size,
                                                                    isArray,
                                                                    arrayLength,
                                                                    allocationSite,
                                                                    pointedFrom,
                                                                    pointsTo,
                                                                    eventType,
                                                                    rootPtrs,
                                                                    age,
                                                                    internalThreadName,
                                                                    externalThreadName);
                if (keys == null) {
                    break;
                }
                if (keys.length == 0) {
                    // 24 April 2018, mw: Let's try to not stop when empty key array is returned by to continue on same level?
                    classify(object,
                             address,
                             objectInfo,
                             space,
                             type,
                             size,
                             isArray,
                             arrayLength,
                             allocationSite,
                             pointedFrom,
                             pointsTo,
                             eventType,
                             rootPtrs,
                             age,
                             internalThreadName,
                             externalThreadName,
                             n,
                             remainingClassifiers);
                    break;
                }
                for (int i = 0; i < keys.length; i++) {
                    currentGrouping = currentGrouping.addChildIfNeeded((Class<? extends Classifier<?>>) classifierToUse.getClass(),
                                                                       keys[i],
                                                                       continueWithSubTreeLevel);
                }
                currentGrouping.classify(object,
                                         address,
                                         objectInfo,
                                         space,
                                         type,
                                         size,
                                         isArray,
                                         arrayLength,
                                         allocationSite,
                                         pointedFrom,
                                         pointsTo,
                                         eventType,
                                         rootPtrs,
                                         age,
                                         internalThreadName,
                                         externalThreadName,
                                         n,
                                         remainingClassifiers);
                break;
            }
            case MANY_HIERARCHY: {
                DetailedHeapGroupingNode currentGrouping = this;
                Object[][] keys = (Object[][]) classifierToUse.classify(object,
                                                                        address,
                                                                        objectInfo,
                                                                        space,
                                                                        type,
                                                                        size,
                                                                        isArray,
                                                                        arrayLength,
                                                                        allocationSite,
                                                                        pointedFrom,
                                                                        pointsTo,
                                                                        eventType,
                                                                        rootPtrs,
                                                                        age,
                                                                        internalThreadName,
                                                                        externalThreadName);

                if (keys == null) {
                    break;
                }
                if (keys.length == 0) {
                    // 24 April 2018, mw: Let's try to not stop when empty key array is returned by to continue on same level?
                    classify(object,
                             address,
                             objectInfo,
                             space,
                             type,
                             size,
                             isArray,
                             arrayLength,
                             allocationSite,
                             pointedFrom,
                             pointsTo,
                             eventType,
                             rootPtrs,
                             age,
                             internalThreadName,
                             externalThreadName,
                             n,
                             remainingClassifiers);
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
                        addDuplicate(size, n);
                    }
                    currentGrouping.classify(object,
                                             address,
                                             objectInfo,
                                             space,
                                             type,
                                             size,
                                             isArray,
                                             arrayLength,
                                             allocationSite,
                                             pointedFrom,
                                             pointsTo,
                                             eventType,
                                             rootPtrs,
                                             age,
                                             internalThreadName,
                                             externalThreadName,
                                             n,
                                             remainingClassifiers);
                }

                break;
            }
        }
        return this;
    }

    public void continueClassificationAfterTransformer(AddressHO object,
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
                                                       List<? extends RootPtr> rootPtrs,
                                                       int age,
                                                       String internalThreadName,
                                                       String externalThreadName,
                                                       DetailedHeapGroupingNode transformerParent,
                                                       List<? extends GroupingNode> toProcess,
                                                       ClassifierChain classifiers,
                                                       long n,
                                                       AtomicBoolean firstClassification) throws Exception {
        for (GroupingNode processGrouping : toProcess) {
            DetailedHeapGroupingNode child = getChild(processGrouping.getKey());
            if (processGrouping.getChildren().size() == 0) {
                // Leaf nodes in transformer -> Continue classification with
                // remaining classifiers on all leaf nodes
                if (!firstClassification.get()) {
                    transformerParent.addDuplicate(size, n);
                }
                child.classify(object,
                               address,
                               objectInfo,
                               space,
                               type,
                               size,
                               isArray,
                               arrayLength,
                               allocationSite,
                               pointedFrom,
                               pointsTo,
                               eventType,
                               rootPtrs,
                               age,
                               internalThreadName,
                               externalThreadName,
                               n,
                               transformerParent.getSubTreeLevel(),
                               classifiers);
                firstClassification.set(false);
            } else {
                child.continueClassificationAfterTransformer(object,
                                                             address,
                                                             objectInfo,
                                                             space,
                                                             type,
                                                             size,
                                                             isArray,
                                                             arrayLength,
                                                             allocationSite,
                                                             pointedFrom,
                                                             pointsTo,
                                                             eventType,
                                                             rootPtrs,
                                                             age,
                                                             internalThreadName,
                                                             externalThreadName,
                                                             transformerParent,
                                                             processGrouping.getChildren(),
                                                             classifiers,
                                                             n,
                                                             firstClassification);
            }
        }
    }

    @Override
    public abstract DetailedHeapGroupingNode getChild(Object key);

    @Override
    public abstract GroupingNode getChildBasedOnStringKey(String key);

    public abstract void addLeaf(int size, long n);

    protected abstract void addDuplicate(int size, long n);
}
