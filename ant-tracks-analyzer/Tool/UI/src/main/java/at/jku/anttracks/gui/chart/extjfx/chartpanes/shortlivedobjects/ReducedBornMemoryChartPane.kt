package at.jku.anttracks.gui.chart.extjfx.chartpanes.shortlivedobjects

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.Size
import cern.extjfx.chart.NumericAxis
import javafx.collections.FXCollections
import javafx.scene.chart.StackedAreaChart
import javafx.scene.chart.XYChart
import javafx.scene.paint.Color

class ReducedBornMemoryChartPane : ReducedXYChartPane<List<Pair<Long, Size>>>() {
    override val chart = StackedAreaChart(NumericAxis(), NumericAxis())
    override val xUnit = Companion.Unit.TIME
    override val yUnits = listOf(Companion.Unit.OBJECTS,
                                 Companion.Unit.BYTES)
    override val seriesColors = listOf((Color.rgb(228, 26, 28)))

    private lateinit var appInfo: AppInfo

    fun init(appInfo: AppInfo, initialYUnit: Companion.Unit = Companion.Unit.OBJECTS) {
        super.init(initialYUnit)
        this.appInfo = appInfo
        extjfxChartPane.title = "Allocations"
        chart.createSymbols = false
    }

    override fun createDataset(data: List<Pair<Long, Size>>) =
            listOf(XYChart.Series<Number, Number>("Born",
                                                  FXCollections.observableArrayList(data.map {
                                                      XYChart.Data<Number, Number>(it.first,
                                                                                   when (selectedYUnitProperty.get()) {
                                                                                       Companion.Unit.OBJECTS -> it.second.objects
                                                                                       Companion.Unit.BYTES -> it.second.bytes
                                                                                       else -> throw IllegalStateException()
                                                                                   })
                                                  })))

    override fun doAfterDatasetReduction() {
        // updated range indicators
        clearRangeIndicatorPlugins()
        // TODO: Improve performance
        // extjfxChartPane.plugins.addAll(createGCRangeIndicatorPlugins(appInfo))
    }
}