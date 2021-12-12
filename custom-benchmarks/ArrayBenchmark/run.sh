#!/bin/bash
set -euxo pipefail

# build
mvn verify

# setup parameters
declare -a numberOfObjects=("100" "1000" "10000" "100000")
declare -a prealloc=("true" "false")

mkdir -p outputs

for numObjs in "${numberOfObjects[@]}"
do
    for prealloc_ in "${prealloc[@]}"
    do
        echo "Running benchmark for numberOfObjects=${numObjs}, prealloc=${prealloc_}"
        java -Xms1g -Xmx1g -jar target/benchmarks.jar -p numberOfObjects=$numObjs -p prealloc=$prealloc_ > "outputs/results-numberOfObjects_${numObjs}-prealloc_${prealloc_}"
    done
done

