
package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.task

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.nodes.DataGroupingNode
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.classification.nodes.ListGroupingNode
import at.jku.anttracks.classification.nodes.MapGroupingNode
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.HeapEvolutionVisualizationTimeSeriesTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.HeapEvolutionVisualizationTimeSeriesDiagramInfo
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.utils.AntTask
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.HeapAdapter
import at.jku.anttracks.heap.io.MetaDataReaderConfig
import at.jku.anttracks.parser.EventType
import at.jku.anttracks.parser.ParserGCInfo
import at.jku.anttracks.parser.ParsingInfo
import at.jku.anttracks.parser.heap.HeapTraceParser
import at.jku.anttracks.parser.io.BaseFile
import at.jku.anttracks.parser.symbols.SymbolsFile
import java.io.*
import java.nio.file.Files
import java.util.*
import java.util.logging.Level

// TODO: This class is currently not used.
// Reintroduce it, since it ensures that multiple time spans that have already been parsed are not parsed a second time!
class HeapEvolutionVisualizationTimeSeriesDiagramTask(private val appInfo: AppInfo,
                                                      private val chain: ClassifierChain,
                                                      private val filters: Array<Filter>,
                                                      private val info: HeapEvolutionInfo,
                                                      private val diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo,
                                                      private val heapEvolutionVisualizationTimeSeriesTab: HeapEvolutionVisualizationTimeSeriesTab,
                                                      private val parseXthHeap: Boolean,
                                                      private val parseHeapAllXSeconds: Boolean,
                                                      private val parseVal: Int) : AntTask<Map<Long, GroupingNode>>() {
    private var currentChartUpdatingTask: HeapEvolutionVisualizationTimeSeriesPlottingTask? = null
    private var lastPlotTime: Long = 0
    private var fullMetaDataPath: String? = null
    private var mapTreeFilePathWithoutTime: String? = null
    private var startMissingTime: Long = -1
    private var endMissingTime: Long = -1
    private val groupings = HashMap<Long, GroupingNode>()
    private val availableClassifier: HashMap<String, Classifier<*>>
    private var lastParsedTime: Long = -10000 //such that the first tree always gets selected
    private var countSinceLastParse = -1

    init {
        availableClassifier = HashMap()
        for (classifierFactory in diagramInfo.heapEvolutionVisualizationInfo.availableClassifier) {
            availableClassifier[classifierFactory.name] = classifierFactory.create()
        }
        try {
            fullMetaDataPath = (if (BaseFile.isPlainFile(SymbolsFile.SYMBOL_FILE_ID, appInfo.symbolsFile))
                appInfo.symbolsFile!!.parent
            else
                appInfo.symbolsFile).toString() + File.separator + Consts.ANT_META_DIRECTORY
            val mapTreeFilePathBuilder = StringBuilder(fullMetaDataPath!!)
            mapTreeFilePathBuilder.append(File.separator)
            for (i in 0 until diagramInfo.heapEvolutionVisualizationInfo.selectedClassifiers.length()) {
                mapTreeFilePathBuilder.append(diagramInfo.heapEvolutionVisualizationInfo.selectedClassifiers.get(i).name)
                mapTreeFilePathBuilder.append("_")
            }
            diagramInfo.heapEvolutionVisualizationInfo.selectedFilters
                    .map { it.name }
                    .sorted()
                    .forEach {   // sort filters because their order does not matter
                        filterName ->
                        mapTreeFilePathBuilder.append(filterName)
                        mapTreeFilePathBuilder.append("_")
                    }
            mapTreeFilePathWithoutTime = mapTreeFilePathBuilder.toString()
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    @Throws(Exception::class)
    override fun backgroundWork(): Map<Long, GroupingNode> {
        return parseTrace()
    }

    @Throws(Exception::class)
    private fun parseTrace(): Map<Long, GroupingNode> {
        LOGGER.log(Level.INFO, "Start parsing trace")
        updateTitle("Calculating heap")

        val allTimes = appInfo.statistics
                .filter { x -> x.info.meta == EventType.GC_START && PARSE_AT_GC_START || x.info.meta == EventType.GC_END && PARSE_AT_GC_END }
                .map { x -> x.info.time }
                .filter { x -> x >= info.startTime && x <= info.endTime }
                .distinct()
                .sorted()

        var reparseWholeTrace = false
        for (i in allTimes.indices) {
            val curTime = allTimes[i]
            val curInputMapTreeFile = File(mapTreeFilePathWithoutTime + curTime + if (USE_INDEX_BASED_HEAP) ".listTree" else ".mapTree")
            if (!curInputMapTreeFile.exists()) {
                if (startMissingTime != -1L) {
                    if (endMissingTime != -1L) {
                        //the segment of missing times already ended previously, so this is the second missing segment
                        LOGGER.log(Level.INFO,
                                   "Going to reparse whole trace - startmissingtime: " + startMissingTime + " endmissingtime: " + endMissingTime + " cur " + "time: " +
                                           curTime)
                        reparseWholeTrace = true
                        break
                    }
                } else {
                    //this is the start of the first missing segment
                    startMissingTime = curTime
                }
            } else {
                if (startMissingTime != -1L && endMissingTime == -1L) {
                    //if this is the first data after a missing segment => the previous time was the last missing one
                    endMissingTime = allTimes[i - 1]
                }
            }
        }

        if (reparseWholeTrace) {
            groupings.clear()
            startMissingTime = info.startTime
            endMissingTime = info.endTime
        } else {
            if (startMissingTime != -1L && endMissingTime == -1L) {
                //if there was no data after the missing part, we have to parse until the end
                endMissingTime = info.endTime
                LOGGER.log(Level.INFO, "Reparsing partially between: $startMissingTime and $endMissingTime")
            }
            for (i in allTimes.indices) {
                val curTime = allTimes[i]
                if (curTime < startMissingTime || curTime > endMissingTime) {
                    val curInputMapTreeFile = File(mapTreeFilePathWithoutTime + curTime + if (USE_INDEX_BASED_HEAP) ".listTree" else ".mapTree")
                    LOGGER.log(Level.INFO, "Loading map grouping file: $curInputMapTreeFile")
                    val loadedMapTreeFile = Files.readAllBytes(curInputMapTreeFile.toPath())
                    val curInput = DataInputStream(ByteArrayInputStream(loadedMapTreeFile))
                    groupings[curTime] = if (USE_INDEX_BASED_HEAP)
                        DataGroupingNode.readTree(curInput, null, availableClassifier)
                    else
                        MapGroupingNode.readTree(curInput, null, availableClassifier)
                    curInput.close()
                }
            }
            if (startMissingTime == -1L) {
                //no files are missing => we don't have to reparse
                return groupings
            }
        }

        LOGGER.log(Level.INFO, "Parsing the heaps from $startMissingTime until $endMissingTime")
        //setup heap traceparser
        val parser = HeapTraceParser(appInfo.symbols,
                                     MetaDataReaderConfig(appInfo.symbols.root + File.separator + Consts.ANT_META_DIRECTORY),
                                     startMissingTime,
                                     endMissingTime)

        parser.addHeapListener(object : HeapAdapter() {
            override fun phaseChanging(
                    sender: Any,
                    from: ParserGCInfo,
                    to: ParserGCInfo,
                    failed: Boolean,
                    position: Long,
                    parsingInfo: ParsingInfo,
                    inParserTimeWindow: Boolean) {
                updateProgress(position - parsingInfo.fromByte, parsingInfo.traceLength)

                if (PARSE_AT_GC_START && inParserTimeWindow && to.eventType == EventType.GC_START) {
                    handleHeap(parser.workspace, to.time)
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
                if (PARSE_AT_GC_END && inParserTimeWindow && to.eventType == EventType.GC_END) {
                    handleHeap(parser.workspace, to.time)
                }
            }
        })
        val finalHeap = parser.parse(cancelProperty)
        if (startMissingTime == endMissingTime) {
            handleHeap(finalHeap, startMissingTime)
        }

        LOGGER.log(Level.INFO, "Finished parsing trace, heaps constructed")
        return groupings
    }

    private fun shouldParse(time: Long): Boolean {
        //TODO: decide whether to always parse last tree
        if (parseHeapAllXSeconds) {
            if (time - lastParsedTime >= parseVal) {
                lastParsedTime = time
                return true
            } else {
                return false
            }
        }
        if (parseXthHeap) {
            println("count since last: $countSinceLastParse")
            countSinceLastParse++
            countSinceLastParse %= parseVal

            return countSinceLastParse == 0

        }
        return true
    }

    private fun handleHeap(heap: DetailedHeap, time: Long) {
        if (time < startMissingTime || time > endMissingTime) {
            return
        }
        if (!shouldParse(time)) {
            return
        }
        LOGGER.log(Level.INFO, "Parsing heap at time: $time")

        var groupingNode: GroupingNode? = null
        if (USE_INDEX_BASED_HEAP) {
            val indexBasedHeap = heap.toIndexBasedHeap()
            chain.forEach { classifier -> classifier.setup({ indexBasedHeap.symbols }, { indexBasedHeap }) }
            Arrays.stream(filters).forEach { filter -> filter.setup({ indexBasedHeap.symbols }, { indexBasedHeap }) }

            //calculate tree
            val listClassificationTree = indexBasedHeap.groupListParallel(filters, chain, false, true, null, null)
            listClassificationTree.init(indexBasedHeap, true, true, false, false)

            //calculate closures
            val closureInitTask = listClassificationTree.initClosureTask(indexBasedHeap, true, true, false, false)
            ClientInfo.operationManager.addNewOperation(closureInitTask)
            heapEvolutionVisualizationTimeSeriesTab.tasks.add(closureInitTask)
            closureInitTask.run()

            groupingNode = DataGroupingNode(indexBasedHeap, listClassificationTree.root as ListGroupingNode)
        } else {
            groupingNode = heap.toObjectStream().filter(*filters).groupMapParallel(chain, false)
        }

        groupings[time] = groupingNode

        //if no file exits for this grouping, we write it
        val groupingFile = File(mapTreeFilePathWithoutTime + time + if (USE_INDEX_BASED_HEAP) ".listTree" else ".mapTree")
        if (!groupingFile.exists()) {
            var curOutput: DataOutputStream? = null
            try {
                curOutput = DataOutputStream(FileOutputStream(groupingFile))
                if (USE_INDEX_BASED_HEAP) {
                    DataGroupingNode.writeTree(curOutput, (groupingNode as DataGroupingNode?)!!, null, 0, null)
                } else {
                    MapGroupingNode.writeTree(curOutput, (groupingNode as MapGroupingNode?)!!)
                }

            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (curOutput != null) {
                    try {
                        curOutput.close()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            }
        }
    }

    override fun finished() {

    }

    companion object {
        val USE_INDEX_BASED_HEAP = true
        val PARSE_AT_GC_START = false
        val PARSE_AT_GC_END = true
    }
}
