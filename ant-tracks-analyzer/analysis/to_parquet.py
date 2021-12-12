import sys
import os

import pandas as pd
import pyarrow as pa
import pyarrow.parquet as pq

csv_file = sys.argv[1]
file_without_ext, _ = os.path.splitext(csv_file)
parquet_file = file_without_ext + ".parquet"
chunksize = 100_000

columns = [
    "bornAt",
    "lastMovedAt",
    "tag",
    "size",
    "arrayLen",
    "address",
    "gcTime",
    "gcId",
    "allocationSite",
    "lifetime",
    "type",
    "isArray",
    "arrayLen_2",
    "bornTime_2"
]

dtype = {
    "bornAt": int,
    "lastMovedAt": int,
    "tag": int,
    "size": int,
    "arrayLen": int,
    "address": str,
    "gcTime": int,
    "gcId": int,
    "allocationSite": str,
    "lifetime": int,
    "type": str,
    "isArray": str,
    "arrayLen_2": int,
    "bornTime_2": int
}

csv_stream = pd.read_csv(csv_file, chunksize=chunksize, names=columns, dtype=dtype)

for i, chunk in enumerate(csv_stream):
    print("Chunk", i)
    if i == 0:
        # Guess the schema of the CSV file from the first chunk
        parquet_schema = pa.Table.from_pandas(df=chunk).schema
        # Open a Parquet file for writing
        parquet_writer = pq.ParquetWriter(parquet_file, parquet_schema, compression='snappy')
    # Write CSV chunk to the parquet file
    table = pa.Table.from_pandas(chunk, schema=parquet_schema)
    parquet_writer.write_table(table)

parquet_writer.close()