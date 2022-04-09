import time
from typing import cast

import pandas as pd

df = pd.read_csv("preprocessed_outputs/objects_14.txt.csv")

df = df[:100000]

# print(df)

types = df["type"].unique()

print(types)

start = time.time()

for type in types:
    df_objs = df[df["type"] == type]
    df_objs = cast(pd.DataFrame, df_objs)

    c = 0
    total = 0

    tag_groups = df_objs.groupby("tag")

    for tag, group in tag_groups:
        # print(group)
        stack = []
        for row in group.itertuples():
            if row.event == "BORN":
                stack.append(row.at)

            elif row.event == "YEETED" and stack:
                born = stack.pop()
                diff = row.at - born
                total += diff
                c += 1

    print(type, total / c if c > 0 else 0)


print("Time taken: ", time.time() - start)
