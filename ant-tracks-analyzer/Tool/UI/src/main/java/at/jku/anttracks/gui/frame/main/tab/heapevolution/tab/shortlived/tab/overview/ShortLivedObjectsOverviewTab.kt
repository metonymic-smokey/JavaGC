package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.tab.overview

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane.Companion.SynchronizationOption.Action.PAN
import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane.Companion.SynchronizationOption.Action.ZOOM
import at.jku.anttracks.gui.chart.extjfx.chartpanes.desynchable
import at.jku.anttracks.gui.chart.extjfx.chartpanes.shortlivedobjects.ReducedBornMemoryChartPane
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.model.ShortLivedObjectsInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.tab.classification.ShortLivedObjectsClassificationTab
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.ideagenerators.ShortLivedObjectsAnalysisIdeaGenerator
import at.jku.anttracks.gui.utils.toNiceNumberString
import at.jku.anttracks.gui.utils.toShortMemoryUsageString
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.chart.BarChart
import javafx.scene.chart.PieChart
import javafx.scene.chart.XYChart
import javafx.scene.control.Tooltip
import javafx.scene.text.Text
import org.controlsfx.control.PopOver

class ShortLivedObjectsOverviewTab : ApplicationBaseTab() {
    @FXML
    private lateinit var gcOverheadChart: BarChart<Double, String>

    @FXML
    lateinit var gcFrequencyChart: BarChart<Double, String>

    @FXML
    private lateinit var gcTimePerGCTypeChart: PieChart

    @FXML
    private lateinit var gcTimePerGCCauseChart: PieChart

    @FXML
    private lateinit var gcCountPerGCTypeChart: PieChart

    @FXML
    private lateinit var gcCountPerGCCauseChart: PieChart

    @FXML
    private lateinit var bornObjectsChartPane: ReducedBornMemoryChartPane

    @FXML
    private lateinit var bornBytesChartPane: ReducedBornMemoryChartPane

    @FXML
    lateinit var objectGarbagePerTypeChart: PieChart

    @FXML
    lateinit var objectGarbagePerAllocationSiteChart: PieChart

    @FXML
    lateinit var memoryGarbagePerTypeChart: PieChart

    @FXML
    lateinit var memoryGarbagePerAllocationSiteChart: PieChart

    override val componentDescriptions by lazy {
        listOf(
                // topmost row
                Triple(gcOverheadChart,
                       Description("The GC overhead is the portion of application runtime spent in garbage collections.")
                               .linebreak()
                               .appendDefault("Comparing the overhead over this timeframe to the overhead over the whole application might tell you whether the GC behaviour is problematic over this timeframe."),
                       PopOver.ArrowLocation.BOTTOM_CENTER),
                Triple(gcFrequencyChart,
                       Description("The GC frequency is the number of garbage collections completed in a second of runtime.")
                               .linebreak()
                               .appendDefault("Comparing the frequency over this timeframe to the frequency over the whole application might tell you whether the GC behaviour is problematic over this timeframe."),
                       PopOver.ArrowLocation.BOTTOM_CENTER),

                // second row
                Triple(gcTimePerGCTypeChart,
                       Description("This chart tells you how the time spent in garbage collections is distributed between minor and major garbage collections.")
                               .linebreak()
                               .appendDefault("Hover the slices to see the absolute numbers!"),
                       PopOver.ArrowLocation.BOTTOM_CENTER),
                Triple(gcTimePerGCCauseChart,
                       Description("Garbage collections are triggered by certain events or states in the application.")
                               .linebreak()
                               .appendDefault("This chart tells you how much each of them contributed to the total time spent in garbage collections over this timeframe.")
                               .linebreak()
                               .appendDefault("Hover the slices to see the absolute numbers!"),
                       PopOver.ArrowLocation.BOTTOM_CENTER),
                Triple(gcCountPerGCTypeChart,
                       Description("This chart tells you how the number of garbage collections is distributed between minor and major garbage collections.")
                               .linebreak()
                               .appendDefault("Hover the slices to see the absolute numbers!"),
                       PopOver.ArrowLocation.BOTTOM_CENTER),
                Triple(gcCountPerGCCauseChart,
                       Description("Garbage collections are triggered by certain events or states in the application.")
                               .linebreak()
                               .appendDefault("This chart tells you how much each of them contributed to the total number of garbage collections over this timeframe.")
                               .linebreak()
                               .appendDefault("Hover the slices to see the absolute numbers!"),
                       PopOver.ArrowLocation.BOTTOM_CENTER),

                // third row
                Triple(bornObjectsChartPane,
                       Description("This chart shows the course of allocations and garbage collections over this timeframe while counting objects."),
                       PopOver.ArrowLocation.BOTTOM_CENTER),
                Triple(bornBytesChartPane,
                       Description("This chart shows the course of allocations and garbage collections over this timeframe while counting bytes."),
                       PopOver.ArrowLocation.BOTTOM_CENTER),

                // fourth row
                Triple(objectGarbagePerTypeChart.parent,
                       Description("These two charts tell you whether there are any types or allocation sites that can be connected to most or a substantial part of the garbage collected objects over this timeframe.")
                               .linebreak()
                               .appendDefault("If you're trying to reduce the number of short-lived object allocations, these types and allocation sites are a good starting point!"),
                       PopOver.ArrowLocation.BOTTOM_CENTER),
                Triple(memoryGarbagePerTypeChart.parent,
                       Description("These two charts tell you whether there are any types or allocation sites that can be connected to most or a substantial part of the garbage collected memory over this timeframe.")
                               .linebreak()
                               .appendDefault("If you're trying to reduce the number of garbage collections, these types and allocation sites are a good starting point!"),
                       PopOver.ArrowLocation.BOTTOM_CENTER)
        )
    }

    override val initialTabIdeas by lazy {
        listOf(Idea("Short-lived objects overview tab",
                    Description("This tab will give you an overview of the garbage collection activities and the composition of the garbage over the previously selected timeframe.")
                            .linebreak()
                            .appendDefault("The garbage collection metrics visualized on top should help you to determine whether problematic garbage collection behaviour is present over this timeframe.")
                            .linebreak()
                            .appendDefault("If this is the case, the visualizations on the bottom might help you in reducing the number of garbage collected objects and bytes."),
                    listOf("I don't get those charts!" does { showComponentDescriptions() }),
                    null,
                    this))
    }

    lateinit var info: ShortLivedObjectsInfo
    private lateinit var classificationTab: ShortLivedObjectsClassificationTab

    private val bornMemoryTimeSeries = mutableListOf<Pair<Long, Size>>()

    init {
        FXMLUtil.load(this, ShortLivedObjectsOverviewTab::class.java)
    }

    fun init(info: ShortLivedObjectsInfo, classificationTab: ShortLivedObjectsClassificationTab) {
        super.init(info.heapEvolutionInfo.appInfo,
                   SimpleStringProperty("GC activity"),
                   SimpleStringProperty("Visualization of GC activity"),
                   SimpleStringProperty("Analyse GC induced overhead and the objects responsible for it."),
                   Consts.CHART_ICON,
                   arrayListOf(),
                   true)
        this.info = info
        this.classificationTab = classificationTab

        bornObjectsChartPane.init(appInfo)
        bornBytesChartPane.init(appInfo, ReducedXYChartPane.Companion.Unit.BYTES)
        ReducedXYChartPane.synchronize(listOf(bornObjectsChartPane, bornBytesChartPane),
                                       ZOOM desynchable true,
                                       PAN desynchable true)

        // size charts to fill available space but never go below a certain height (use scrollpane if not enough space is available)
        heightProperty().addListener { _, _, _ ->
            listOf(gcTimePerGCTypeChart,
                   gcTimePerGCCauseChart,
                   gcCountPerGCCauseChart,
                   gcCountPerGCTypeChart,
                   objectGarbagePerAllocationSiteChart,
                   objectGarbagePerTypeChart,
                   memoryGarbagePerAllocationSiteChart,
                   memoryGarbagePerTypeChart).forEach {
                it.minHeight = 200.0
                it.prefHeight = height / 100 * 15
            }

            listOf(gcOverheadChart, gcFrequencyChart, bornObjectsChartPane, bornBytesChartPane).forEach {
                it.minHeight = 250.0
                it.prefHeight = height / 100 * 25

            }
        }
    }

    fun updateCharts(atGCStart: Boolean, bornObjectCount: Int, bornByteCount: Long) {
        // plot born memory trend
        bornMemoryTimeSeries.add(info.latestGCTime to Size(bornObjectCount.toDouble(), bornByteCount.toDouble()))
        bornObjectsChartPane.plot(bornMemoryTimeSeries)
        bornBytesChartPane.plot(bornMemoryTimeSeries)

        if (!atGCStart || info.latestGCTime == info.heapEvolutionInfo.endTime) {
            if (info.completedGCsCount >= 2) {
                gcOverheadChart.data = FXCollections.observableArrayList(XYChart.Series("GC overhead",
                                                                                        FXCollections.observableArrayList(XYChart.Data(info.heapEvolutionInfo.appInfo.gcOverhead * 100,
                                                                                                                                       "Whole app"),
                                                                                                                          XYChart.Data(info.gcDurationPortion * 100,
                                                                                                                                       "This window"))))

                gcFrequencyChart.data = FXCollections.observableArrayList(XYChart.Series("GC frequency",
                                                                                         FXCollections.observableArrayList(XYChart.Data(info.heapEvolutionInfo.appInfo.gcFrequency,
                                                                                                                                        "Whole app"),
                                                                                                                           XYChart.Data(info.gcFrequency, "This window"))))

                gcTimePerGCTypeChart.data = FXCollections.observableArrayList(PieChart.Data("Minor GC", info.minorGCDuration.toDouble()),
                                                                              PieChart.Data("Major GC", info.majorGCDuration.toDouble()))
                gcTimePerGCCauseChart.data = FXCollections.observableArrayList(info.gcCauseDurations.map { PieChart.Data(it.key.name, it.value.toDouble()) })

                gcCountPerGCTypeChart.data = FXCollections.observableArrayList(PieChart.Data("Minor GC", info.completedMinorGCsCount.toDouble()),
                                                                               PieChart.Data("Major GC", info.completedMajorGCsCount.toDouble()))
                gcCountPerGCCauseChart.data = FXCollections.observableArrayList(info.gcCauseCount.map { PieChart.Data(it.key.name, it.value.toDouble()) })

                listOf(gcTimePerGCTypeChart,
                       gcTimePerGCCauseChart).forEach {
                    addPieChartTooltips(it,
                                        Companion.PieChartUnit.MILLISECONDS)
                }

                listOf(gcCountPerGCTypeChart,
                       gcCountPerGCCauseChart).forEach {
                    addPieChartTooltips(it,
                                        Companion.PieChartUnit.GC_COUNT)
                }
            }
        }

        if (!atGCStart && info.latestGCTime != info.heapEvolutionInfo.startTime) {
            // at every gc end (skip initial gc end in time window) show types and allocation sites that produce the most garbage
            updateGarbagePieCharts()
        }

        if (info.latestGCTime == info.heapEvolutionInfo.endTime) {
            makeGarbagePieChartsClickable()
            ShortLivedObjectsAnalysisIdeaGenerator.analyzeOverviewTab(this)
        }
    }

    private fun updateGarbagePieCharts() {
        // find types with most dead objects
        val garbageObjectsByType = info.garbageGroupedByType.root.children.find { it.key.toString() == "0 GCs survived" }!!.children.sortedByDescending { it.objectCount }
        val typesWithMostGarbageObjects = FXCollections.observableArrayList(garbageObjectsByType.take(PIE_CHART_DATA_SIZE).map {
            // remove package from type name
            PieChart.Data(it.key.toString().split(".").last(), it.objectCount.toDouble())
        })
        typesWithMostGarbageObjects.add(PieChart.Data("Other", garbageObjectsByType.drop(PIE_CHART_DATA_SIZE).sumByDouble { it.objectCount.toDouble() }))
        objectGarbagePerTypeChart.data = typesWithMostGarbageObjects

        // find types with most dead memory
        val garbageMemoryByType = info.garbageGroupedByType.root.children.find { it.key.toString() == "0 GCs survived" }!!.children.sortedByDescending { it.getData(true).getBytes(null).toDouble() }

        val typesWithMostGarbageMemory = FXCollections.observableArrayList(garbageMemoryByType.take(PIE_CHART_DATA_SIZE).map {
            // remove package from type name
            PieChart.Data(it.key.toString().split(".").last(), it.getData(true).getBytes(null).toDouble())
        })
        typesWithMostGarbageMemory.add(PieChart.Data("Other", garbageMemoryByType.drop(PIE_CHART_DATA_SIZE).sumByDouble
        { it.getData(true).getBytes(null).toDouble() }))
        memoryGarbagePerTypeChart.data = typesWithMostGarbageMemory

        // find alloc sites with most dead objects
        val garbageObjectsByAllocSite = info.garbageGroupedByAllocSite.root.children.find { it.key.toString() == "0 GCs survived" }!!.children.sortedByDescending { it.objectCount }
        val allocSitesWithMostGarbageObjects = FXCollections.observableArrayList(garbageObjectsByAllocSite.take(PIE_CHART_DATA_SIZE).map {
            PieChart.Data(it.key.toString(), it.objectCount.toDouble())
        })
        allocSitesWithMostGarbageObjects.add(PieChart.Data("Other", garbageObjectsByAllocSite.drop(PIE_CHART_DATA_SIZE).sumByDouble
        { it.objectCount.toDouble() }))
        objectGarbagePerAllocationSiteChart.data = allocSitesWithMostGarbageObjects

        // find alloc sites with most dead memory
        val garbageMemoryByAllocSite = info.garbageGroupedByAllocSite.root.children.find { it.key.toString() == "0 GCs survived" }!!.children.sortedByDescending {
            it.getData(true)
                    .getBytes(null)
                    .toDouble()
        }
        val allocSitesWithMostGarbageMemory = FXCollections.observableArrayList(garbageMemoryByAllocSite.take(PIE_CHART_DATA_SIZE).map {
            PieChart.Data(it.key.toString(), it.getData(true).getBytes(null).toDouble())
        })
        allocSitesWithMostGarbageMemory.add(PieChart.Data("Other", garbageMemoryByAllocSite.drop(PIE_CHART_DATA_SIZE).sumByDouble
        { it.getData(true).getBytes(null).toDouble() }))
        memoryGarbagePerAllocationSiteChart.data = allocSitesWithMostGarbageMemory

        listOf(objectGarbagePerTypeChart, objectGarbagePerAllocationSiteChart).forEach {
            addPieChartTooltips(it,
                                Companion.PieChartUnit.OBJECT_COUNT)
        }
        listOf(memoryGarbagePerTypeChart, memoryGarbagePerAllocationSiteChart).forEach {
            addPieChartTooltips(it,
                                Companion.PieChartUnit.BYTES)
        }
    }

    private fun addPieChartTooltips(pieChart: PieChart, unit: PieChartUnit) {
        pieChart.data.forEach { data ->
            // add tooltip to pie slice showing full label and metric
            val metricString: String = when (unit) {
                Companion.PieChartUnit.OBJECT_COUNT -> "${toNiceNumberString(data.pieValue.toLong())} objects"
                Companion.PieChartUnit.BYTES -> toShortMemoryUsageString(data.pieValue.toLong())
                Companion.PieChartUnit.MILLISECONDS -> "${toNiceNumberString(data.pieValue.toLong())} ms"
                Companion.PieChartUnit.GC_COUNT -> "${toNiceNumberString(data.pieValue.toLong())} GCs"
            }
            Tooltip.install(data.node, Tooltip("${data.name}: $metricString"))

            // long labels should be shortened
            if (data.name.length >= 20) {
                // install a tooltip to the label containing the full label
                Tooltip.install(pieChart.lookupAll(".chart-pie-label").map { it as Text }.first { it.text == data.name },
                                Tooltip(data.name))
                // shorten label
                data.name = data.name.take(17).plus("...")
            }
        }
    }

    private fun makeGarbagePieChartsClickable() {
        val typePieCharts = listOf(objectGarbagePerTypeChart, memoryGarbagePerTypeChart)
        val allocSitePieCharts = listOf(objectGarbagePerAllocationSiteChart, memoryGarbagePerAllocationSiteChart)

        typePieCharts.plus(allocSitePieCharts).flatMap { it.data }.forEach { data ->
            data.node.opacity = 0.75

            data.node.setOnMouseEntered { data.node.opacity = 1.0 }

            data.node.setOnMouseExited { data.node.opacity = 0.75 }
        }


        objectGarbagePerTypeChart.data.forEach { data ->
            data.node.setOnMouseClicked {
                classificationTab.showTabAndHighlight(true, info.garbageGroupedByType, data.name, true)
            }
        }

        memoryGarbagePerTypeChart.data.forEach { data ->
            data.node.setOnMouseClicked {
                classificationTab.showTabAndHighlight(true, info.garbageGroupedByType, data.name, false)
            }
        }

        objectGarbagePerAllocationSiteChart.data.forEach { data ->
            data.node.setOnMouseClicked {
                classificationTab.showTabAndHighlight(false, info.garbageGroupedByAllocSite, data.name, true)
            }
        }

        memoryGarbagePerAllocationSiteChart.data.forEach { data ->
            data.node.setOnMouseClicked {
                classificationTab.showTabAndHighlight(false, info.garbageGroupedByAllocSite, data.name, false)
            }
        }
    }

    override fun cleanupOnClose() {}

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {}

    companion object {
        private const val PIE_CHART_DATA_SIZE = 4

        enum class PieChartUnit {
            OBJECT_COUNT,
            BYTES,
            MILLISECONDS,
            GC_COUNT
        }
    }
}