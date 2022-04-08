package at.jku.anttracks.gui.utils

import at.jku.anttracks.gui.frame.main.tab.application.ApplicationTab
import at.jku.anttracks.gui.frame.main.tab.application.task.ApplicationTabPlottingTask
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.DetailedHeap.INITIAL_PARSER_GC_INFO
import at.jku.anttracks.heap.HeapAdapter
import at.jku.anttracks.heap.StatisticGCInfo
import at.jku.anttracks.heap.io.MetaDataWriterConfig
import at.jku.anttracks.heap.io.MetaDataWriterListener
import at.jku.anttracks.heap.io.StatisticsListener
import at.jku.anttracks.heap.statistics.SpaceStatistics
import at.jku.anttracks.heap.statistics.Statistics
import at.jku.anttracks.parser.EventType
import at.jku.anttracks.parser.ParserGCInfo
import at.jku.anttracks.parser.ParsingInfo
import at.jku.anttracks.parser.heap.HeapTraceParser
import at.jku.anttracks.util.ThreadUtil
import javafx.application.Platform
import javafx.scene.control.Alert
import javafx.scene.control.ButtonType
import javafx.scene.layout.Region
import java.io.File
import java.util.concurrent.FutureTask
import java.util.logging.Level

class MetaParserTask(private val appInfo: AppInfo, private val tab: ApplicationTab, private val metaHeaderFilePath: String) : AntTask<Void?>() {
    private var currentChartUpdatingTask: ApplicationTabPlottingTask? = null
    private var lastPlotTime: Long = 0

    init {
        tab.tasks.add(this)
    }

    @Throws(Exception::class)
    override fun backgroundWork(): Void? {
        updateTitle("Construct meta-data for " + appInfo.appName + " (File: " + appInfo.selectedTraceFile + ")")
        updateMessage("Parsing trace")

        var parser: HeapTraceParser? = null
        val metaWriterListener = object : MetaDataWriterListener(MetaDataWriterConfig(appInfo.metaDataPath), { _, _, _, _ -> appInfo.statistics.last() }) {
            override fun close(sender: Any, parsingInfo: ParsingInfo) {
                if (appInfo.statistics.last().info.meta == EventType.GC_END) {
                    val lastStatistics = Statistics.collect(parser!!.workspace,
                                                            StatisticGCInfo(
                                                                    INITIAL_PARSER_GC_INFO.type,
                                                                    INITIAL_PARSER_GC_INFO.cause,
                                                                    false,
                                                                    false,
                                                                    EventType.GC_START,
                                                                    (appInfo.statistics.last().info.id + 1).toShort(),
                                                                    appInfo.traceEnd + (appInfo.traceLengthInMilliseconds / appInfo.statistics.size)),
                                                            false,
                                                            appInfo.parsingInfo
                    )
                    appInfo.statistics.add(appInfo.statistics.size, lastStatistics)
                    writeIndex(parsingInfo.toByte, lastStatistics.info, false)
                    writeStatistics(lastStatistics)
                }
                super.close(sender, parsingInfo)
            }
        }

        appInfo.statistics.clear()

        // statistics for t=0ms
        val statisticAtZero = Statistics(
                StatisticGCInfo(
                        INITIAL_PARSER_GC_INFO.type,
                        INITIAL_PARSER_GC_INFO.cause,
                        false,
                        false,
                        INITIAL_PARSER_GC_INFO.eventType,
                        INITIAL_PARSER_GC_INFO.id,
                        INITIAL_PARSER_GC_INFO.time),
                SpaceStatistics(appInfo.symbols),
                SpaceStatistics(appInfo.symbols),
                SpaceStatistics(appInfo.symbols)
        )
        appInfo.statistics.add(statisticAtZero)
        metaWriterListener.writeIndex(0, statisticAtZero.info, false)
        metaWriterListener.writeStatistics(statisticAtZero)

        LOGGER.log(Level.INFO, "parsing")
        try {
            parser = object : HeapTraceParser(appInfo.symbols) {
                override fun doWorkspaceCompletion(workspace: DetailedHeap) {
                    // Do not call super since we do not want to complete the heap (e.g., from-pointer setting), since this is not needed for meta-parsing
                }
            }
            // This listener gets executed first
            val tagListener = object : HeapAdapter() {
                override fun phaseChanged(
                        sender: Any,
                        from: ParserGCInfo,
                        to: ParserGCInfo,
                        failed: Boolean,
                        position: Long,
                        parsingInfo: ParsingInfo,
                        inParserTimeWindow: Boolean) {
                    appInfo.tags = parser.workspace.tags
                    if (appInfo.parsingInfo == null) {
                        appInfo.parsingInfo = parsingInfo
                    }
                }
            }
            parser.addHeapListener(tagListener)
            // this listener gets executed second
            val statisticsListener = StatisticsListener(appInfo.statistics)
            parser.addHeapListener(statisticsListener)
            // this listener gets executed third
            parser.addHeapListener(metaWriterListener)
            // this listener gets executed fourth
            val progressWriterAndChartUpdateListener = object : HeapAdapter() {
                var lastPercentInt = 0

                override fun phaseChanging(
                        sender: Any,
                        from: ParserGCInfo,
                        to: ParserGCInfo,
                        failed: Boolean,
                        position: Long,
                        parsingInfo: ParsingInfo,
                        inParserTimeWindow: Boolean) {
                    updateProgress(position - parsingInfo.fromByte, parsingInfo.traceLength)

                    val percent = (position.toFloat() - parsingInfo.fromByte) / parsingInfo.traceLength * 100.0
                    if (percent.toInt() > lastPercentInt) {
                        lastPercentInt = percent.toInt()
                        LOGGER.log(Level.INFO,
                                   "($lastPercentInt)\n" +
                                           "AllocTime: ${parser.allocTime.get()}\n" +
                                           "MoveTime: ${parser.moveTime.get()}\n" +
                                           "GCStartProcessTime: ${parser.gcStartCompleteProcessingTime.get()}\n" +
                                           "GCEndProcessTime: ${parser.gcEndCompleteProcessingTime.get()}")
                    }
                }

                override fun phaseChanged(
                        sender: Any,
                        from: ParserGCInfo,
                        to: ParserGCInfo,
                        failed: Boolean,
                        position: Long,
                        parsingInfo: ParsingInfo,
                        inParserTimeWindow: Boolean) {
                    if (currentChartUpdatingTask == null && System.currentTimeMillis() > lastPlotTime + PLOT_THRESHOLD) {
                        currentChartUpdatingTask = ApplicationTabPlottingTask(appInfo, tab)
                        currentChartUpdatingTask!!.setOnSucceeded {
                            currentChartUpdatingTask = null
                            lastPlotTime = System.currentTimeMillis()
                        }
                        ThreadUtil.startTask(currentChartUpdatingTask)
                    }
                }
            }
            parser.addHeapListener(progressWriterAndChartUpdateListener)
            parser.parse(cancelProperty)
        } catch (e: InterruptedException) {
            // cancelled parsing stopped
            // invalidate metadata
            val headerFile = File(metaHeaderFilePath)
            assert(headerFile.exists())
            headerFile.delete()

        } catch (e: Exception) {
            // Delete header file if parsing did not complete
            val headerFile = File(metaHeaderFilePath)
            assert(headerFile.exists())
            headerFile.delete()

            val alertTask = FutureTask {
                val alert = Alert(Alert.AlertType.ERROR, "An internal error occured while parsing, do you want to retry?", ButtonType.YES, ButtonType.NO)
                alert.title = "Error"
                alert.dialogPane.minHeight = Region.USE_PREF_SIZE
                WindowUtil.centerInMainFrame(alert)
                val retryChoice = alert.showAndWait()
                retryChoice.isPresent && retryChoice.get() == ButtonType.YES
            }
            Platform.runLater(alertTask)
            return if (alertTask.get()) {
                backgroundWork()
            } else {
                throw Exception(e)
            }
        }

        return null
    }

    override fun finished() {}

    companion object {
        private val PLOT_THRESHOLD = 1000
    }
}
