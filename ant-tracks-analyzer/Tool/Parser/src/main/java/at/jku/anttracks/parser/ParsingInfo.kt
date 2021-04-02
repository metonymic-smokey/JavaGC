package at.jku.anttracks.parser

class ParsingInfo(val parsingStartTime: Long,
                  val fromTime: Long,
                  val toTime: Long,
                  val fromByte: Long,
                  val toByte: Long,
                  val traceLength: Long) {
    val parsingDuration: Long
        get() = System.currentTimeMillis() - parsingStartTime
    var reachableMemoryCalculationDuration: Long = 1
    val reachableMemoryCalculationOverhead: Double
        get() = reachableMemoryCalculationDuration.toDouble() / parsingDuration

    fun isWithinParseTimeWindow(timeToCheck: Long): Boolean = timeToCheck in fromTime..toTime
}