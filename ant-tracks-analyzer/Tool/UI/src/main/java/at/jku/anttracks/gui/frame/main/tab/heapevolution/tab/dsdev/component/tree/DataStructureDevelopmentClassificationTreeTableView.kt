package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.component.tree

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.classification.nodes.ListGroupingNode
import at.jku.anttracks.classification.trees.ListClassificationTree
import at.jku.anttracks.gui.classification.classifier.LocalClassifier
import at.jku.anttracks.gui.component.treetableview.AutomatedTreeItem
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.DataStructureDevelopmentInfo
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.classificationtreetableview.ClassificationTreeTableView
import at.jku.anttracks.gui.model.ApproximateDouble
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.model.Percentage
import at.jku.anttracks.gui.utils.AntTask
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.ObjectStream
import at.jku.anttracks.util.Counter
import javafx.beans.property.ReadOnlyLongWrapper
import javafx.beans.property.ReadOnlyObjectWrapper
import javafx.beans.value.ObservableValue
import javafx.concurrent.Task
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.control.TreeItem
import javafx.scene.control.TreeTableColumn

class DataStructureDevelopmentClassificationTreeTableView : ClassificationTreeTableView() {

    private val objectCountBeforeColumn = TreeTableColumn<GroupingNode, ApproximateDouble>("Before")
    private val objectCountAfterColumn = TreeTableColumn<GroupingNode, ApproximateDouble>("After")
    private val objectCountAbsoluteGrowthColumn = TreeTableColumn<GroupingNode, ApproximateDouble>("Absolute Growth")
    private val objectCountRelativeGrowthColumn = TreeTableColumn<GroupingNode, Percentage>("Relative Growth [%]")

    private val shallowSizeBeforeColumn = TreeTableColumn<GroupingNode, Number>("Before")
    private val shallowSizeAfterColumn = TreeTableColumn<GroupingNode, Number>("After")
    private val shallowSizeAbsoluteGrowthColumn = TreeTableColumn<GroupingNode, Number>("Absolute Growth")
    private val shallowSizeRelativeGrowthColumn = TreeTableColumn<GroupingNode, Percentage>("Relative Growth [%]")

    private val transitiveSizeBeforeColumn = TreeTableColumn<GroupingNode, Number>("Before")
    private val transitiveSizeAfterColumn = TreeTableColumn<GroupingNode, Number>("After")
    private val transitiveClosureAbsoluteGrowthColumn = TreeTableColumn<GroupingNode, Number>("Absolute Growth")
    private val transitiveClosureRelativeGrowthColumn = TreeTableColumn<GroupingNode, Percentage>("Relative Growth [%]")
    private val transitiveClosureHeapGrowthPortion = TreeTableColumn<GroupingNode, Percentage>()

    private val retainedSizeBeforeColumn = TreeTableColumn<GroupingNode, Number>("Before")
    private val retainedSizeAfterColumn = TreeTableColumn<GroupingNode, Number>("After")
    private val retainedClosureAbsoluteGrowthColumn = TreeTableColumn<GroupingNode, Number>("Absolute Growth")
    private val retainedClosureRelativeGrowthColumn = TreeTableColumn<GroupingNode, Percentage>("Relative Growth [%]")
    private val retainedClosureHeapGrowthPortion = TreeTableColumn<GroupingNode, Percentage>()

    private val dataStructureBeforeColumn = TreeTableColumn<GroupingNode, Number>("Before")
    private val dataStructureAfterColumn = TreeTableColumn<GroupingNode, Number>("After")
    private val dataStructureClosureAbsoluteGrowthColumn = TreeTableColumn<GroupingNode, Number>("Absolute Growth")
    private val dataStructureClosureRelativeGrowthColumn = TreeTableColumn<GroupingNode, Percentage>("Relative Growth [%]")
    private val dataStructureClosureHeapGrowthPortion = TreeTableColumn<GroupingNode, Percentage>()

    private val deepDataStructureBeforeColumn = TreeTableColumn<GroupingNode, Number>("Before")
    private val deepDataStructureAfterColumn = TreeTableColumn<GroupingNode, Number>("After")
    private val deepDataStructureClosureAbsoluteGrowthColumn = TreeTableColumn<GroupingNode, Number>("Absolute Growth")
    private val deepDataStructureClosureRelativeGrowthColumn = TreeTableColumn<GroupingNode, Percentage>("Relative Growth [%]")
    private val deepDataStructureClosureHeapGrowthPortion = TreeTableColumn<GroupingNode, Percentage>()

    private lateinit var info: DataStructureDevelopmentInfo

    fun init(info: DataStructureDevelopmentInfo,
             selectLocalClassifiers: (Classifier<*>) -> Boolean,
             addTab: (ApplicationBaseTab) -> Unit,
             switchToTab: (ApplicationBaseTab) -> Unit,
             showTTVOnScreen: () -> Unit,
             setSelectedClassifiers: (ClassifierChain) -> Unit) {
        super.init(info.heapEvolutionInfo.appInfo, info.heapEvolutionInfo, selectLocalClassifiers, addTab, switchToTab, showTTVOnScreen, setSelectedClassifiers)
        this.info = info

        this.prefHeight = 0.0  // fixes cutoff in configuration pane - tree table shrinks in favor of classifier selection

        val transitiveClosureHeapGrowthPortionColumnHeaderLabel = Label("HGP [%]")
        val retainedClosureHeapGrowthPortionColumnHeaderLabel = Label("HGP [%]")
        val dataStructureClosureHeapGrowthPortionColumnHeaderLabel = Label("HGP [%]")
        val deepDataStructureClosureHeapGrowthPortionColumnHeaderLabel = Label("HGP [%]")
        transitiveClosureHeapGrowthPortionColumnHeaderLabel.tooltip = Tooltip("Heap Growth Portion [%]")
        retainedClosureHeapGrowthPortionColumnHeaderLabel.tooltip = Tooltip("Heap Growth Portion [%]")
        dataStructureClosureHeapGrowthPortionColumnHeaderLabel.tooltip = Tooltip("Heap Growth Portion [%]")
        deepDataStructureClosureHeapGrowthPortionColumnHeaderLabel.tooltip = Tooltip("Heap Growth Portion [%]")
        transitiveClosureHeapGrowthPortion.graphic = transitiveClosureHeapGrowthPortionColumnHeaderLabel
        retainedClosureHeapGrowthPortion.graphic = retainedClosureHeapGrowthPortionColumnHeaderLabel
        dataStructureClosureHeapGrowthPortion.graphic = dataStructureClosureHeapGrowthPortionColumnHeaderLabel
        deepDataStructureClosureHeapGrowthPortion.graphic = deepDataStructureClosureHeapGrowthPortionColumnHeaderLabel

        columns.clear()
        columns.addAll(nameColumn, objectsColumn, retainedSizeColumn, deepDataStructureSizeColumn, shallowSizeColumn, transitiveClosureSizeColumn, dataStructureSizeColumn)
        columns.forEach { topColumn ->
            topColumn.prefWidthProperty().unbind()
            topColumn.columns.forEach { firstChildLevelColumn -> firstChildLevelColumn.prefWidthProperty().unbind() }
        }

        objectCountBeforeColumn.setCellValueFactory { ReadOnlyObjectWrapper(ApproximateDouble(getBefore(info, it, { node -> node.objectCount }).doubleValue(), true)) }
        objectCountAfterColumn.setCellValueFactory { ReadOnlyObjectWrapper(ApproximateDouble(getAfter(info, it, { node -> node.objectCount }).doubleValue(), true)) }
        objectCountAbsoluteGrowthColumn.setCellValueFactory {
            ReadOnlyObjectWrapper(ApproximateDouble(getAbsoluteGrowth(info, it, { node -> node.objectCount }).value.toDouble(),
                                                    true))
        }
        objectCountRelativeGrowthColumn.setCellValueFactory { getRelativeGrowth(info, it, { node -> node.objectCount }) }
        objectsColumn.columns.add(objectCountBeforeColumn)
        objectsColumn.columns.add(objectCountAfterColumn)
        objectsColumn.columns.add(objectCountAbsoluteGrowthColumn)
        objectsColumn.columns.add(objectCountRelativeGrowthColumn)

        shallowSizeBeforeColumn.setCellValueFactory { getBefore(info, it, { node -> node.getByteCount(info.heapEvolutionInfo.startHeap) }) }
        shallowSizeAfterColumn.setCellValueFactory { getAfter(info, it, { node -> node.getByteCount(info.heapEvolutionInfo.endHeap) }) }
        shallowSizeAbsoluteGrowthColumn.setCellValueFactory { getAbsoluteGrowth(info, it, { node -> node.getByteCount(null) }) }
        shallowSizeRelativeGrowthColumn.setCellValueFactory { getRelativeGrowth(info, it, { node -> node.getByteCount(null) }) }
        shallowSizeColumn.columns.add(shallowSizeBeforeColumn)
        shallowSizeColumn.columns.add(shallowSizeAfterColumn)
        shallowSizeColumn.columns.add(shallowSizeAbsoluteGrowthColumn)
        shallowSizeColumn.columns.add(shallowSizeRelativeGrowthColumn)

        transitiveSizeBeforeColumn.setCellValueFactory { getBefore(info, it, { node -> node.transitiveClosureSizeProperty().longValue() }) }
        transitiveSizeAfterColumn.setCellValueFactory { getAfter(info, it, { node -> node.transitiveClosureSizeProperty().longValue() }) }
        transitiveClosureAbsoluteGrowthColumn.setCellValueFactory { getAbsoluteGrowth(info, it, { node -> node.transitiveClosureSizeProperty().longValue() }) }
        transitiveClosureRelativeGrowthColumn.setCellValueFactory { getRelativeGrowth(info, it, { node -> node.transitiveClosureSizeProperty().longValue() }) }
        transitiveClosureHeapGrowthPortion.setCellValueFactory {
            ReadOnlyObjectWrapper(Percentage((getAbsoluteGrowth(info, it, { node -> node.transitiveClosureSizeProperty().longValue() }).value.toLong() * 100.0 / info
                    .heapEvolutionInfo.absoluteHeapGrowth)))
        }
        transitiveClosureSizeColumn.columns.add(transitiveSizeBeforeColumn)
        transitiveClosureSizeColumn.columns.add(transitiveSizeAfterColumn)
        transitiveClosureSizeColumn.columns.add(transitiveClosureAbsoluteGrowthColumn)
        transitiveClosureSizeColumn.columns.add(transitiveClosureRelativeGrowthColumn)
        transitiveClosureSizeColumn.columns.add(transitiveClosureHeapGrowthPortion)

        retainedSizeBeforeColumn.setCellValueFactory { getBefore(info, it, { node -> node.retainedSizeProperty().longValue() }) }
        retainedSizeAfterColumn.setCellValueFactory { getAfter(info, it, { node -> node.retainedSizeProperty().longValue() }) }
        retainedClosureAbsoluteGrowthColumn.setCellValueFactory { getAbsoluteGrowth(info, it, { node -> node.retainedSizeProperty().longValue() }) }
        retainedClosureRelativeGrowthColumn.setCellValueFactory { getRelativeGrowth(info, it, { node -> node.retainedSizeProperty().longValue() }) }
        retainedClosureHeapGrowthPortion.setCellValueFactory {
            ReadOnlyObjectWrapper(
                    Percentage(
                            getAbsoluteGrowth(
                                    info,
                                    it,
                                    { node -> node.retainedSizeProperty().longValue() }).value.toLong() * 100.0 / info.heapEvolutionInfo.absoluteHeapGrowth))
        }
        retainedSizeColumn.columns.add(retainedSizeBeforeColumn)
        retainedSizeColumn.columns.add(retainedSizeAfterColumn)
        retainedSizeColumn.columns.add(retainedClosureAbsoluteGrowthColumn)
        retainedSizeColumn.columns.add(retainedClosureRelativeGrowthColumn)
        retainedSizeColumn.columns.add(retainedClosureHeapGrowthPortion)

        dataStructureBeforeColumn.setCellValueFactory {
            if (it.value.parent == null || it.value.parent === root) {
                getBefore(info, it, { node -> node.dataStructureSizeProperty().longValue() })
            } else {
                null
            }
        }
        dataStructureAfterColumn.setCellValueFactory {
            if (it.value.parent == null || it.value.parent === root) {
                getAfter(info, it, { node -> node.dataStructureSizeProperty().longValue() })
            } else {
                null
            }
        }
        dataStructureClosureAbsoluteGrowthColumn.setCellValueFactory {
            if (it.value.parent == null || it.value.parent === root) {
                getAbsoluteGrowth(info, it, { node -> node.dataStructureSizeProperty().longValue() })
            } else {
                null
            }
        }
        dataStructureClosureRelativeGrowthColumn.setCellValueFactory {
            if (it.value.parent == null || it.value.parent === root) {
                getRelativeGrowth(info, it, { node -> node.dataStructureSizeProperty().longValue() })
            } else {
                null
            }
        }
        dataStructureClosureHeapGrowthPortion.setCellValueFactory {
            if (it.value.parent == null || it.value.parent === root) {
                ReadOnlyObjectWrapper(Percentage((getAbsoluteGrowth(info,
                                                                    it,
                                                                    { node -> node.dataStructureSizeProperty().longValue() }).value.toLong() * 100.0 / info.heapEvolutionInfo.absoluteHeapGrowth)))
            } else {
                null
            }

        }
        dataStructureSizeColumn.columns.add(dataStructureBeforeColumn)
        dataStructureSizeColumn.columns.add(dataStructureAfterColumn)
        dataStructureSizeColumn.columns.add(dataStructureClosureAbsoluteGrowthColumn)
        dataStructureSizeColumn.columns.add(dataStructureClosureRelativeGrowthColumn)
        dataStructureSizeColumn.columns.add(dataStructureClosureHeapGrowthPortion)

        deepDataStructureBeforeColumn.setCellValueFactory {
            if (it.value.parent == null || it.value.parent === root) {
                getBefore(info, it, { node -> node.deepDataStructureSizeProperty().longValue() })
            } else {
                null
            }
        }
        deepDataStructureAfterColumn.setCellValueFactory {
            if (it.value.parent == null || it.value.parent === root) {
                getAfter(info, it, { node -> node.deepDataStructureSizeProperty().longValue() })
            } else {
                null
            }
        }
        deepDataStructureClosureAbsoluteGrowthColumn.setCellValueFactory {
            if (it.value.parent == null || it.value.parent === root) {
                getAbsoluteGrowth(info, it, { node -> node.deepDataStructureSizeProperty().longValue() })
            } else {
                null
            }
        }
        deepDataStructureClosureRelativeGrowthColumn.setCellValueFactory {
            if (it.value.parent == null || it.value.parent === root) {
                getRelativeGrowth(info, it, { node -> node.deepDataStructureSizeProperty().longValue() })
            } else {
                null
            }
        }
        deepDataStructureClosureHeapGrowthPortion.setCellValueFactory {
            if (it.value.parent == null || it.value.parent === root) {
                ReadOnlyObjectWrapper(Percentage((getAbsoluteGrowth(info,
                                                                    it,
                                                                    { node ->
                                                                        node.deepDataStructureSizeProperty()
                                                                                .longValue()
                                                                    }).value.toLong() * 100.0 / info.heapEvolutionInfo.absoluteHeapGrowth)))
            } else {
                null
            }

        }
        deepDataStructureSizeColumn.columns.add(deepDataStructureBeforeColumn)
        deepDataStructureSizeColumn.columns.add(deepDataStructureAfterColumn)
        deepDataStructureSizeColumn.columns.add(deepDataStructureClosureAbsoluteGrowthColumn)
        deepDataStructureSizeColumn.columns.add(deepDataStructureClosureRelativeGrowthColumn)
        deepDataStructureSizeColumn.columns.add(deepDataStructureClosureHeapGrowthPortion)

        // show all columns
        columns.forEach { topColumn ->
            topColumn.prefWidthProperty().unbind()
            topColumn.isVisible = true
            topColumn.columns.forEach { firstChildLevelColumn ->
                firstChildLevelColumn.prefWidthProperty().unbind()
                firstChildLevelColumn.setVisible(true)
            }
        }
        // hide less interesting columns
        nameColumn.isVisible = true

        objectsColumn.isVisible = true
        objectCountBeforeColumn.isVisible = true
        objectCountAfterColumn.isVisible = true
        objectCountAbsoluteGrowthColumn.isVisible = false
        objectCountRelativeGrowthColumn.isVisible = false

        shallowSizeColumn.isVisible = true
        shallowSizeBeforeColumn.isVisible = false
        shallowSizeAfterColumn.isVisible = false
        shallowSizeAbsoluteGrowthColumn.isVisible = true
        shallowSizeRelativeGrowthColumn.isVisible = false

        transitiveClosureSizeColumn.isVisible = true
        transitiveSizeBeforeColumn.isVisible = false
        transitiveSizeAfterColumn.isVisible = false
        transitiveClosureAbsoluteGrowthColumn.isVisible = true
        transitiveClosureRelativeGrowthColumn.isVisible = false
        transitiveClosureHeapGrowthPortion.isVisible = true

        retainedSizeColumn.isVisible = true
        retainedSizeBeforeColumn.isVisible = false
        retainedSizeAfterColumn.isVisible = false
        retainedClosureAbsoluteGrowthColumn.isVisible = true
        retainedClosureRelativeGrowthColumn.isVisible = false
        retainedClosureHeapGrowthPortion.isVisible = true

        dataStructureSizeColumn.isVisible = true
        dataStructureBeforeColumn.isVisible = false
        dataStructureAfterColumn.isVisible = false
        dataStructureClosureAbsoluteGrowthColumn.isVisible = true
        dataStructureClosureRelativeGrowthColumn.isVisible = false
        dataStructureClosureHeapGrowthPortion.isVisible = true

        deepDataStructureSizeColumn.isVisible = true
        deepDataStructureBeforeColumn.isVisible = false
        deepDataStructureAfterColumn.isVisible = false
        deepDataStructureClosureAbsoluteGrowthColumn.isVisible = true
        deepDataStructureClosureRelativeGrowthColumn.isVisible = false
        deepDataStructureClosureHeapGrowthPortion.isVisible = true

        // set column widths
        nameColumn.prefWidthProperty().bind(widthProperty().subtract(20).divide(5.0).multiply(2.0))

        objectsColumn.prefWidthProperty().bind(widthProperty().divide(5.0))
        objectCountBeforeColumn.prefWidthProperty().bind(objectsColumn.prefWidthProperty().divide(2))
        objectCountAfterColumn.prefWidthProperty().bind(objectsColumn.prefWidthProperty().divide(2))

        shallowSizeColumn.prefWidthProperty().bind(widthProperty().divide(5.0))
        shallowSizeAbsoluteGrowthColumn.prefWidthProperty().bind(shallowSizeColumn.prefWidthProperty())

        transitiveClosureSizeColumn.prefWidthProperty().bind(widthProperty().divide(5.0))
        transitiveClosureAbsoluteGrowthColumn.prefWidthProperty().bind(transitiveClosureSizeColumn.prefWidthProperty().divide(2))
        transitiveClosureHeapGrowthPortion.prefWidthProperty().bind(transitiveClosureSizeColumn.prefWidthProperty().divide(2))

        retainedSizeColumn.prefWidthProperty().bind(widthProperty().divide(5.0))
        retainedClosureAbsoluteGrowthColumn.prefWidthProperty().bind(retainedSizeColumn.prefWidthProperty().divide(2))
        retainedClosureHeapGrowthPortion.prefWidthProperty().bind(retainedSizeColumn.prefWidthProperty().divide(2))

        dataStructureSizeColumn.prefWidthProperty().bind(widthProperty().divide(5.0))
        dataStructureClosureAbsoluteGrowthColumn.prefWidthProperty().bind(dataStructureSizeColumn.prefWidthProperty().divide(2))
        dataStructureClosureHeapGrowthPortion.prefWidthProperty().bind(dataStructureSizeColumn.prefWidthProperty().divide(2))

        deepDataStructureSizeColumn.prefWidthProperty().bind(widthProperty().divide(5.0))
        deepDataStructureClosureAbsoluteGrowthColumn.prefWidthProperty().bind(deepDataStructureSizeColumn.prefWidthProperty().divide(2))
        deepDataStructureClosureHeapGrowthPortion.prefWidthProperty().bind(deepDataStructureSizeColumn.prefWidthProperty().divide(2))
    }

    private fun getBefore(info: DataStructureDevelopmentInfo,
                          param: TreeTableColumn.CellDataFeatures<GroupingNode, *>,
                          valueExtractor: (GroupingNode) -> Long): ReadOnlyLongWrapper {
        val key = param.value.value.fullKey
        val matchingChild = info.startClassificationTree.root.getChildWithFullKey(key) ?: return ReadOnlyLongWrapper(0)
        return ReadOnlyLongWrapper(valueExtractor(matchingChild))
    }

    private fun getAfter(info: DataStructureDevelopmentInfo,
                         param: TreeTableColumn.CellDataFeatures<GroupingNode, *>,
                         valueExtractor: (GroupingNode) -> Long): ReadOnlyLongWrapper {
        val key = param.value.value.fullKey
        val matchingChild = info.endClassificationTree.root.getChildWithFullKey(key) ?: return ReadOnlyLongWrapper(0)
        return ReadOnlyLongWrapper(valueExtractor(matchingChild))
    }

    private fun getAbsoluteGrowth(info: DataStructureDevelopmentInfo,
                                  param: TreeTableColumn.CellDataFeatures<GroupingNode, *>,
                                  valueExtractor: (GroupingNode) -> Long): ObservableValue<Number> {
        val key = param.value.value.fullKey
        val matchingChild = info.classificationTreeDifference.getChildWithFullKey(key)
        if (matchingChild == null) {
            // TODO MW: Do something here, this should not happen...
            // EG: this happens sometimes on local classification when the table wants to update cells but the underlying grouping tree changed already (local classification), thus we simply ignore it
            return ReadOnlyLongWrapper(0)
        }
        return ReadOnlyLongWrapper(valueExtractor(matchingChild))
    }

    private fun getRelativeGrowth(info: DataStructureDevelopmentInfo,
                                  param: TreeTableColumn.CellDataFeatures<GroupingNode, *>,
                                  valueExtractor: (GroupingNode) -> Long): ObservableValue<Percentage> {
        val startNode = info.startClassificationTree.root.getChildWithFullKey(param.value.value.fullKey)
        return if (startNode == null) {
            // born data structure has no start node
            ReadOnlyObjectWrapper(Percentage(-1.0))
        } else {
            var growth = getAbsoluteGrowth(info, param, valueExtractor).value.toFloat() * 100.0f
            growth /= valueExtractor(startNode).toFloat()
            ReadOnlyObjectWrapper(Percentage(growth.toDouble()))
        }
    }

    override fun localClassificationTask(selectedItem: TreeItem<GroupingNode>,
                                         node: ListGroupingNode,
                                         classifierChain: ClassifierChain,
                                         targetSubTreeLevel: Int,
                                         completionCallback: Runnable?): Task<Void?> {
        return object : AntTask<Void?>() {
            override fun backgroundWork(): Void? {
                updateTitle("Local classification")
                updateMessage(String.format("%,d objects in Node %s with:/n",
                                            node.objectCount,
                                            node.fullKey,
                                            (if (classifierChain.get(0) is LocalClassifier)
                                                (classifierChain.get(0) as LocalClassifier).classifiers.toString()
                                            else
                                                classifierChain.toString())))

                val objectsProcessed = Counter()
                val iterationListener = object : ObjectStream.IterationListener {
                    override fun objectsIterated(objectCount: Long) {
                        objectsProcessed.add(objectCount)
                        updateProgress(objectsProcessed.get(), node.objectCount)
                    }
                }

                val classifyLocalFunc: (ListGroupingNode, IndexBasedHeap) -> Unit =
                        if (node.subTreeLevel == 0) { node, heap -> node.locallyClassify(heap, classifierChain, iterationListener, cancelProperty) } // regular node
                        else { node, heap -> node.locallyClassifyTransformerOnSameSubTreeLevel(heap, classifierChain, iterationListener, cancelProperty) } // transformer node

                // 1. classify end tree
                val endHeap = info.heapEvolutionInfo.endHeap
                classifierChain.list
                        .filter { it != null }
                        .forEach { it.setup({ endHeap.symbols }, { endHeap }) }
                classifyLocalFunc.invoke(node, endHeap)
                val endTreeUpdateClosuresTask =
                        ListClassificationTree(node, arrayOf(), classifierChain)
                                .initClosureTask(endHeap,
                                                 true,
                                                 true,
                                                 true,
                                                 true)
                ClientInfo.operationManager.addNewOperation(endTreeUpdateClosuresTask)
                endTreeUpdateClosuresTask.run()

                // 2. classify start tree
                val startHeap = info.heapEvolutionInfo.startHeap
                classifierChain.list
                        .filter { it != null }
                        .forEach { it.setup({ startHeap.symbols }, { startHeap }) }
                val key = node.fullKey
                val matchingStartChild: ListGroupingNode? = info.startClassificationTree.root.getChildWithFullKey(key) as? ListGroupingNode
                if (matchingStartChild != null) {
                    classifyLocalFunc.invoke(matchingStartChild, startHeap)
                    val startTreeUpdateClosuresTask = ListClassificationTree(matchingStartChild,
                                                                             arrayOf(),
                                                                             classifierChain).initClosureTask(startHeap,
                                                                                                              true,
                                                                                                              true,
                                                                                                              true,
                                                                                                              true)
                    ClientInfo.operationManager.addNewOperation(startTreeUpdateClosuresTask)
                    startTreeUpdateClosuresTask.run()
                }

                // 3. update diff tree
                info.classificationTreeDifference = info.endClassificationTree.root.subtractRecursive(endHeap,
                                                                                                      startHeap,
                                                                                                      info.startClassificationTree.root)

                return null
            }

            override fun finished() {
                // display result of local classification
                val newItem: TreeItem<GroupingNode>
                // if the classified item was selected we'll have to reselect the new item later
                val reselect = selectionModel.isSelected(getRow(selectedItem))
                // unselect the classified item because otherwise removing it from the treetable causes weird selection behaviour
                selectionModel.clearSelection(getRow(selectedItem))

                if (root === selectedItem) {
                    // the overall node is reclassified
                    setRoot(ListClassificationTree(node, classificationTree.filters, classifierChain),
                            true,
                            true,
                            null)
                    newItem = root

                    // TODO Probably pass function setSelectedClassifier
                    //setSelectedClassifiers.accept(classifierChain)
                } else {
                    // some descendant of overall is locally classified
                    newItem = AutomatedTreeItem(node, titleFunction, dataFunction, childFunction, subTreeLevelFunction, iconFunction, expansionListener)
                    val selectionParent = selectedItem.parent
                    val idx = selectionParent.children.indexOf(selectedItem)
                    selectionParent.children.removeAt(idx)
                    selectionParent.children.add(idx, newItem)
                }

                if (reselect) {
                    selectionModel.select(getRow(newItem))
                }
                // expand the new node to show the results of the classification
                newItem.isExpanded = true

                completionCallback?.run()
            }
        }
    }

    fun sortByAbsoluteRetainedSizeGrowth() {
        val sortColum = if (sortOrder.isEmpty()) null else sortOrder[0]
        if (sortColum == null || sortColum !== retainedClosureAbsoluteGrowthColumn || sortColum.sortType != TreeTableColumn.SortType.DESCENDING) {
            // sort by absolute retained size growth
            sortOrder.clear()
            sortOrder.add(retainedClosureAbsoluteGrowthColumn)
            sortOrder[0].sortType = TreeTableColumn.SortType.DESCENDING
            sortOrder[0].isSortable = true
            sort()
        }
    }
}