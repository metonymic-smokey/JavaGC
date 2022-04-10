package at.jku.anttracks.util

fun ClosedRange<Double>.coerceIn(otherRange: ClosedRange<Double>) = start.coerceIn(otherRange)..endInclusive.coerceIn(otherRange)

fun ClosedRange<Double>.contains(otherRange: ClosedRange<Double>) = contains(otherRange.start) && contains(otherRange.endInclusive)

fun ClosedRange<Double>.width() = endInclusive - start

operator fun ClosedRange<Double>.plus(offset: Double): ClosedRange<Double> = (start + offset)..(endInclusive + offset)

