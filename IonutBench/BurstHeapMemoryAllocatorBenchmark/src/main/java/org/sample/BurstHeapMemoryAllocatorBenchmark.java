package org.sample;

import java.util.ArrayList;
import java.util.List;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

public class BurstHeapMemoryAllocatorBenchmark {

    @Benchmark
    public void testMethod(Blackhole blackhole) {
        long heapMaxSize = Runtime.getRuntime().maxMemory();
        int sizeInBytes = 4096;
        double maxObjects = (double) heapMaxSize / (double) sizeInBytes;

        int numberOfObjects = Math.toIntExact(Math.round(0.6 * maxObjects));

        for (int iter = 0; iter < 4; iter++) {
            List<byte[]> junk = new ArrayList<>(numberOfObjects);
            for (int j = 0; j < numberOfObjects; j++) {
                junk.add(new byte[sizeInBytes]);
            }
            blackhole.consume(junk);
        }
    }

}
