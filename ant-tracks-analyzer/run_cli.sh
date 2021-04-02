#!/bin/sh
java -jar -Dat.jku.anttracks.gui.printStatisticsPath="stats.txt" Tool/Parser/build/libs/Parser.jar $1 ReportGC=true UseCallContext=true
