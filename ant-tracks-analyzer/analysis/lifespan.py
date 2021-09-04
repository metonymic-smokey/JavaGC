import time

import pandas as pd

df = pd.read_csv("preprocessed_outputs/objects_14.txt.csv")

df = df[:1000]

types = df["type"].unique()

print(types)

df_objs = dict()

start = time.time()

for type in types:
    df_objs[type] = df[df["type"] == type]

    stack = []
    c = 0
    total = 0

    tags = df_objs[type]["tag"].unique()
    print(tags)

    temp = df_objs[type]

    for tag in tags:
        tag_objs = dict()
        if tag not in tag_objs:
            tag_objs[tag] = []

        for index, row in df_objs[type].iterrows():
            if row["tag"] == tag:
                tag_objs[tag].append(row)

        for k in tag_objs.keys():
            tag_objs[k] = pd.DataFrame(tag_objs[k])

        for index, row in tag_objs[tag].iterrows():

            if row["event"] == "BORN":
                stack.append(row["at"])
                c += 1

            if row["event"] == "YEETED":
                if len(stack) > 0:
                    born = stack.pop()
                    diff = row["at"] - born
                    total += diff
                    c += 1

    print(type, total / c)


print("Time taken: ", time.time() - start)
