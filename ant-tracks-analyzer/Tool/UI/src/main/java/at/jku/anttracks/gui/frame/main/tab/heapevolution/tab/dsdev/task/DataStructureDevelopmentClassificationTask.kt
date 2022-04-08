package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.task

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.trees.ListClassificationTree
import at.jku.anttracks.gui.classification.classifier.AddressClassifier
import at.jku.anttracks.gui.classification.filter.OnlyDataStructureHeadsFilter
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.DataStructureDevelopmentInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.tab.classification.DataStructureDevelopmentClassificationTab
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.utils.AntTask
import at.jku.anttracks.gui.utils.ideagenerators.DataStructureDevelopmentIdeaGenerator
import at.jku.anttracks.heap.ObjectStream
import at.jku.anttracks.util.Counter
import javafx.concurrent.Task

class DataStructureDevelopmentClassificationTask(private val info: DataStructureDevelopmentInfo,
                                                 private val classificationTab: DataStructureDevelopmentClassificationTab) : AntTask<ListClassificationTree>() {

    private val startFilters: Array<Filter> = arrayOf(*info.selectedFilters.toTypedArray(),
                                                      object : Filter() {
                                                          override fun classify(): Boolean = info.dataStructuresByStartIndex[index()] != null
                                                      },
                                                      object : Filter() {
                                                          override fun classify(): Boolean {
                                                              val endIndex = info.dataStructuresByStartIndex[index()]?.endIndex ?: -1
                                                              with(info) {
                                                                  return endIndex >= 0 && endIndex.isDataStructureHead() && endIndex.isTopLevelDataStructure()
                                                              }
                                                          }
                                                      })

    private val replaceStartWithEndAddressClassifier: AddressClassifier = object : AddressClassifier() {
        override fun address(): Long = info.dataStructuresByStartIndex.getValue(index()).endAddress
    }

    private val startClassifiers = ClassifierChain(info.selectedClassifiers.list.map { classifier ->
        when (classifier) {
            is AddressClassifier -> replaceStartWithEndAddressClassifier.apply {
                additionalClassifier = classifier.additionalClassifier
                prepend = classifier.prepend
            }
            else -> classifier
        }
    })

    private val endFilters: Array<Filter> = arrayOf(*info.selectedFilters.toTypedArray(),
                                                    OnlyDataStructureHeadsFilter().apply { onlyTopLevelDataStructures = true })

    private val endClassifiers = info.selectedClassifiers

    override fun backgroundWork(): ListClassificationTree {
        updateTitle(String.format("Classifying %,d survived data structures and %,d newly born data structures",
                                  info.dataStructures.count { it.startIndex >= 0 },
                                  info.dataStructures.count { it.startIndex < 0 }))
        val objectIterationCounter = Counter()
        val listener: ObjectStream.IterationListener = object : ObjectStream.IterationListener {
            override fun objectsIterated(objectCount: Long) {
                objectIterationCounter.add(objectCount)
                updateProgress(objectIterationCounter.get(), info.heapEvolutionInfo.endHeap.objectCount.toLong())
            }
        }

        updateMessage("Creating start classification tree...")
        info.startClassificationTree = info.heapEvolutionInfo.startHeap.groupListParallel(startFilters,
                                                                                          startClassifiers,
                                                                                          false,
                                                                                          false,
                                                                                          listener,
                                                                                          cancelProperty)
        updateMessage("Initializing start classification tree...")
        info.startClassificationTree.init(info.heapEvolutionInfo.startHeap, false, false, false, false)

        // Run closure initialization sequentially
        var closureInitTask: Task<*> = info.startClassificationTree.initClosureTask(info.heapEvolutionInfo.startHeap, true, true, true, true)
        ClientInfo.operationManager.addNewOperation(closureInitTask)
        classificationTab.tasks.add(closureInitTask)
        closureInitTask.run()

        updateMessage("Creating end classification tree...")
        info.endClassificationTree = info.heapEvolutionInfo.endHeap.groupListParallel(endFilters,
                                                                                      endClassifiers,
                                                                                      false,
                                                                                      false,
                                                                                      listener,
                                                                                      cancelProperty)
        updateMessage("Initializing end classification tree...")
        info.endClassificationTree.init(info.heapEvolutionInfo.endHeap, false, false, false, false)

        // Run closure initialization sequentially
        closureInitTask = info.endClassificationTree.initClosureTask(info.heapEvolutionInfo.endHeap, true, true, true, true)
        ClientInfo.operationManager.addNewOperation(closureInitTask)
        classificationTab.tasks.add(closureInitTask)
        closureInitTask.run()

        updateMessage("Computing difference between classification trees...")
        info.classificationTreeDifference = info.endClassificationTree.root.subtractRecursive(info.heapEvolutionInfo.endHeap, info.heapEvolutionInfo.startHeap, info.startClassificationTree.root)

        updateMessage("Compute metric growth info for every data structure...")
        info.initDataStructureGrowths()

        return info.endClassificationTree
    }

    override fun finished() {
        classificationTab.treeTableView.setRoot(info.endClassificationTree, false, false) { node ->
            val endAddress = DataStructureDevelopmentChartTask.addressRegex.find(node.key.toString())!!.value.replace(",", "").replace(".", "").toLong()
            info.dataStructures.find { it.endAddress == endAddress }!!.sizeInfo!!.transitiveHeapGrowthPortion > 1.0
        }
        classificationTab.dsDevelopmentTab.chartTab.updateChart()
        DataStructureDevelopmentIdeaGenerator.analyze(classificationTab)
    }
}
