package at.jku.anttracks.heap.iteration;

import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.nodes.MapGroupingNode;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.heap.space.SpaceInfo;

import java.util.List;

class ThreadLocalHeapObjectMapGroupingVisitor extends MapGroupingNode implements ObjectVisitor {
    private final DetailedHeap heap;
    private final ClassifierChain classifiers;
    private final Filter[] filters;
    private final boolean addFilterNodeInTree;

    public ThreadLocalHeapObjectMapGroupingVisitor(DetailedHeap heap, ClassifierChain classifiers, List<Filter> filters, boolean addFilterNodeInTree) {
        this.heap = heap;
        this.classifiers = classifiers;
        this.filters = filters.toArray(new Filter[filters.size()]);
        this.addFilterNodeInTree = addFilterNodeInTree;
    }

    @Override
    public void visit(long address,
                      AddressHO object,
                      SpaceInfo space,
                      List<? extends RootPtr> rootPtrs) {
        try {
            classify(object,
                     address,
                     object.getInfo(),
                     space,
                     object.getType(),
                     object.getSize(),
                     object.isArray(),
                     object.getArrayLength(),
                     object.getSite(),
                     null, // TODO Pointer
                     null, // TODO Pointer
                     object.getEventType(),
                     rootPtrs,
                     -1,// TODO Age
                     object.getInfo().thread,
                     heap.getExternalThreadName(object.getInfo().thread),
                     classifiers,
                     filters,
                     addFilterNodeInTree);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}