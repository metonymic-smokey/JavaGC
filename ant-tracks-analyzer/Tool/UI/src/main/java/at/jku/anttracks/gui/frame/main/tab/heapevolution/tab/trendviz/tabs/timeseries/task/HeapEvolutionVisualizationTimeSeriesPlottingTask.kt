package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.task

import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.HeapEvolutionVisualizationTimeSeriesTab
import at.jku.anttracks.gui.utils.AntTask

class HeapEvolutionVisualizationTimeSeriesPlottingTask(private val heapEvolutionVisualizationTimeSeriesTab: HeapEvolutionVisualizationTimeSeriesTab) : AntTask<Unit>() {
    override fun backgroundWork() = heapEvolutionVisualizationTimeSeriesTab.chartContainers.forEach { it.updateChartData() }
    override fun showOnUI() = false
    override fun finished() {
        heapEvolutionVisualizationTimeSeriesTab.chartContainers.forEach { it.configureClassificationLabel() }
    }
}
