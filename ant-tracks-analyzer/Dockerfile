FROM debian:stretch

RUN apt-get update && \
    apt-get -y upgrade && \
    apt-get -y install openjdk-8-jdk openjfx

ADD . /anttracks

WORKDIR /anttracks

RUN ["sh", "-c", "./gradlew jar"]

CMD ["sh", "-c", "./run.sh"]
