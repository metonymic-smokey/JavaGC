import re
import os
import sys
import itertools
import pandas as pd

move_matcher = re.compile(
    r"OBJECT (YEETED|MOVED): .*type: (.*), size: (-?\d+), isMirror: (true|false), isArray: (true|false), arrLen: (-?\d+)], bornAt=(-?\d+), lastMovedAt=(-?\d+), tag=(-?\d+)\) at: (\d+)"
)

data = []

filename = sys.argv[1]
with open(filename) as f:
    LIMIT = itertools.repeat(None)
    for _, line in zip(LIMIT, f):
        for match in move_matcher.finditer(line):
            groups = match.groups()

            event_type, type, size, isMirror, isArray, arrLen, bornAt, lastMovedAt, tag, at = groups

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
            }

            data.append(row)

df = pd.DataFrame(data)
print(df)
df.to_csv(f"./preprocessed_outputs/{filename.split(os.path.sep)[-1]}.csv")
