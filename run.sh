#!/usr/bin/env bash

set -euox pipefail

jarfullpath="$1"
shift
jarfilename=$(basename ${jarfullpath})

mkdir -p outputs/

outputdir="outputs/${jarfullpath//\//___}"

echo "Saving outputs to $outputdir"

docker run -it --rm --name object-analyzer \
    -v "$PWD/object-analyzer:/workdir/object-analyzer" \
    -v "$jarfullpath:/host/$jarfilename" \
    -v "$PWD/$outputdir:/workdir/outputs/" \
    samyaks/object-analyzer \
    ./object-analyzer \
    "$jarfilename" $@

echo "Saved output to $outputdir"
