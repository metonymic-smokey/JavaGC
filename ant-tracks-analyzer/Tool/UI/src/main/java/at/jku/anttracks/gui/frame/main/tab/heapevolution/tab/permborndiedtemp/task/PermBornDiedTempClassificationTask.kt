package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.task

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.classification.nodes.DetailedHeapGroupingNode
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.classification.nodes.ListGroupingNode
import at.jku.anttracks.classification.nodes.MapGroupingNode
import at.jku.anttracks.diff.PermBornDiedTempGrouping
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.tab.classification.PermBornDiedTempClassificationTab
import at.jku.anttracks.gui.utils.AntTask
import at.jku.anttracks.gui.utils.ideagenerators.PermBornDiedTempIdeaGenerator
import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.ObjectStream
import java.util.*

class PermBornDiedTempClassificationTask(val tab: PermBornDiedTempClassificationTab,
                                         val info: PermBornDiedTempInfo) : AntTask<PermBornDiedTempGrouping>() {

    private val classificationTaskTime = info.currentTime

    // progress indication
    val toClassify = info.permObjectCount + info.bornObjectCount + info.diedObjectCount + info.tempObjectCount
    var classified = 0L

    override fun backgroundWork(): PermBornDiedTempGrouping {
        val needIndexBasedHeap =
                info.selectedClassifiers.list.any { it.sourceCollection == ClassifierSourceCollection.FASTHEAP } ||
                        info.selectedFilters.any { it.sourceCollection == ClassifierSourceCollection.FASTHEAP }

        val permIndicesInStartHeap = BitSet()
        info.permHeapObjectMap.values.forEach {
            permIndicesInStartHeap.set(it.startIndex)
        }

        val diedIndicesInStartHeap = info.diedStartIndices

        updateTitle("Perm/Born/Died/Temp grouping")
        updateMessage("Classify perm...")
        val permGrouping = groupIndexBasedHeap(permIndicesInStartHeap, info.heapEvolutionInfo.initialIndexBasedHeap) as ListGroupingNode?

        updateMessage("Classify born...")
        val bornGrouping =
                if (needIndexBasedHeap) {
                    val bornIndices = BitSet().also { bornIndices ->
                        info.bornHeapObjectMap.forEach { bornIndices.set(it.value.endIndex) }
                    }
                    groupIndexBasedHeap(bornIndices, info.currentIndexBasedHeap)
                } else {
                    groupDetailedHeap(object : Filter() {
                        override fun classify(): Boolean {
                            return !info.permHeapObjectMap.containsKey(`object`())
                        }
                    }, info.detailedHeap)
                }

        updateMessage("Classify died...")
        val diedGrouping = groupIndexBasedHeap(diedIndicesInStartHeap, info.heapEvolutionInfo.initialIndexBasedHeap) as ListGroupingNode?

        updateMessage("Classify temp...")
        // omit temp classification if we need an index based heap (temp objects dont store enough info for fastheap classifiers and their heaps have already been discarded)
        val tempGrouping: MapGroupingNode?

        if (needIndexBasedHeap) {
            tempGrouping = null
            // TODO give the user some notice that temp is omitted from the classification
        } else {
            val classificationTree = info.tempAgeCollection.classify(
                    info.detailedHeap,
                    info.selectedClassifiers,
                    info.selectedFilters.toTypedArray() + listOf(object : Filter() {
                        // we use an empty filter to make sure that the resulting grouping has a 'filtered' node, just like the perm, born and died groupings
                        // this is necessary to make sure that combining the four groupings (perm, born, died, temp) can be combined into a permborndiedtempgrouping
                        override fun classify() = true
                    }),
                    object : ObjectStream.IterationListener {
                        override fun objectsIterated(objectCount: Long) {
                            classified += objectCount
                            updateProgress(classified, toClassify.toLong())
                        }
                    },
                    true)
            // If root contains "Filtered" node, at least one object was classified
            // Otherwise, not a single object was classified and we return a fake "Filtered" node
            tempGrouping =
                    if (classificationTree.root.containsChild("Filtered")) {
                        classificationTree.root.getChild("Filtered") as MapGroupingNode
                    } else {
                        MapGroupingNode(null, 0, 0, null, "Filtered")
                    }
        }
        return PermBornDiedTempGrouping(permGrouping, bornGrouping, diedGrouping, tempGrouping)
    }

    override fun finished() {
        tab.treeTableView.setRoot(get())
        tab.classificationTaskRunning.set(false)

        if (classificationTaskTime == info.heapEvolutionInfo.endTime) {
            // the diffing has run through
            tab.removeAllButInitialIdeas()
            PermBornDiedTempIdeaGenerator.analyze(tab)
        }
    }

    private fun groupIndexBasedHeap(trackedObjects: BitSet, heap: IndexBasedHeap): GroupingNode? {
        val filters = arrayOf(object : Filter() {
            override fun classify(): Boolean {
                return trackedObjects.get(index())
            }
        }, *info.selectedFilters.toTypedArray())

        val classificationTree = heap.groupListParallel(
                filters,
                info.selectedClassifiers,
                true,
                true,
                object : ObjectStream.IterationListener {
                    override fun objectsIterated(objectCount: Long) {
                        classified += objectCount
                        updateProgress(classified, toClassify.toLong())
                    }
                },
                cancelProperty)

        // If root contains "Filtered" node, at least one object was classified
        // Otherwise, not a single object was classified and we return a fake "Filtered" node
        return if (classificationTree.root.containsChild("Filtered")) {
            classificationTree.root.getChild("Filtered")
        } else {
            ListGroupingNode(null, 0, 0, null, "Filtered")
        }
    }

    private fun groupDetailedHeap(shouldGroupFilter: Filter, heap: DetailedHeap): DetailedHeapGroupingNode {
        val stream = heap.toObjectStream()
        stream.filter(shouldGroupFilter)
        info.selectedFilters.forEach { stream.filter(it) }
        val classificationTree = stream.groupMapParallel(info.selectedClassifiers, true)
        return if (classificationTree.containsChild("Filtered")) classificationTree.getChild("Filtered") else MapGroupingNode(null, 0, 0, null, "Filtered")
    }
}