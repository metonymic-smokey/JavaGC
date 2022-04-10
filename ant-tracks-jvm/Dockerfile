FROM ubuntu:18.04

WORKDIR /anttracks

RUN apt-get update && apt-get install -y unzip wget mercurial && rm -rf /var/lib/apt/lists/*

# this step can take a long long time, so avoid redoing this by putting it at the top
RUN hg clone https://hg.openjdk.java.net/jdk8u/jdk8u /anttracks/jdk8u && cd /anttracks/jdk8u && bash get_source.sh

# we will replace the hotspot directory with the AntTracks JVM
RUN rm -rf /anttracks/jdk8u/hotspot && mkdir /anttracks/jdk8u/hotspot

WORKDIR /anttracks/jdk8u

# this will take time too
RUN apt-get update \
    && apt-get install -y build-essential libfreetype6-dev libcups2-dev libasound2-dev \
    libx11-dev libxext-dev libxrender-dev libxrandr-dev libxtst-dev libxt-dev libasound2-dev \
    libfontconfig1-dev openjdk-8-jdk \
    openjfx=8u161-b12-1ubuntu2 libopenjfx-java=8u161-b12-1ubuntu2 libopenjfx-jni=8u161-b12-1ubuntu2 \
    libffi-dev autoconf && rm -rf /var/lib/apt/lists/*

# if you want install additional packages, do it here
RUN apt-get update \
    && apt-get install -y \
    less \
    zip \
    file \
    # add here
    && rm -rf /var/lib/apt/lists/*

# copy AntTracks JVM source
ADD ./src /anttracks/jdk8u/hotspot/

# copy AntTracks build scripts to JDK root folder
RUN sh -c "cp /anttracks/jdk8u/hotspot/build-scripts/build_AntTracksVM* /anttracks/jdk8u/"

# we have already done the required apt install previously, so remove it from the build script
RUN sed -i 's/sudo apt install .*//g' /anttracks/jdk8u/build_AntTracksVM_type_FixJavaFX.sh

# build time!
# look in the "dist" folder for the builds
RUN bash build_AntTracksVM.sh 0 0 ./dist
