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

        _, large, numberOfSmallArrays, bytes = file.split("-")

        large = large.split("_")[1]
        numberOfSmallArrays = int(numberOfSmallArrays.split("_")[1])
        bytes = int(bytes.split("_")[1])
        res = extract_times(os.path.join(dirname, file))

        results.append(
            {
                "large": large,
                "numberOfSmallArrays": numberOfSmallArrays,
                "bytes": bytes,
                "avg": res[0],
                "ci_lower": res[1][0],
                "ci_higher": res[1][1],
            }
        )

    results.sort(key=lambda x: (x["numberOfSmallArrays"], x["bytes"], x["large"]))

    return pd.DataFrame(results)


df = extract_all_from_dir("outputs")

print(df)

df["avg_log10"] = np.log10(df["avg"])
df["numberOfSmallArrays_log10"] = np.log10(df["numberOfSmallArrays"])

fig, axs = plt.subplots(2, 1, tight_layout=True, figsize=(10, 10))

# non_boxed = df[df["bytes"] == "65536"]
# print(non_boxed)
sns.barplot(x="numberOfSmallArrays", hue="bytes", y="avg", data=df, ax=axs[0])
sns.lineplot(x="numberOfSmallArrays_log10", hue="bytes", y="avg_log10", data=df, ax=axs[1], linewidth=3)
# non_prealloc = df[df["prealloc"] == "false"]
# print(non_prealloc)
# sns.barplot(x="numberOfObjects", hue="boxed", y="avg", data=non_prealloc, ax=axs[1])

plt.savefig("outputs/results.png")
plt.show()

