package at.jku.anttracks.gui.chart.extjfx.chartpanes.application

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane.Companion.Unit.*
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.parser.EventType
import cern.extjfx.chart.NumericAxis
import javafx.collections.FXCollections
import javafx.scene.chart.StackedAreaChart
import javafx.scene.chart.XYChart
import javafx.scene.paint.Color

class ReducedAliveDeadChartPane : ReducedXYChartPane<AppInfo>() {
    override val chart = StackedAreaChart<Number, Number>(NumericAxis(), NumericAxis())
    override val seriesColors = listOf(Color.rgb(152, 78, 163),
                                       Color.GRAY)
    override val xUnit = TIME
    override val yUnits = listOf(OBJECTS_RELATIVE, BYTES_RELATIVE, OBJECTS, BYTES)

    override fun init(initialYUnit: Companion.Unit) {
        super.init(initialYUnit)
        chart.createSymbols = false
        extjfxChartPane.title = "Alive/Dead"
    }

    override fun createDataset(data: AppInfo): List<XYChart.Series<Number, Number>> {
        val preliminaryDataset = data.statistics.dropWhile { it.info.meta != EventType.GC_START }
                .chunked(2)
                .dropLastWhile { it.size != 2 }
                .map {
                    val before = when (selectedYUnitProperty.get()) {
                        OBJECTS, OBJECTS_RELATIVE -> it.first().totalObjects
                        BYTES, BYTES_RELATIVE -> it.first().totalBytes
                        else -> throw IllegalStateException()
                    }
                    val after = when (selectedYUnitProperty.get()) {
                        OBJECTS, OBJECTS_RELATIVE -> it.last().totalObjects
                        BYTES, BYTES_RELATIVE -> it.last().totalBytes
                        else -> throw IllegalStateException()
                    }
                    val alive = when (selectedYUnitProperty.get()) {
                        // survived ratio > 1 may happen if during a (concurrent) GC more
                        OBJECTS_RELATIVE, BYTES_RELATIVE -> if (after.toDouble() / before.toDouble() > 1.0) 1.0 else after.toDouble() / before.toDouble()
                        OBJECTS, BYTES -> after.toDouble()
                        else -> throw IllegalStateException()
                    }
                    val dead = when (selectedYUnitProperty.get()) {
                        OBJECTS_RELATIVE, BYTES_RELATIVE -> 1.0 - alive
                        OBJECTS, BYTES -> (before - after).toDouble()
                        else -> throw IllegalStateException()
                    }
                    Triple(it.last().info.time, alive, dead)
                }
                .toMutableList()

        // add 0 time datapoint where nothing is alive
        preliminaryDataset.add(0, Triple(0,
                                         0.0,
                                         when (selectedYUnitProperty.get()) {
                                             OBJECTS_RELATIVE, REACHABLE_BYTES -> 1.0
                                             OBJECTS, BYTES -> 0.0
                                             else -> throw IllegalStateException()
                                         }))

        return listOf(XYChart.Series<Number, Number>("Alive", FXCollections.observableArrayList(preliminaryDataset.map { XYChart.Data<Number, Number>(it.first, it.second) })),
                      XYChart.Series<Number, Number>("Dead", FXCollections.observableArrayList(preliminaryDataset.map { XYChart.Data<Number, Number>(it.first, it.third) })))
    }
}