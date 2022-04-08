package at.jku.anttracks.gui.chart.extjfx.chartpanes.application

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.model.AppInfo
import cern.extjfx.chart.NumericAxis
import javafx.collections.FXCollections
import javafx.scene.chart.StackedAreaChart
import javafx.scene.chart.XYChart
import javafx.scene.paint.Color

class ReducedObjectKindsChartPane : ReducedXYChartPane<AppInfo>() {
    override val chart = StackedAreaChart<Number, Number>(NumericAxis(), NumericAxis())
    override val seriesColors = listOf(Color.rgb(27, 158, 119),
                                       Color.rgb(217, 95, 2),
                                       Color.rgb(117, 112, 179))
    override val xUnit = Companion.Unit.TIME
    override val yUnits = listOf(Companion.Unit.OBJECTS,
                                 Companion.Unit.BYTES)

    override fun init(initialYUnit: Companion.Unit) {
        super.init(initialYUnit)
        chart.createSymbols = false
        extjfxChartPane.title = "Object kinds"
    }

    override fun createDataset(data: AppInfo): List<XYChart.Series<Number, Number>> =
            listOf(XYChart.Series<Number, Number>("Big arrays",
                                                  FXCollections.observableArrayList(data.statistics.map
                                                  {
                                                      XYChart.Data<Number, Number>(it.info.time,
                                                                                   when (selectedYUnitProperty.get()) {
                                                                                       Companion.Unit.OBJECTS -> it.totalBigArrays
                                                                                       Companion.Unit.BYTES -> it.totalBigArraysBytes
                                                                                       else -> throw IllegalStateException()
                                                                                   })
                                                  })),
                   XYChart.Series<Number, Number>("Small arrays",
                                                  FXCollections.observableArrayList(data.statistics.map {
                                                      XYChart.Data<Number, Number>(it.info.time,
                                                                                   when (selectedYUnitProperty.get()) {
                                                                                       Companion.Unit.OBJECTS -> it.totalSmallArrays
                                                                                       Companion.Unit.BYTES -> it.totalSmallArraysBytes
                                                                                       else -> throw IllegalStateException()
                                                                                   })
                                                  })),
                   XYChart.Series<Number, Number>("Instances",
                                                  FXCollections.observableArrayList(data.statistics.map {
                                                      XYChart.Data<Number, Number>(it.info.time,
                                                                                   when (selectedYUnitProperty.get()) {
                                                                                       Companion.Unit.OBJECTS -> it.totalInstances
                                                                                       Companion.Unit.BYTES -> it.totalInstancesBytes
                                                                                       else -> throw IllegalStateException()
                                                                                   })
                                                  })))

    override fun doAfterDatasetReduction() {
        if (lastPlottedData != null) {
            // updated range indicators
            clearRangeIndicatorPlugins()
            // TODO: Improve performance
            // extjfxChartPane.plugins.addAll(createGCRangeIndicatorPlugins(lastPlottedData!!))
        }
    }
}