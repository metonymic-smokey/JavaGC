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
public class MultipleArrayBenchmark {

    @Param({"4096"})
    public int totalBytes;

    @Param({"24"})
    public int numberOfSmallArrays;
   
    
    @Param({"true"})
    public boolean large;

    @Benchmark
    public void testMethod(Blackhole blackhole) {
	if(large) {
		List<byte[]> junk; // creating list even though there is only one array since a list of arrays will be needed for the other case, so making it comparable.
		junk = new ArrayList<>();
		byte[] arr;
		arr = new byte[totalBytes];
		junk.add(arr);
		blackhole.consume(arr);		
	} else {
		int smallArrSize = totalBytes/numberOfSmallArrays;
		List<byte[]> junk;
		junk = new ArrayList<>();
		byte[] arr;
		for(int i = 0;i<numberOfSmallArrays;i++) {
			arr = new byte[smallArrSize];
			junk.add(arr);
		}
		blackhole.consume(junk);
	}
    }
}
