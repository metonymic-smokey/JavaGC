package at.jku.anttracks.gui.chart.extjfx.chartpanes

import at.jku.anttracks.gui.chart.extjfx.AntTracksDataReducer
import at.jku.anttracks.gui.chart.extjfx.chartpanes.components.ChartControlsPane
import at.jku.anttracks.gui.chart.extjfx.plugins.LinkableXYChartPlugin
import at.jku.anttracks.gui.chart.extjfx.plugins.XPanner
import at.jku.anttracks.gui.chart.extjfx.plugins.XScrollZoomer
import at.jku.anttracks.gui.chart.extjfx.plugins.XSelector
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.utils.*
import at.jku.anttracks.heap.StatisticGCInfo
import at.jku.anttracks.heap.statistics.Statistics
import at.jku.anttracks.parser.EventType
import cern.extjfx.chart.NumericAxis
import cern.extjfx.chart.XYChartPane
import cern.extjfx.chart.data.AntTracksDataReducingObservableList
import cern.extjfx.chart.data.ListData
import cern.extjfx.chart.plugins.*
import javafx.application.Platform
import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.ReadOnlyObjectProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.collections.FXCollections
import javafx.scene.Node
import javafx.scene.chart.ValueAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.ComboBox
import javafx.scene.layout.BorderPane
import javafx.scene.paint.Color
import javafx.util.StringConverter
import java.nio.file.Files
import java.nio.file.StandardOpenOption

abstract class ReducedXYChartPane<Data> : BorderPane() {

    init {
        FXMLUtil.load(this, ReducedXYChartPane::class.java)
    }

    protected lateinit var extjfxChartPane: XYChartPane<Number, Number>
    abstract val chart: XYChart<Number, Number>
    protected open val dataReducer = AntTracksDataReducer(2)
    open val xSelector: XSelector<Number>? = XSelector<Number>({ x ->
                                                                   val gcInfo = (lastPlottedData as AppInfo).getStatistics(x.toLong())!!.first().info
                                                                   "GC #${gcInfo.id} ${if (gcInfo.meta == EventType.GC_START) "START" else "END"} @ ${toNiceNumberString(
                                                                           gcInfo.time)}ms"
                                                               })
    protected abstract val xUnit: Unit
    protected abstract val yUnits: List<Unit>
    protected open val seriesColors = listOf<Color>()
    protected open val chartOptionsNodes = listOf<Node>()
    private val yUnitComboBox: ComboBox<Unit>
        get() = chartControlsPane.chartOptionsPopup.yUnitComboBox
    val selectedYUnitProperty: ReadOnlyObjectProperty<Unit>
        get() = yUnitComboBox.selectionModel.selectedItemProperty()
    val synchronizedProperty: BooleanProperty
        get() = chartControlsPane.chartOptionsPopup.synchronizedCheckBox.selectedProperty()
    protected var lastPlottedData: Data? = null
    private var rangeOfLastPlottedData: ClosedRange<Double>? = null
    val chartControlsPane = ChartControlsPane()
    private val automaticallyAdjustZoomToDataset = SimpleBooleanProperty(true)
    val xZoomer = XScrollZoomer<Number>(automaticallyAdjustZoomToDataset) { rangeOfLastPlottedData ?: 0.0..0.0 }

    open fun init(initialYUnit: Unit = yUnits.first()) {
        extjfxChartPane = XYChartPane(chart)
        chart.animated = false
        chart.xAxis.animated = false
        chart.yAxis.animated = false
        chart.xAxis.label = xUnit.labelText
        (chart.xAxis as NumericAxis).tickLabelFormatter = xUnit.createTickLabelFormatter(chart.xAxis as NumericAxis)
        chart.xAxis.isAutoRanging = false

        yUnitComboBox.selectionModel.selectedItemProperty().addListener { _, _, newSelection ->
            // update axis labels and replot when selecting a different y axis unit
            chart.yAxis.label = newSelection.labelText
            (chart.yAxis as NumericAxis).tickLabelFormatter = newSelection.createTickLabelFormatter(chart.yAxis as NumericAxis)
            if (lastPlottedData != null) {
                plot(lastPlottedData!!)
            }
        }
        yUnitComboBox.selectionModel.select(initialYUnit)

        // by default charts can only be zoomed and panned, selection only when selector provided by subclass
        chartControlsPane.init(xZoomer, yUnits, chartOptionsNodes, automaticallyAdjustZoomToDataset)
        extjfxChartPane.plugins.addAll(xZoomer,
                                       XPanner<Number>(automaticallyAdjustZoomToDataset) { rangeOfLastPlottedData ?: 0.0..0.0 },
                                       ChartOverlay<Number, Number>(ChartOverlay.OverlayArea.PLOT_AREA, chartControlsPane))
        if (xSelector != null) {
            extjfxChartPane.plugins.add(xSelector)
        }

        // chart is wrapped in extjfxpane (necessary for plugins), which is wrapped in this class
        center = extjfxChartPane
        extjfxChartPane

        // define an additional css file that colorizes chart series as defined by the subclass
        val tempCssFile = Files.createTempFile(null, ".css")
        seriesColors.forEachIndexed { index, color ->
            // how to change chart colors https://docs.oracle.com/javase/8/javafx/user-interface-tutorial/css-styles.htm#CIHGIAGE
            val css = """.default-color$index.chart-series-area-line { -fx-stroke: ${color.toRGBAString()}}
                    .default-color$index.chart-series-area-fill { -fx-fill: ${color.makeTransparent(0.6).toRGBAString()}}
                    .default-color$index.chart-area-symbol { -fx-background-color: ${color.toRGBAString()}}
                    .default-color$index.chart-series-line { -fx-stroke: ${color.toRGBAString()}}
                    .default-color$index.chart-line-symbol { -fx-background-color: ${color.toRGBAString()}}""".trimIndent()
            Files.write(tempCssFile, css.toByteArray(), StandardOpenOption.APPEND)
        }
        stylesheets.add(tempCssFile.toUri().toString())
    }

    fun plot(data: Data) {
        val dataset = createDataset(data)
        lastPlottedData = data
        rangeOfLastPlottedData = dataset.map { it.data.firstOrNull()?.xValue?.toDouble() ?: 0.0 }.min()?.rangeTo(
                dataset.map { it.data.lastOrNull()?.xValue?.toDouble() ?: 0.0 }.max() ?: 0.0) ?: 0.0..0.0
        val reducedDataset = reduceDataset(dataset)
        Platform.runLater {
            chart.data.clear()
            chart.data.addAll(reducedDataset)

            if (automaticallyAdjustZoomToDataset.value) {
                xZoomer.zoomOrigin()
            }
        }
    }

    private fun reduceDataset(dataset: List<XYChart.Series<Number, Number>>): List<XYChart.Series<Number, Number>> {
        var reductionCounter = 0

        return dataset.map {
            val reducedSeriesData = AntTracksDataReducingObservableList<Number, Number>(chart.xAxis as ValueAxis<Number>).apply {
                maxPointsCountProperty().bind(Bindings.max(2, widthProperty().divide(4)))   // max 1 data point on 4 pixels
                dataReducer = this@ReducedXYChartPane.dataReducer
                data = ListData(it.data)
                setReductionListener {
                    if (++reductionCounter == dataset.size) {
                        // all of the series in this dataset have been reduced
                        doAfterDatasetReduction()
                        reductionCounter = 0
                    }
                }
            }
            XYChart.Series(it.name, reducedSeriesData)
        }
    }

    protected abstract fun createDataset(data: Data): List<XYChart.Series<Number, Number>>

    protected open fun doAfterDatasetReduction() {}

    // TODO: Improve performance, becomes really laggy on "userstudy_2"
    protected fun createGCRangeIndicatorPlugins(appInfo: AppInfo): List<AntTracksRangeIndicator<Number>> {
        val rangeIndicatorPlugins = mutableListOf<AntTracksRangeIndicator<Number>>()
        fun addOrExtendPreviousRangeIndicator(rangeIndicatorToAdd: AntTracksRangeIndicator<Number>) {
            if (rangeIndicatorToAdd.pixelWidth(chart.xAxis as ValueAxis<Number>) >= 0.5) {
                if (rangeIndicatorToAdd is ReducedRangeIndicator && rangeIndicatorPlugins.lastOrNull() is ReducedRangeIndicator) {
                    // extend the last ReducedRangeIndicator
                    val previousReducedRangeIndicator = rangeIndicatorPlugins.removeAt(rangeIndicatorPlugins.size - 1) as ReducedRangeIndicator
                    rangeIndicatorPlugins.add(ReducedRangeIndicator(previousReducedRangeIndicator.lowerBound,
                                                                    rangeIndicatorToAdd.upperBound,
                                                                    previousReducedRangeIndicator.hiddenGCs + rangeIndicatorToAdd.hiddenGCs))
                } else {
                    rangeIndicatorPlugins.add(rangeIndicatorToAdd)
                }
            }
        }

        // go over currently displayed data points and create range indicators that highlight garbage collections or mutator phases
        val plottedGCInfos = chart.data.flatMap { it.data }
                .distinctBy { it.xValue }
                .sortedBy { it.xValue.toDouble() }  // because after flatMap the data is no longer sorted!
                .flatMap { appInfo.getStatistics(it.xValue.toLong()) }
                .map { it.info }
        if (plottedGCInfos.size < 2) {
            return listOf()
        }
        var previousGC: StatisticGCInfo = plottedGCInfos.first()
        plottedGCInfos.drop(1).forEach { currentGC ->
            if (currentGC.meta == EventType.GC_START) {
                if (previousGC.meta == EventType.GC_START) {
                    // two subsequent GC starts => data points missing due to reduction!
                    addOrExtendPreviousRangeIndicator(ReducedRangeIndicator(previousGC.time.toDouble(),
                                                                            currentGC.time.toDouble(),
                                                                            currentGC.id - previousGC.id))
                } else if (previousGC.meta == EventType.GC_END) {
                    // a GC start followed by a GC end
                    if (previousGC.id.toInt() == currentGC.id - 1) {
                        // the events belong to two subsequent GCs
                        addOrExtendPreviousRangeIndicator(MutatorRangeIndicator(previousGC.time.toDouble(), currentGC.time.toDouble()))
                    } else {
                        // there are hidden mutators/gcs happening between these two events => data points missing due to reduction!
                        addOrExtendPreviousRangeIndicator(ReducedRangeIndicator(previousGC.time.toDouble(),
                                                                                currentGC.time.toDouble(),
                                                                                currentGC.id - previousGC.id + 1))
                    }
                }
            } else if (currentGC.meta == EventType.GC_END) {
                if (previousGC.meta == EventType.GC_END) {
                    // two subsequent GC ends => data points missing due to reduction!
                    addOrExtendPreviousRangeIndicator(ReducedRangeIndicator(previousGC.time.toDouble(),
                                                                            currentGC.time.toDouble(),
                                                                            currentGC.id - previousGC.id))
                } else if (previousGC.meta == EventType.GC_START) {
                    // a GC end followed by a GC start
                    if (previousGC.id == currentGC.id) {
                        addOrExtendPreviousRangeIndicator(GCRangeIndicator(previousGC.time.toDouble(),
                                                                           currentGC.time.toDouble(),
                                                                           currentGC.id,
                                                                           currentGC.type.isFull,
                                                                           currentGC.cause))
                    } else {
                        // there are hidden mutators/gcs happening between these two events => data points missing due to reduction!
                        addOrExtendPreviousRangeIndicator(ReducedRangeIndicator(previousGC.time.toDouble(),
                                                                                currentGC.time.toDouble(),
                                                                                currentGC.id - previousGC.id))
                    }
                }
            }
            previousGC = currentGC
        }

        return rangeIndicatorPlugins
    }

    protected fun clearRangeIndicatorPlugins() {
        // must remove all plugins at once, otherwise the (observable) plugin list reports too many changes
        val rangeIndicatorPlugins = extjfxChartPane.plugins.mapNotNull { it as? AntTracksRangeIndicator }
        extjfxChartPane.plugins.removeAll(rangeIndicatorPlugins)
    }

    companion object {
        enum class Unit(val labelText: String, val stringConverter: (Number, NumericAxis) -> String) {
            TIME("Time", { num, axis -> toShortTimeAxisLabelString(num.toLong(), axis) }),
            OBJECTS("Objects", { num, _ -> toShortNumberString(num.toLong()) }),
            BYTES("Bytes", { num, _ -> toShortMemoryUsageString(num.toLong()) }),
            OBJECTS_RELATIVE("Objects (relative)", { num, _ -> toPercentageString(num.toDouble()) }),
            BYTES_RELATIVE("Bytes (relative)", { num, _ -> toPercentageString(num.toDouble()) }),
            REACHABLE_BYTES("Reachable bytes", { num, _ -> toShortMemoryUsageString(num.toLong()) }),
            GC_TIME_RELATIVE("Time spent in GC (relative)", { num, _ -> toPercentageString(num.toDouble()) });

            fun createTickLabelFormatter(axis: NumericAxis) = object : StringConverter<Number>() {
                override fun toString(n: Number): String {
                    return stringConverter(n, axis)
                }

                override fun fromString(s: String): Number = s.toLong()
            }
        }

        // SYNCHRONIZATION
        fun synchronize(chartPanes: List<ReducedXYChartPane<out Any>>, vararg options: SynchronizationOption) {
            // update synchronization UI
            chartPanes.forEach { it.synchronizedProperty.set(true) }
            if (chartPanes.size == 1 || options.none { it.allowsDesynchronization }) {
                // user has no control over synchronization with this setup
                chartPanes.forEach { it.chartControlsPane.hideSynchronizationControl() }
            } else {
                chartPanes.forEach { it.chartControlsPane.showSynchronizationControl() }
            }

            chartPanes.forEach { chartPane ->
                // apply the given synchronization options
                options.forEach { option ->
                    option.apply(chartPanes)
                }

                // handle selection and deselection of synchronized checkbox
                chartPane.synchronizedProperty.addListener { _, _, _ ->
                    // desynchronize all charts as far as allowed (only for those options that allow user desynchronization)
                    options.filter { it.allowsDesynchronization }.forEach { it.revert(chartPanes) }
                    // resynchronize those charts that want synchronization
                    options.filter { it.allowsDesynchronization }.forEach { option -> option.apply(chartPanes.filter { it.synchronizedProperty.get() }) }
                }
            }
        }

        data class SynchronizationOption(val action: Action, val allowsDesynchronization: Boolean) {
            enum class Action {
                SELECTION,
                ZOOM,
                PAN
            }

            fun apply(chartPanes: List<ReducedXYChartPane<out Any>>) {
                when (action) {
                    Action.SELECTION -> LinkableXYChartPlugin.link(getLinkablePlugins<XSelector<Number>>(chartPanes))
                    Action.ZOOM -> LinkableXYChartPlugin.link(getLinkablePlugins<XScrollZoomer<Number>>(chartPanes))
                    Action.PAN -> LinkableXYChartPlugin.link(getLinkablePlugins<XPanner<Number>>(chartPanes))
                }
            }

            fun revert(chartPanes: List<ReducedXYChartPane<out Any>>) {
                when (action) {
                    Action.SELECTION -> getLinkablePlugins<XSelector<Number>>(chartPanes).forEach { it.clearLinks() }
                    Action.ZOOM -> getLinkablePlugins<XScrollZoomer<Number>>(chartPanes).forEach { it.clearLinks() }
                    Action.PAN -> getLinkablePlugins<XPanner<Number>>(chartPanes).forEach { it.clearLinks() }
                }
            }
        }

        private inline fun <reified PluginType : LinkableXYChartPlugin<Number, Number>> getLinkablePlugins(chartPanes: List<ReducedXYChartPane<out Any>>) =
                chartPanes.flatMap { it.extjfxChartPane.plugins }.mapNotNull { it as? PluginType }

    }
}

// allow infix creation of synchronization options
infix fun ReducedXYChartPane.Companion.SynchronizationOption.Action.desynchable(that: Boolean) = ReducedXYChartPane.Companion.SynchronizationOption(this, that)

// fix garbage collection with duration 0
// TODO do this kind of preprocessing after parsing statistics
fun List<Statistics>.buildSeries(seriesName: String, yValueExtractor: ((Statistics) -> Number)): XYChart.Series<Number, Number> {
    val data = mutableListOf<XYChart.Data<Number, Number>>()
    var lastTime = -1L

    forEach {
        var time = it.info.time.toDouble()
        if (it.info.time == lastTime) {
            // avoid data points with identical x value
            time = data.last().xValue.toDouble() + 0.1
        }
        data.add(XYChart.Data(time, yValueExtractor(it)))
        lastTime = it.info.time
    }

    return XYChart.Series<Number, Number>(seriesName, FXCollections.observableArrayList(data))
}