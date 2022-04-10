
package at.jku.anttracks.gui.frame.main.tab.heapstate

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.chart.extjfx.chartpanes.evolution.SimplifiedReducedMemoryChartPaneWithFixedSelection
import at.jku.anttracks.gui.classification.CombinationType
import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapstate.component.combinationselectionlistview.CombinationSelectionPane
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.HeapStateClassificationTab
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.task.HeapStateClassificationTask
import at.jku.anttracks.gui.frame.main.tab.heapstate.task.HeapStateTask
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.model.IAppInfo.ChangeType
import at.jku.anttracks.gui.utils.AntTask
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.util.ParallelizationUtil
import at.jku.anttracks.util.ThreadUtil
import javafx.application.Platform
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox

class HeapStateTab : ApplicationBaseTab() {
    interface HeapStateSubTab {
        fun heapStateChanged()
    }

    @FXML
    private lateinit var outdatedHeapStateHBox: HBox

    @FXML
    private lateinit var outdatedHeapStateLabel: Label

    @FXML
    private lateinit var centerContent: VBox

    @FXML
    private lateinit var outdatedHeapStateButton: Button

    @FXML
    private lateinit var combinationSelectionPane: CombinationSelectionPane

    @FXML
    private lateinit var chart: SimplifiedReducedMemoryChartPaneWithFixedSelection

    // Initialized in init() method
    private lateinit var heapStateInfo: FastHeapInfo

    override val componentDescriptions by lazy {
        combinationSelectionPane.expertConfigurationPane.componentDescriptions
    }
    override val initialTabIdeas by lazy {
        listOf(Idea("Heap state tab",
                    Description("This tab enables you to inspect the heap at the point in time you previously selected.")
                            .linebreak()
                            .appendDefault("Since a heap usually contains a large amount of objects that is hard to make sense of, the heap objects are split into smaller groups using classifiers.")
                            .linebreak()
                            .appendDefault("There are predefined classifier combinations to apply, and an expert mode in which you can select your own classifier combination.")
                            .linebreak()
                            .appendDefault("You can also apply filters to filter out objects you are not interested in.")
                            .linebreak()
                            .appendDefault("There exists a wide range of predefined classifiers and filters, but you can also write your own."),
                    listOf("How do I do all that?" does {
                        combinationSelectionPane.expertConfigurationPane.classifierSelectionPane.isExpanded = true
                        combinationSelectionPane.expertConfigurationPane.filterSelectionPane.isExpanded = true
                        // have to wait a bit until the configuration pane has expanded .....
                        ThreadUtil.runDeferred({ Platform.runLater { showComponentDescriptions() } }, ThreadUtil.DeferredPeriod.LONG)
                        Unit
                    }),
                    null,
                    this))
    }

    init {
        FXMLUtil.load(this, HeapStateTab::class.java)
    }

    fun init(heapStateInfo: FastHeapInfo, task: HeapStateTask?) {
        super.init(heapStateInfo.appInfo,
                   SimpleStringProperty("Heap state"),
                   SimpleStringProperty(" @ " + heapStateInfo.time + "ms"),
                   SimpleStringProperty("Please select a classification combination to group the heap objects.\n" +
                                                "There exists two typical analysis approaches:\n" +
                                                "(1) Bottom up: Search for objects that exists in large quantities and inspect why they are kept alive (e.g., inspect their paths to roots or their " +
                                                "containing data structures).\n" +
                                                "(2) Top down: Search for a few objects (often data structures) that keep alive a lot of other objects (i.e., that have a high retained size)."),
                   Consts.DETAILS_ICON,
                   listOf(
                           ActionTabAction("Apply",
                                           "Apply selected classifier and filter combination",
                                           "Classification",
                                           combinationSelectionPane.validClassifierSelection.and(task?.runningProperty()?.not() ?: SimpleBooleanProperty(true)),
                                           null,
                                           ::acceptEdit),
                           ActionTabAction("Reset",
                                           "Reset expert classifiers and filters to last selection",
                                           "Classification",
                                           SimpleBooleanProperty(true),
                                           null) {
                               combinationSelectionPane.resetExpertUI()
                           }
                   ),
                   true)

        this.heapStateInfo = heapStateInfo

        combinationSelectionPane.init(heapStateInfo.appInfo, heapStateInfo, CombinationType.BOTTOM_UP, CombinationType.TOP_DOWN)

        if (appInfo.statistics.size > 1) {
            // This is the case if we have a AntTracks trace file
            chart.init(ReducedXYChartPane.Companion.Unit.BYTES, appInfo, heapStateInfo.time.toDouble(), heapStateInfo.time.toDouble())
            chart.plot(appInfo)
        } else {
            // In this case we have a HPROF file
            centerContent.children.remove(chart)
        }

        if (task != null) {
            tasks.add(task)
        }
    }

    fun acceptEdit() {
        if (heapStateInfo.fastHeapSupplier.get() != null) {
            combinationSelectionPane.acceptEdit()
            val classificationInfo = HeapStateClassificationInfo(heapStateInfo,
                                                                 SelectedClassifierInfo(
                                                                         combinationSelectionPane.selectedClassifierInfo.selectedClassifiers,
                                                                         combinationSelectionPane.selectedClassifierInfo.selectedFilters))

            val classificationTab = HeapStateClassificationTab().apply {
                init(classificationInfo)
                closeable = true
            }
            ClientInfo.mainFrame.addAndSelectTab(this, classificationTab)

            val classificationTask = HeapStateClassificationTask(classificationInfo, classificationTab)
            ParallelizationUtil.submitTask(classificationTask)
        }
    }

    override fun appInfoChangeAction(type: ChangeType) {
        if (type == ChangeType.NAME) {
            // TODO fix update
            // updateTabTitle();
        }

        if (type == IAppInfo.ChangeType.DATA_STRUCTURE) {
            outdatedHeapStateLabel.text = "Data structure definitions changed! This heap state might be outdated."
            outdatedHeapStateHBox.isVisible = true
            outdatedHeapStateHBox.isManaged = true

            outdatedHeapStateButton.setOnAction { evt ->
                val updateDataStructures = object : AntTask<Void?>() {
                    @Throws(Exception::class)
                    override fun backgroundWork(): Void? {
                        // TODO: DSL datastructures deactivated at the moment
                        // update data structure information in the heap
                        // heapStateInfo.fastHeapSupplier.get()?.initDataStructures(null)
                        return null
                    }

                    override fun finished() {
                        // refresh the classifications/metrics shown in the sub tabs of this HeapStateTab
                        // copy tab list in case new tabs are opened by the handlers
                        // TODO
                        //new ArrayList<>(tabPane.getTabs()).forEach(tab -> {
                        //    if (tab instanceof HeapStateSubTab) {
                        //        ((HeapStateSubTab) tab).heapStateChanged();
                        //    }
                        //});
                        outdatedHeapStateHBox.isVisible = false
                        outdatedHeapStateHBox.isManaged = false
                    }
                }

                tasks.add(updateDataStructures)
                ParallelizationUtil.submitTask(updateDataStructures)
            }
        }
    }

    override fun cleanupOnClose() {
        val heap = heapStateInfo.fastHeapSupplier.get()
        heap?.clear()
    }

    /*
    fun setConfigurationSavepoint() {
        // successful grouping with current filter/classifier configuration
        classifierConfigurationSavepoint = SelectedClassifierInfo(ClassifierChain(configurationPane.classifierSelectionPane.selected), null,
                                                                  configurationPane.filterSelectionPane.selected)
    }

    fun rollbackConfiguration() {
        // restore configuration to previous savepoint
        if (classifierConfigurationSavepoint != null) {
            configurationPane.classifierSelectionPane.resetSelected(classifierConfigurationSavepoint!!.selectedClassifiers.list)
            configurationPane.filterSelectionPane.resetSelected(classifierConfigurationSavepoint!!.selectedFilters)
        }
    }
    */
}
