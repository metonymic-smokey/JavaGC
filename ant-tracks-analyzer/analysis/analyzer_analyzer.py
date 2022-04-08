import re
import os
import sys
import itertools
import pandas as pd

move_matcher = re.compile(
    r"OBJECT (BORN|YEETED|MOVED): .*type: (.*), size: (-?\d+), isMirror: (true|false), isArray: (true|false), arrLen: (-?\d+)], bornAt=(-?\d+), lastMovedAt=(-?\d+), tag=(-?\d+)\) at: (\d+) address: (\d+) gcId: (\d+) allocationSites: (.*)"
)

data = []

filename = sys.argv[1]
with open(filename) as f:
    LIMIT = itertools.repeat(None)
    # LIMIT = range(100)
    for _, line in zip(LIMIT, f):
        for match in move_matcher.finditer(line):
            groups = match.groups()

            event_type, type, size, isMirror, isArray, arrLen, bornAt, lastMovedAt, tag, at, address, gcId, allocationSites = groups

            row = {
                "event": event_type,
                "type": type,
                "size": int(size),
                "isMirror": isMirror == "true",
                "isArray": isArray == "true",
                "arrLen": int(arrLen),
                "bornAt": int(bornAt),
                "lastMovedAt": int(lastMovedAt),
                "tag": int(tag),
                "at": int(at),
                "address": int(address),
                "gcId": int(gcId),
                "allocationSites": allocationSites
            }

            data.append(row)

df = pd.DataFrame(data)
print(df)
df.to_csv(f"./preprocessed_outputs/{filename.split(os.path.sep)[-1]}.csv", index=False)
