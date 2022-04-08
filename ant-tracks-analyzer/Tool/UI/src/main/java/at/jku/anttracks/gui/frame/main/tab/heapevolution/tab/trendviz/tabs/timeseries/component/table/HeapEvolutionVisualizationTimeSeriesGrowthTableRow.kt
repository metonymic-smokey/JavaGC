

package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.component.table

import at.jku.anttracks.gui.chart.base.AntSeries
import at.jku.anttracks.util.toString
import javafx.beans.property.*
import java.awt.Color

class HeapEvolutionVisualizationTimeSeriesGrowthTableRow(label: String, color: Color, start: Double, average: Double, end: Double, relGrowth: Double, absGrowth: Double) {
    constructor(series: AntSeries) : this(series.key, series.color, series.start, series.average, series.end, series.relativeGrowth, series.absoluteGrowth)

    val labelProperty: StringProperty
    val colorProperty: ObjectProperty<Color>
    val start: DoubleProperty
    val average: DoubleProperty
    val end: DoubleProperty
    val relativeGrowth: DoubleProperty
    val absoluteGrowth: DoubleProperty

    init {
        this.labelProperty = SimpleStringProperty(label)
        this.colorProperty = SimpleObjectProperty(color)
        this.start = SimpleDoubleProperty(start)
        this.average = SimpleDoubleProperty(average)
        this.end = SimpleDoubleProperty(end)
        this.relativeGrowth = SimpleDoubleProperty(relGrowth)
        this.absoluteGrowth = SimpleDoubleProperty(absGrowth)
    }

    override fun toString(): String {
        return "${labelProperty.get()} (RGB ${colorProperty.get().rgb}): " +
                "START ${start.get().toString("%,2f")}, AVG ${average.get().toString("%,2f")}, END ${end.get().toString("%,2f")}" +
                "REL ${relativeGrowth.get().toString("$,2f")}%, ABS ${absoluteGrowth.get().toString("%,2f")}"
    }
}
