
package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.component.chartpane

import at.jku.anttracks.gui.chart.base.ChartSynchronizer
import at.jku.anttracks.gui.chart.jfreechart.xy.mixed.objectgrouptrend.ObjectGroupTrendChartDataSet
import at.jku.anttracks.gui.chart.jfreechart.xy.mixed.objectgrouptrend.ObjectGroupTrendJFreeChartPane
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.model.HeapEvolutionVisualizationInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.HeapEvolutionVisualizationTimeSeriesTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.HeapEvolutionVisualizationTimeSeriesDiagramInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.DiagramMetric
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.SeriesSort
import at.jku.anttracks.gui.model.ChartSelection
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.ImageUtil
import javafx.embed.swing.SwingFXUtils
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import javafx.scene.text.Font
import java.util.*

private const val stackedChartType = "Stacked"
private const val lineChartType = "Line"
private const val WINDOW_TO_CHART_HEIGHT_DIVISOR = 4

class HeapEvolutionVisualizationTimeSeriesChartContainer : BorderPane() {
    @FXML
    lateinit var settingsButton: Button
    @FXML
    private lateinit var settings: VBox
    @FXML
    private lateinit var arrow: ImageView
    @FXML
    private lateinit var label: Label
    @FXML
    private lateinit var chartTypeComboBox: ComboBox<String>
    @FXML
    private lateinit var seriesCountComboBox: ComboBox<Int>
    @FXML
    private lateinit var seriesSortComboBox: ComboBox<SeriesSort>
    @FXML
    private lateinit var memoryMetricComboBox: ComboBox<DiagramMetric>
    @FXML
    private lateinit var showOtherSeriesCheckBox: CheckBox
    @FXML
    lateinit var chart: ObjectGroupTrendJFreeChartPane

    /*
    private val examineHeapButtons = ArrayList<Button>()
    */

    lateinit var diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo
    private lateinit var info: HeapEvolutionVisualizationInfo
    private lateinit var parentTab: HeapEvolutionVisualizationTimeSeriesTab

    init {
        FXMLUtil.load(this, HeapEvolutionVisualizationTimeSeriesChartContainer::class.java)
    }

    fun init(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo, info: HeapEvolutionVisualizationInfo, parentTab: HeapEvolutionVisualizationTimeSeriesTab) {
        this.diagramInfo = diagramInfo
        this.info = info
        this.parentTab = parentTab

        // Do this configuration first, because others rely on the chart's LifetimeVisualizationDiagramInfo
        configureChart()

        configureArrow()
        configureClassificationLabel()
        configureSettingsButton()
        configureChartType()
        configureSeriesCount()
        configureSeriesSort()
        configureMemoryMetric()
        configureOtherSeries()
    }

    /*
    private fun createButtonRow(chart: LifetimeJFreeChartPane): HBox {
        val hBox = HBox()
        hBox.spacing = 10.0
        hBox.padding = Insets(10.0, 0.0, 10.0, 0.0)

        val examineHeapBtn = Button("Examine Heap")
        val chain = ClassifierChain(heapEvolutionInfo.selectedClassifierInfo.selectedClassifiers)
        val selectedFilters = heapEvolutionInfo.selectedClassifierInfo.selectedFilters
        examineHeapBtn.setOnAction { event ->
            ThreadUtil.startTask<IndexBasedHeap>(HeapStateTask(appInfo,
                                                               this,
                                                               chart.chartSynchronizer.selection[0].x.toLong(),
                                                               chain, null, selectedFilters))
        }
        examineHeapBtn.isDisable = true
        examineHeapButtons.add(examineHeapBtn)
        hBox.children.add(examineHeapBtn)

        return hBox
    }
    */

    fun updateChartData() {
        chart.update(info)
    }

    private fun configureChart() {
        chart.init(diagramInfo, parentTab.chartSynchronizer)
        chart.update(info)
        chart.minHeightProperty().bind(ClientInfo.stage.heightProperty().divide(WINDOW_TO_CHART_HEIGHT_DIVISOR))
        chart.maxHeightProperty().bind(ClientInfo.stage.heightProperty().divide(WINDOW_TO_CHART_HEIGHT_DIVISOR))
        chart.prefHeightProperty().bind(ClientInfo.stage.heightProperty().divide(WINDOW_TO_CHART_HEIGHT_DIVISOR))

        // chart.setOnMouseClicked { evt -> chart.requestFocus() }   // for some reason chart does not receive focus when clicked...
        chart.setOnMouseClicked { this.requestFocus() } // Set focus back to this pane if chart was clicked... Scrolling should happen on the pane, not on the chart
        // NodeUtil.ignoreScrollingUnlessFocused(chart);
        // NodeUtil.ignoreScrollingUnlessFocused(chart);

        chart.addListener(object : ObjectGroupTrendJFreeChartPane.ObjectGroupTrendChartListener {
            override fun keySelectedPrimary(key: String?) {
                var subChart: ObjectGroupTrendJFreeChartPane? = null
                if (key != null) {
                    subChart = tryCreateSubChartForKey(diagramInfo, key, ChartSynchronizer()) // ChartSynchronizer will be reset to a common one later
                }
                parentTab.removeAllDiagramContainersBelow(this@HeapEvolutionVisualizationTimeSeriesChartContainer)
                if (subChart != null) {
                    parentTab.addDiagramContainerFor(subChart.diagramInfo)
                }
            }

            override fun keySelectedAlternative(key: String) {
                val subChart = tryCreateSubChartForKey(diagramInfo, key, ChartSynchronizer()) // ChartSynchronizer will be reset to a common one later
                if (subChart != null) {
                    val newTab = HeapEvolutionVisualizationTimeSeriesTab()
                    newTab.init(subChart.diagramInfo.heapEvolutionVisualizationInfo, parentTab)
                    parentTab.openNewTabAndShowCharts(this@HeapEvolutionVisualizationTimeSeriesChartContainer, subChart.diagramInfo, newTab)
                }
            }
        })
    }

    private fun configureArrow() {
        arrow.image = SwingFXUtils.toFXImage(Consts.ARROW_IMAGE, null)
        arrow.prefWidth(Consts.ARROW_IMAGE.width.toDouble())
        arrow.prefHeight(Consts.ARROW_IMAGE.height.toDouble())
        arrow.rotate = 90.0
        arrow.isVisible = diagramInfo.selectedKeys.isNotEmpty()
        arrow.isManaged = arrow.isVisible
    }

    fun configureClassificationLabel() {
        label.text = getClassificationText(chart.diagramInfo, chart.dataset)
        label.padding = Insets(20.0, 0.0, 5.0, 0.0)
        label.font = Font(label.font.name, 20.0)
    }

    private fun configureSettingsButton() {
        settingsButton.graphic = ImageUtil.getIconNode(Consts.SETTINGS_IMAGE, 24, 24)
        settingsButton.onAction = EventHandler {
            settings.isVisible = !settings.isVisible
            settings.isManaged = !settings.isManaged
        }
    }

    private fun configureChartType() {
        chartTypeComboBox.items.addAll(lineChartType, stackedChartType)
        chartTypeComboBox.value = if (chart.diagramInfo.useLineChart) lineChartType else stackedChartType
        chartTypeComboBox.valueProperty().addListener { _, _, newValue ->
            diagramInfo.useLineChart = newValue == lineChartType
            chart.setUseLineChart(newValue == lineChartType)
            chart.update(info)
            val chartSynchronizer = chart.chartSynchronizer
            val lastChartSelection = chartSynchronizer.lastChartSelection
            // refresh chart selection with new chart
            if (lastChartSelection != null) {
                chartSynchronizer.select(ChartSelection(0, 0, 0.0, 0.0, ""))
                chartSynchronizer.select(lastChartSelection)
            }
        }
    }

    private fun configureSeriesCount() {
        for (i in 1..7) {
            seriesCountComboBox.items.add(i)
        }
        seriesCountComboBox.value = chart.diagramInfo.seriesCount
        seriesCountComboBox.valueProperty().addListener { _, _, newValue ->
            diagramInfo.seriesCount = newValue
            chart.update(info)
        }
    }

    private fun configureSeriesSort() {
        seriesSortComboBox.items.addAll(*SeriesSort.values())
        seriesSortComboBox.value = chart.diagramInfo.seriesSort
        seriesSortComboBox.valueProperty().addListener { _, _, newValue ->
            diagramInfo.seriesSort = newValue
            chart.update(info)
        }
    }

    private fun configureMemoryMetric() {
        memoryMetricComboBox.items.addAll(*DiagramMetric.values())
        memoryMetricComboBox.value = chart.diagramInfo.diagramMetric
        memoryMetricComboBox.valueProperty().addListener { _, _, newValue ->
            diagramInfo.diagramMetric = newValue
            chart.update(info)
        }
    }

    private fun configureOtherSeries() {
        showOtherSeriesCheckBox.isSelected = chart.diagramInfo.isShowOtherSeries
        showOtherSeriesCheckBox.selectedProperty().addListener { _, oldValue, newValue ->
            if (oldValue != newValue) {
                diagramInfo.isShowOtherSeries = newValue
                chart.update(info)
            }
        }
    }

    private fun tryCreateSubChartForKey(originalDiagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo, key: Any, chartSynchronizer: ChartSynchronizer): ObjectGroupTrendJFreeChartPane? {
        val newSelectedKeys = ArrayList(originalDiagramInfo.selectedKeys)
        newSelectedKeys.add(key.toString())

        val newDiagramInfo = HeapEvolutionVisualizationTimeSeriesDiagramInfo(info,
                                                                             originalDiagramInfo.useLineChart,
                                                                             originalDiagramInfo.seriesCount,
                                                                             originalDiagramInfo.seriesSort,
                                                                             originalDiagramInfo.diagramMetric,
                                                                             newSelectedKeys,
                                                                             originalDiagramInfo.isShowOtherSeries)
        val newChart = ObjectGroupTrendJFreeChartPane()
        newChart.init(newDiagramInfo, chartSynchronizer)
        newChart.update(info)

        if (newChart.dataset?.seriesCount == 0) {
            // Newly created chart would have no data, return null instead of new chart
            return null
        }

        return newChart
    }

    companion object {
        fun getClassificationText(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo, dataset: ObjectGroupTrendChartDataSet?) = buildString {
            val selectedKeysClassifiers = diagramInfo.getSelectedKeysClassifiers()

            if (diagramInfo.selectedKeys.isNotEmpty()) {
                append("Drill-down selection: ")
            }

            for (keyIndex in diagramInfo.selectedKeys.indices) {
                if (keyIndex > 0) {
                    append(" >>> ")
                }
                var shortKey = diagramInfo.selectedKeys[keyIndex].take(20)
                if (shortKey != diagramInfo.selectedKeys[keyIndex]) {
                    shortKey += "..."
                }
                append("(${keyIndex + 1}) ${selectedKeysClassifiers[keyIndex]}: ${shortKey}")
            }

            if (diagramInfo.selectedKeys.isNotEmpty()) {
                appendln()
            }

            if (dataset != null) {
                val classifiers = dataset.getClassifiersOfSeries().joinToString(" / ")
                append("$classifiers:")
            }
        }
    }
}
