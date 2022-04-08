package at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.cpuutilization

import at.jku.anttracks.gui.chart.base.AxisScaler
import at.jku.anttracks.gui.chart.base.ChartSynchronizer
import at.jku.anttracks.gui.chart.base.NoAxisScaler
import at.jku.anttracks.gui.chart.jfreechart.base.JFreeChartFactory
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.base.StackedAreaJFreeChartPane
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.cpuutilization.dialog.GCActivityStackedAreaJFreeChartDialog
import at.jku.anttracks.gui.chart.jfreechart.xy.util.AddALotXYSeries
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.WindowUtil
import at.jku.anttracks.util.safe
import javafx.event.ActionEvent
import javafx.event.EventHandler
import org.jfree.data.xy.DefaultTableXYDataset
import org.jfree.data.xy.TableXYDataset
import org.jfree.data.xy.XYDataItem
import java.awt.Color
import java.util.*

class GCActivityStackedAreaJFreeChartPane : StackedAreaJFreeChartPane<AppInfo, TableXYDataset>() {
    enum class Mode {
        GC,
    }

    private lateinit var mode: Mode
    var plateauWidth = 1

    private var latestData: AppInfo? = null

    override val topMarginPercent: Float
        get() = 0.0f

    override val onConfigureAction: EventHandler<ActionEvent>?
        get() = EventHandler {
            GCActivityStackedAreaJFreeChartDialog().also {
                it.init(this)
                WindowUtil.centerInMainFrame(it)
                it.showAndWait()
            }
            update(latestData)
        }

    override val scaler: AxisScaler<TableXYDataset>
        get() = NoAxisScaler()

    init {
        FXMLUtil.load(this, GCActivityStackedAreaJFreeChartPane::class.java)
    }

    fun init(chartSynchronizer: ChartSynchronizer, mode: Mode) {
        super.init(chartSynchronizer, true)
        this.mode = mode

    }

    public override fun createDataSet(data: AppInfo): TableXYDataset {
        latestData = data

        // val mutatorSeries = AddALotXYSeries("Application (\"Mutator\")", false)
        // val mutatorData = ArrayList<XYDataItem>()
        val gcSeries = AddALotXYSeries("GC Overhead", false)
        val gcData = ArrayList<XYDataItem>()

        val epsilon = 0.0001

        var lastAddedX = 0.0
        var lastEventTime = 0.0
        var mutatorSum = 0.0
        var gcSum = 0.0
        var countedGCEnd = 0

        when (mode) {
            Mode.GC -> for (stat in data.statistics) {
                val gcEvent = stat.info.meta
                val time = stat.info.time.toDouble()

                if (time != 0.0) { // Ignore the GC event at time 0
                    if (gcEvent == at.jku.anttracks.parser.EventType.GC_START) {
                        // GC Start
                        mutatorSum += time - lastEventTime
                        lastEventTime = time

                    } else {
                        // GC End
                        countedGCEnd++
                        gcSum += time - lastEventTime

                        if (countedGCEnd == plateauWidth || stat == data.statistics.last()) {
                            val mutatorPercent = 100.0 * mutatorSum / (mutatorSum + gcSum)
                            // val mutatorStartOfWindow = XYDataItem(lastAddedX + epsilon, mutatorPercent)
                            // val mutatorEndOfWindow = XYDataItem(time, mutatorPercent)
                            // mutatorData.add(mutatorStartOfWindow)
                            // mutatorData.add(mutatorEndOfWindow)

                            val gcPercent = 100.0 - mutatorPercent
                            val gcStartOfWindow = XYDataItem(lastAddedX + epsilon, gcPercent)
                            val gcEndOfWindow = XYDataItem(time, gcPercent)
                            gcData.add(gcStartOfWindow)
                            gcData.add(gcEndOfWindow)

                            lastAddedX = time
                            mutatorSum = 0.0
                            gcSum = 0.0
                            countedGCEnd = 0
                        }

                        lastEventTime = time
                    }
                }
            }
        }.safe

        // mutatorSeries.setData(mutatorData, false)
        gcSeries.setData(gcData, false)

        val dataset = DefaultTableXYDataset()
        dataset.addSeries(gcSeries)
        // dataset.addSeries(mutatorSeries)
        return dataset
    }

    override fun initializeChart() {
        val chart =
                JFreeChartFactory.createStackedXYAreaChart(this,
                                                           "GC Overhead",
                                                           null,
                                                           "Time [ms]",
                                                           "Time spent in GC [%]",
                                                           DefaultTableXYDataset(),
                                                           null,
                                                           null,
                                                           null,
                                                           chartSynchronizer)


        chartViewer.setChart(chart)
    }

    override val seriesColors: Array<Color>
        get() = arrayOf(
                Color(0xf0, 0x3b, 0x20),
                Color(0xff, 0xee, 0xa0)
        )
}
