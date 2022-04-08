#!/bin/sh
~/PESU/Capstone/AntTracks/JVM/j2sdk-image/bin/java -Djava.util.logging.config.file=log.config -Xmx$(java -jar Tool/HeapSizeSelector/build/libs/HeapSizeSelector.jar AntTracks ~/.ant_tracks/heap_size.config) -XX:+UseG1GC -XX:OnOutOfMemoryError="kill -9 %p" -XX:+IgnoreUnrecognizedVMOptions -XX:-TraceObjects -jar Tool/UI/build/libs/UI.jar || rm ~/.anttracks_heap_size.config
