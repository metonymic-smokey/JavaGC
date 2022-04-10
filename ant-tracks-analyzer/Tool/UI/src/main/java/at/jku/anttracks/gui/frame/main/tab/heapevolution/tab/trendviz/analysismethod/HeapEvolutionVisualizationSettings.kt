package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.analysismethod

import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.HeapParsingPolicy

interface IHeapEvolutionVisualizationSettings {
    val calculateClosures: Boolean
    val heapParsingPolicy: HeapParsingPolicy
    val heapsToSkip: Int
    val secondsBetweenHeaps: Int
    val exportAsJSON: Boolean
}

class HeapEvolutionVisualizationSettings(override val calculateClosures: Boolean = false,
                                         override val heapParsingPolicy: HeapParsingPolicy = HeapParsingPolicy.ALL_HEAPS,
                                         override val heapsToSkip: Int = 0,
                                         override val secondsBetweenHeaps: Int = 0,
                                         override val exportAsJSON: Boolean = false) : IHeapEvolutionVisualizationSettings