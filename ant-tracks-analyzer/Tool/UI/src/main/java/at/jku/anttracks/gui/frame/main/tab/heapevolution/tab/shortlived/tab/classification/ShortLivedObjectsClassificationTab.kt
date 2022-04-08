package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.tab.classification

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.trees.ClassificationTree
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.component.configurationpane.ShortLivedObjectsClassificationConfigurationPane
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.component.tree.ShortLivedObjectsClassificationTreeTableView
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.model.ShortLivedObjectsInfo
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.model.IAppInfo
import at.jku.anttracks.gui.model.Idea
import at.jku.anttracks.gui.model.does
import at.jku.anttracks.gui.utils.AntTask
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.ideagenerators.ShortLivedObjectsAnalysisIdeaGenerator
import at.jku.anttracks.heap.ObjectStream
import at.jku.anttracks.util.ThreadUtil
import com.sun.javafx.scene.control.skin.TableColumnHeader
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import org.controlsfx.control.PopOver

class ShortLivedObjectsClassificationTab : ApplicationBaseTab() {

    @FXML
    lateinit var classificationConfigurationPane: ShortLivedObjectsClassificationConfigurationPane
        private set

    @FXML
    lateinit var treeTableView: ShortLivedObjectsClassificationTreeTableView

    override val componentDescriptions by lazy {
        classificationConfigurationPane.componentDescriptions +
                listOf(Triple(treeTableView,
                              Description("The table shows you all objects that have been garbage collected over this timeframe, grouped according to the selected classifiers."),
                              PopOver.ArrowLocation.RIGHT_BOTTOM),
                       Triple(treeTableView.lookupAll(".column-header").find { (it as TableColumnHeader).childrenUnmodifiable.first().toString().contains("objects") }!!,
                              Description("This column contains the average number of objects that have been discarded during each garbage collection."),
                              PopOver.ArrowLocation.BOTTOM_CENTER),
                       Triple(treeTableView.lookupAll(".column-header").find { (it as TableColumnHeader).childrenUnmodifiable.first().toString().contains("memory") }!!,
                              Description("This column contains the average number of bytes that have been discarded during each garbage collection."),
                              PopOver.ArrowLocation.BOTTOM_CENTER))
    }

    override val initialTabIdeas by lazy {
        listOf(Idea("Short-lived objects classification tab",
                    Description("This tab shows you all objects that have been garbage collected over the previously selected timeframe.")
                            .linebreak()
                            .appendDefault("Since this is usually a very large amount of objects that is hard to make sense of, AntTracks now grouped them by their type. ")
                            .appendDefault("You can modify this grouping using our predefined classifiers or even by writing your own classifiers!"),
                    listOf("How do I do all that?" does {
                        if (!classificationConfigurationPane.isConfigureMode) {
                            classificationConfigurationPane.switchMode()
                        }
                        // have to wait a bit until config pane is in max mode.....
                        ThreadUtil.runDeferred({ Platform.runLater { showComponentDescriptions() } }, ThreadUtil.DeferredPeriod.LONG)
                        Unit
                    }),
                    null,
                    this))
    }

    lateinit var info: ShortLivedObjectsInfo

    init {
        FXMLUtil.load(this, ShortLivedObjectsClassificationTab::class.java)
    }

    fun init(info: ShortLivedObjectsInfo) {
        super.init(info.heapEvolutionInfo.appInfo,
                   SimpleStringProperty("Collected objects"),
                   SimpleStringProperty("Classify garbage collected objects"),
                   SimpleStringProperty("Analyse all objects that have been garbage collected during the selected time window and inspect them with respect to average live time."),
                   Consts.TABLE_ICON,
                   listOf(),
                   false)
        this.info = info

        classificationConfigurationPane.init(info, this)
        treeTableView.init(info)
    }

    fun updateClassification(completionCallback: () -> Unit = {}) {
        val classificationTask = object : AntTask<ClassificationTree>() {
            override fun backgroundWork(): ClassificationTree {
                updateTitle("Classifying garbage...")
                treeTableView.isDisable = true
                classificationConfigurationPane.isDisable = true
                val classifiers = ClassifierChain(classificationConfigurationPane.classifierSelectionPane.selected)
                info.selectedClassifiers = classifiers
                updateMessage(classifiers.toString())
                var progress = 0L
                val grouping = info.garbageObjectAgeCollection.classify(
                        info.heapEvolutionInfo.detailedHeap,
                        classifiers,
                        classificationConfigurationPane.filterSelectionPane.selected.toTypedArray(),
                        object : ObjectStream.IterationListener {
                            override fun objectsIterated(objectCount: Long) {
                                progress += objectCount
                                updateProgress(progress, info.garbageObjectAgeCollection.objectCount.toLong())
                            }
                        },
                        true)
                // null because map grouping does not use heap for sampling
                if (grouping.root.getChild("Filtered") == null) {
                    grouping.root.sampleTopDown(null)
                } else {
                    grouping.root.getChild("Filtered").sampleTopDown(null)
                }
                // TODO question by mw: do we need sampling here?
                // seems like we do not use default trees at all here?
                return grouping
            }

            override fun finished() {
                treeTableView.setRoot(get(), false, false, null)
                treeTableView.isDisable = false
                classificationConfigurationPane.isDisable = false
                this@ShortLivedObjectsClassificationTab.removeAllButInitialIdeas()
                ShortLivedObjectsAnalysisIdeaGenerator.analyzeClassificationTab(this@ShortLivedObjectsClassificationTab)

                completionCallback.invoke()
            }
        }

        tasks.add(classificationTask)
        ThreadUtil.startTask(classificationTask)
    }

    override fun cleanupOnClose() {}

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {}

    fun showTabAndHighlight(typeClassifierChain: Boolean, grouping: ClassificationTree, name: String, sortByObjects: Boolean) {
        if (typeClassifierChain) {
            classificationConfigurationPane.selectTypeClassifierChain()
        } else {
            classificationConfigurationPane.selectAllocSiteClassifierChain()
        }

        treeTableView.setRoot(grouping, false, false, null)

        if (sortByObjects) {
            treeTableView.sortByObjects()
        } else {
            treeTableView.sortByBytes()
        }

        removeAllButInitialIdeas()
        ShortLivedObjectsAnalysisIdeaGenerator.analyzeClassificationTab(this)

        if (name != "Other") {
            val matches = treeTableView.getMatchingTopLevelItems(name)
            if (matches.isNotEmpty()) {
                // for types, expand only first match, for allocation sites expand all (top alloc sites are grouped
                if (typeClassifierChain) {
                    matches.first().isExpanded = true
                } else {
                    matches.forEach {
                        it.isExpanded = true
                    }
                }
                treeTableView.selectionModel.select(matches.first())
            }
        }

        parentTab!!.tabbedPane!!.select(this)
    }

}