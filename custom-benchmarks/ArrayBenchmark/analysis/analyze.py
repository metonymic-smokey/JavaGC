import os
from typing import List

import pandas as pd
import numpy as np
import matplotlib.pyplot as plt
import seaborn as sns

from common import extract_times


def extract_all_from_dir(dirname: str):
    results: List = []

    for file in os.listdir(dirname):
        print(file)

        if file.endswith(".png"):
            continue

        _, numberOfObjects, prealloc, boxed = file.split("-")

        numberOfObjects = int(numberOfObjects.split("_")[1])
        prealloc = prealloc.split("_")[1]
        boxed = boxed.split("_")[1]
        res = extract_times(os.path.join(dirname, file))

        results.append(
            {
                "numberOfObjects": numberOfObjects,
                "prealloc": prealloc,
                "boxed": boxed,
                "avg": res[0],
                "ci_lower": res[1][0],
                "ci_higher": res[1][1],
            }
        )

    results.sort(key=lambda x: (x["numberOfObjects"], x["prealloc"]))

    return pd.DataFrame(results)


df = extract_all_from_dir("outputs")

print(df)

df["avg"] = np.log10(df["avg"])

fig, axs = plt.subplots(2, 1, tight_layout=True, figsize=(10, 10))

non_boxed = df[df["boxed"] == "false"]
print(non_boxed)
sns.barplot(x="numberOfObjects", hue="prealloc", y="avg", data=non_boxed, ax=axs[0])
non_prealloc = df[df["prealloc"] == "false"]
print(non_prealloc)
sns.barplot(x="numberOfObjects", hue="boxed", y="avg", data=non_prealloc, ax=axs[1])

plt.savefig("outputs/results.png")
plt.show()

