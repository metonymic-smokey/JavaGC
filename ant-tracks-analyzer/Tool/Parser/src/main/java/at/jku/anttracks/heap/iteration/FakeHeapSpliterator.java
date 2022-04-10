package at.jku.anttracks.heap.iteration;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.util.TraceException;

import java.util.Collections;

public class FakeHeapSpliterator extends HeapSpliteratorBase {

    public FakeHeapSpliterator(DetailedHeap heap, int startSpace, int startLab, int fenceSpace, int fenceLab, boolean current, boolean sorted) {
        this(heap, new SpliteratorRange(startSpace, startLab, fenceSpace, fenceLab), current, sorted);
    }

    public FakeHeapSpliterator(DetailedHeap heap, SpliteratorRange spliteratorRange, boolean current, boolean sorted) {
        super(heap, spliteratorRange, current, sorted);
    }

    public boolean tryAdvance(ObjectVisitor action, ObjectVisitor.Settings vistitorSettings) {
        if (!advanceStep()) {
            return false;
        }

        try {
            AddressHO obj = getCurrentObject();
            action.visit(getCurrentAddress(),
                         getCurrentObject(),
                         getCurrentSpaceInfo(),
                         vistitorSettings.getRootPointerInfoNeeded() ? heap.rootPtrs.get(getCurrentAddress()) : Collections.emptyList());
        } catch (TraceException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    FakeHeapSpliterator trySplit() {
        SpliteratorRange lower = getLowerSpliteratorRange();
        SpliteratorRange upper = getUpperSpliteratorRange();

        if (lower != null && upper != null) {
            setRange(lower);
            FakeHeapSpliterator fhs = new FakeHeapSpliterator(heap, upper, this.current, this.sorted);
            fhs.setListener(this.listener);
            return fhs;
        }
        return null;
    }
}