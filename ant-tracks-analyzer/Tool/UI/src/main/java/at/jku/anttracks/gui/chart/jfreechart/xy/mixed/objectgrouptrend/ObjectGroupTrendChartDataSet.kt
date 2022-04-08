package at.jku.anttracks.gui.chart.jfreechart.xy.mixed.objectgrouptrend

import at.jku.anttracks.gui.chart.base.AntSeries
import at.jku.anttracks.gui.chart.jfreechart.xy.util.AddALotXYSeries
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.model.HeapEvolutionVisualizationInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.HeapEvolutionVisualizationTimeSeriesDiagramInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.SeriesSort
import at.jku.anttracks.gui.utils.ColorBrewer
import org.jfree.data.xy.DefaultTableXYDataset
import org.jfree.data.xy.XYDataItem
import java.awt.Color

class ObjectGroupTrendChartDataSet(val info: HeapEvolutionVisualizationInfo, selectedKey: String?, val diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo) : DefaultTableXYDataset() {
    val antSeries = ArrayList<AntSeries>()
    val otherSeries: AntSeries

    init {
        val allSeries = info.toChartSeries(diagramInfo.selectedKeysAsArray, diagramInfo.diagramMetric).entries

        val seriesToShow = when (diagramInfo.seriesSort) {
            SeriesSort.START -> allSeries.sortedByDescending { it.value.first().yValue }
            SeriesSort.AVG -> allSeries.sortedByDescending { it.value.map { xy -> xy.yValue }.sum() / it.value.size }
            SeriesSort.END -> allSeries.sortedByDescending { it.value.last().yValue }
            SeriesSort.ABS_GROWTH -> allSeries.sortedByDescending { it.value.last().yValue - it.value.first().yValue }
            SeriesSort.REL_GROWTH -> allSeries.sortedByDescending { if (it.value.first().yValue > 0) it.value.last().yValue / it.value.first().yValue else 0.0 }
        }.take(diagramInfo.seriesCount)

        val otherData = List(info.groupings.size) { i -> XYDataItem(allSeries.first().value[i].x, 0.0) }
        if (diagramInfo.isShowOtherSeries && allSeries.size - seriesToShow.size > 0) {
            allSeries.filter { !seriesToShow.contains(it) }.forEach { listToMerge -> listToMerge.value.forEachIndexed { i, xy -> otherData[i].y = otherData[i].yValue + xy.yValue } }
        }
        otherSeries = AntSeries(OTHER, otherData, OTHER_COLOR)
        if (diagramInfo.isShowOtherSeries) {
            antSeries += otherSeries
        }

        val divergingPalettes = ColorBrewer.getDivergingColorPalettes(false)
        val divergingPalette = divergingPalettes[diagramInfo.selectedKeys.size % divergingPalettes.size]
        val sizedDivergingPalette = divergingPalette.getColorPalette(diagramInfo.seriesCount).map { Color(it.red, it.green, it.blue, NORMAL_ALPHA) }.toMutableList()

        // add all the selected series (on top of the 'other' series)
        var colorIndex = 0
        for (namedSeries in seriesToShow.reversed()) {
            val color = if (namedSeries.key.toString() == selectedKey) HIGHLIGHT_COLOR else sizedDivergingPalette[colorIndex]
            antSeries += AntSeries(namedSeries.key.toString(), namedSeries.value, color)
            colorIndex++
        }

        for (s in antSeries) {
            addSeries(AddALotXYSeries(s.key, false).apply {
                setData(s.data, false)
            })
        }
    }

    fun getClassifiersOfSeries(): List<String> {
        val keysToFind = antSeries.map { series -> mutableListOf(*diagramInfo.selectedKeysAsArray).apply { add(series.key) }.toTypedArray() }
        val classifiers = mutableListOf<String>()
        for (keys in keysToFind) {
            for (tree in info.groupings) {
                val childWithKey = tree.value.getDeepChild(keys)
                if (childWithKey != null) {
                    classifiers.add(diagramInfo.heapEvolutionVisualizationInfo.getDummyClassifier(childWithKey.classifier)!!.name)
                    break
                }
            }
        }
        return classifiers.distinct()
    }

    companion object {
        const val OTHER = "Other"
        const val NORMAL_ALPHA = 190
        const val HIGHLIGHT_ALPHA = 255
        val OTHER_COLOR = Color(255, 0, 0, NORMAL_ALPHA)
        val HIGHLIGHT_COLOR = Color(255, 255, 0, HIGHLIGHT_ALPHA)
    }
}