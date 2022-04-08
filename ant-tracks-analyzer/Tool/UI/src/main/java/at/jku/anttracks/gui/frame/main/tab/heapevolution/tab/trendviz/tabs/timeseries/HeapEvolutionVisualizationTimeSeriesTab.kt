
package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries

import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.classification.trees.asJSON
import at.jku.anttracks.gui.chart.base.ChartSynchronizer
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane
import at.jku.anttracks.gui.classification.filter.OnlyDataStructureHeadsFilter
import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.frame.main.component.applicationbase.WebSocketEnabledTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.model.HeapEvolutionVisualizationInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.component.chartpane.HeapEvolutionVisualizationTimeSeriesChartContainer
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.HeapEvolutionVisualizationTimeSeriesDiagramInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.DiagramMetric
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.SeriesSort
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.task.HeapEvolutionVisualizationTimeSeriesPlottingTask
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.configurationpane.SelectedClassifiersConfigurationPane
import at.jku.anttracks.gui.frame.main.tab.heapstate.task.HeapStateTask
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.util.ParallelizationUtil
import at.jku.anttracks.util.ThreadUtil
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.MapChangeListener
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import org.controlsfx.control.PopOver
import java.util.*

private const val PLOT_THRESHOLD_MS = 1000

class HeapEvolutionVisualizationTimeSeriesTab : WebSocketEnabledTab() {
    override val webSocketHandlers: List<WebSocketCapabilityHandler> by lazy {
        listOf(
                WebSocketCapabilityHandler(WebSocketCapability.GET_ALL_TREES) {
                    heapEvolutionVisualizationInfo.groupings
                            .map { (time, root) -> root.asAntTracksJSON(null, heapEvolutionVisualizationInfo.selectedClassifiers, time) }
                            .fold(JsonArray()) { array: JsonArray, tree: JsonElement -> array.apply { add(tree) } }

                },
                WebSocketCapabilityHandler(WebSocketCapability.GET_ALL_POINTS_TO_MAPS) {
                    heapEvolutionVisualizationInfo.pointsToMaps.asJSON
                },
                WebSocketCapabilityHandler(WebSocketCapability.GET_ALL_POINTED_FROM_MAPS) {
                    heapEvolutionVisualizationInfo.pointedFromMaps.asJSON
                }
        )
    }

    @FXML
    lateinit var selectedClassifiersPane: SelectedClassifiersConfigurationPane

    @FXML
    lateinit var chartContainerBox: VBox

    override val componentDescriptions by lazy {
        val firstChartContainer = chartContainerBox.children.first() as HeapEvolutionVisualizationTimeSeriesChartContainer
        listOf(Triple(firstChartContainer.settingsButton,
                      Description("Click to configure the content and style of the chart"),
                      PopOver.ArrowLocation.BOTTOM_LEFT),
               Triple(firstChartContainer.chart.chartViewer,
                      Description("The chart plots the result of each classification that has been performed over the selected timeframe.")
                              .linebreak()
                              .appendDefault("Remember that in AntTracks a classification can have multiple levels (e.g. Type -> Allocation site -> Call sites).")
                              .appendDefault("This chart shows you just the first level! ")
                              .linebreak()
                              .appendDefault("To explore the next levels of the classification, simply click on the chart series you're interested in."),
                      PopOver.ArrowLocation.TOP_CENTER),
               Triple(firstChartContainer.chart.table,
                      Description("The table contains the series plotted in the chart and summarizes each series with a metric.")
                              .linebreak()
                              .appendDefault("You can configure this metric by clicking the configure button on the left!")
                              .linebreak()
                              .appendDefault("Just like in the chart, you can click a series to see the next level in the classification."),
                      PopOver.ArrowLocation.TOP_RIGHT))
    }

    override val initialTabIdeas by lazy {
        listOf(Idea("Object group trend tab",
                    Description("In this tab you can see how the heap has developed over the selected timeframe.")
                            .linebreak()
                            .appendDefault("In more detail: AntTracks parses your trace again and at multiple points in time, all heap objects are grouped by type, allocation site and call sites. ")
                            .appendDefault("In the chart and the table you can see the results of those groupings.")
                            .linebreak()
                            .appendDefault("When you select your analysis time window manually, you can also adjust the number of groupings and the used classifiers.")
                            .linebreak()
                            .appendDefault("Try to look for trends in the classification result, they might point you towards a memory leak!"),
                    listOf("I still don't get it..." does { showComponentDescriptions() }),
                    null,
                    this))
    }

    val chartSynchronizer = ChartSynchronizer()
    val chartContainers
        get() = chartContainerBox.children.filterIsInstance(HeapEvolutionVisualizationTimeSeriesChartContainer::class.java)

    private var currentChartUpdatingTask: HeapEvolutionVisualizationTimeSeriesPlottingTask? = null
    private var lastPlotTime: Long = 0

    lateinit var heapEvolutionVisualizationInfo: HeapEvolutionVisualizationInfo
    private var parent: HeapEvolutionVisualizationTimeSeriesTab? = null
    private lateinit var defaultDiagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo

    init {
        FXMLUtil.load(this, HeapEvolutionVisualizationTimeSeriesTab::class.java)
    }

    fun init(info: HeapEvolutionVisualizationInfo, parent: HeapEvolutionVisualizationTimeSeriesTab? = null) {
        this.heapEvolutionVisualizationInfo = info

        fun initDiagrams() {
            //clear old diagram data
            chartContainerBox.children.clear()
            //unregister from updates through parent
            if (this.parent != null) {
                //we set parent to null here, as we do not have any connection anymore - this also ensures that removeFromChildTabUpdates is not called multiple times
                this.parent = null
            }

            //create initial chart without data
            addDiagramContainerFor(defaultDiagramInfo)
        }

        super.init(info.appInfo,
                   SimpleStringProperty("Object group trend visualization"),
                   SimpleStringProperty("Time-series chart visualization based on the used classification"),
                   SimpleStringProperty("Visualizes the evolution of the heap over time. The objects are grouped according to the selected classifiers.\n" +
                                                "You can drill-down into object groups by clicking on the chart series or by selection the series in the table."),
                   Consts.HEAP_TREND_ICON,
                   listOf(
                           ActionTabAction("Inspect final heap state",
                                           "Open the heap state at the end of the selected time window",
                                           "Heap state",
                                           SimpleBooleanProperty(true),
                                           null,
                                           { openFinalHeapState() })
                   ),
                   false)

        this.defaultDiagramInfo = HeapEvolutionVisualizationTimeSeriesDiagramInfo(heapEvolutionVisualizationInfo,
                                                                                  false,
                                                                                  4,
                                                                                  SeriesSort.ABS_GROWTH,
                                                                                  DiagramMetric.BYTES,
                                                                                  ArrayList(),
                                                                                  false)

        initializeConfigurationPane()

        chartContainerBox.padding = Insets(5.0)
        initDiagrams()
        this.parent = parent

        heapEvolutionVisualizationInfo.groupings.addListener(MapChangeListener<Long, GroupingNode> { tryRunUpdatingThread() })
    }

    private fun initializeConfigurationPane() {
        selectedClassifiersPane.init(heapEvolutionVisualizationInfo.appInfo,
                                     heapEvolutionVisualizationInfo,
                                     ClassificationSelectionPane.ClassificationSelectionListener.NO_OP_CLASSIFIER_LISTENER,
                                     ClassificationSelectionPane.ClassificationSelectionListener.NOOP_FILTER_LISTENER)
        selectedClassifiersPane.filterSelectionPane.resetSelected(heapEvolutionVisualizationInfo.selectedFilters.filter { it.name != OnlyDataStructureHeadsFilter.NAME })
        selectedClassifiersPane.classifierSelectionPane.resetSelected(heapEvolutionVisualizationInfo.selectedClassifiers.list)
        selectedClassifiersPane.switchToAnalysisMode()
        selectedClassifiersPane.dataStructureSwitch.isDisable = true
    }

    private fun tryRunUpdatingThread() {
        if (currentChartUpdatingTask == null && System.currentTimeMillis() > lastPlotTime + PLOT_THRESHOLD_MS) {
            currentChartUpdatingTask = HeapEvolutionVisualizationTimeSeriesPlottingTask(this).apply {
                setOnSucceeded {
                    currentChartUpdatingTask = null
                    lastPlotTime = System.currentTimeMillis()
                }
                ParallelizationUtil.submitTask(this)
            }
        }
    }

    fun isDiagramAlreadyOpen(selectedKeys: List<String>): Boolean {
        for (chartContainer in chartContainerBox.children) {
            if ((chartContainer as? HeapEvolutionVisualizationTimeSeriesChartContainer)?.diagramInfo?.selectedKeys == selectedKeys) {
                return true
            }
        }
        return false
    }

    fun removeAllDiagramContainersBelow(chartContainer: HeapEvolutionVisualizationTimeSeriesChartContainer) {
        //remove charts from curCharts list
        var chartIndex = -1
        var i = 0
        while (i < chartContainerBox.children.size) {
            val curChartContainer = chartContainerBox.children[i] as HeapEvolutionVisualizationTimeSeriesChartContainer
            //save index of base chart
            if (curChartContainer == chartContainer) {
                chartIndex = i
            }
            i++
        }

        //remove charts from ui
        chartContainerBox.children.remove(chartIndex + 1, chartContainerBox.children.size)
    }

    fun addDiagramContainerFor(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo) {
        if (!isDiagramAlreadyOpen(diagramInfo.selectedKeys)) {
            chartContainerBox.children.add(HeapEvolutionVisualizationTimeSeriesChartContainer().apply {
                init(diagramInfo,
                     heapEvolutionVisualizationInfo,
                     this@HeapEvolutionVisualizationTimeSeriesTab)
            })
            Platform.runLater {
                var possibleParentScrollPane = parentProperty().get()
                while (possibleParentScrollPane != null && possibleParentScrollPane !is ScrollPane) {
                    possibleParentScrollPane = possibleParentScrollPane.parent
                }
                if (possibleParentScrollPane != null) {
                    (possibleParentScrollPane as ScrollPane).vvalueProperty().set(Double.MAX_VALUE) // scroll to bottom
                }
            }
        }
    }

    fun openNewTabAndShowCharts(baseChart: HeapEvolutionVisualizationTimeSeriesChartContainer,
                                newChartDiagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo,
                                newTab: HeapEvolutionVisualizationTimeSeriesTab) {
        for (curChart in chartContainerBox.children.drop(1)) {
            newTab.addDiagramContainerFor((curChart as? HeapEvolutionVisualizationTimeSeriesChartContainer)!!.diagramInfo)
            //we stop copying charts after the base chart
            if (curChart === baseChart) {
                break
            }
        }
        newTab.addDiagramContainerFor(newChartDiagramInfo)
        ClientInfo.mainFrame.addAndSelectTab(this, newTab)
    }

    fun openFinalHeapState(skipClassifierCombinationSelection: Boolean = false) {
        val heapStateTask = HeapStateTask(appInfo,
                                          this,
                                          heapEvolutionVisualizationInfo.endTime,
                                          !skipClassifierCombinationSelection) {
            if (skipClassifierCombinationSelection) {
                // Automatically apply default classification
                this.heapStateTab.acceptEdit()
            }
        }
        tasks.add(heapStateTask)
        ThreadUtil.startTask(heapStateTask)
        Unit
    }

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {}

    override fun cleanupOnClose() {

    }
}