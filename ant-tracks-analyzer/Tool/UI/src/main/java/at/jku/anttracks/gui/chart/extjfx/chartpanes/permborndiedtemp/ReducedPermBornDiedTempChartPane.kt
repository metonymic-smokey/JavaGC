package at.jku.anttracks.gui.chart.extjfx.chartpanes.permborndiedtemp

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempData
import cern.extjfx.chart.NumericAxis
import javafx.collections.FXCollections
import javafx.scene.chart.StackedAreaChart
import javafx.scene.chart.XYChart
import javafx.scene.paint.Color

class ReducedPermBornDiedTempChartPane : ReducedXYChartPane<List<PermBornDiedTempData>>() {
    override val chart = StackedAreaChart<Number, Number>(NumericAxis(), NumericAxis())
    override val xUnit = Companion.Unit.TIME
    override val yUnits = listOf(Companion.Unit.OBJECTS,
                                 Companion.Unit.BYTES)
    override val seriesColors = listOf(Color.rgb(55, 126, 184),
                                       Color.rgb(228, 26, 28),
                                       Color.rgb(77, 175, 74),
                                       Color.GRAY)

    override fun init(initialYUnit: Companion.Unit) {
        super.init(initialYUnit)
        chart.createSymbols = false
        extjfxChartPane.title = "Perm/Born/Died/Temp"
    }

    override fun createDataset(data: List<PermBornDiedTempData>): List<XYChart.Series<Number, Number>> =
            listOf(XYChart.Series<Number, Number>("Perm",
                                                  FXCollections.observableArrayList(data.map {
                                                      XYChart.Data<Number, Number>(it.time,
                                                                                   when (selectedYUnitProperty.get()) {
                                                                                       Companion.Unit.OBJECTS -> it.perm.objects
                                                                                       Companion.Unit.BYTES -> it.perm.bytes
                                                                                       else -> throw IllegalStateException()
                                                                                   })
                                                  })),
                   XYChart.Series<Number, Number>("Died",
                                                  FXCollections.observableArrayList(data.map {
                                                      XYChart.Data<Number, Number>(it.time,
                                                                                   when (selectedYUnitProperty.get()) {
                                                                                       Companion.Unit.OBJECTS -> it.died.objects
                                                                                       Companion.Unit.BYTES -> it.died.bytes
                                                                                       else -> throw IllegalStateException()
                                                                                   })
                                                  })),
                   XYChart.Series<Number, Number>("Born",
                                                  FXCollections.observableArrayList(data.map {
                                                      XYChart.Data<Number, Number>(it.time,
                                                                                   when (selectedYUnitProperty.get()) {
                                                                                       Companion.Unit.OBJECTS -> it.born.objects
                                                                                       Companion.Unit.BYTES -> it.born.bytes
                                                                                       else -> throw IllegalStateException()
                                                                                   })
                                                  })),
                   XYChart.Series<Number, Number>("Temp",
                                                  FXCollections.observableArrayList(data.map {
                                                      XYChart.Data<Number, Number>(it.time,
                                                                                   when (selectedYUnitProperty.get()) {
                                                                                       Companion.Unit.OBJECTS -> it.temp.objects
                                                                                       Companion.Unit.BYTES -> it.temp.bytes
                                                                                       else -> throw IllegalStateException()
                                                                                   })
                                                  })))

}