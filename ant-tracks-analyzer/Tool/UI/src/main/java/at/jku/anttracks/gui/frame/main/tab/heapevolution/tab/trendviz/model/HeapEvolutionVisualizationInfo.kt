package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.model

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.nodes.DataGroupingNode
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.classification.nodes.ListGroupingNode
import at.jku.anttracks.classification.trees.asJSON
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.IHeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.analysismethod.HeapEvolutionVisualizationSettings
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.analysismethod.IHeapEvolutionVisualizationSettings
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.DiagramMetric
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.model.IAvailableClassifierInfo
import at.jku.anttracks.gui.model.SelectedClassifierInfo
import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.parser.heapevolution.IHeapEvolutionData
import at.jku.anttracks.util.deepCount
import at.jku.anttracks.util.deepSize
import io.micrometer.core.instrument.Metrics
import javafx.collections.FXCollections
import org.jfree.data.xy.XYDataItem
import java.nio.file.Files
import java.nio.file.Paths

class HeapEvolutionVisualizationInfo(val heapEvolutionInfo: HeapEvolutionInfo,
                                     val heapEvolutionVisualizationSettings: HeapEvolutionVisualizationSettings,
                                     selectedClassifiers: ClassifierChain = ClassifierChain(),
                                     selectedFilters: List<Filter> = listOf()) :
        IHeapEvolutionInfo by heapEvolutionInfo,
        IHeapEvolutionData by heapEvolutionInfo,
        IHeapEvolutionVisualizationSettings by heapEvolutionVisualizationSettings,
        IAvailableClassifierInfo by heapEvolutionInfo,
        SelectedClassifierInfo(selectedClassifiers, selectedFilters) {
    val groupings = FXCollections.observableHashMap<Long, GroupingNode>()
    val pointsToMaps = FXCollections.observableHashMap<Long, Map<String, Map<String, Long>>?>()
    val pointedFromMaps = FXCollections.observableHashMap<Long, Map<String, Map<String, Long>>?>()

    // Paths used for export
    private val appDir = Paths.get(System.getProperty("user.home"),
                                   "anttracks",
                                   "json-export",
                                   "${appInfo.appName}_${heapEvolutionInfo.startTime}_${heapEvolutionInfo.endTime}",
                                   selectedClassifiers.list.joinToString("") { it.name.replace(" ", "") })
    private val antTracksJsonDir = appDir.resolve("anttracks-format")
    private val defaultJsonDir = appDir.resolve("default-format")
    private val pointsToMapsJsonDir = defaultJsonDir.resolve("points-to-maps")
    private val pointedFromMapsJsonDir = defaultJsonDir.resolve("pointed-from-maps")

    init {
        if (exportAsJSON) {
            antTracksJsonDir.toFile().deleteRecursively()
            defaultJsonDir.toFile().deleteRecursively()
            pointsToMapsJsonDir.toFile().deleteRecursively()
            pointedFromMapsJsonDir.toFile().deleteRecursively()
            Files.createDirectories(antTracksJsonDir)
            Files.createDirectories(defaultJsonDir)
            Files.createDirectories(pointsToMapsJsonDir)
            Files.createDirectories(pointedFromMapsJsonDir)
        }
    }

    fun handleHeap(time: Long, heap: DetailedHeap) {
        val mapGrouping =
                heap.toObjectStream()
                        .filter(*selectedFilters.toTypedArray())
                        .groupMapParallel(selectedClassifiers, false)

        // DO NOT DO THIS, this takes up far too much memory on traversal
        /*
        if (ClientInfo.assertionsEnabled) {
            Metrics.counter("anttracks.objectgrouptrend.mapgroupings.count").increment()
            Metrics.counter("anttracks.objectgrouptrend.mapgroupings.deep.count").increment(mapGrouping.deepCount.toDouble())
            Metrics.counter("anttracks.objectgrouptrend.mapgroupings.deep.bytes").increment(mapGrouping.deepSize.toDouble())
        }
        */
        val dataGrouping = DataGroupingNode(mapGrouping)
        exportJson(time, dataGrouping, null, null)
        handleHeap(time, dataGrouping, null, null)
    }

    fun handleHeap(time: Long, heap: IndexBasedHeap) {
        selectedClassifiers.forEach { classifier -> classifier.setup({ heap.symbols }, { heap }) }
        selectedFilters.forEach { filter -> filter.setup({ heap.symbols }, { heap }) }

        //calculate tree
        val listClassificationTree = heap.groupListParallel(selectedFilters.toTypedArray(),
                                                            selectedClassifiers,
                                                            false,
                                                            true,
                                                            null,
                                                            null)
        listClassificationTree.init(heap, false, false, false, false)

        val listGrouping = listClassificationTree.root as ListGroupingNode

        // DO NOT DO THIS, this takes up far too much memory on traversal
        /*
        if (ClientInfo.assertionsEnabled) {
            Metrics.counter("anttracks.objectgrouptrend.listgroupings.count").increment()
            Metrics.counter("anttracks.objectgrouptrend.listgroupings.deep.count").increment(listGrouping.deepCount.toDouble())
            Metrics.counter("anttracks.objectgrouptrend.listgroupings.deep.bytes").increment(listGrouping.deepSize.toDouble())
        }
        */

        val dataGrouping = DataGroupingNode(heap, listGrouping)
        exportJson(time, dataGrouping, listClassificationTree.pointsToMapStringKeys, listClassificationTree.pointedFromMapStringKeys)
        handleHeap(time, dataGrouping, listClassificationTree.pointsToMapStringKeys, listClassificationTree.pointedFromMapStringKeys)
    }

    private fun exportJson(time: Long, grouping: GroupingNode, pointsToMap: Map<String, Map<String, Long>>?, pointedFromMap: Map<String, Map<String, Long>>?) {
        if (exportAsJSON) {
            val antTracksJsonFile = antTracksJsonDir.resolve("$time.json")
            val defaultJsonFile = defaultJsonDir.resolve("$time.json")
            val pointsToMapJsonFile = pointsToMapsJsonDir.resolve("$time.json")
            val pointedFromMapJsonFile = pointedFromMapsJsonDir.resolve("$time.json")
            grouping.exportAsJSON(null, selectedClassifiers, time, antTracksJsonFile.toFile(), defaultJsonFile.toFile())
            Files.write(pointsToMapJsonFile, (time to pointsToMap).asJSON.toString().toByteArray())
            Files.write(pointedFromMapJsonFile, (time to pointedFromMap).asJSON.toString().toByteArray())
        }
    }

    private fun handleHeap(time: Long, classificationResult: DataGroupingNode, pointsToMap: Map<String, Map<String, Long>>?, pointedFromMap: Map<String, Map<String, Long>>?) {
        if (ClientInfo.assertionsEnabled) {
            Metrics.counter("anttracks.objectgrouptrend.datagroupings.count").increment()
            Metrics.counter("anttracks.objectgrouptrend.datagroupings.deep.count").increment(classificationResult.deepCount.toDouble())
            Metrics.counter("anttracks.objectgrouptrend.datagroupings.deep.bytes").increment(classificationResult.deepSize.toDouble())
        }

        // TODO Synchronization handling may be needed
        groupings[time] = classificationResult
        pointsToMaps[time] = pointsToMap
        pointedFromMaps[time] = pointedFromMap
    }

    fun toChartSeries(keys: Array<out Any>, diagramMetric: DiagramMetric): HashMap<Any, List<XYDataItem>> {
        val dataSet = HashMap<Any, List<XYDataItem>>()

        Metrics.timer("anttracks.objectgrouptrend.data.createdataset").record {
            val sortedGroupings: List<MutableMap.MutableEntry<Long, GroupingNode>> = groupings.entries.sortedBy { it.key }

            var treeIndex = 0
            for ((_, tree) in sortedGroupings) {
                var cur: GroupingNode? = tree
                for (key in keys) {
                    cur = cur?.getChild(key)
                }

                if (cur != null) {
                    // If we _find_ a matching child node for the given key at the current time, for every child either:
                    for (child in cur.children) {
                        val series = dataSet[child.key]
                        if (series == null) {
                            // add a new series, with 0.0 everywhere
                            dataSet[child.key] = List(sortedGroupings.size) { i -> XYDataItem(sortedGroupings[i].key.toDouble(), 0.0) }
                        }
                        // change the series's latest entry from 0.0 to the real value
                        dataSet[child.key]!![treeIndex].y = diagramMetric.producer(child)
                    }
                }

                treeIndex++
            }
        }

        return dataSet
    }
}