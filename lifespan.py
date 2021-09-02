import pandas as pd
import itertools
from tqdm.notebook import tqdm

df = pd.read_csv("objects_17.txt.csv")

types = df["type"].unique()

df_objs = dict()

for type in types:
    df_objs[type] = df[df["type"] == type]
    
    born = 100000
    died = -1

    for index,row in df_objs[type].iterrows():
        #print(row['event'])
        if row['event'] == "BORN":
            born = min(born,row['at'])

        if row['event'] == "YEETED":
            died = max(died,row['at'])

    print(type,born,died)

