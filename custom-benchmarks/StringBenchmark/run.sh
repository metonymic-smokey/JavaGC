#!/bin/bash
set -euxo pipefail

# build
mvn verify

# setup parameters
declare -a stringSize=("100" "1000" "10000" "100000")
declare -a implicitAdd=("true" "false")
declare -a numberOfParts=("5","10")

mkdir -p outputs

for size in "${stringSize[@]}"
do
    for part in "${numberOfParts[@]}"
    do
	    for implicitAdd_ in "${implicitAdd[@]}"
    		do
        	echo "Running benchmark for stringSize=${size}, implicitAdd=${implicitAdd_}"
        	java -Djmh.ignoreLock=true -Xms1g -Xmx1g -jar target/benchmarks.jar -p stringSize=${size} -p numberOfParts=${part} -p implicitAdd=$implicitAdd_ > "outputs/results-string-size_${size}-implicitAdd_${implicitAdd_}-parts_${part}"
    		done
	done
done

