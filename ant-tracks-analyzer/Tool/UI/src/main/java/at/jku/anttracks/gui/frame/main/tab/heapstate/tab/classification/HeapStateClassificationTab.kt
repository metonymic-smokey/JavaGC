
package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierFactory
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.FilterFactory
import at.jku.anttracks.classification.trees.ListClassificationTree
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane
import at.jku.anttracks.gui.classification.dialog.properties.ClassificationPropertiesDialog
import at.jku.anttracks.gui.classification.filter.OnlyDataStructureHeadsFilter
import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.frame.main.component.applicationbase.WebSocketEnabledTab
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.classificationtreetableview.ClassificationTreeTableView
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.configurationpane.SelectedClassifiersConfigurationPane
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.heapmetricstable.HeapMetricsTable
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.task.HeapStateClassificationTask
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.util.Counter
import at.jku.anttracks.util.ParallelizationUtil
import com.google.gson.JsonObject
import com.sun.javafx.scene.control.skin.TableColumnHeader
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import javafx.stage.DirectoryChooser
import javafx.stage.FileChooser
import org.controlsfx.control.PopOver
import java.io.File

private val jsonFilter = FileChooser.ExtensionFilter("JSON", "*.json")
private val homeDir = File(System.getProperty("user.home"))

class HeapStateClassificationTab : WebSocketEnabledTab() {

    @FXML
    lateinit var configurationPane: SelectedClassifiersConfigurationPane

    @FXML
    lateinit var treeTableView: ClassificationTreeTableView

    @FXML
    lateinit var heapMetricsTable: HeapMetricsTable
        private set

    override val componentDescriptions by lazy {
        configurationPane.componentDescriptions +
                listOf(Triple(treeTableView.lookupAll(".column-header").find { (it as TableColumnHeader).childrenUnmodifiable.first().toString().contains("Shallow") }!!,
                              Description("The shallow size tells you how much memory an object group occupies"),
                              PopOver.ArrowLocation.TOP_CENTER),
                       Triple(treeTableView.lookupAll(".column-header").find { (it as TableColumnHeader).childrenUnmodifiable.first().toString().contains("Retained") }!!,
                              Description("The retained size tells you how much memory an object group keeps alive (how much memory could be freed if all objects in this group were removed)"),
                              PopOver.ArrowLocation.TOP_CENTER),
                       Triple(treeTableView,
                              Description("The table shows you all objects in this heap state grouped according to the selected classifiers.")
                                      .linebreak()
                                      .appendDefault("There is also a context menu! Try it by right-clicking in any row."),
                              PopOver.ArrowLocation.TOP_CENTER),
                       Triple(ClientInfo.mainFrame.mainTabbedPane.actionPanels,
                              Description("Each row in the table represents a group of one or more objects.")
                                      .linebreak()
                                      .appendDefault("In here you have you multiple actions that you can apply to the currently selected object group in the table.")
                                      .linebreak()
                                      .appendDefault("You can also select multiple rows in the table by pressing CTRL while clicking!"),
                              PopOver.ArrowLocation.LEFT_CENTER))
    }
    override val initialTabIdeas by lazy {
        listOf(Idea("Heap state classification tab",
                    Description("This tab shows you the composition of the heap at the point in time you previously selected.")
                            .linebreak()
                            .appendDefault("Since a heap usually contains a large amount of objects that is hard to make sense of, AntTracks now grouped them according to your selected classifiers."),
                    listOf("I don't understand this table!" does { showComponentDescriptions() },
                           "Change selected classifiers" does { ClientInfo.mainFrame.selectTab(parentTab) }),
                    null,
                    this))
    }

    lateinit var statisticsInfo: HeapStateClassificationInfo

    init {
        FXMLUtil.load(this, HeapStateClassificationTab::class.java)
    }

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {
        // TODO
    }

    fun init(statisticsInfo: HeapStateClassificationInfo) {
        val classifiers = statisticsInfo.selectedClassifierInfo.selectedClassifiers.list
        val filters = statisticsInfo.selectedClassifierInfo.selectedFilters.filter { it.name != OnlyDataStructureHeadsFilter.NAME }
        val classifierText = "Grouped by: " + classifiers.joinToString(" -> ") { "'${it.name}'" }
        val filterText = if (filters.isNotEmpty()) {
            "\nFiltered by: " + filters.joinToString(" and ") { "'${it.name}'" }
        } else {
            ""
        }

        super.init(statisticsInfo.heapStateInfo.appInfo,
                   SimpleStringProperty("Classification"),
                   SimpleStringProperty(classifierText + filterText),
                   SimpleStringProperty("Inspect object groups"),
                   Consts.TABLE_ICON,
                   listOf(ActionTabAction("Export as JSON",
                                          "Writes current classification tree as JSON to disc.",
                                          "Utility",
                                          SimpleBooleanProperty(true),
                                          null,
                                          ::exportAsJSON)) + treeTableView.actions,
                   false)

        this.statisticsInfo = statisticsInfo
        initializeConfigurationPane()
        initializeTable()
        initializeMetricsTable()
        initDataStructureSwitch()
    }

    private fun exportAsJSON() {
        val heap = statisticsInfo.heapStateInfo.fastHeapSupplier.get()
        if (heap != null) {
            val fileChooser = DirectoryChooser()
            fileChooser.title = "Save JSON"
            fileChooser.initialDirectory = homeDir

            val dir = fileChooser.showDialog(ClientInfo.stage)
            if (dir != null) {
                statisticsInfo.grouping?.root?.exportAsJSON(heap,
                                                            statisticsInfo.selectedClassifierInfo.selectedClassifiers,
                                                            statisticsInfo.heapStateInfo.time,
                                                            dir.resolve("${statisticsInfo.heapStateInfo.appInfo.appName}-anttracks-format.json"),
                                                            dir.resolve("${statisticsInfo.heapStateInfo.appInfo.appName}-default-format.json"))
            }
        }
    }

    private fun initializeConfigurationPane() {
        configurationPane.init(statisticsInfo.heapStateInfo.appInfo,
                               statisticsInfo.heapStateInfo,
                               object : ClassificationSelectionPane.ClassificationSelectionListener<Classifier<*>, ClassifierFactory> {
                                   override fun selected(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>?, x: Classifier<*>?) {

                                   }

                                   override fun deselected(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>?, x: Classifier<*>?) {

                                   }

                                   override fun propertiesChanged(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>?, x: Classifier<*>?) {
                                       // Reclassifiy if properties of classifiers change
                                       reclassify()
                                   }

                               },
                               object : ClassificationSelectionPane.ClassificationSelectionListener<Filter, FilterFactory> {
                                   override fun selected(sender: ClassificationSelectionPane<Filter, FilterFactory>?, x: Filter?) {

                                   }

                                   override fun deselected(sender: ClassificationSelectionPane<Filter, FilterFactory>?, x: Filter?) {

                                   }

                                   override fun propertiesChanged(sender: ClassificationSelectionPane<Filter, FilterFactory>?, x: Filter?) {
                                       // Reclassifiy if properties of classifiers change
                                       reclassify()
                                   }
                               })
        configurationPane.filterSelectionPane.resetSelected(statisticsInfo.selectedClassifierInfo.selectedFilters.filter { it.name != OnlyDataStructureHeadsFilter.NAME })
        configurationPane.classifierSelectionPane.resetSelected(statisticsInfo.selectedClassifierInfo.selectedClassifiers.list)
        configurationPane.switchToAnalysisMode()
    }

    private fun initializeTable() {
        treeTableView.init(appInfo,
                           configurationPane.availableClassifierInfo,
                           { classifier -> ClassificationPropertiesDialog.showDialogForClassifier(classifier, statisticsInfo.heapStateInfo) },
                           { tab ->
                               if (!childTabs.contains(tab)) {
                                   childTabs.add(tab)
                               }
                           },
                           { tab -> ClientInfo.mainFrame.selectTab(tab) },
                           { ClientInfo.mainFrame.selectTab(this) },
                           { newClassifierChain -> configurationPane.classifierSelectionPane.resetSelected(newClassifierChain.list) })
    }

    private fun initDataStructureSwitch() {
        // set initial state of DS switch according to given filters
        val onlyTopLevelDSHeadsFilter = statisticsInfo.heapStateInfo.availableFilter[OnlyDataStructureHeadsFilter.NAME]
        if (statisticsInfo.selectedClassifierInfo.selectedFilters.contains(onlyTopLevelDSHeadsFilter)) {
            configurationPane.dataStructureSwitch.isSelected = true
            // hidden filter is applied now
        }

        configurationPane.dataStructureSwitch.selectedProperty().addListener { _, wasSelected, isSelected ->
            if (wasSelected!! != isSelected!!) {
                // take currently selected classifiers and filters (including hidden ds heads filter)
                // run grouping with modified filters
                statisticsInfo.selectedClassifierInfo.selectedFilters = configurationPane.filterSelectionPane.selected
                treeTableView.dataStructureSizeColumn.isVisible = isSelected
                treeTableView.deepDataStructureSizeColumn.isVisible = isSelected
                treeTableView.shallowSizeColumn.isVisible = !isSelected
                reclassify()
            }
        }
    }

    private fun initializeMetricsTable() {
        heapMetricsTable.init()
    }

    fun updateGrouping(classificationTree: ListClassificationTree) {
        statisticsInfo.grouping = classificationTree
        treeTableView.setRoot(statisticsInfo.grouping, false, false, null)

        heapMetricsTable.treeNodesMetric.valueProperty.set(treeTableView.classificationTree.getnNodes().toDouble())
        heapMetricsTable.dataTreeNodesMetric.valueProperty.set(treeTableView.classificationTree.getnDataNodes().toDouble())
        heapMetricsTable.treeNodeDataCollectionPartsMetric.valueProperty.set(treeTableView.classificationTree.getnDataCollectionParts().toDouble())

        heapMetricsTable.avgTreeNodeDataCollectionPartsPerNodeMetric.valueProperty.set(treeTableView.classificationTree.avgTreeNodeDataCollectionPartsPerNode)
        heapMetricsTable.avgTreeNodeDataCollectionPartsPerDataNodeMetric.valueProperty.set(treeTableView.classificationTree.avgTreeNodeDataCollectionPartsPerDataNode)
        heapMetricsTable.avgObjectsPerNodeMetric.valueProperty.set(treeTableView.classificationTree.avgObjectsPerNode)
        heapMetricsTable.avgObjectsPerDataNodeMetric.valueProperty.set(treeTableView.classificationTree.avgObjectsPerDataNode)
        heapMetricsTable.avgObjectsPerTreeNodeDataCollectionPartMetric.valueProperty.set(treeTableView.classificationTree.avgObjectsPerTreeNodeDataCollectionPart)

        // updateMessage("Calculate heap metrics");
        val heap = statisticsInfo.heapStateInfo.fastHeapSupplier.get()
        if (heap != null) {
            heapMetricsTable.nObjects.valueProperty.set(heap.objectCount.toDouble())
            if (heap.symbols.expectPointers) {
                val nToPointers = Counter()
                heap.stream().forEach { nToPointers.add(heap.getToPointers(it)?.size ?: 0) }
                val nToPointersWithoutNull = Counter()
                heap.stream().forEach { objIndex ->
                    val toPointers = heap.getToPointers(objIndex)
                    if (toPointers != null) {
                        for (toPointer in toPointers) {
                            if (toPointer != IndexBasedHeap.NULL_INDEX) {
                                nToPointersWithoutNull.inc()
                            }
                        }
                    }
                }
                val nFromPointers = Counter()
                heap.stream().forEach { objIndex ->
                    val fromPointers = heap.getFromPointers(objIndex)
                    nFromPointers.add(fromPointers?.size ?: 0)
                }

                heapMetricsTable.nToPointersMetric.valueProperty.set(nToPointers.get().toDouble())
                heapMetricsTable.nToPointersWithoutNullMetric.valueProperty.set(nToPointersWithoutNull.get().toDouble())
                heapMetricsTable.nFromPointersMetric.valueProperty.set(nFromPointers.get().toDouble())
                heapMetricsTable.nRootPointers.valueProperty.set(heap.rootPointerList.size.toDouble())
            }
        }
    }

    fun switchToDataStructureView() {
        configurationPane.dataStructureSwitch.isSelected = true
    }

    fun isInDataStructureView() = configurationPane.dataStructureSwitch.isSelected

    private fun reclassify() {
        val groupingTask = HeapStateClassificationTask(statisticsInfo, this)
        tasks.add(groupingTask)
        ParallelizationUtil.submitTask(groupingTask)
    }

    override fun cleanupOnClose() {

    }

    override val webSocketHandlers: List<WebSocketCapabilityHandler> by lazy {
        listOf(
                WebSocketCapabilityHandler(WebSocketCapability.GET_SINGLE_TREE) {
                    if (statisticsInfo.grouping != null) {
                        statisticsInfo.grouping?.root?.asAntTracksJSON(statisticsInfo.heapStateInfo.fastHeapSupplier.get(),
                                                                       statisticsInfo.grouping?.classifiers,
                                                                       statisticsInfo.heapStateInfo.time)
                    } else {
                        JsonObject()
                    }
                },
                WebSocketCapabilityHandler(WebSocketCapability.GET_SINGLE_TREE_POINTER_MAP) { parameters ->
                    if (statisticsInfo.grouping != null) {
                        val key = parameters!![0].toString()
                        val pointerMap = statisticsInfo.grouping!!.calculatePointerMapOf(key, statisticsInfo.heapStateInfo.fastHeapSupplier.get()!!)
                        JsonObject().also { json ->
                            pointerMap.entries.forEach { (node, pointedAmount) ->
                                json.addProperty(node.fullKeyAsString, pointedAmount);
                            }
                        }
                    } else {
                        JsonObject()
                    }
                })
    }
}
