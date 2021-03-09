#!/bin/sh

Type=("Serial" "ParallelOld" "ConcMarkSweep" "G1" "Shenandoah" "Z" "Epsilon")

for t in ${Type[@]}; do
  echo $t
  GC="Use"$t"GC"
  java -XX:+UnlockDiagnosticVMOptions -XX:+UnlockExperimentalVMOptions -XX:+$GC -XX:+PrintNMTStatistics -XX:NativeMemoryTracking=summary -Xms4g -Xmx4g -XX:+AlwaysPreTouch NMTMeasurementsMain > $t.log


done


