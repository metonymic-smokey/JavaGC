package at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane

import at.jku.anttracks.gui.chart.base.ApplicationChartFactory
import at.jku.anttracks.gui.chart.base.ChartSynchronizer
import at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane.base.StackedAreaJavaFXChartPane
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.utils.FXMLUtil
import cern.extjfx.chart.NumericAxis
import cern.extjfx.chart.data.DataReducingObservableList
import javafx.application.Platform
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.chart.XYChart

class MemoryApplicationStackedAreaChart : StackedAreaJavaFXChartPane<AppInfo>() {

    private var memoryUnit: ApplicationChartFactory.MemoryConsumptionUnit? = null

    override val onConfigureAction: EventHandler<ActionEvent>?
        get() = null

    init {
        FXMLUtil.load(this, MemoryApplicationStackedAreaChart::class.java)
    }

    override fun createsNewDatasetOnUpdate(): Boolean {
        return true
    }

    fun init(chartSynchronizer: ChartSynchronizer, memoryUnit: ApplicationChartFactory.MemoryConsumptionUnit) {
        super.init(chartSynchronizer, true)
        this.memoryUnit = memoryUnit
        chart.title = memoryUnit.label
    }

    override fun createDataSet(appInfo: AppInfo): ObservableList<XYChart.Series<Number, Number>>? {
        // create observable lists with complete data
        val edenList = FXCollections.observableList<XYChart.Data<Number, Number>>(
                appInfo.statistics.map { stat -> XYChart.Data<Number, Number>(stat.info.time, stat.eden.memoryConsumption.bytes) })
        val survivorList = FXCollections.observableList<XYChart.Data<Number, Number>>(
                appInfo.statistics.map { stat -> XYChart.Data<Number, Number>(stat.info.time, stat.survivor.memoryConsumption.bytes) })
        val oldList = FXCollections.observableList<XYChart.Data<Number, Number>>(
                appInfo.statistics.map { stat -> XYChart.Data<Number, Number>(stat.info.time, stat.old.memoryConsumption.bytes) })

        // reduce the data
        val reducedEdenList = DataReducingObservableList(chart.xAxis as NumericAxis, edenList)
        val reducedSurvivorList = DataReducingObservableList(chart.xAxis as NumericAxis, survivorList)
        val reducedOldList = DataReducingObservableList(chart.xAxis as NumericAxis, oldList)
        reducedEdenList.maxPointsCount = 500
        reducedSurvivorList.maxPointsCount = 500
        reducedOldList.maxPointsCount = 500

        // create series from the reduced data
        val reducedEdenSeries = XYChart.Series("EDEN", reducedEdenList)
        val reducedSurvivorSeries = XYChart.Series("SURVIVOR", reducedSurvivorList)
        val reducedOldSeries = XYChart.Series("OLD", reducedOldList)

        // display the series in the chart
        Platform.runLater {
            chart.data.clear()
            chart.data.addAll(reducedOldSeries, reducedSurvivorSeries, reducedEdenSeries)
        }

        return null
    }

    override fun updateDataSet(appInfo: AppInfo): ObservableList<XYChart.Series<Number, Number>>? {
        return null
    }

    override fun updateDataLabelContent() {

    }

    override fun initializeChartSeries() {

    }
}
