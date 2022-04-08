package at.jku.anttracks.parser.hprof.datastructures

class Thread(val threadSerialNum: Int,
             val threadObjectId: Long,
             val stackTraceSerialNum: Int,
             val threadNameStringId: Long,
             val threadGroupNameId: Long,
             val threadParentGroupNameId: Long)