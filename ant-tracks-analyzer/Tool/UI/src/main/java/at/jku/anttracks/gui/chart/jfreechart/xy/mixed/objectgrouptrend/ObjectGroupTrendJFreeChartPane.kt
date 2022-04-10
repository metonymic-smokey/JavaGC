
package at.jku.anttracks.gui.chart.jfreechart.xy.mixed.objectgrouptrend

import at.jku.anttracks.gui.chart.base.AxisScaler
import at.jku.anttracks.gui.chart.base.ChartSynchronizer
import at.jku.anttracks.gui.chart.base.JFreeChartAxisScaler
import at.jku.anttracks.gui.chart.jfreechart.base.AntJFreeChart
import at.jku.anttracks.gui.chart.jfreechart.base.JFreeChartFactory
import at.jku.anttracks.gui.chart.jfreechart.xy.mixed.objectgrouptrend.ObjectGroupTrendChartDataSet.Companion.OTHER
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.base.StackedAreaJFreeChartPane
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.model.HeapEvolutionVisualizationInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.component.table.HeapEvolutionVisualizationTimeSeriesGrowthTable
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.HeapEvolutionVisualizationTimeSeriesDiagramInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.DiagramMetric
import at.jku.anttracks.gui.utils.FXMLUtil
import io.micrometer.core.instrument.Metrics
import javafx.beans.property.SimpleStringProperty
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.input.ScrollEvent
import org.jfree.chart.entity.XYItemEntity
import org.jfree.chart.fx.ChartCanvas
import org.jfree.chart.fx.interaction.AbstractMouseHandlerFX
import org.jfree.chart.labels.StandardXYToolTipGenerator
import org.jfree.data.xy.DefaultTableXYDataset
import java.awt.Color
import java.util.*
import java.util.logging.Level

class ObjectGroupTrendJFreeChartPane : StackedAreaJFreeChartPane<HeapEvolutionVisualizationInfo, ObjectGroupTrendChartDataSet>() {
    @FXML
    lateinit var table: HeapEvolutionVisualizationTimeSeriesGrowthTable

    private val listeners = ArrayList<ObjectGroupTrendChartListener>()

    lateinit var diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo
    private lateinit var stackedChart: AntJFreeChart
    private lateinit var lineChart: AntJFreeChart

    val selectedKeyProperty = SimpleStringProperty("")
    private var latestInfo: HeapEvolutionVisualizationInfo? = null

    init {
        FXMLUtil.load(this, ObjectGroupTrendJFreeChartPane::class.java)
    }

    fun init(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo, synchronizer: ChartSynchronizer) {
        this.diagramInfo = diagramInfo
        this.chartViewer.onScroll = EventHandler<ScrollEvent> { it.consume() }
        table.init(diagramInfo)
        // We do not use table.selectionModel.selectedItemProperty().addListener on purpose here
        // because ChangeListener only fire on value changes
        // yet we also want to be notified if the same value has been clicked.
        // Thus, we use a MouseClicked event
        table.onMouseClicked = EventHandler { event ->
            if (table.selectionModel.selectedItem != null) {
                notifyKeySelectedPrimary(table.selectionModel.selectedItem.labelProperty.get())
            }
        }

        this.chartViewer.canvas.addAuxiliaryMouseHandler(SeriesSelectionMouseHandler())

        super.init(synchronizer, false)
    }

    override val scaler: AxisScaler<ObjectGroupTrendChartDataSet>
        get() {
            return when (diagramInfo.diagramMetric) {
                DiagramMetric.OBJECTS -> JFreeChartAxisScaler.ObjectsScaler(this)
                DiagramMetric.BYTES, DiagramMetric.RETAINED_SIZE, DiagramMetric.TRANSITIVE_CLOSURE_SIZE -> JFreeChartAxisScaler.BytesScaler(this)
            }
        }

    override val onConfigureAction: EventHandler<ActionEvent>?
        get() = null

    interface ObjectGroupTrendChartListener {
        fun keySelectedAlternative(key: String)
        fun keySelectedPrimary(key: String?)
    }

    fun addListener(l: ObjectGroupTrendChartListener) {
        listeners.add(l)
    }

    fun notifyKeyClickedMiddleMouseButton(selectedKey: String) {
        listeners.forEach { l -> l.keySelectedAlternative(selectedKey) }
    }

    fun notifyKeySelectedPrimary(selectedKey: String) {
        if (selectedKey == selectedKeyProperty.get()) {
            selectedKeyProperty.set(null)
            listeners.forEach { l -> l.keySelectedPrimary(null) }
        } else {
            selectedKeyProperty.set(selectedKey)
            listeners.forEach { l -> l.keySelectedPrimary(selectedKey) }
        }
        if (latestInfo != null) {
            // Regenerate dataset with selected key's series highlighted
            update(latestInfo)
        }
    }

    override fun createDataSet(info: HeapEvolutionVisualizationInfo): ObjectGroupTrendChartDataSet {
        latestInfo = info
        var dataset: ObjectGroupTrendChartDataSet? = null
        Metrics.timer("anttracks.objectgrouptrend.chart.createdataset").record {
            dataset = ObjectGroupTrendChartDataSet(info, selectedKeyProperty.get(), diagramInfo)
        }

        return dataset!!
    }

    override fun updateChartOnUIThread(dataset: ObjectGroupTrendChartDataSet) {
        super.updateChartOnUIThread(dataset)
        table.update(dataset)
        lineChart.xyPlot.domainAxis.isAutoRange = true
        stackedChart.xyPlot.domainAxis.isAutoRange = true
    }

    override val seriesColors: Array<Color>
        get() {
            return if (dataset == null) {
                super.seriesColors
            } else {
                dataset!!.antSeries.map { it.color }.toTypedArray()
            }
        }

    override fun initializeChart() {
        lineChart = JFreeChartFactory.createLineXYChart(this, "", null, "Time", "", DefaultTableXYDataset(), null, chartSynchronizer, false)
        stackedChart = JFreeChartFactory.createStackedXYAreaChart(this, "", null, "Time", "", DefaultTableXYDataset(), null, StandardXYToolTipGenerator(), null, chartSynchronizer, false)
        setCorrectChart()
    }

    private fun setCorrectChart() {
        chartViewer.setChart(if (diagramInfo.useLineChart) lineChart else stackedChart)
    }

    fun setUseLineChart(useLineChart: Boolean) {
        diagramInfo.useLineChart = useLineChart
        setCorrectChart()
    }

    private inner class SeriesSelectionMouseHandler : AbstractMouseHandlerFX("ObjectGroupTrendClickListener", false, false, false, false) {
        private var isInDragMotion = false

        override fun handleMouseDragged(canvas: ChartCanvas, e: MouseEvent) {
            isInDragMotion = true
        }

        override fun handleMouseReleased(canvas: ChartCanvas, e: MouseEvent) {
            if (isInDragMotion) {
                // ignore click that happened after drag
                isInDragMotion = false
                return
            }

            if (e.button == MouseButton.MIDDLE) {
                val selectedKey = getSelectedKey(canvas, e)
                if (selectedKey != null) {
                    notifyKeyClickedMiddleMouseButton(selectedKey.toString())
                }
            } else if (e.button == MouseButton.PRIMARY) {
                val selectedKey = getSelectedKey(canvas, e)
                if (selectedKey != null) {
                    notifyKeySelectedPrimary(selectedKey.toString())
                }
            }
        }

        //returns null if no series was clicked
        private fun getSelectedKey(canvas: ChartCanvas, e: MouseEvent): Any? {
            val info = canvas.renderingInfo ?: return null
            val entities = info.entityCollection ?: return null
            val entity = entities.getEntity(e.x, e.y) ?: return null

            if (entity is XYItemEntity) {
                val dataset = entity.dataset as DefaultTableXYDataset
                val selectedKeyString = dataset.getSeries(entity.seriesIndex).key
                if (selectedKeyString == OTHER) {
                    LOGGER.log(Level.INFO, "Cannot open diagram when clicking other")
                    return null
                }
                return selectedKeyString
            }
            return null
        }
    }
}