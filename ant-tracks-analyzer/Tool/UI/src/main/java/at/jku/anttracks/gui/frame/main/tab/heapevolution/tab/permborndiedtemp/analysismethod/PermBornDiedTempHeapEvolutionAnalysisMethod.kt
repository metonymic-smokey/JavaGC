package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.analysismethod

import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.analysismethod.HeapEvolutionAnalysisMethod
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.PermBornDiedTempTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempInfo
import at.jku.anttracks.parser.heapevolution.HeapEvolutionAction
import at.jku.anttracks.parser.heapevolution.HeapEvolutionUpdateListener
import javafx.scene.layout.VBox

class PermBornDiedTempHeapEvolutionAnalysisMethod(val heapEvolutionInfo: HeapEvolutionInfo) : HeapEvolutionAnalysisMethod() {
    //================================================================================
    // TEXT
    //================================================================================
    override val name: String = "Perm/Born/Died/Temp analysis"
    override val description: String = "Show which objects survived a given time span and which have been born, died, or only lived temporarily in it"

    //================================================================================
    // INFO
    //================================================================================
    override lateinit var info: PermBornDiedTempInfo

    //================================================================================
    // TABS
    //================================================================================
    private lateinit var tab: PermBornDiedTempTab

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
        info = PermBornDiedTempInfo(heapEvolutionInfo)
    }

    // 3
    override fun createTabs(): List<ApplicationBaseTab> {

        tab = PermBornDiedTempTab().apply { init(info) }
        return listOf(tab)
    }
    //================================================================================
    // ACTIONS
    //================================================================================
    override val parserActions: Set<HeapEvolutionAction> = setOf(HeapEvolutionAction.TRACK_PERM_OBJECTS,
                                                                 HeapEvolutionAction.TRACK_BORN_OBJECTS,
                                                                 HeapEvolutionAction.TRACK_DIED_OBJECTS,
                                                                 HeapEvolutionAction.TRACK_TEMP_OBJECTS)

    override fun createHeapEvolutionAnalysisUpdateListeners(): List<HeapEvolutionUpdateListener> = tab.heapEvolutionUpdateListener
}
