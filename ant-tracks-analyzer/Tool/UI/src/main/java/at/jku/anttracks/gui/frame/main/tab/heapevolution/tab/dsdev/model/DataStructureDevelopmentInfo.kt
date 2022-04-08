package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model

import at.jku.anttracks.classification.nodes.DataGroupingNode
import at.jku.anttracks.classification.trees.ListClassificationTree
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.IHeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.setting.GrowthType
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.task.DataStructureDevelopmentChartTask
import at.jku.anttracks.gui.model.SelectedClassifierInfo
import at.jku.anttracks.heap.datastructures.dsl.DSLDataStructure
import at.jku.anttracks.parser.heapevolution.StartEndAddress
import kotlin.streams.toList

class DataStructureDevelopmentInfo(val heapEvolutionInfo: HeapEvolutionInfo) :
        SelectedClassifierInfo(),
        IHeapEvolutionInfo by heapEvolutionInfo {
    lateinit var dataStructures: List<StartEndAddress>
    lateinit var dataStructuresByStartIndex: Map<Int, StartEndAddress>
    lateinit var dataStructuresByEndAddress: Map<Long, StartEndAddress>
    lateinit var startClassificationTree: ListClassificationTree
    lateinit var endClassificationTree: ListClassificationTree
    lateinit var classificationTreeDifference: DataGroupingNode

    var chartGrowthTypeSorting = GrowthType.RETAINED
    val seriesDisplayedInChart = mutableMapOf(Pair(GrowthType.TRANSITIVE, false),
                                              Pair(GrowthType.RETAINED, true),
                                              Pair(GrowthType.DATA_STRUCTURE, false),
                                              Pair(GrowthType.DEEP_DATA_STRUCTURE, true))

    fun Int.isDataStructureHead() = DSLDataStructure.isDataStructureHead(this, heapEvolutionInfo.endHeap)
    fun Int.isTopLevelDataStructure() = heapEvolutionInfo.endHeap.getHeadedDataStructure(this).isTopLevelDataStructure

    fun buildDevelopmentsList() {
        dataStructures = heapEvolutionInfo.endHeap.stream().filter { endIndex ->
            endIndex.isDataStructureHead() && endIndex.isTopLevelDataStructure()
        }.mapToObj {
            heapEvolutionInfo.permEndIndexMap[it] ?: heapEvolutionInfo.bornEndIndexMap.getValue(it)
        }.toList()

        dataStructures.forEach { ds ->
            if (ds.startIndex >= 0) {
                // PERM
                assert(heapEvolutionInfo.startHeap.getType(ds.startIndex) === heapEvolutionInfo.endHeap.getType(ds.endIndex)) { "Our object may not change its type!" }
                assert(DSLDataStructure.isDataStructureHead(ds.startIndex, heapEvolutionInfo.startHeap)) { "Object must be a DS head" }
                assert(heapEvolutionInfo.startHeap.getType(ds.startIndex).dataStructureLayout.isHead) { "Object must be a DS head" }
            }
            // PERM + BORN
            assert(DSLDataStructure.isDataStructureHead(ds.endIndex, heapEvolutionInfo.endHeap)) { "Object must be a DS head" }
            assert(heapEvolutionInfo.endHeap.getType(ds.endIndex).dataStructureLayout.isHead) { "Object must be a DS head" }
        }

        dataStructuresByStartIndex = dataStructures.associateBy { it.startIndex }
        dataStructuresByEndAddress = dataStructures.associateBy { it.endAddress }
    }

    fun initDataStructureGrowths() {
        // Only initialize the developments once
        if (dataStructures.isNotEmpty() && dataStructures.first().sizeInfo == null) {
            val startNodesByEndAddresses = startClassificationTree.root.children.associateBy { node ->
                DataStructureDevelopmentChartTask.addressRegex.find(node.key.toString())!!.value.replace(",", "").replace(".", "").toLong()
            }

            // initialize growths
            for (endNode in endClassificationTree.root.children) {
                val endAddress = DataStructureDevelopmentChartTask.addressRegex.find(endNode.key.toString())!!.value.replace(",", "").replace(".", "").toLong()
                val startNodeIfExisting = startNodesByEndAddresses[endAddress]
                dataStructuresByEndAddress.getValue(endAddress).calculateSizeInfo(startNodeIfExisting, endNode, heapEvolutionInfo.absoluteHeapGrowth)
            }
        }
    }
}

