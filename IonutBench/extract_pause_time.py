from collections import defaultdict
import csv
import os
from pprint import pprint
import re
from typing import List, Union

log_dir = "./moreLogs"

fils = sorted(os.listdir(log_dir))
# pprint(fils)

d = defaultdict(list)
for fil in fils:
    ind = fil.find(".log")
    d[fil[:ind]].append(fil)

pprint(d)

outfile = open("gc_stats.csv", "wt")
outcsv = csv.writer(outfile)

# CMS GC
total_young: List[Union[int, float]] = [0, 0, 0]
total_old: List[Union[int, float]] = [0, 0, 0]
for fil in d["gc_ConcMarkSweep"]:
    young_gc_re = re.compile(
        r"young generation GC time (\d+\.\d+) secs, (\d+) GC's, avg GC time (\d+\.\d+)"
    )
    old_gc_re = re.compile(
        r"old generation GC time (\d+\.\d+) secs, (\d+) GC's, avg GC time (\d+\.\d+)"
    )
    with open(os.path.join(log_dir, fil), "rt") as f:
        last_2_lines = f.readlines()[-2::1]

    matc = young_gc_re.search(last_2_lines[0])
    # print(matc.group(1), matc.group(2), matc.group(3))
    total_young[0] += float(matc.group(1))
    total_young[1] += int(matc.group(2))
    total_young[2] += float(matc.group(3))

    matc = old_gc_re.search(last_2_lines[1])
    print(matc.group(1), matc.group(2), matc.group(3))
    total_old[0] += float(matc.group(1))
    total_old[1] += int(matc.group(2))
    total_old[2] += float(matc.group(3))

outcsv.writerow(
    [
        "GC",
        "Young total time",
        "No. of young GC runs",
        "Avg young GC time",
        "Old total time",
        "No. of old GC runs",
        "Avg old GC time",
    ]
)
pprint(total_young)
pprint(total_old)
num = len(d["gc_ConcMarkSweep"])
outcsv.writerow(
    [
        "ConcMarkSweep",
        total_young[0] / num,
        total_young[1] // num,
        total_young[2] / num,
        total_old[0] / num,
        total_old[1] // num,
        total_old[1] / num,
    ]
)
outcsv.writerow([])

# for gc_name, fils in d.items():
#     for fil in fils:

outfile.close()
