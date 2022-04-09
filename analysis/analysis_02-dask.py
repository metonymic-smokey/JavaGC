#!/usr/bin/env python
# coding: utf-8

# In[1]:


import pandas as pd


# In[2]:


import dask.dataframe as dd
# from dask.diagnostics import ProgressBar


# In[3]:


import matplotlib.pyplot as plt

import seaborn as sns

from tqdm import tqdm
from tqdm.dask import TqdmCallback

import os
import sys


# In[4]:


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


# In[5]:


# change params here
filename = sys.argv[1]
# classes with less than these many objects will be ignored
num_objects_threshold = 1000


# In[6]:


# directory to store graphs, etc.
output_dir_name = os.path.join("output", filename.replace(os.path.sep, "___").replace(".", "_._"))
os.makedirs(output_dir_name, exist_ok=True)

# In[7]:


df = dd.read_parquet(filename, engine="pyarrow")
# df = dd.read_csv(filename, names=columns, dtype=dtype, blocksize=None)
# df = dd.read_csv(filename, names=columns, dtype=dtype, blocksize="100MB")


# density plots

# with TqdmCallback(desc="lifetime density plot"):
#     sns.kdeplot(df["lifetime"])
# plt.savefig(os.path.join(output_dir_name, "overall_lifetime_density_plot.png"), bbox_inches='tight')
# plt.clf()

# with TqdmCallback(desc="lifetime density plot - log"):
#     sns.kdeplot(df["lifetime"], log_scale=True)
# plt.savefig(os.path.join(output_dir_name, "overall_lifetime_density_plot_log.png"), bbox_inches='tight')
# plt.clf()

# In[9]:


# def get_groups(group_by_cols, N=num_objects_threshold):
#     groups = df.groupby(group_by_cols)
    
#     def _process_group(group):
#         if len(group) >= N:
#             types.append(name)
            
#         return group
        
#     with TqdmCallback(desc="get groups"):
#         res = groups.count()
#         # bornAt is used here, but all cols have same value at this point
#         res = res[res["bornAt"] >= N]
#         res = res

#         types = list(res.index.compute().tolist())
    
#     return types, groups


# In[ ]:


groups = df.groupby("type")
# types, groups = get_groups("type", N=num_objects_threshold)
# num_types = len(types)

# print("Number of type groups:", num_types)


# ## Distribution of lifetimes for each type

# In[ ]:


# fig, axs = plt.subplots(num_types, 1, tight_layout=True, figsize=(10, 5 * num_types))

# with TqdmCallback(desc="lifetime distribution"):
#     for name, ax in tqdm(zip(types, axs), position=2, desc="lifetime distribution total"):
#         group = groups.get_group(name)
#         # display(group)
#         # print(name)

#         ax.hist(group.lifetime)
#         ax.set_xlabel(name)

# plt.savefig(os.path.join(output_dir_name, "lifetime_distribution.png"), bbox_inches='tight')
# plt.close(fig)


# ## Average lifetime of each class

# In[ ]:


def plot_agg(which_agg: str, N=num_objects_threshold):
    means = []
    print(f"getting {which_agg} of all types...")

    with TqdmCallback(desc=f"{which_agg}"):
        res = groups.agg({"lifetime": [which_agg, "count"]})
        res = res[res.lifetime["count"] >= N]
        res = res.compute()

        types = res.index.tolist()
        means = res.lifetime[which_agg].tolist()

    num_types = len(means)
    plt.gcf().set_size_inches(15, num_types // 2)
    plt.barh([i for i in range(num_types)], means, tick_label=types)

    # scale x-axis between min and max lifetime
    minx = min(means)
    maxx = max(means)
    # show 5% extra on both sides
    delta = 0.05 * minx
    plt.xlim(minx - delta, maxx + delta)
    # plt.xticks(rotation=45)
    
    plt.savefig(os.path.join(output_dir_name, f"{which_agg}_lifetimes.png"), bbox_inches='tight')
    plt.clf()


# In[ ]:


plot_agg("mean")


# In[ ]:


plot_agg("min")


# In[ ]:


plot_agg("max")


# ## Average lifetime of each class grouped by allocation site

# In[ ]:


alloc_site_groups = df.groupby(["type", "allocationSite"])
# alloc_site_group_names, alloc_site_groups = get_groups(["type", "allocationSite"], num_objects_threshold)
# num_names = len(alloc_site_group_names)
# print("Number of allocation site groups:", num_names)


# In[ ]:


def plot_aggs_alloc_site(which_agg: str, N=num_objects_threshold):
    means = []
    print(f"getting {which_agg} of all types...")

    with TqdmCallback(desc=f"{which_agg} alloc site"):
        res = alloc_site_groups.agg({"lifetime": [which_agg, "count"]})
        res = res[res.lifetime["count"] >= N]
        res = res.compute()

        alloc_site_group_names = res.index.tolist()
        means = res.lifetime[which_agg].tolist()

    num_names = len(means)
    plt.gcf().set_size_inches(15, num_names // 2)
    plt.barh([i for i in range(num_names)], means, tick_label=["-".join(i) for i in alloc_site_group_names])
    # scale x-axis between min and max lifetime
    minx = min(means)
    maxx = max(means)
    # show 5% extra on both sides
    delta = 0.05 * minx
    plt.xlim(minx - delta, maxx + delta)
    # plt.xticks(rotation=90)
    
        
    plt.savefig(os.path.join(output_dir_name, f"{which_agg}_lifetimes_alloc_site.png"), bbox_inches='tight')
    plt.clf()


# In[ ]:


plot_aggs_alloc_site("mean")


# In[ ]:


plot_aggs_alloc_site("min")


# In[ ]:


plot_aggs_alloc_site("max")


# In[ ]:


print(f"**** Saved outputs to {output_dir_name} *****")
