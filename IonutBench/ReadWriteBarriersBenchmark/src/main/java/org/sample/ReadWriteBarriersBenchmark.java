package org.sample;

import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Benchmark;

public class ReadWriteBarriersBenchmark {

    @State(Scope.Thread)
    public static class ArrayState {
        private int size = 262144;
        private int[] array = new int[size];
        private int index = 0;
    }

    @Benchmark
    public void test(ArrayState arrayState) {
        int lSize = arrayState.size;
        int[] array = arrayState.array;
        int index = arrayState.index;
        int mask = lSize - 1;

        for (int i = 0; i < lSize; i++) {
            Integer aux = array[i];
            array[i] = array[(i + index) & mask];
            array[(i + index) & mask] = aux;
        }

        arrayState.index++;
    }

}
