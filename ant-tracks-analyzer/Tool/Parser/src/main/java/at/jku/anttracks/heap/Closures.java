package at.jku.anttracks.heap;

import at.jku.anttracks.util.ClosureUtil;

import java.util.BitSet;

public class Closures {
    private final int[] objectIndices;
    private final BitSet transitiveClosure;
    private final BitSet gcClosure;
    private final IndexBasedHeap fastHeap;
    private final BitSet dataStructureClosure;
    private final BitSet deepDataStructureClosure;

    private long flatByteCount = -1;
    private long transitiveClosureBytes = -1;
    private long gcClosureBytes = -1;
    private long dataStructureClosureBytes = -1;
    private long deepDataStructureClosureBytes = -1;

    public Closures(IndexBasedHeap fastHeap, int[] objIndices, BitSet closure, BitSet gcClosure, BitSet dataStructureClosure, BitSet deepDataStructureClosure) {
        this.objectIndices = objIndices;
        this.transitiveClosure = closure;
        this.gcClosure = gcClosure;
        this.dataStructureClosure = dataStructureClosure;
        this.deepDataStructureClosure = deepDataStructureClosure;

        this.fastHeap = fastHeap;
    }

    public int getTransitiveClosureObjectCount() {
        return transitiveClosure.cardinality();
    }

    public int getGCClosureObjectCount() {
        return gcClosure.cardinality();
    }

    public synchronized long getTransitiveClosureByteCount() {
        if (transitiveClosureBytes < 0) {
            transitiveClosureBytes = ClosureUtil.getClosureByteCount(transitiveClosure, fastHeap);
        }
        return transitiveClosureBytes;
    }

    public long getGCClosureByteCount() {
        if (fastHeap != null) {
            if (gcClosureBytes < 0) {
                gcClosureBytes = ClosureUtil.getClosureByteCount(gcClosure, fastHeap);
            }
        }
        return gcClosureBytes;
    }

    public long getDataStructureClosureByteCount() {
        if (fastHeap != null) {
            if (dataStructureClosureBytes < 0) {
                dataStructureClosureBytes = ClosureUtil.getClosureByteCount(dataStructureClosure, fastHeap);
            }
        }
        return dataStructureClosureBytes;
    }

    public long getDeepDataStructureClosureByteCount() {
        if (fastHeap != null) {
            if (deepDataStructureClosureBytes < 0) {
                deepDataStructureClosureBytes = ClosureUtil.getClosureByteCount(deepDataStructureClosure, fastHeap);
            }
        }
        return deepDataStructureClosureBytes;
    }

    public BitSet getTransitiveClosure() {
        return transitiveClosure;
    }

    public BitSet getGCClosure() {
        return gcClosure;
    }

    public BitSet getDataStructureClosure() {
        return dataStructureClosure;
    }

    public BitSet getDeepDataStructureClosure() {
        return deepDataStructureClosure;
    }

    public int[] getObjectIndices() {
        return objectIndices;
    }

    public long getFlatByteCount() {
        if (fastHeap != null) {
            if (flatByteCount < 0) {
                flatByteCount = 0;
                for (int i = 0; i < objectIndices.length; i++) {
                    flatByteCount += fastHeap.getSize(objectIndices[i]);
                }
            }
        }
        return flatByteCount;
    }
}
