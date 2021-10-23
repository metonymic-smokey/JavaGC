docker rm wchadoop

docker run \
    --name wchadoop \
    -v /home/aayushnaik/Capstone/Hadoop/docker-hadoop-wordcount/src:/src \
    -it hadoop /bin/bash
