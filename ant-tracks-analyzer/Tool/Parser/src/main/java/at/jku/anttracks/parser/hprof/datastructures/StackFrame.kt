package at.jku.anttracks.parser.hprof.datastructures

class StackFrame(val stackFrameId: Long,
                 val methodNameStringId: Long,
                 val methodSigStringId: Long,
                 val sourceFileNameStringId: Long,
                 val classSerialNum: Int,
                 val location: Int)
