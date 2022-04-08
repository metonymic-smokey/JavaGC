package at.jku.anttracks.gui.chart.jfreechart.xy.mixed.objectgrouptrend

import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.HeapEvolutionVisualizationTimeSeriesDiagramInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.DiagramMetric
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.SeriesSort
import at.jku.anttracks.util.safe
import java.util.*

// Call with level = 0 from outside, and key = [] for root node
private fun getNodeWithKey(root: GroupingNode?, keys: Array<String>): GroupingNode? {
    var cur = root
    for (key in keys) {
        cur = cur?.getChild(key)
    }
    return cur
}

private fun getAmountOfCurUnit(gn: GroupingNode, diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo): Long = when (diagramInfo.diagramMetric) {
    DiagramMetric.OBJECTS -> gn.objectCount
    DiagramMetric.BYTES -> gn.getByteCount(null)
    DiagramMetric.RETAINED_SIZE -> gn.retainedSizeProperty().longValue()
    DiagramMetric.TRANSITIVE_CLOSURE_SIZE -> gn.transitiveClosureSizeProperty().longValue()
}.safe

fun Map<Long, GroupingNode>.calculateRelativeGrowths(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo): HashMap<String, Double> {
    val sortedNodes = timeSortedGroupingsMatchingSelectedKey(diagramInfo)

    val growthPerKey = HashMap<String, Double>()
    if (sortedNodes.isEmpty()) {
        return growthPerKey
    }

    val startTree = sortedNodes[0] ?: return growthPerKey
    val lastTree = sortedNodes[sortedNodes.size - 1]
    for (child in startTree.children) {
        val amount = getAmountOfCurUnit(child, diagramInfo).toDouble()
        // we only put the key into the map if the amount is not 0, because if it is 0 is should be in the 'other'-series anyway
        if (amount != 0.0) {
            growthPerKey[child.key.toString()] = amount
        }
    }
    for (key in growthPerKey.keys) {
        var amount = 0.0
        if (lastTree != null) {
            val child = getNodeWithKey(lastTree, arrayOf(key))
            if (child != null) {
                amount = getAmountOfCurUnit(child, diagramInfo).toDouble()
            }
        }
        val relativeGrowth = (amount - growthPerKey[key]!!) / growthPerKey[key]!!
        //now we overwrite the old value with the actual relative growth
        growthPerKey[key] = relativeGrowth
    }
    return growthPerKey
}

fun Map<Long, GroupingNode>.calculateAbsoluteGrowths(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo): HashMap<String, Long> {
    val sortedNodes = timeSortedGroupingsMatchingSelectedKey(diagramInfo)

    val changePerKey = HashMap<String, Long>()
    if (sortedNodes.size == 0) {
        return changePerKey
    }
    val startTree = sortedNodes[0]
    if (startTree != null) {
        for (child in startTree.children) {
            //we multiply it by -1, because if the key is not present in the last tree, it should be a negative value and we dont have to perform further steps
            changePerKey[child.key.toString()] = -1 * getAmountOfCurUnit(child, diagramInfo)
        }
    }

    val lastTree = sortedNodes[sortedNodes.size - 1]

    if (lastTree != null) {
        for (child in lastTree.children) {
            val curKey = child.key.toString()
            if (!changePerKey.containsKey(curKey)) {
                changePerKey[curKey] = getAmountOfCurUnit(child, diagramInfo)
            } else {
                val startValue = changePerKey[curKey]
                //we ADD the startValue to the end value as the start value is negative
                val diff = getAmountOfCurUnit(child, diagramInfo) + startValue!!
                changePerKey[curKey] = diff
            }
        }
    }
    return changePerKey
}

fun Map<Long, GroupingNode>.timeSortedGroupingsMatchingSelectedKey(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo): List<GroupingNode?> = entries
        .sortedBy { it.key }
        .map { x -> getNodeWithKey(x.value, diagramInfo.selectedKeysAsArray) }

fun Map<Long, GroupingNode>.getSortedKeysByRelativeGrowth(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo): List<String> {
    val relativeGrowths = calculateRelativeGrowths(diagramInfo)
    return relativeGrowths.entries
            .sortedByDescending { it.value }
            .take(diagramInfo.seriesCount)
            .map { it.key }
}

fun Map<Long, GroupingNode>.getSortedKeysByAbsoluteGrowth(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo): List<String> {
    val changePerKey = calculateAbsoluteGrowths(diagramInfo)
    return changePerKey.entries
            .sortedByDescending { it.value }
            .take(diagramInfo.seriesCount)
            .map { it.key }
}

fun Map<Long, GroupingNode>.getSortedKeysBySortedTimes(sortedTimes: List<Long>, diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo): ArrayList<String> {
    val selectedSeriesKeys = ArrayList<String>()

    for (curTime in sortedTimes) {
        val curRoot = get(curTime)
        val targetNode = getNodeWithKey(curRoot, diagramInfo.selectedKeysAsArray) ?: continue
        val targetChildren = targetNode.children
        val curKeys = targetChildren
                .filter { child -> !selectedSeriesKeys.contains(child.key.toString()) }
                .sortedByDescending { getAmountOfCurUnit(it, diagramInfo) }
                .take((diagramInfo.seriesCount - selectedSeriesKeys.size))
                .map { x -> x.key.toString() }

        selectedSeriesKeys.addAll(curKeys)
        if (selectedSeriesKeys.size >= diagramInfo.seriesCount) {
            break
        }
    }
    return selectedSeriesKeys
}

fun Map<Long, GroupingNode>.getSortedKeysByAvg(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo): List<String> {
    val amountPerKey = HashMap<String, Long>()

    for (curRoot in values) {
        val targetNode = getNodeWithKey(curRoot, diagramInfo.selectedKeysAsArray) ?: continue
        for (curKeyNode in targetNode.children) {
            // TODO Inefficient! Use Counter instead of Long as map value
            var keyVal: Long = amountPerKey.getOrDefault(curKeyNode.key, 0L)
            keyVal += getAmountOfCurUnit(curKeyNode, diagramInfo)
            amountPerKey[curKeyNode.key.toString()] = keyVal
        }
    }
    return amountPerKey.entries
            .sortedByDescending { it.key }
            .take(diagramInfo.seriesCount)
            .map { it.key }
}

fun Map<Long, GroupingNode>.getSortedKeys(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo) = when (diagramInfo.seriesSort) {
    SeriesSort.START -> getSortedKeysBySortedTimes(keys.sorted(), diagramInfo)
    SeriesSort.AVG -> getSortedKeysByAvg(diagramInfo)
    SeriesSort.END -> getSortedKeysBySortedTimes(keys.sortedDescending(), diagramInfo)
    SeriesSort.ABS_GROWTH -> getSortedKeysByAbsoluteGrowth(diagramInfo)
    SeriesSort.REL_GROWTH -> getSortedKeysByRelativeGrowth(diagramInfo)
}.safe