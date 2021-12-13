#!/bin/bash
set -euxo pipefail

# build
mvn verify

# setup parameters
declare -a numberOfSmallArrays=("1" "12" "96" "768" "6144")
declare -a totalBytes=("16384" "65536" "262144" "1048576")
declare -a large=("false")

mkdir -p outputs

for large_ in "${large[@]}"
do
for numSmallArr in "${numberOfSmallArrays[@]}"
do
    for bytes in "${totalBytes[@]}"
    do
            echo "Running benchmark for type=${large_},numSmallArr=${numSmallArr}, totalBytes=${bytes}"
            java -Xms8g -Xmx8g -jar target/benchmarks.jar -p numberOfSmallArrays=$numSmallArr -p totalBytes=$bytes -p large=$large_  > "outputs/results-large_${large_}-numberofsmallarrays_${numSmallArr}-bytesallocated_${bytes}"
    done
done
done

python3 analysis/analyze.py

