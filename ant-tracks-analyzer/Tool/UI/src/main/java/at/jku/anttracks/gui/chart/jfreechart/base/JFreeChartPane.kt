package at.jku.anttracks.gui.chart.jfreechart.base

import at.jku.anttracks.gui.chart.base.AntChartPane
import at.jku.anttracks.gui.chart.base.ChartSynchronizer
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.fxml.FXML
import org.jfree.chart.title.LegendTitle
import javax.naming.OperationNotSupportedException

abstract class JFreeChartPane<DS, DATA> : AntChartPane<DS, DATA>() {
    /**
     * chartViewer represents the JavaFX container for a JFreeChart.
     * it contains the following parts:
     * chartViewer.getCanvas()                         ... painting canvas, used to add overlays
     * chartViewer.getChart()                          ... the JFreeChart
     * chartViewer.getChart().getXYPlot()              ... the chart's plot
     * chartViewer.getChart().getXYPlot().getDataset() ... the chart's dataset
     */
    @FXML
    lateinit var chartViewer: AntChartViewer

    init {
        FXMLUtil.load(this, JFreeChartPane::class.java)
    }

    override fun init(chartSynchronizer: ChartSynchronizer, showZoomSyncCheckbox: Boolean) {
        super.init(chartSynchronizer, showZoomSyncCheckbox)
        var storedLegend: LegendTitle? = null

        chartViewer.hoverProperty().addListener { observable, oldValue, newValue ->
            if (newValue) {
                storedLegend = chartViewer.chart.legend
                chartViewer.chart.removeLegend()
            } else {
                if (storedLegend != null) {
                    chartViewer.chart.addLegend(storedLegend)
                }
            }
        }
    }

    override fun createsNewDatasetOnUpdate(): Boolean {
        return true
    }

    override fun postDataSetUpdate() {

    }

    override fun updateDataSet(data: DATA): DS {
        throw OperationNotSupportedException("JFreeChart charts always create a new dataset and do not update existing data set")
    }
}
