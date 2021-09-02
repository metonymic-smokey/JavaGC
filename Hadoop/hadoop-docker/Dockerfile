FROM debian:stretch

LABEL maintainer="samyak201@gmail.com"

RUN apt-get update && apt-get install -y \
    openjdk-8-jdk-headless \
    ssh \
    vim

ENV JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64
ENV PDSH_RCMD_TYPE=ssh

RUN service ssh start

RUN ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa && \
    cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys && \
    chmod 0600 ~/.ssh/authorized_keys
RUN ssh-keyscan -H localhost >> ~/.ssh/known_hosts

COPY ./hadoop-3.3.0 /hadoop
COPY entrypoint.sh /hadoop
ENV PATH=/hadoop/bin:$PATH
WORKDIR /hadoop

RUN head -n -3 /hadoop/etc/hadoop/core-site.xml > /hadoop/etc/hadoop/core-site.xml && printf \
'<configuration>\n\
    <property>\n\
        <name>fs.defaultFS</name>\n\
        <value>hdfs://localhost:9000</value>\n\
    </property>\n\
</configuration>' >> /hadoop/etc/hadoop/core-site.xml

RUN head -n -3 /hadoop/etc/hadoop/hdfs-site.xml > /hadoop/etc/hadoop/hdfs-site.xml && printf \
'<configuration>\n\
    <property>\n\
        <name>dfs.replication</name>\n\
        <value>1</value>\n\
    </property>\n\
</configuration>' >> /hadoop/etc/hadoop/hdfs-site.xml

RUN sed -i '/# export JAVA_HOME=/c\export JAVA_HOME=/usr/lib/jvm/java-8-openjdk-amd64' /hadoop/etc/hadoop/hadoop-env.sh && \
printf \
'export HDFS_NAMENODE_USER="root"\n\
export HDFS_DATANODE_USER="root"\n\
export HDFS_SECONDARYNAMENODE_USER="root"\n\
export YARN_RESOURCEMANAGER_USER="root"\n\
export YARN_NODEMANAGER_USER="root"\n\
export HADOOP_CLASSPATH=${JAVA_HOME}/lib/tools.jar' >> /hadoop/etc/hadoop/hadoop-env.sh

RUN bin/hdfs namenode -format
EXPOSE 9870

ENTRYPOINT ./entrypoint.sh
