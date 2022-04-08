package at.jku.anttracks.gui.chart.extjfx.chartpanes.application

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.model.AppInfo
import cern.extjfx.chart.NumericAxis
import javafx.collections.FXCollections
import javafx.scene.chart.StackedAreaChart
import javafx.scene.chart.XYChart
import javafx.scene.paint.Color

open class ReducedMemoryChartPane : ReducedXYChartPane<AppInfo>() {
    override val chart = StackedAreaChart<Number, Number>(NumericAxis(), NumericAxis())
    override val seriesColors = listOf(Color.rgb(228, 26, 28),
                                       Color.rgb(55, 126, 184),
                                       Color.rgb(77, 175, 74))
    override val xUnit = Companion.Unit.TIME
    override val yUnits = listOf(Companion.Unit.OBJECTS,
                                 Companion.Unit.BYTES)

    override fun init(initialYUnit: Companion.Unit) {
        super.init(initialYUnit)
        chart.createSymbols = false
        extjfxChartPane.title = "Memory"
    }

    override fun createDataset(data: AppInfo): List<XYChart.Series<Number, Number>> =
            listOf(XYChart.Series<Number, Number>("Old",
                                                  FXCollections.observableArrayList(data.statistics.map {
                                                      XYChart.Data<Number, Number>(it.info.time,
                                                                                   when (selectedYUnitProperty.get()) {
                                                                                       Companion.Unit.OBJECTS -> it.old.memoryConsumption.objects
                                                                                       Companion.Unit.BYTES -> it.old.memoryConsumption.bytes
                                                                                       else -> throw IllegalStateException()
                                                                                   })
                                                  })),
                   XYChart.Series<Number, Number>("Survivor",
                                                  FXCollections.observableArrayList(data.statistics.map {
                                                      XYChart.Data<Number, Number>(it.info.time,
                                                                                   when (selectedYUnitProperty.get()) {
                                                                                       Companion.Unit.OBJECTS -> it.survivor.memoryConsumption.objects
                                                                                       Companion.Unit.BYTES -> it.survivor.memoryConsumption.bytes
                                                                                       else -> throw IllegalStateException()
                                                                                   })
                                                  })),
                   XYChart.Series<Number, Number>("Eden",
                                                  FXCollections.observableArrayList(data.statistics.map {
                                                      XYChart.Data<Number, Number>(it.info.time,
                                                                                   when (selectedYUnitProperty.get()) {
                                                                                       Companion.Unit.OBJECTS -> it.eden.memoryConsumption.objects
                                                                                       Companion.Unit.BYTES -> it.eden.memoryConsumption.bytes
                                                                                       else -> throw IllegalStateException()
                                                                                   })
                                                  })))

    override fun doAfterDatasetReduction() {
        if (lastPlottedData != null) {
            // updated range indicators
            clearRangeIndicatorPlugins()
            // TODO: Improve performance, becomes really laggy on "userstudy_2" example
            // extjfxChartPane.plugins.addAll(createGCRangeIndicatorPlugins(lastPlottedData!!))
        }
    }
}