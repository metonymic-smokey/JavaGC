FROM ubuntu:18.04

WORKDIR /anttracks

RUN apt-get update && apt-get install -y unzip wget mercurial && rm -rf /var/lib/apt/lists/*

RUN hg clone https://hg.openjdk.java.net/jdk8u/jdk8u /anttracks/jdk8u && cd /anttracks/jdk8u && bash get_source.sh

RUN rm -rf /anttracks/jdk8u/hotspot && mkdir /anttracks/jdk8u/hotspot

ADD ./src /anttracks/jdk8u/hotspot/

RUN sh -c "cp /anttracks/jdk8u/hotspot/build-scripts/build_AntTracksVM* /anttracks/jdk8u/"

WORKDIR /anttracks/jdk8u

#RUN apt-get update \
#    && apt-get install -y buildadd-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/-essential libfreetype6-dev libcups2-dev libx11-dev \
#    libxext-dev libxrender-dev libxrandr-dev libxtst-dev libxt-dev libasound2-dev libffi-dev autoconf openjdk-8-jdk && rm -rf /var/lib/apt/lists/*

#RUN apt-get update && apt-get install -y apt-transport-https gnupg software-properties-common && rm -rf /var/lib/apt/lists/*

#RUN wget -qO - https://adoptopenjdk.jfrog.io/adoptopenjdk/api/gpg/key/public | apt-key add -
#RUN add-apt-repository --yes https://adoptopenjdk.jfrog.io/adoptopenjdk/deb/ && apt-get update && apt-get install -y adoptopenjdk-8-hotspot && rm -rf /var/lib/apt/lists/*

RUN apt-get update \
    && apt-get install -y build-essential libfreetype6-dev libcups2-dev libasound2-dev \
    libx11-dev libxext-dev libxrender-dev libxrandr-dev libxtst-dev libxt-dev libasound2-dev \
    zip file \
    libfontconfig1-dev openjdk-8-jdk \
    libffi-dev autoconf && rm -rf /var/lib/apt/lists/*

RUN sed -i 's/sudo apt install .*//g' /anttracks/jdk8u/build_AntTracksVM_type_FixJavaFX.sh

RUN apt-get update \
    && apt-get install -y openjfx=8u161-b12-1ubuntu2 libopenjfx-java=8u161-b12-1ubuntu2 libopenjfx-jni=8u161-b12-1ubuntu2 \
    && rm -rf /var/lib/apt/lists/*

CMD bash build_AntTracksVM.sh 0 0 ./dist; bash
