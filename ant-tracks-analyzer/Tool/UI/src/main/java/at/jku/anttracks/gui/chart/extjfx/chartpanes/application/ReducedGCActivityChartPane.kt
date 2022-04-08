package at.jku.anttracks.gui.chart.extjfx.chartpanes.application

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.parser.EventType
import cern.extjfx.chart.NumericAxis
import javafx.collections.FXCollections
import javafx.scene.chart.StackedAreaChart
import javafx.scene.chart.XYChart
import javafx.scene.control.Label
import javafx.scene.control.Slider
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import kotlin.math.max

class ReducedGCActivityChartPane : ReducedXYChartPane<AppInfo>() {
    override val chart = StackedAreaChart<Number, Number>(NumericAxis(), NumericAxis())
    override val xUnit = Companion.Unit.TIME
    override val yUnits = listOf(Companion.Unit.GC_TIME_RELATIVE)
    override val seriesColors = listOf(Color.STEELBLUE)

    private val plateauWidthSlider = Slider(1.0, 10.0, 1.0)
    override val chartOptionsNodes = listOf(VBox(Label("Plateau width (GC count):"), plateauWidthSlider))

    override fun init(initialYUnit: Companion.Unit) {
        super.init(initialYUnit)
        chart.title = "GC Overhead"
        chart.createSymbols = false

        plateauWidthSlider.apply {
            isShowTickLabels = true
            isShowTickMarks = true
            majorTickUnit = 2.0
            minorTickCount = 1
            blockIncrement = 2.0
            isSnapToTicks = true

            valueProperty().addListener { _, _, _ ->
                if (lastPlottedData != null) {
                    plot(lastPlottedData!!)
                }
            }
        }
    }

    override fun createDataset(data: AppInfo): List<XYChart.Series<Number, Number>> {
        val gcSeries = XYChart.Series<Number, Number>("GC Overhead", FXCollections.observableArrayList())
        val gcData = mutableListOf<XYChart.Data<Number, Number>>()

        val epsilon = 0.0001

        var lastAddedX = 0.0
        var lastEventTime = 0.0
        var mutatorSum = 0.0
        var gcSum = 0.0
        var countedGCEnd = 0

        for (stat in data.statistics) {
            val gcEvent = stat.info.meta
            val time = stat.info.time.toDouble()

            if (time != 0.0) { // Ignore the GC event at time 0
                if (gcEvent == EventType.GC_START) {
                    // GC Start
                    mutatorSum += time - lastEventTime
                    lastEventTime = time

                } else {
                    // GC End
                    countedGCEnd++
                    gcSum += time - lastEventTime

                    if (countedGCEnd == plateauWidthSlider.value.toInt() || stat == data.statistics.last()) {
                        // TODO min(...) because of risk for division by 0 when there are GCs or mutators with duration 0 -> preprocess statistics to prevent!
                        val mutatorPortion = mutatorSum / max(1.0, mutatorSum + gcSum)
                        val gcPortion = 1.0 - mutatorPortion
                        val gcStartOfWindow = XYChart.Data<Number, Number>(lastAddedX + epsilon, gcPortion)
                        val gcEndOfWindow = XYChart.Data<Number, Number>(time, gcPortion)
                        gcData.add(gcStartOfWindow)
                        gcData.add(gcEndOfWindow)

                        lastAddedX = time
                        mutatorSum = 0.0
                        gcSum = 0.0
                        countedGCEnd = 0
                    }

                    lastEventTime = time
                }
            }
        }

        gcSeries.data.addAll(gcData)
        return listOf(gcSeries)
    }

}