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
import org.openjdk.jmh.infra.Blackhole;

// TODO: fully understand these parameters
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 1, timeUnit = TimeUnit.SECONDS)
@Fork(1)
@State(Scope.Benchmark)
public class StringBenchmark {

    @Param({"true"})
    public boolean implicitAdd;

    @Param({"1000"})
    public int stringSize;

    @Param({"5"})
    public int numberOfParts;

    public String randomString() {
	byte[] array = new byte[256];
        new Random().nextBytes(array);
  
        String randomString = new String(array, Charset.forName("UTF-8"));
 
        int n = stringSize/numberOfParts;	
        StringBuffer r = new StringBuffer();
    	for (int k = 0; k < randomString.length(); k++) {
            char ch = randomString.charAt(k);
  
            if (((ch >= 'a' && ch <= 'z')
                 || (ch >= 'A' && ch <= 'Z')
                 || (ch >= '0' && ch <= '9'))
                && (n > 0)) {
  
                r.append(ch);
                n--;
            }
        }

	    return r.toString();
    }

    @Benchmark
    public void testMethod(Blackhole blackhole) {
	String res = "";
	StringBuilder stringBuilder = new StringBuilder(stringSize);
    	for(int i = 0;i<numberOfParts;i++) {
        	String rand = randomString();
        	if(implicitAdd) {
		   res = res + rand; 
        	} else {
		   stringBuilder.append(rand); 
		}
	}
	blackhole.consume(res);
    	blackhole.consume(stringBuilder);
    }
}
