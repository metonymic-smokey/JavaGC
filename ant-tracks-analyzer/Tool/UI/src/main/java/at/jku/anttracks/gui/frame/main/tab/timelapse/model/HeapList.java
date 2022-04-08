
package at.jku.anttracks.gui.frame.main.tab.timelapse.model;

import at.jku.anttracks.gui.model.DetailedHeapInfo;

import java.util.LinkedList;
import java.util.Queue;

/**
 * @author Christina Rammerstorfer
 */
public class HeapList {

    private final Queue<DetailedHeapInfo> heaps;
    private long minStartAddress = Long.MAX_VALUE;

    public HeapList() {
        heaps = new LinkedList<DetailedHeapInfo>();
    }

    /**
     * Adds a new DetailsInfo to the HeapList and sets the new minStartAddress if necessary
     *
     * @param d The new DetailsInfo containing the Heap to be added
     */
    public void add(DetailedHeapInfo d) {
        heaps.offer(d);
        long addr = d.getFastHeapSupplier().get().stream().mapToLong(x -> d.getFastHeapSupplier().get().getAddress(x)).min().getAsLong();
        if (addr < minStartAddress) {
            minStartAddress = addr;
        }
    }

    public DetailedHeapInfo getFirst() {
        return heaps.poll();
    }

    public long getMinStartAddress() {
        return minStartAddress;
    }

}
