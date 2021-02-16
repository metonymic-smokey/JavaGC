#!/bin/bash

n=$1

javac HeapMemoryBandwidthAllocatorBenchmark.java 
java HeapMemoryBandwidthAllocatorBenchmark $n 
jar cmvf META-INF/MANIFEST.MF HeapMemoryBandwidthAllocatorBenchmark.jar HeapMemoryBandwidthAllocatorBenchmark.class
java  -Xlog:gc*:file=trace.log -jar HeapMemoryBandwidthAllocatorBenchmark.jar $n 
