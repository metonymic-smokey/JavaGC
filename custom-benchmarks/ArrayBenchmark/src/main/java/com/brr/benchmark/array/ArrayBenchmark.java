package com.brr.benchmark.array;

import java.util.ArrayList;
import java.util.List;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.infra.Blackhole;

// TODO: fully understand these parameters
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class ArrayBenchmark {

    @Param({"true"})
    public boolean prealloc;

    @Param({"1000"})
    public int numberOfObjects;

    @Benchmark
    public void testMethod(Blackhole blackhole) {
        int sizeInBytes = 4096;

        for (int iter = 0; iter < 4; iter++) {
            // this is essentially pre-allocating space for "numberOfObjects"?
            List<byte[]> junk;
            if (prealloc) {
                junk = new ArrayList<>(numberOfObjects);
            } else {
                junk = new ArrayList<>();
            }
            for (int j = 0; j < numberOfObjects; j++) {
                // will "add" allocate more space?
                //  A: the constructor only allocates capacity, there are no elements in the list initially (https://docs.oracle.com/javase/8/docs/api/java/util/ArrayList.html#ArrayList-int-)
                junk.add(new byte[sizeInBytes]);
            }
            blackhole.consume(junk);
        }
    }
}
