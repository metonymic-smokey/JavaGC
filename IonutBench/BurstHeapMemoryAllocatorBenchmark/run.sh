#!/bin/sh
set -euxo pipefail

mvn verify

Type=("Serial" "ParallelOld" "ConcMarkSweep" "G1" "Shenandoah" "Z" "Epsilon")

for t in ${Type[@]}; do
  echo $t
  GC="Use"$t"GC"
  java -XX:+UnlockExperimentalVMOptions -Xms4g -Xmx4g -XX:+AlwaysPreTouch -XX:+$GC -jar target/benchmarks.jar> Results/$t.log
  

done


