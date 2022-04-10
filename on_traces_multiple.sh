#!/usr/bin/env bash

set -euox pipefail

tracefiledir="$1"
shift
tracefilenames="$@"

mkdir -p outputs/

outputdir="outputs/${tracefiledir//\//___}"

echo "Saving outputs to $outputdir"

docker run -it --rm --name object-analyzer \
    -v "$PWD/$outputdir:/workdir/outputs/" \
    -v "$tracefiledir:/workdir/trace_files:ro" \
    samyaks/object-analyzer \
    ./trace-analyzer \
    $tracefilenames

echo "Saved outputs to $outputdir"
