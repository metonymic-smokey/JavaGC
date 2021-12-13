package com.brr.benchmark.array;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.nio.charset.*;

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
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.infra.Blackhole;

// TODO: fully understand these parameters
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class StringBenchmark {

    // should be changed to whether to do implicit concat or string builder 
    @Param({"true"})
    public boolean prealloc;

    @Param({"1000"})
    public int stringSize;

    @Param({"5"})
    public int numberOfParts;

    public String[] randStrs;

    // creates an array of random strings for use in the benchmarked method. Created separately and hence, is not included in the benchmark time. 
    @Setup(Level.Trial)
    public void randomString() {
	    byte[] array = new byte[stringSize];
        new Random().nextBytes(array);
  
        String randomString = new String(array, Charset.forName("UTF-8"));
 
        int n = stringSize/numberOfParts;	
        for(int i = 0;i<numberOfParts;i++) {
            StringBuffer r = new StringBuffer();
    	    for (int k = 0; k < randomString.length(); k++) {
                char ch = randomString.charAt(k);
  
                if (((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')) && (n > 0)) {
                    r.append(ch);
                    n--;
                }
            }
	        randStrs[i] = r.toString();
        }
    }

    @Benchmark
    public void testMethod(Blackhole blackhole) {
	String res = "";
	StringBuilder stringBuilder = new StringBuilder(stringSize);
    for(int i = 0;i<numberOfParts;i++) {
        if(prealloc) {
           stringBuilder.append(randStrs[i]); 
        } else {
		   	res = res + randStrs[i]; 
        }
	}
	blackhole.consume(res);
    blackhole.consume(stringBuilder);
    }
}
