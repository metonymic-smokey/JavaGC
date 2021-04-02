package at.jku.anttracks.util;

import at.jku.anttracks.heap.IndexBasedHeap;

import java.util.BitSet;
import java.util.function.Function;
import java.util.function.Supplier;

public class ClosureUtil {
    public static int getClosureByteCount(BitSet closure, IndexBasedHeap heap) {
        if (heap != null && closure != null) {
            int closureBytes = 0;
            for (int idx = closure.nextSetBit(0); idx != -1; idx = closure.nextSetBit(idx + 1)) {
                closureBytes += heap.getSize(idx);
            }
            return closureBytes;
        } else {
            return -1;
        }
    }

    public static int getClosureByteCount(boolean[] closure, IndexBasedHeap heap) {
        if (heap != null && closure != null) {
            int closureBytes = 0;
            for (int idx = 0; idx < closure.length; idx++) {
                if(closure[idx]) {
                    closureBytes += heap.getSize(idx);
                }
            }
            return closureBytes;
        } else {
            return -1;
        }
    }

    public static BitSet transitiveClosure(int[] objIndices,
                                           BitSet assumeClosure,
                                           Supplier<BitSet> bitSetSupplier,
                                           Function<Integer, int[]> successorsFunction,
                                           Function<Integer, Boolean> validationFunction) {
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Calculate Transitive Closure");
        //ApplicationStatistics.Measurement m2 = ApplicationStatistics.getInstance().createMeasurement("Preperation");
        BitSet closure = assumeClosure != null ? assumeClosure : bitSetSupplier.get();
        BitSet toProcess = bitSetSupplier.get();

        // build closure of objIndices
        for (int objIndex : objIndices) {
            if (validationFunction.apply(objIndex)) {
                toProcess.set(objIndex);
                closure.set(objIndex);
            }
        }
        //m2.end();
        //m2 = ApplicationStatistics.getInstance().createMeasurement("Traversal");
        while (!toProcess.isEmpty()) {
            for (int idx = toProcess.nextSetBit(0); idx != -1; idx = toProcess.nextSetBit(idx + 1)) {
                toProcess.clear(idx);
                int[] pointers = successorsFunction.apply(idx);
                if (pointers != null) {
                    for (int ptr : pointers) {
                        if (validationFunction.apply(ptr) && !closure.get(ptr)) {
                            closure.set(ptr);
                            toProcess.set(ptr);
                        }
                    }
                }
            }
        }

        //m2.end();
        //m.end();
        return closure;
    }
}
