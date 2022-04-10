docker rm wchadoop2

#     -v /home/aayushnaik/Capstone/Hadoop/docker-hadoop-wordcount/src:/src \
docker run \
    --name wchadoop2 \
    -v /home/aayushnaik/Capstone/AntTracks/JVM/builds/gallant_franklin/slowdebug-64/j2sdk-image:/mnt \
    -v /home/aayushnaik/Capstone/Hadoop/traces:/src \
    -it hadoop /bin/bash
