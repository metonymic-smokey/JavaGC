package at.jku.anttracks.classification.nodes;

import at.jku.anttracks.heap.Closures;
import at.jku.anttracks.heap.Heap;
import at.jku.anttracks.heap.IndexBasedHeap;

import java.util.Arrays;
import java.util.BitSet;

public class IndexCollection implements GroupingDataCollection {
    private int[] data;
    private int count;
    private boolean sorted;
    private Closures closures = null;

    public IndexCollection() {
        this(1);
    }

    public IndexCollection(int initialSize) {
        if (initialSize < 1) {
            throw new IllegalArgumentException("Initial size must be at least 1!");
        }
        this.data = new int[initialSize];
        this.count = 0;
        this.sorted = false;
    }

    public IndexCollection(int[] data) {
        this.data = data;
        this.count = data.length;
        this.sorted = false;
    }

    public int get(int index) {
        if (index < 0 || index >= count) {
            throw new IllegalArgumentException("Invalid index given");
        }

        return data[index];
    }

    public int getObjectCount() {
        return count;
    }

    @Override
    public long getBytes(Heap heap) {
        long bytes = 0;
        for (int i = 0; i < count; i++) {
            bytes += ((IndexBasedHeap) heap).getSize(get(i));
        }
        return bytes;
    }

    public Closures calculateClosures(IndexBasedHeap heap,
                                      boolean calculateTransitiveClosure,
                                      boolean calculateGCClosure,
                                      boolean calculateDataStructureClosure,
                                      boolean calculateDeepDataStructureClosure) {
        return calculateClosures(heap, calculateTransitiveClosure, calculateGCClosure, calculateDataStructureClosure, calculateDeepDataStructureClosure, null);
    }

    public Closures calculateClosures(IndexBasedHeap heap,
                                      boolean calculateTransitiveClosure,
                                      boolean calculateGCClosure,
                                      boolean calculateDataStructureClosure,
                                      boolean calculateDeepDataStructureClosure,
                                      BitSet assumeChildClosure) {
        if (!sorted) {
            sort();
        }
        return heap.getClosures(calculateTransitiveClosure,
                                calculateGCClosure,
                                calculateDataStructureClosure,
                                calculateDeepDataStructureClosure,
                                Arrays.copyOf(data, count),
                                assumeChildClosure);
    }

    /*
    public Closures getClosures() {
        return getClosures(null);
    }

    public Closures getClosures(HashSet<Integer> assumeHelpClosure) {
        if (closures == null) {
            closures = heap.getClosures(Arrays.copyOf(data, count), assumeHelpClosure);
        }
        return closures;
    }
    */

    void setCount(int count) {
        this.count = count;
    }

    boolean isSorted() {
        return sorted;
    }

    void add(int index) {
        if (!isSorted()) {
            sort();
        }
        int insertionPoint = Arrays.binarySearch(data, 0, count, index);
        if (insertionPoint < 0) {
            // index not yet stored
            // enlarge if necessary
            if (count >= data.length) {
                data = Arrays.copyOfRange(data, 0, data.length * 2);
            }

            insertionPoint = Math.abs(insertionPoint) - 1;
            // shift data from insertion point to the right by 1
            System.arraycopy(data, insertionPoint, data, insertionPoint + 1, count - insertionPoint);
            // insert new index into the resulting gap
            data[insertionPoint] = index;
            count++;
        }
    }

    /**
     * Used by union - does not check uniqueness/sort (guaranteed by union algorithm)
     *
     * @param index
     */
    void fastAdd(int index) {
        if (count >= data.length) {
            data = Arrays.copyOfRange(data, 0, data.length * 2);
        }

        data[count++] = index;
        sorted = false;
    }

    @Override
    public void clear() {
        count = 0;
    }

    void sort() {
        Arrays.sort(data, 0, count);
        sorted = true;
    }

    public boolean contains(int x) {
        if (!sorted) {
            sort();
        }
        return Arrays.binarySearch(data, x) >= 0;
    }

    public synchronized void unionWith(IndexCollection[] childData) {
        if (childData == null) {
            return;
        }

        int finalSize = count;
        for (IndexCollection child : childData) {
            if (child != null) {
                finalSize += child.count;
            }
        }

        if (finalSize == this.count) {
            return;
        }

        int loc = this.count;

        // Increase data size to fit all children
        this.data = Arrays.copyOf(this.data, finalSize);
        this.count = finalSize;
        // Copy all children
        for (IndexCollection child : childData) {
            if (child != null) {
                System.arraycopy(child.data, 0, this.data, loc, child.count);
                loc += child.count;
            }
        }
        // Sort array
        sort();
        // Eliminate duplicates
        int[] newData = new int[this.data.length];
        this.count = 0;
        for (int fromIdx = 0; fromIdx < this.data.length; fromIdx++) {
            if (fromIdx + 1 < this.data.length && this.data[fromIdx] == this.data[fromIdx + 1]) {
                continue;
            }
            newData[this.count++] = this.data[fromIdx];
        }
        int[] newCroppedData = new int[this.count];
        System.arraycopy(newData, 0, newCroppedData, 0, this.count);
        this.data = newCroppedData;
    }

    public synchronized void unionWith(IndexCollection other) {
        if (other == null) {
            return;
        }

        if (!this.isSorted()) {
            sort();
        }
        if (!other.isSorted()) {
            other.sort();
        }

        int initialSize = count + other.getObjectCount();
        IndexCollection union = new IndexCollection(initialSize >= 1 ? initialSize : 1);
        // other remains unchanged
        int i = 0, j = 0;
        // union while retaining element order
        while (i < count && j < other.getObjectCount()) {
            if (this.get(i) < other.get(j)) {
                union.fastAdd(this.get(i++));

            } else if (this.get(i) == other.get(j)) {
                union.fastAdd(this.get(i));
                i++;
                j++;

            } else { // this.get(i) > other.get(j)
                union.fastAdd(other.get(j++));
            }
        }

        // add remaining elements
        while (i < count) {
            union.fastAdd(this.get(i++));
        }
        while (j < other.getObjectCount()) {
            union.fastAdd(other.get(j++));
        }

        // save
        this.data = union.data;
        this.setCount(union.getObjectCount());
    }

    public synchronized int overlap(IndexCollection other) {
        if (other == null) {
            return 0;
        }

        if (!this.isSorted()) {
            sort();
        }
        if (!other.isSorted()) {
            other.sort();
        }

        // other remains unchanged
        int thisIndex = 0, otherIndex = 0, overlap = 0;
        // union while retaining element order
        while (thisIndex < count && otherIndex < other.getObjectCount()) {
            if (this.get(thisIndex) < other.get(otherIndex)) {
                thisIndex++;
            } else if (this.get(thisIndex) == other.get(otherIndex)) {
                overlap++;
                thisIndex++;
                otherIndex++;
            } else {
                otherIndex++;
            }
        }

        return overlap;
    }

    public IndexCollection clone() {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("IndexCollection - Clone");
        IndexCollection dolly = new IndexCollection();
        dolly.data = Arrays.copyOf(data, data.length);
        dolly.count = count;
        dolly.sorted = sorted;
        dolly.closures = closures;
        //m.end();
        return dolly;
    }

    public boolean isEmpty() {
        return count == 0;
    }

    public BitSet asBitset() {
        BitSet bs = new BitSet();
        for (int i = 0; i < count; i++) {
            bs.set(data[i]);
        }
        return bs;
    }
}
