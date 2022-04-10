#!/usr/bin/env bash

set -euox pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
parquetfile="$1"

python3 "$SCRIPT_DIR/analysis_02-dask.py" $parquetfile

