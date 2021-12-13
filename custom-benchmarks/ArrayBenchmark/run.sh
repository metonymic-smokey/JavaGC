#!/bin/bash
set -euxo pipefail

# build
mvn verify

# setup parameters
declare -a numberOfObjects=("100" "1000" "10000" "100000")
declare -a prealloc=("true" "false")
declare -a boxed=("true" "false")

mkdir -p outputs

for numObjs in "${numberOfObjects[@]}"
do
    for prealloc_ in "${prealloc[@]}"
    do
        for boxed_ in "${boxed[@]}"
        do
            echo "Running benchmark for numberOfObjects=${numObjs}, prealloc=${prealloc_}, boxed=${boxed_}"
            java -Xms8g -Xmx8g -jar target/benchmarks.jar -p numberOfObjects=$numObjs -p prealloc=$prealloc_ -p boxed=$boxed_ > "outputs/results-numberOfObjects_${numObjs}-prealloc_${prealloc_}-boxed_${boxed_}"
        done
    done
done

python3 analysis/analyze.py

