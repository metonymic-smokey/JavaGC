package at.jku.anttracks.parser.hprof.datastructures

class StackTrace(val stackTraceSerialNum: Int,
                 val threadSerialNum: Int,
                 val numFrames: Int,
                 val stackFrameIds: LongArray)