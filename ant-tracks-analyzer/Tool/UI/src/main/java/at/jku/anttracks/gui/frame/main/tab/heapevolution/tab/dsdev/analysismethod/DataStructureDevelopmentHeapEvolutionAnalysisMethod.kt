package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.analysismethod

import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.analysismethod.HeapEvolutionAnalysisMethod
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.DataStructureDevelopmentTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.DataStructureDevelopmentInfo
import at.jku.anttracks.parser.heapevolution.HeapEvolutionAction
import at.jku.anttracks.parser.heapevolution.HeapEvolutionData
import at.jku.anttracks.parser.heapevolution.HeapEvolutionUpdateListener
import javafx.scene.layout.VBox

class DataStructureDevelopmentHeapEvolutionAnalysisMethod(val heapEvolutionInfo: HeapEvolutionInfo) : HeapEvolutionAnalysisMethod() {
    //================================================================================
    // TEXT
    //================================================================================
    override val name: String = "Data structure development analysis"
    override val description: String = "Detect and analyze data structures that have grown the most over the selected time span"

    //================================================================================
    // INFO
    //================================================================================
    override lateinit var info: DataStructureDevelopmentInfo

    //================================================================================
    // TABS
    //================================================================================
    private lateinit var dataStructureDevelopmentTab: DataStructureDevelopmentTab

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
        info = DataStructureDevelopmentInfo(heapEvolutionInfo)
    }

    // 3
    override fun createTabs(): List<ApplicationBaseTab> {
        dataStructureDevelopmentTab = DataStructureDevelopmentTab()
        dataStructureDevelopmentTab.init(info)

        return listOf(dataStructureDevelopmentTab)
    }

    //================================================================================
    // ACTIONS
    //================================================================================
    override val parserActions: Set<HeapEvolutionAction> = setOf(HeapEvolutionAction.TRACK_PERM_OBJECTS)

    override fun createHeapEvolutionAnalysisUpdateListeners(): List<HeapEvolutionUpdateListener> = listOf(object : HeapEvolutionUpdateListener {
        override fun timeWindowEnd(heapEvolutionData: HeapEvolutionData) {
            info.buildDevelopmentsList()
            dataStructureDevelopmentTab.classificationTab.updateClassification()
        }
    })
}
