package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.tab.classification

import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.component.configurationpane.PermBornDiedTempClassificationConfigurationPane
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.component.table.PermBornDiedTempTreeTableView
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.task.PermBornDiedTempClassificationTask
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.parser.heapevolution.HeapEvolutionData
import at.jku.anttracks.parser.heapevolution.HeapEvolutionUpdateListener
import at.jku.anttracks.util.ThreadUtil
import com.sun.javafx.scene.control.skin.TableColumnHeader
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import org.controlsfx.control.PopOver
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.system.measureTimeMillis

class PermBornDiedTempClassificationTab : ApplicationBaseTab() {
    @FXML
    lateinit var classificationConfigurationPane: PermBornDiedTempClassificationConfigurationPane
        private set

    @FXML
    lateinit var treeTableView: PermBornDiedTempTreeTableView
        private set

    override val componentDescriptions by lazy {
        classificationConfigurationPane.componentDescriptions +
                listOf(Triple(treeTableView,
                              Description("The table shows you all objects that have been allocated over this timeframe grouped according to the selected classifiers.")
                                      .linebreak()
                                      .appendDefault("The colored bars tell you how many Perm, Born, Died or Temp objects belong to each classification group."),
                              PopOver.ArrowLocation.RIGHT_BOTTOM),
                       Triple(treeTableView.lookupAll(".column-header").findLast { (it as TableColumnHeader).childrenUnmodifiable.first().toString().contains("Died") }!!,
                              Description("Remember: red is Died, blue is Perm and green in Born!")
                                      .linebreak()
                                      .appendDefault("Hover the cells to see their absolute numbers."),
                              PopOver.ArrowLocation.BOTTOM_LEFT))
    }

    override val initialTabIdeas by lazy {
        listOf(Idea("Perm/Born/Died/Temp classification tab",
                    Description("This tab shows you which objects belong to the sets of Perm, Born, Died and Temp.")
                            .linebreak()
                            .appendDefault("Since each of these sets usually contains a large amount of objects that is hard to make sense of, AntTracks now grouped them by their type. ")
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

    lateinit var info: PermBornDiedTempInfo
    val classificationTaskRunning = AtomicBoolean(false)
    var lastClassificationTime = 0L
    var totalClassificationDuration = 0L

    init {
        FXMLUtil.load(this, PermBornDiedTempClassificationTab::class.java)
    }

    fun init(info: PermBornDiedTempInfo) {
        super.init(info.heapEvolutionInfo.appInfo,
                   SimpleStringProperty("Tabular representation"),
                   SimpleStringProperty(""),
                   SimpleStringProperty("Classify Perm/Born/Died/Temp object groups and show results in a table."),
                   Consts.TABLE_ICON,
                   listOf(),
                   false)
        this.info = info
        classificationConfigurationPane.init(this, info)
        treeTableView.init(info, Runnable { ClientInfo.mainFrame.selectTab(this) })
    }

    fun getHeapEvolutionUpdateListener() = object : HeapEvolutionUpdateListener {
        var lastGCStartTime: Long = -1

        override fun gcStart(heapEvolutionData: HeapEvolutionData) {
            // update gc overhead statistics
            lastGCStartTime = heapEvolutionData.currentTime

            if (info.heapEvolutionAnalysisCompleted) {
                // classify at gc start only when we reached the end of the timeframe (final classification)
                classify()
            }
        }

        override fun gcEnd(heapEvolutionData: HeapEvolutionData) {
            // update gc overhead statistics
            if (lastGCStartTime >= 0) {
                if (heapEvolutionData.gcInfos.last().type.isFull) {
                    info.majorGCDuration += heapEvolutionData.currentTime - lastGCStartTime
                } else {
                    info.minorGCDuration += heapEvolutionData.currentTime - lastGCStartTime
                }
            }

            if (info.heapEvolutionAnalysisCompleted || (!classificationTaskRunning.getAndSet(true) && System.currentTimeMillis() - lastClassificationTime >= 10_000)) {
                // classify at start, end or every 10 seconds
                // note that if the diffing is still running, we have to wait for the classification to complete (otherwise there is a risk of concurrentmodexception)
                totalClassificationDuration += measureTimeMillis { classify(!info.heapEvolutionAnalysisCompleted) }
                lastClassificationTime = System.currentTimeMillis()
            }
        }
    }

    fun classify(waitForCompletion: Boolean = false) {
        val classificationTask = PermBornDiedTempClassificationTask(this, info)
        tasks.add(classificationTask)
        ThreadUtil.startTask(classificationTask, waitForCompletion)
    }

    override fun cleanupOnClose() {
    }

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {
    }

}