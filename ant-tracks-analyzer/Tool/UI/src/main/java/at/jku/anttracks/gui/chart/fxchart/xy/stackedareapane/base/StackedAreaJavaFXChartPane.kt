
package at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane.base

import at.jku.anttracks.gui.chart.fxchart.xy.base.XYBaseJavaFXChartPane
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.toShortMemoryUsageString
import cern.extjfx.chart.AxisMode
import cern.extjfx.chart.NumericAxis
import cern.extjfx.chart.XYChartPane
import cern.extjfx.chart.plugins.Panner
import cern.extjfx.chart.plugins.XRangeIndicator
import cern.extjfx.chart.plugins.Zoomer
import javafx.collections.ObservableList
import javafx.fxml.FXML
import javafx.geometry.Pos
import javafx.scene.chart.StackedAreaChart
import javafx.scene.chart.XYChart
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.util.StringConverter
import java.util.*

abstract class StackedAreaJavaFXChartPane<DATA> : XYBaseJavaFXChartPane<DATA>() {

    @FXML
    private lateinit var centerContent: VBox

    init {
        FXMLUtil.load(this, StackedAreaJavaFXChartPane::class.java)

        val xAxis = NumericAxis()
        xAxis.animated = false

        val yAxis = NumericAxis()
        yAxis.animated = false
        yAxis.tickLabelFormatter = object : StringConverter<Number>() {
            override fun toString(n: Number): String {
                return toShortMemoryUsageString(n.toLong())
            }

            override fun fromString(p0: String): Number {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }
        }

        val chart = StackedAreaChart(xAxis, yAxis)
        chart.animated = false
        this.chart = chart

        val extjfxChartPane = XYChartPane(chart)
        extjfxChartPane.plugins.add(Zoomer(AxisMode.X))
        extjfxChartPane.plugins.add(Panner())
        extjfxChartPane.plugins.add(XRangeIndicator(150_000.0, 200_000.0, "selected time window"))

        centerContent.children.add(extjfxChartPane)
        BorderPane.setAlignment(extjfxChartPane, Pos.CENTER)
        extjfxChartPane.prefHeight = 500.0
        extjfxChartPane.prefWidth = 500.0
        chart.prefHeight = 500.0
        chart.prefWidth = 500.0
    }

    override fun getMaxYValue(dataset: ObservableList<XYChart.Series<Number, Number>>): Double {
        var maxYValue = java.lang.Double.MIN_VALUE

        val longestSeries = dataset.stream().max(Comparator.comparingInt<XYChart.Series<Number, Number>> { s -> s.getData().size }).get()

        for (i in 0 until longestSeries.data.size) {
            // sum up the y value at index i of each series in list
            val stackedSum = dataset.stream().mapToDouble { s -> if (s.data.size > 0) s.data[i].yValue.toDouble() else 0.0 }.sum()

            if (stackedSum > maxYValue) {
                maxYValue = stackedSum
            }
        }

        return maxYValue
    }

    override fun getMinYValue(dataset: ObservableList<XYChart.Series<Number, Number>>): Double {
        var minYValue = java.lang.Double.MAX_VALUE

        val longestSeries = dataset.stream().max(Comparator.comparingInt<XYChart.Series<Number, Number>> { s -> s.data.size }).get()

        for (i in 0 until longestSeries.data.size) {
            // sum up the y value at index i of each series in list
            val stackedSum = dataset.stream().mapToDouble { s -> if (s.data.size > 0) s.data[i].yValue.toDouble() else 0.0 }.sum()

            if (stackedSum < minYValue) {
                minYValue = stackedSum
            }
        }

        return minYValue
    }
}