#!/usr/bin/env bash

set -euox pipefail

python3 to_parquet.py $1

python3 analysis_02-dask.py $1

