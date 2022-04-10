#!/usr/bin/env bash

set -euox pipefail

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )
csvfile="$1"
csvfilewithoutextension="${csvfile%.*}"
parquetfile="$csvfilewithoutextension.parquet"

python3 "$SCRIPT_DIR/to_parquet.py" $csvfile

python3 "$SCRIPT_DIR/analysis_02-dask.py" $parquetfile

