FROM openjdk8-jre

WORKDIR JavaGC

COPY --from=ant-tracks-analyzer:latest /anttracks/Tool/CLI/build/libs/CLI.jar CLI.jar

COPY --from=ant-tracks-jvm:latest /anttracks/jdk8u/dist/ jvm-builds/

RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/*
