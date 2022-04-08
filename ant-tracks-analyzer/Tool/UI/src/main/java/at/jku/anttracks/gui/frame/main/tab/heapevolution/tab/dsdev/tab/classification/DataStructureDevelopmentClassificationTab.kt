package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.tab.classification

import at.jku.anttracks.gui.classification.dialog.properties.ClassificationPropertiesDialog
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.DataStructureDevelopmentTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.component.configurationpane.DataStructureDevelopmentClassificationConfigurationPane
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.component.tree.DataStructureDevelopmentClassificationTreeTableView
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.DataStructureDevelopmentInfo
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.util.ThreadUtil
import com.sun.javafx.scene.control.skin.TableColumnHeader
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import org.controlsfx.control.PopOver

class DataStructureDevelopmentClassificationTab : ApplicationBaseTab() {

    @FXML
    private lateinit var configurationPane: DataStructureDevelopmentClassificationConfigurationPane

    @FXML
    lateinit var treeTableView: DataStructureDevelopmentClassificationTreeTableView
        private set

    override val componentDescriptions by lazy {
        configurationPane.componentDescriptions +
                listOf(Triple(treeTableView,
                              Description("The table displays each data structure that survived or was born in the selected timeframe"),
                              PopOver.ArrowLocation.RIGHT_CENTER),
                       Triple(treeTableView.lookupAll(".column-header").find { (it as TableColumnHeader).childrenUnmodifiable.first().toString().contains("Objects") }!!,
                              Description("The object count before and after the selected timeframe.")
                                      .linebreak()
                                      .appendDefault("For data structures this tells you whether a data structure was already part of the heap at the start of the time window or whether it was born in it.")
                                      .linebreak()
                                      .appendDefault("When you apply the data structure leaves transformer, you can see how many data objects have been added to this structure over the selected timeframe."),
                              PopOver.ArrowLocation.BOTTOM_RIGHT),
                       Triple(treeTableView.lookupAll(".column-header").find { (it as TableColumnHeader).childrenUnmodifiable.first().toString().contains("Retained") }!!,
                              Description("Growth of memory owned by a data structure"),
                              PopOver.ArrowLocation.TOP_CENTER),
                       Triple(treeTableView.lookupAll(".column-header").find { (it as TableColumnHeader).childrenUnmodifiable.first().toString().contains("Deep") }!!,
                              Description("Growth of memory occupied by data objects in a data structure.")
                                      .linebreak()
                                      .appendDefault("A strong retained size growth coupled with a weak deep data structure growth tells you that its not the data structure that grows but the data objects that lie in it!"),
                              PopOver.ArrowLocation.BOTTOM_RIGHT))
    }

    override val initialTabIdeas by lazy {
        listOf(Idea("Data structure development tab",
                    Description("This tab shows you all data structures that survived the selected timeframe. ")
                            .appendDefault("Each data structure instance is represented by a row in the table.")
                            .linebreak()
                            .appendDefault("By default you only see the type and address of each data structure. ")
                            .appendDefault("To get more information, use our predefined classifiers or maybe even write your own!"),
                    listOf("How do I do all that?" does {
                        if (!configurationPane.isConfigureMode) {
                            configurationPane.switchMode()
                        }
                        // have to wait a bit until config pane is in max mode.....
                        ThreadUtil.runDeferred({ Platform.runLater { showComponentDescriptions() } }, ThreadUtil.DeferredPeriod.LONG)
                        Unit
                    }),
                    null,
                    this))
    }

    init {
        FXMLUtil.load(this, DataStructureDevelopmentClassificationTab::class.java)
    }

    lateinit var info: DataStructureDevelopmentInfo
    lateinit var dsDevelopmentTab: DataStructureDevelopmentTab

    fun init(info: DataStructureDevelopmentInfo, dsDevelopmentTab: DataStructureDevelopmentTab) {
        super.init(info.heapEvolutionInfo.appInfo,
                   SimpleStringProperty("Data structure classification"),
                   SimpleStringProperty("Classify data structures that grow over time"),
                   SimpleStringProperty("Classify all data structures that survived the time window or were born in it."),
                   Consts.TABLE_ICON,
                   listOf(),
                   true)

        this.info = info
        this.dsDevelopmentTab = dsDevelopmentTab

        configurationPane.init(this, info)

        treeTableView.init(info = info,
                           selectLocalClassifiers = { ClassificationPropertiesDialog.showDialogForClassifier(it, info.heapEvolutionInfo) },
                           addTab = { if (!childTabs.contains(it)) childTabs.add(it) },
                           switchToTab = { ClientInfo.mainFrame.selectTab(it) },
                           showTTVOnScreen = { ClientInfo.mainFrame.selectTab(this) },
                           setSelectedClassifiers = { configurationPane.classifierSelectionPane.resetSelected(it.list) })
    }

    fun updateClassification() {
        configurationPane.classify()
    }

    override fun cleanupOnClose() {
    }

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {
    }

}