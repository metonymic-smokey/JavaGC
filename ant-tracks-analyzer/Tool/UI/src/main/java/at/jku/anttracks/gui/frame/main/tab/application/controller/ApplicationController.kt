package at.jku.anttracks.gui.frame.main.tab.application.controller

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.component.actiontab.tab.ActionTab
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.application.tab.detail.ApplicationDetailTab
import at.jku.anttracks.gui.frame.main.tab.application.tab.overview.ApplicationOverviewTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.HeapEvolutionTab
import at.jku.anttracks.gui.frame.main.tab.heapstate.task.HeapStateTask
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.AntTask
import at.jku.anttracks.gui.utils.DetectedTimeWindows
import at.jku.anttracks.gui.utils.toShortMemoryUsageString
import at.jku.anttracks.util.ThreadUtil.startTask
import at.jku.anttracks.util.contains
import at.jku.anttracks.util.width
import javafx.scene.chart.ValueAxis
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object ApplicationController {
    fun heapEvolutionAnalysis(parent: ActionTab,
                              appInfo: AppInfo,
                              timeWindow: ClosedRange<Long>,
                              permBornDiedTempSelected: Boolean = false,
                              dataStructureDevelopmentSelected: Boolean = false,
                              objectGroupTrendSelected: Boolean = false,
                              shortLivedObjectsSelected: Boolean = false,
                              startHeapEvolutionAnalysisAutomatically: Boolean = false) {
        val fromTime = timeWindow.start
        val toTime = timeWindow.endInclusive

        val startStatistic = appInfo.statistics.find { stat -> stat.info.time == fromTime }!!
        val endStatistic = appInfo.statistics.find { stat -> stat.info.time == toTime }!!

        val existingDiffingConfigurationTab =
                parent.childTabs.find {
                    (it as? HeapEvolutionTab)?.info?.heapEvolutionData?.startTime == fromTime &&
                            (it as? HeapEvolutionTab)?.info?.heapEvolutionData?.endTime == toTime
                } as? HeapEvolutionTab
        if (existingDiffingConfigurationTab != null) {
            // there is already a diffing tab for the selected timeframe -> switch to that one
            ClientInfo.mainFrame.selectTab(existingDiffingConfigurationTab)
            existingDiffingConfigurationTab.selectHeapEvolutionAnalysisMethods(permBornDiedTempSelected,
                                                                               objectGroupTrendSelected,
                                                                               dataStructureDevelopmentSelected,
                                                                               shortLivedObjectsSelected)
            if (startHeapEvolutionAnalysisAutomatically) {
                existingDiffingConfigurationTab.runHeapEvolutionAnalysis()
            }

        } else {
            // there is no diffing tab for the selected timeframe -> create one
            val tab = HeapEvolutionTab()

            tab.init(appInfo, startStatistic, endStatistic, permBornDiedTempSelected, objectGroupTrendSelected, dataStructureDevelopmentSelected, shortLivedObjectsSelected)
            ClientInfo.mainFrame.addAndSelectTab(parent, tab)

            if (startHeapEvolutionAnalysisAutomatically) {
                tab.runHeapEvolutionAnalysis()
            }
        }
    }

    fun heapStateAnalysis(parent: ActionTab, appInfo: AppInfo, time: Long) {
        startTask(HeapStateTask(appInfo, parent, time))
    }

    fun startSuitableTimeWindowsDetectionTask(overviewTab: ApplicationOverviewTab,
                                              detailsTab: ApplicationDetailTab,
                                              parentTab: ApplicationBaseTab,
                                              appInfo: AppInfo) {
        val chartPanes = overviewTab.chartPanes + detailsTab.rootChartPane.chartPanes

        // detect some hotspots in the application...
        val windowDetectionTask = object : AntTask<DetectedTimeWindows>() {
            override fun backgroundWork(): DetectedTimeWindows {
                updateTitle("Detecting suitable diffing time windows...")
                return DetectedTimeWindows(appInfo.statistics)
            }

            override fun finished() {
                val windows = get()
                val xSelector = chartPanes.first().xSelector!!  // selection is always synchronized so one selector suffices
                val zoomers = (listOf(chartPanes.filter { it.synchronizedProperty.get() }.map { it.xZoomer }.firstOrNull()) +    // one synchronized zoomer plus...
                        chartPanes.filterNot { it.synchronizedProperty.get() }.map { it.xZoomer }).filterNotNull()   // all unsynchronized zoomers

                var beforeIdeaTab: ActionTab? = null

                // add detected windows as ideas
                windows.highGCOverheadWindow?.also { detectedTimeWindow ->
                    val gcOverhead = (detectedTimeWindow.metric * 100).roundToInt()
                    val idea = Idea("High garbage collection overhead!",
                                    Description()
                                            a "AntTracks has detected a timeframe where your application spends over " e "$gcOverhead%" a " of its runtime with GCs! \n"
                                            a "GCs become very time consuming when most objects survive the collection because then they have to be copied. \n"
                                            a "The " e "Perm/Born/Died/Temp analysis " a "in AntTracks shows you which objects survived this timeframe and might have slowed down GCs.",
                                    listOf("Run Perm/Born/Died/Temp analysis" does {
                                        heapEvolutionAnalysis(parentTab,
                                                              appInfo,
                                                              detectedTimeWindow.fromTime..detectedTimeWindow.toTime,
                                                              true,
                                                              false,
                                                              false,
                                                              false,
                                                              true)
                                    }),
                                    listOf(overviewTab.gcActivityChartPane at Idea.BulbPosition.TOP_RIGHT),
                                    overviewTab,
                                    {
                                        xSelector.select(detectedTimeWindow.fromTime.toDouble(), detectedTimeWindow.toTime.toDouble())
                                        getBetterXAxisRange(detectedTimeWindow, chartPanes, appInfo)?.let { betterRange -> zoomers.forEach { it.zoomTo(betterRange) } } ?: Unit
                                    } revertVia {
                                        xSelector.clearSelection()
                                    })
                    overviewTab.ideas.add(idea)
                    detailsTab.ideas.add(idea)
                }

                windows.highGCFrequencyWindow?.also { detectedTimeWindow ->
                    val idea = Idea("High garbage collection frequency!",
                                    Description("AntTracks has detected a timeframe where your application performs over ")
                                            .appendEmphasized("${detectedTimeWindow.metric.roundToInt()} GCs per second")
                                            .appendDefault("!")
                                            .linebreak()
                                            .appendDefault("A high GC frequency occurs when many ")
                                            .appendEmphasized("short-lived objects ")
                                            .appendDefault("are being allocated. ")
                                            .appendDefault("Note that too many GCs can slow down your application even if the GCs themselves are very quick! ")
                                            .linebreak()
                                            .appendDefault("AntTracks can help you reduce GCs by showing you where and why short-lived objects have been allocated."),
                                    listOf("Analyze short-lived objects" does {
                                        heapEvolutionAnalysis(parentTab,
                                                              appInfo,
                                                              detectedTimeWindow.fromTime..detectedTimeWindow.toTime,
                                                              false,
                                                              false,
                                                              false,
                                                              true,
                                                              true)
                                    }),
                                    listOf(overviewTab.gcActivityChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.objectsChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.bytesChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.objectKindsChartPane at Idea.BulbPosition.TOP_RIGHT),
                                    detailsTab,
                                    {
                                        xSelector.select(detectedTimeWindow.fromTime.toDouble(), detectedTimeWindow.toTime.toDouble())
                                        getBetterXAxisRange(detectedTimeWindow, chartPanes, appInfo)?.let { betterRange -> zoomers.forEach { it.zoomTo(betterRange) } } ?: Unit
                                    } revertVia {
                                        xSelector.clearSelection()
                                    })
                    overviewTab.ideas.add(idea)
                    detailsTab.ideas.add(idea)
                }

                windows.highChurnRateWindow?.also { detectedTimeWindow ->
                    val idea = Idea("High memory churn!",
                                    Description("AntTracks has detected a timeframe where your application throws away over ")
                                            .appendEmphasized("${toShortMemoryUsageString(detectedTimeWindow.metric.toLong())} per second")
                                            .appendDefault("!")
                                            .linebreak()
                                            .appendDefault("High memory churn occurs when many ")
                                            .appendEmphasized("short-lived objects ")
                                            .appendDefault("are being allocated in a short time span, which leads to frequent garbage collections. ")
                                            .appendDefault("Note that too many GCs can slow down your application even if the GCs themselves are very quick! ")
                                            .linebreak()
                                            .appendDefault("AntTracks can help you reduce GCs by showing you where and why short-lived objects have been allocated."),
                                    listOf("Analyze short-lived objects" does {
                                        heapEvolutionAnalysis(parentTab,
                                                              appInfo,
                                                              detectedTimeWindow.fromTime..detectedTimeWindow.toTime,
                                                              false,
                                                              false,
                                                              false,
                                                              true,
                                                              true)
                                    }),
                                    listOf(overviewTab.simplifiedMemoryChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.objectsChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.bytesChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.objectKindsChartPane at Idea.BulbPosition.TOP_RIGHT),
                                    detailsTab,
                                    {
                                        xSelector.select(detectedTimeWindow.fromTime.toDouble(), detectedTimeWindow.toTime.toDouble())
                                        getBetterXAxisRange(detectedTimeWindow, chartPanes, appInfo)?.let { betterRange -> zoomers.forEach { it.zoomTo(betterRange) } } ?: Unit
                                    } revertVia {
                                        xSelector.clearSelection()
                                    })
                    overviewTab.ideas.add(idea)
                    detailsTab.ideas.add(idea)
                }

                windows.linRegMemoryLeakAnalysisWindow?.also { detectedTimeWindow ->
                    val idea = Idea("Detected memory leak window based on linear regression!",
                                    Description("AntTracks has detected a timeframe over which the reachable memory is continuously growing. ")
                                            .appendDefault("This is an indicator for a memory leak. ")
                                            .linebreak()
                                            .appendDefault("Memory leaks are often caused by ")
                                            .appendEmphasized("indefinitely growing data structures ")
                                            .appendDefault("- AntTracks can help you find them by calculating their growth over time. ")
                                            .linebreak()
                                            .appendDefault("AntTracks can also reveal ")
                                            .appendEmphasized("trends in the heap evolution ")
                                            .appendDefault("like objects of a certain type that accumulate continuously. "),
                                    listOf("Analyze object group trends and data structure growth" does {
                                        heapEvolutionAnalysis(parentTab,
                                                              appInfo,
                                                              detectedTimeWindow.fromTime..detectedTimeWindow.toTime,
                                                              false,
                                                              true,
                                                              true,
                                                              false,
                                                              true)
                                    }),
                                    listOf(overviewTab.simplifiedMemoryChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.objectsChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.bytesChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.objectKindsChartPane at Idea.BulbPosition.TOP_RIGHT),
                                    overviewTab,
                                    {
                                        xSelector.select(detectedTimeWindow.fromTime.toDouble(), detectedTimeWindow.toTime.toDouble())
                                        getBetterXAxisRange(detectedTimeWindow, chartPanes, appInfo)?.let { betterRange -> zoomers.forEach { it.zoomTo(betterRange) } } ?: Unit
                                    } revertVia { xSelector.clearSelection() })
                    overviewTab.ideas.add(idea)
                    detailsTab.ideas.add(idea)
                }

                windows.shortMemoryLeakAnalysisWindow?.also { detectedTimeWindow ->
                    val idea = Idea("Rapidly growing memory usage!",
                                    Description("AntTracks has detected a timeframe where the reachable memory grows very quickly: over ")
                                            .appendEmphasized("${toShortMemoryUsageString(detectedTimeWindow.metric.toLong())} per second")
                                            .appendDefault("! ")
                                            .linebreak()
                                            .appendDefault("You might want to run the memory leak analyses over this shorter timeframe instead of the long one. This way they will take less time to complete."),
                                    listOf("Analyze object group trends and data structure growth" does {
                                        heapEvolutionAnalysis(parentTab,
                                                              appInfo,
                                                              detectedTimeWindow.fromTime..detectedTimeWindow.toTime,
                                                              false,
                                                              true,
                                                              true,
                                                              false,
                                                              true)
                                    }),
                                    listOf(overviewTab.simplifiedMemoryChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.objectsChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.bytesChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.objectKindsChartPane at Idea.BulbPosition.TOP_RIGHT),
                                    overviewTab,
                                    {
                                        xSelector.select(detectedTimeWindow.fromTime.toDouble(), detectedTimeWindow.toTime.toDouble())
                                        getBetterXAxisRange(detectedTimeWindow, chartPanes, appInfo)?.let { betterRange -> zoomers.forEach { it.zoomTo(betterRange) } } ?: Unit
                                    } revertVia { xSelector.clearSelection() })
                    overviewTab.ideas.add(idea)
                    detailsTab.ideas.add(idea)
                }

                windows.longMemoryLeakAnalysisWindow?.also { detectedTimeWindow ->
                    val idea = Idea("Potential memory leak!",
                                    Description("AntTracks has detected a timeframe over which the reachable memory is continuously growing. ")
                                            .appendDefault("This is an indicator for a memory leak. ")
                                            .linebreak()
                                            .appendDefault("Memory leaks are often caused by ")
                                            .appendEmphasized("indefinitely growing data structures ")
                                            .appendDefault("- AntTracks can help you find these data structures by calculating their growth over time. ")
                                            .linebreak()
                                            .appendDefault("Also, if a memory leak exists, typically objects of a few common types accumulate over time. AntTracks can help you to identify these " +
                                                                   "objects by visualizing the ")
                                            .appendEmphasized("evolution of the heap composition over time."),
                                    listOf("Analyze object group trends and data structure growth" does {
                                        heapEvolutionAnalysis(parentTab,
                                                              appInfo,
                                                              detectedTimeWindow.fromTime..detectedTimeWindow.toTime,
                                                              false,
                                                              true,
                                                              true,
                                                              false,
                                                              true)
                                    }),
                                    listOf(overviewTab.simplifiedMemoryChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.objectsChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.bytesChartPane at Idea.BulbPosition.TOP_RIGHT,
                                           detailsTab.rootChartPane.objectKindsChartPane at Idea.BulbPosition.TOP_RIGHT),
                                    overviewTab,
                                    {
                                        xSelector.select(detectedTimeWindow.fromTime.toDouble(), detectedTimeWindow.toTime.toDouble())
                                        getBetterXAxisRange(detectedTimeWindow, chartPanes, appInfo)?.let { betterRange -> zoomers.forEach { it.zoomTo(betterRange) } } ?: Unit
                                    } revertVia { xSelector.clearSelection() })
                    overviewTab.ideas.add(idea)
                    detailsTab.ideas.add(idea)
                }
            }
        }

        overviewTab.tasks.add(windowDetectionTask)
        startTask(windowDetectionTask)
    }

    private fun getBetterXAxisRange(timeWindow: DetectedTimeWindows.TimeWindow, chartPanes: List<ReducedXYChartPane<*>>, appInfo: AppInfo): ClosedRange<Double>? {
        val timeWindowRange = timeWindow.fromTime.toDouble()..timeWindow.toTime.toDouble()
        val shouldAdjustXAxis = chartPanes.any {
            val xAxis = it.chart.xAxis as ValueAxis
            val xAxisRange = xAxis.lowerBound..xAxis.upperBound
            !xAxisRange.contains(timeWindowRange) || timeWindowRange.width() / xAxisRange.width() < 0.05
        }

        if (shouldAdjustXAxis) {
            var newXAxisLowerBound = timeWindow.fromTime - timeWindowRange.width() * 4.5
            var newXAxisUpperBound = timeWindow.toTime + timeWindowRange.width() * 4.5
            if (newXAxisLowerBound < appInfo.traceStart) {
                newXAxisUpperBound = min(appInfo.traceEnd.toDouble(), newXAxisUpperBound + (appInfo.traceStart - newXAxisLowerBound))
                newXAxisLowerBound = appInfo.traceStart.toDouble()
            }
            if (newXAxisUpperBound > appInfo.traceEnd) {
                newXAxisLowerBound = max(appInfo.traceStart.toDouble(), newXAxisLowerBound - (newXAxisUpperBound - appInfo.traceEnd))
                newXAxisUpperBound = appInfo.traceEnd.toDouble()
            }

            return newXAxisLowerBound..newXAxisUpperBound
        }

        return null
    }
}