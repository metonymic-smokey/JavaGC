package at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.memory

import at.jku.anttracks.gui.chart.base.ApplicationChartFactory
import at.jku.anttracks.gui.chart.base.AxisScaler
import at.jku.anttracks.gui.chart.base.ChartSynchronizer
import at.jku.anttracks.gui.chart.base.JFreeChartAxisScaler
import at.jku.anttracks.gui.chart.jfreechart.base.JFreeChartFactory
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.base.StackedAreaJFreeChartPane
import at.jku.anttracks.gui.chart.jfreechart.xy.util.AddALotXYSeries
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.util.safe
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.CheckBox
import javafx.scene.control.Dialog
import javafx.scene.input.Clipboard
import javafx.scene.input.ClipboardContent
import javafx.scene.layout.VBox
import javafx.stage.StageStyle
import org.jfree.chart.labels.StandardXYToolTipGenerator
import org.jfree.chart.labels.XYItemLabelGenerator
import org.jfree.data.xy.DefaultTableXYDataset
import org.jfree.data.xy.XYDataItem
import java.util.*
import java.util.stream.Collectors

class MemoryStackedAreaJFreeChartPane : StackedAreaJFreeChartPane<AppInfo, DefaultTableXYDataset>() {
    override val onConfigureAction: EventHandler<ActionEvent>
        get() = EventHandler { actionEvent ->
            val dialog = Dialog<Void>()
            dialog.initStyle(StageStyle.UTILITY)
            dialog.title = "Configure chart"

            val container = VBox(10.0)
            val showTagsCheckBox = CheckBox("Show trace tags")
            val showOnlyReachableMemoryCheckBox = CheckBox("Show only reachable memory")
            showTagsCheckBox.isSelected = showTags
            showOnlyReachableMemoryCheckBox.isSelected = showOnlyReachableMemory
            container.children.add(showTagsCheckBox)
            if (memoryUnit === ApplicationChartFactory.MemoryConsumptionUnit.BYTES) {
                container.children.add(showOnlyReachableMemoryCheckBox)
            }
            container.children.add(Button("Copy R data to clipboard").apply {
                onAction = EventHandler {
                    val result = buildString {
                        for (seriesId in 0 until (dataset?.seriesCount ?: 0)) {
                            val series = dataset!!.getSeries(seriesId)
                            append(series.key)
                            append("_x=c(")
                            for (itemId in 0 until series.itemCount) {
                                val data = series.getDataItem(itemId)
                                append(data.xValue)
                                append(",")
                            }
                            dropLast(1)
                            append(")\n")
                            append(series.key)
                            append("_y=c(")
                            for (itemId in 0 until series.itemCount) {
                                val data = series.getDataItem(itemId)
                                append(data.yValue)
                                append(",")
                            }
                            dropLast(1)
                            append(")\n")
                        }
                    }
                    val clipboard = Clipboard.getSystemClipboard()
                    val content = ClipboardContent()
                    content.putString(result)
                    clipboard.setContent(content)
                }
            })
            dialog.dialogPane.children.add(container)
            dialog.dialogPane.content = container

            dialog.dialogPane.buttonTypes.addAll(ButtonType.APPLY, ButtonType.CANCEL)
            dialog.setResultConverter { clickedButton ->
                if (clickedButton == ButtonType.APPLY) {
                    val showOnlyReachableMemoryChanged = showOnlyReachableMemory != showOnlyReachableMemoryCheckBox.isSelected
                    showTags = showTagsCheckBox.isSelected
                    showOnlyReachableMemory = showOnlyReachableMemoryCheckBox.isSelected
                    if (showOnlyReachableMemoryChanged) {
                        update(ClientInfo.getCurrentAppInfo())
                    } else {
                        chartViewer.chart.fireChartChanged()
                    }
                }
                null
            }

            dialog.showAndWait()
        }

    override val scaler: AxisScaler<DefaultTableXYDataset>
        get() = when (memoryUnit) {
            ApplicationChartFactory.MemoryConsumptionUnit.OBJECTS -> JFreeChartAxisScaler.ObjectsScaler(this)
            ApplicationChartFactory.MemoryConsumptionUnit.BYTES -> JFreeChartAxisScaler.BytesScaler(this)
        }

    lateinit var memoryUnit: ApplicationChartFactory.MemoryConsumptionUnit
    var onlyShowGCEndSimpleMemory = false
    private var showTags = true
    private var showOnlyReachableMemory = false
    private var tags: List<at.jku.anttracks.heap.Tag>? = null

    init {
        FXMLUtil.load(this, MemoryStackedAreaJFreeChartPane::class.java)
    }

    fun init(chartSynchronizer: ChartSynchronizer, memoryUnit: ApplicationChartFactory.MemoryConsumptionUnit) {
        this.memoryUnit = memoryUnit
        super.init(chartSynchronizer, true)
    }

    public override fun createDataSet(appInfo: AppInfo): DefaultTableXYDataset {
        tags = appInfo.tags

        val dataset = DefaultTableXYDataset()

        if (showOnlyReachableMemory) {
            val reachableMemorySeries = AddALotXYSeries("Reachable memory", false)
            val reachableMemoryData = ArrayList<XYDataItem>()

            appInfo.statistics.stream()
                    .filter { statistics -> statistics.info.reachableBytes != null }
                    .forEach { statistics -> reachableMemoryData.add(XYDataItem(statistics.info.time.toDouble(), statistics.info.reachableBytes!!.toDouble())) }

            reachableMemorySeries.setData(reachableMemoryData, false)
            dataset.addSeries(reachableMemorySeries)

        } else {
            val memorySeries = AddALotXYSeries("Occupied memory", false)
            val memoryData = ArrayList<XYDataItem>()

            val edenSeries = AddALotXYSeries("Eden", false)
            val edenData = ArrayList<XYDataItem>()
            val survivorSeries = AddALotXYSeries("Survivor", false)
            val survivorData = ArrayList<XYDataItem>()
            val oldSeries = AddALotXYSeries("Old", false)
            val oldData = ArrayList<XYDataItem>()

            for (i in 0 until appInfo.statistics.size) {
                val stat = appInfo.statistics[i]
                val gcEvent = stat.info.meta
                val time = stat.info.time.toDouble()

                // Prevent duplicate x values
                if (edenData.isEmpty() || time != edenData.last().xValue) {
                    when (memoryUnit) {
                        ApplicationChartFactory.MemoryConsumptionUnit.BYTES -> {
                            edenData.add(XYDataItem(time, stat.eden.memoryConsumption.bytes.toDouble()))
                            survivorData.add(XYDataItem(time, stat.survivor.memoryConsumption.bytes.toDouble()))
                            oldData.add(XYDataItem(time, stat.old.memoryConsumption.bytes.toDouble()))
                        }
                        ApplicationChartFactory.MemoryConsumptionUnit.OBJECTS -> {
                            edenData.add(XYDataItem(time, stat.eden.memoryConsumption.objects.toDouble()))
                            survivorData.add(XYDataItem(time, stat.survivor.memoryConsumption.objects.toDouble()))
                            oldData.add(XYDataItem(time, stat.old.memoryConsumption.objects.toDouble()))
                        }
                    }.safe
                }
                if (memoryData.isEmpty() || time != memoryData.last().xValue)
                    if (onlyShowGCEndSimpleMemory && gcEvent == at.jku.anttracks.parser.EventType.GC_END) {
                        when (memoryUnit) {
                            ApplicationChartFactory.MemoryConsumptionUnit.BYTES -> {
                                memoryData.add(XYDataItem(time,
                                                          stat.eden.memoryConsumption.bytes.toDouble() +
                                                                  stat.survivor.memoryConsumption.bytes.toDouble() +
                                                                  stat.old.memoryConsumption.bytes.toDouble()))
                            }
                            ApplicationChartFactory.MemoryConsumptionUnit.OBJECTS -> {
                                memoryData.add(XYDataItem(time,
                                                          stat.eden.memoryConsumption.objects.toDouble() +
                                                                  stat.survivor.memoryConsumption.objects.toDouble() +
                                                                  stat.old.memoryConsumption.objects.toDouble()))
                            }
                        }.safe
                    }
            }

            if (onlyShowGCEndSimpleMemory) {
                memorySeries.setData(memoryData, false)

                dataset.addSeries(memorySeries)
            } else {
                edenSeries.setData(edenData, false)
                survivorSeries.setData(survivorData, false)
                oldSeries.setData(oldData, false)

                dataset.addSeries(oldSeries)
                dataset.addSeries(survivorSeries)
                dataset.addSeries(edenSeries)
            }
        }

        return dataset
    }

    override fun initializeChart() {
        val chart =
                JFreeChartFactory.createStackedXYAreaChart(this,
                                                           memoryUnit.label,
                                                           null,
                                                           "x",
                                                           "y",
                                                           DefaultTableXYDataset(),
                                                           XYItemLabelGenerator { dataset, series, item ->
                                                               if (showTags) {
                                                                   try {
                                                                       val x = dataset.getX(series, item).toLong()
                                                                       return@XYItemLabelGenerator tags!!.stream()
                                                                               .filter({ tag -> tag.gcInfo.time == x })
                                                                               .map({ tag -> tag.text })
                                                                               .collect(Collectors.joining("; "))
                                                                   } catch (ex: Exception) {
                                                                       return@XYItemLabelGenerator ""
                                                                   }

                                                               } else {
                                                                   return@XYItemLabelGenerator ""
                                                               }
                                                           },
                                                           StandardXYToolTipGenerator(), null,
                                                           chartSynchronizer)


        chartViewer.setChart(chart)
    }
}
