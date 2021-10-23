FROM debian:stretch

LABEL maintainer="samyak201@gmail.com"

RUN apt-get update && apt-get install -y \
    ssh \
    vim

ENV PDSH_RCMD_TYPE=ssh

RUN service ssh start

RUN ssh-keygen -t rsa -P '' -f ~/.ssh/id_rsa && \
    cat ~/.ssh/id_rsa.pub >> ~/.ssh/authorized_keys && \
    chmod 0600 ~/.ssh/authorized_keys

RUN ssh-keyscan -H localhost >> ~/.ssh/known_hosts

COPY ./j2sdk-image /mnt
COPY ./hadoop-3.3.1 /hadoop
COPY entrypoint.sh /hadoop
ENV JAVA_HOME=/mnt
ENV PATH=/hadoop/bin:/mnt/bin:$PATH
WORKDIR /hadoop
RUN head -n -3 /hadoop/etc/hadoop/core-site.xml > /hadoop/etc/hadoop/core-site.xml && printf \
'<configuration>\n\
    <property>\n\
        <name>fs.defaultFS</name>\n\
        <value>hdfs://localhost:9000</value>\n\
    </property>\n\
</configuration>\n\
' >> /hadoop/etc/hadoop/core-site.xml

RUN head -n -3 /hadoop/etc/hadoop/hdfs-site.xml > /hadoop/etc/hadoop/hdfs-site.xml && printf \
'<configuration>\n\
    <property>\n\
        <name>dfs.replication</name>\n\
        <value>1</value>\n\
    </property>\n\
</configuration>\n\
' >> /hadoop/etc/hadoop/hdfs-site.xml

RUN head -n -3 /hadoop/etc/hadoop/mapred-site.xml > /hadoop/etc/hadoop/mapred-site.xml && printf \
'<configuration>\n\
    <property>\n\
        <name>mapred.child.java.opts</name>\n\
            <value>\n\
            -XX:+TraceObjects -XX:+TraceObjectsPointers\n\
            </value>\n\
    </property>\n\
    <property>\n\
        <name>mapreduce.map.java.opts</name>\n\
            <value>\n\
            -XX:+TraceObjects -XX:+TraceObjectsPointers\n\
            </value>\n\
    </property>\n\
    <property>\n\
        <name>mapreduce.reduce.java.opts</name>\n\
            <value>\n\
            -XX:+TraceObjects -XX:+TraceObjectsPointers\n\
            </value>\n\
    </property>\n\
</configuration>\n\
' >> /hadoop/etc/hadoop/mapred-site.xml


RUN sed -i '/# export JAVA_HOME=/c\export JAVA_HOME=/mnt' /hadoop/etc/hadoop/hadoop-env.sh && \
printf \
'export HDFS_NAMENODE_USER="root"\n\
export HDFS_DATANODE_USER="root"\n\
export HDFS_SECONDARYNAMENODE_USER="root"\n\
export YARN_RESOURCEMANAGER_USER="root"\n\
export YARN_NODEMANAGER_USER="root"\n\
export HADOOP_CLASSPATH=${JAVA_HOME}/lib/tools.jar' >> /hadoop/etc/hadoop/hadoop-env.sh

# RUN sed -i '/export HADOOP_OPTS=/c\export HADOOP_OPTS="-XX:+TraceObjects -XX:+TraceObjectsPointers -XX:TraceObjectsTraceFile=/src/trace -XX:TraceObjectsSymbolsFile=/src/symbols -XX:TraceObjectsClassDefinitionsFile=/src/classdefinitons"' /hadoop/etc/hadoop/hadoop-env.sh
RUN sed -i '/export HADOOP_OPTS=/c\export HADOOP_OPTS="-XX:+TraceObjects -XX:+TraceObjectsPointers"' /hadoop/etc/hadoop/hadoop-env.sh

RUN bin/hdfs namenode -format
EXPOSE 9870

RUN ["chmod", "+x", "entrypoint.sh"]

ENTRYPOINT ./entrypoint.sh
