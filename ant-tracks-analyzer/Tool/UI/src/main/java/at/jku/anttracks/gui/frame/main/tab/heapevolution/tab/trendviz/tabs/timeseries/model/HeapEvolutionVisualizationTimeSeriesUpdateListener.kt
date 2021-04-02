package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model

import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.model.HeapEvolutionVisualizationInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.HeapEvolutionVisualizationTimeSeriesTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.HeapParsingPolicy
import at.jku.anttracks.gui.utils.ideagenerators.ObjectGroupTrendVisualizationIdeaGenerator
import at.jku.anttracks.parser.heapevolution.HeapEvolutionData
import at.jku.anttracks.parser.heapevolution.HeapEvolutionUpdateListener
import javafx.application.Platform

class HeapEvolutionVisualizationTimeSeriesUpdateListener(val tab: HeapEvolutionVisualizationTimeSeriesTab?,
                                                         val info: HeapEvolutionVisualizationInfo) : HeapEvolutionUpdateListener {
    var skippedHeapsCounter: Int = 0
    var lastHeapTime: Long = 0
    var needIndexBasedHeap = info.selectedClassifiers.list.any { it.sourceCollection == ClassifierSourceCollection.FASTHEAP } || info.calculateClosures

    override fun gcEnd(heapEvolutionData: HeapEvolutionData) {
        when (info.heapParsingPolicy) {
            HeapParsingPolicy.ALL_HEAPS -> {
                if (needIndexBasedHeap) {
                    info.handleHeap(heapEvolutionData.currentTime, heapEvolutionData.currentIndexBasedHeap)
                } else {
                    info.handleHeap(heapEvolutionData.currentTime, heapEvolutionData.detailedHeap)
                }
            }

            HeapParsingPolicy.EVERY_NTH_HEAP -> {
                if (skippedHeapsCounter == info.heapsToSkip || heapEvolutionData.currentTime == info.startTime || heapEvolutionData.currentTime == info.endTime) {
                    skippedHeapsCounter = 0
                    if (needIndexBasedHeap) {
                        info.handleHeap(heapEvolutionData.currentTime, heapEvolutionData.currentIndexBasedHeap)
                    } else {
                        info.handleHeap(heapEvolutionData.currentTime, heapEvolutionData.detailedHeap)
                    }
                } else {
                    skippedHeapsCounter++
                }
            }

            HeapParsingPolicy.HEAP_EVERY_N_SECS -> {
                if ((heapEvolutionData.currentTime - lastHeapTime) / 1_000 > info.secondsBetweenHeaps || heapEvolutionData.currentTime == info.startTime || heapEvolutionData.currentTime == info.endTime) {
                    lastHeapTime = heapEvolutionData.currentTime
                    if (needIndexBasedHeap) {
                        info.handleHeap(heapEvolutionData.currentTime, heapEvolutionData.currentIndexBasedHeap)
                    } else {
                        info.handleHeap(heapEvolutionData.currentTime, heapEvolutionData.detailedHeap)
                    }
                }
            }
        }
    }

    override fun timeWindowEnd(heapEvolutionData: HeapEvolutionData) {
        if (heapEvolutionData.currentTime == info.endTime && tab != null) {
            Platform.runLater { ObjectGroupTrendVisualizationIdeaGenerator.analyze(tab, info.selectedClassifiers, info.selectedFilters) }
        }
    }
}