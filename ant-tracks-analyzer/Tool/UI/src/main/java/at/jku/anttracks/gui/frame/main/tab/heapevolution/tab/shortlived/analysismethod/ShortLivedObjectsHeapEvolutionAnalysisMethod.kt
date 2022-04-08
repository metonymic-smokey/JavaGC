package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.analysismethod

import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.analysismethod.HeapEvolutionAnalysisMethod
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.ShortLivedObjectsTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.model.ShortLivedObjectsInfo
import at.jku.anttracks.gui.utils.PlatformUtil
import at.jku.anttracks.parser.heapevolution.HeapEvolutionAction
import at.jku.anttracks.parser.heapevolution.HeapEvolutionData
import at.jku.anttracks.parser.heapevolution.HeapEvolutionUpdateListener
import javafx.scene.layout.VBox

class ShortLivedObjectsHeapEvolutionAnalysisMethod(val heapEvolutionInfo: HeapEvolutionInfo) : HeapEvolutionAnalysisMethod() {
    //================================================================================
    // TEXT
    //================================================================================
    override val name: String = "Short-lived objects analysis"
    override val description: String = "Identify objects that are repeatedly allocated and collected"

    //================================================================================
    // INFO
    //================================================================================
    override lateinit var info: ShortLivedObjectsInfo

    //================================================================================
    // TABS
    //================================================================================
    private lateinit var tab: ShortLivedObjectsTab

    //================================================================================
    // SETTINGS
    //================================================================================
    // Does not need settings
    override val settingsPane: VBox? = null

    //================================================================================
    // PROCESS
    //================================================================================
    // 1
    override fun parseAndCheckSettings(): Boolean = true

    // 2
    override fun initInfo() {
        info = ShortLivedObjectsInfo(heapEvolutionInfo)
    }

    // 3
    override fun createTabs(): List<ApplicationBaseTab> {
        tab = ShortLivedObjectsTab().apply { init(info) }
        return listOf(tab)
    }

    //================================================================================
    // ACTIONS
    //================================================================================
    override val parserActions: Set<HeapEvolutionAction> = setOf(HeapEvolutionAction.TRACK_DIED_OBJECTS,
                                                                 HeapEvolutionAction.TRACK_BORN_OBJECTS,
                                                                 HeapEvolutionAction.TRACK_TEMP_OBJECTS)

    override fun createHeapEvolutionAnalysisUpdateListeners(): List<HeapEvolutionUpdateListener> =
            listOf(object : HeapEvolutionUpdateListener {
                override fun gcStart(heapEvolutionData: HeapEvolutionData) {
                    info.updateAtGCStart(heapEvolutionData)
                    PlatformUtil.runAndWait { tab.overviewTab.updateCharts(true, heapEvolutionData.bornObjectCount, heapEvolutionData.bornByteCount) }
                }

                override fun gcEnd(heapEvolutionData: HeapEvolutionData) {
                    info.updateAtGCEnd(heapEvolutionData)
                    PlatformUtil.runAndWait { tab.overviewTab.updateCharts(false, heapEvolutionData.bornObjectCount, heapEvolutionData.bornByteCount) }
                }

                override fun timeWindowEnd(heapEvolutionData: HeapEvolutionData) {
                    // prepare groupings for treetable
                    info.sampleDefaultTrees()

                    PlatformUtil.runAndWait {
                        // This initialized the default chart that is shown if we switch to the treetable table without clicking on a pie chart.
                        tab.classificationTab.updateClassification()
                    }
                }
            })
}