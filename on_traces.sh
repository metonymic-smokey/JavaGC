#!/usr/bin/env bash

set -euox pipefail

tracefilepath="$1"

mkdir -p outputs/

outputdir="outputs/${tracefilepath//\//___}"

echo "Saving outputs to $outputdir"

docker run -it --rm --name object-analyzer \
    -v "$PWD/$outputdir:/workdir/outputs/" \
    samyaks/object-analyzer \
    ./trace-analyzer \
    "$tracefilepath"

echo "Saved outputs to $outputdir"
