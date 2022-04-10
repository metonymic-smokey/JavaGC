package at.jku.anttracks.classification.nodes;

import at.jku.anttracks.heap.Heap;

import java.util.HashMap;

public class HeapObjectMapGroupingMap extends HashMap<HeapObjectGroupingKey, GroupingCounter> implements GroupingDataCollection {

    private static final long serialVersionUID = 8792062703249862984L;

    @Override
    public int getObjectCount() {
        return (int) this.values().stream().mapToDouble(GroupingCounter::get).sum();
    }

    @Override
    public long getBytes(Heap heap) {
        return (long) this.entrySet().stream().mapToDouble(x -> x.getKey().objectSizeInBytes * x.getValue().get()).sum();
    }

    public long getNonSampledBytes() {
        return (long) this.entrySet().stream().mapToDouble(x -> x.getKey().objectSizeInBytes * x.getValue().exact.get()).sum();
    }
}

