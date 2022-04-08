#!/bin/sh
# java -XX:+PrintGCDetails -XX:+PrintGC -Xloggc:gc.log -jar -Dat.jku.anttracks.gui.printStatisticsPath="stats.txt" Tool/CLI/build/libs/CLI.jar $1 ReportGC=true UseCallContext=true
java -jar -Dat.jku.anttracks.gui.printStatisticsPath="stats.txt" Tool/CLI/build/libs/CLI.jar $1 ReportGC=true UseCallContext=true
