package at.jku.anttracks.gui.chart.base

import org.jfree.data.xy.XYDataItem
import java.awt.Color

class AntSeries(val key: String, val data: List<XYDataItem>, val color: Color) {
    val first = if (data.isEmpty()) 0.0 else data.first().yValue
    val last = if (data.isEmpty()) 0.0 else data.last().yValue
    val absoluteGrowth = last - first
    val relativeGrowth = if (first > 0) last / first else 0.0
    val start = if (data.isEmpty()) 0.0 else data.first().y.toDouble()
    val average = if (data.isEmpty()) 0.0 else data.map { it.yValue }.sum() / data.size
    val end = if (data.isEmpty()) 0.0 else data.last().y.toDouble()
}
