package at.jku.anttracks.util

fun <T> List<T>.filterInRangePadded(padding: Int, range: ClosedRange<Double>, valueExtractor: (T) -> Double, padEvenIfNoValuesWithinRange: Boolean = true): List<T> {
    val ret = mutableListOf<T>()

    var firstIndexBeforeRange = -1
    var firstIndexAfterRange = -1

    // filter
    for (i in 0 until size) {
        val value = valueExtractor(get(i))
        when {
            value in range                                           -> ret.add(get(i))
            value < range.start                                      -> firstIndexBeforeRange = i
            value > range.endInclusive && firstIndexAfterRange == -1 -> firstIndexAfterRange = i
        }
    }

    if (firstIndexAfterRange == -1) {
        firstIndexAfterRange = size
    }

    if (ret.isNotEmpty() || padEvenIfNoValuesWithinRange) {
        // pad
        for (i in 0 until padding) {
            if (firstIndexBeforeRange - i >= 0) {
                ret.add(0, get(firstIndexBeforeRange - i))
            }

            if (firstIndexAfterRange + i < size) {
                ret.add(get(firstIndexAfterRange + i))
            }
        }
    }

    return ret
}