package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model

import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.model.HeapEvolutionVisualizationInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.DiagramMetric
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.SeriesSort

class HeapEvolutionVisualizationTimeSeriesDiagramInfo(val heapEvolutionVisualizationInfo: HeapEvolutionVisualizationInfo,
                                                      var useLineChart: Boolean,
                                                      var seriesCount: Int,
                                                      var seriesSort: SeriesSort,
                                                      var diagramMetric: DiagramMetric,
                                                      val selectedKeys: List<String>, //this List is used to save to selection of the user when he opens a new detail diagram by clicking on one of the series
                                                      var isShowOtherSeries: Boolean) {

    fun getSelectedKeysClassifiers(): Array<String> {
        val classifiers = mutableListOf<String>()

        val keyList = mutableListOf<String>()
        for (key in selectedKeys) {
            keyList.add(key)
            for (tree in heapEvolutionVisualizationInfo.groupings) {
                val childWithKey = tree.value.getDeepChild(keyList.toTypedArray())
                if (childWithKey != null) {
                    classifiers.add(heapEvolutionVisualizationInfo.getDummyClassifier(childWithKey.classifier)!!.name)
                    break
                }
            }
        }

        return classifiers.toTypedArray()
    }

    val selectedKeysAsArray: Array<String>
        get() = selectedKeys.toTypedArray()
}
