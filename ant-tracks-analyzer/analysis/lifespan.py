import pandas as pd
import itertools
from tqdm.notebook import tqdm

df = pd.read_csv("objects_17.txt.csv")

types = df["type"].unique()

df_objs = dict()

for type in types:
    df_objs[type] = df[df["type"] == type]
    
    stack = []
    c=0
    total=0

    for index,row in df_objs[type].iterrows():
        #print(row['event'])
        if row['event'] == "BORN":
            stack.append(row['at'])
            c+=1

        if row['event'] == "YEETED":
            if len(stack) > 0:
                born = stack.pop()
                diff = row['at'] - born
                total+=diff
                c+=1

    print(type,total/c)


