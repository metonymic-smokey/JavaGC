#!/usr/bin/env bash

set -euox pipefail

tracefilepath="$1"
tracefiledir=$(dirname ${tracefilepath})
tracefilename=$(basename ${tracefilepath})

mkdir -p outputs/

outputdir="outputs/${tracefilepath//\//___}"

echo "Saving outputs to $outputdir"

docker run -it --rm --name object-analyzer \
    -v "$PWD/$outputdir:/workdir/outputs/" \
    -v "$tracefiledir:/workdir/trace_files" \
    samyaks/object-analyzer \
    ./trace-analyzer \
    "/workdir/trace_files/$tracefilename"

echo "Saved outputs to $outputdir"
