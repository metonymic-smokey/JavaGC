package at.jku.anttracks.gui.utils

import cern.extjfx.chart.NumericAxis
import org.apache.commons.lang3.time.DurationFormatUtils
import java.text.DecimalFormat
import kotlin.math.absoluteValue

private val numberFormat = DecimalFormat("###,###.#")

fun toNiceNumberString(number: Number) =
        numberFormat.format(number)

fun toShortMemoryUsageString(byteCount: Long): String {
    var shortMemoryUsageString =
            when {
                byteCount.absoluteValue >= 1000000000 -> numberFormat.format(byteCount / 1_000_000_000.0) + " GB"
                byteCount.absoluteValue >= 1000000 -> numberFormat.format(byteCount / 1_000_000.0) + " MB"
                byteCount.absoluteValue >= 1000 -> numberFormat.format(byteCount / 1_000.0) + " KB"
                else -> numberFormat.format(byteCount) + " B"
            }

    if (byteCount < 0) {
        shortMemoryUsageString = "-$shortMemoryUsageString"
    }

    return shortMemoryUsageString
}

fun toPercentageString(number: Double) = "${numberFormat.format(number * 100.0)}%"

fun toBytesMemoryUsageString(byteCount: Long) =
        numberFormat.format(byteCount) + " B"

fun toShortNumberString(number: Long): String {
    var shortNumberString =
            when (number.absoluteValue) {
                in 1000..999_999 -> numberFormat.format(number / 1000.0) + "K"
                in 1_000_000..999_999_999 -> numberFormat.format(number / 1_000_000.0) + "M"
                in 1_000_000_000..999_999_999_999 -> numberFormat.format(number / 1_000_000_000.0) + "B"
                else -> number.toString()
            }

    if (number < 0) {
        shortNumberString = "-$shortNumberString"
    }

    return shortNumberString
}

fun toShortTimeAxisLabelString(millis: Long, axis: NumericAxis): String {
    // create full duration string and split into parts
    val timeStringParts = DurationFormatUtils.formatDuration(millis.absoluteValue, "H'h 'm'm 's's 'S'ms'", false)
            .dropWhile { !it.isDigit() || it.toString().toInt() == 0 }
            .split(" ")
            .filter { it.isNotEmpty() }
    val millisPart = timeStringParts.lastOrNull()
    val secondsPart = timeStringParts.dropLast(1).lastOrNull()
    val minutesPart = timeStringParts.dropLast(2).lastOrNull()
    val hoursPart = timeStringParts.dropLast(3).lastOrNull()

    val shortTimeParts = listOfNotNull(hoursPart, minutesPart, secondsPart, millisPart).filter { part -> part.filter { it.isDigit() }.toInt() > 0 }.toMutableList()
    if (axis.tickUnit >= 1_000 && shortTimeParts.size >= 2) {
        shortTimeParts.remove(millisPart)
        if (axis.tickUnit >= 60_000 && shortTimeParts.size >= 2) {
            shortTimeParts.remove(secondsPart)
            if (axis.tickUnit >= 3_600_000 && shortTimeParts.size >= 2) {
                shortTimeParts.remove(minutesPart)
            }
        }
    }

    return shortTimeParts.joinToString("")
}