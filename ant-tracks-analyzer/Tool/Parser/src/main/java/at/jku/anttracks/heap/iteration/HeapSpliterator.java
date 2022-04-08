package at.jku.anttracks.heap.iteration;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.util.TraceException;

import java.util.Spliterator;
import java.util.function.Consumer;

public class HeapSpliterator extends HeapSpliteratorBase implements Spliterator<AddressHO> {
    public HeapSpliterator(DetailedHeap heap, int startSpace, int startLab, int fenceSpace, int fenceLab, boolean current, boolean sorted) {
        this(heap, new SpliteratorRange(startSpace, startLab, fenceSpace, fenceLab), current, sorted);
    }

    public HeapSpliterator(DetailedHeap heap, SpliteratorRange spliteratorRange, boolean current, boolean sorted) {
        super(heap, spliteratorRange, current, sorted);
    }

    @Override
    public boolean tryAdvance(Consumer<? super AddressHO> action) {
        if (!advanceStep()) {
            return false;
        }

        try {
            action.accept(getCurrentObject());
        } catch (TraceException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public HeapSpliterator trySplit() {
        SpliteratorRange lower = getLowerSpliteratorRange();
        SpliteratorRange upper = getUpperSpliteratorRange();

        if (lower != null && upper != null) {
            setRange(lower);
            HeapSpliterator hs = new HeapSpliterator(heap, upper, this.current, this.sorted);
            hs.setListener(this.listener);
            return hs;
        }
        return null;
    }

    @Override
    public long estimateSize() {
        return size();
    }

    @Override
    public int characteristics() {
        return Spliterator.SIZED | // estimateSize() represents a finite
                // size that is an exact count of the // If we are not multithreading or did not split, visit our
                // Spliterators objects in sequential
                // number of elements that will
                // be encounter on a complete traversal
                // (prior to traversal or splitting)
                Spliterator.SUBSIZED | // Characteristic value signifying
                // that all Spliterators resulting
                // from trySplit() will be both
                // SIZED and SUBSIZED. (This means
                // that all child Spliterators,
                // whether direct or indirect, will
                // be SIZED.)
                Spliterator.ORDERED | // The stream is ordered, it
                // encounters objects space-wise,
                // and within each space lab-wise,
                // from
                // lower address to higher address
                Spliterator.NONNULL | // There are no null-values in the
                // stream (since objects are only
                // created for addresses that
                // really contain objects)
                Spliterator.DISTINCT; // There are no two equal elements
        // (given by the distinct addresses
        // of objects)
        // Since spaces are not necessarily ordered by address,
        // Spliterator.SORTED is not applied
        // Spliterator.IMMUTABLE cannot be assured, since the heap could
        // change during iteration
        // Spliterator.CONCURRENT cannot be assured, since the element
        // source (=heap) cannot be concurrently modified by multiple
        // threads without external synchronization.
    }
}