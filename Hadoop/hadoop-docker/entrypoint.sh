#!/usr/bin/env bash

service ssh start

sbin/start-dfs.sh

bin/hdfs dfs -mkdir /user
bin/hdfs dfs -mkdir /user/root
bin/hdfs dfs -mkdir input
bin/hdfs dfs -put etc/hadoop/*.xml input

bash $@
