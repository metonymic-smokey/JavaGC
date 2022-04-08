package at.jku.anttracks.gui.frame.main.tab.heapevolution.analysismethod

import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.IHeapEvolutionInfo
import at.jku.anttracks.parser.heapevolution.HeapEvolutionAction
import at.jku.anttracks.parser.heapevolution.HeapEvolutionUpdateListener
import javafx.scene.layout.VBox

abstract class HeapEvolutionAnalysisMethod() {
    /**
     * String used to identify the [HeapEvolutionAnalysisMethod]
     */
    abstract val name: String

    /**
     * Short description of the [HeapEvolutionAnalysisMethod]
     */
    abstract val description: String

    /*
    Collection of settings, data, etc. used by this heap evolution analysis that can be passed to tabs
     */
    abstract val info: IHeapEvolutionInfo

    /**
     * A set of [HeapEvolutionAction] that tell the parser what data has to be calculated.
     * The [HeapEvolutionUpdateListener] of this [HeapEvolutionAnalysisMethod], should only access those parameters that are related to the specified parser actions
     */
    abstract val parserActions: Set<HeapEvolutionAction>

    /**
     * A settings pane that allows to configure this diffing method.
     * It is displayed below the respective [HeapEvolutionAnalysisMethod] in the [HeapEvolutionAnalysisConfigurationTab]
     */
    abstract val settingsPane: VBox?

    /**
     * Specify how to handle heap evolution analysis updates coming in at every GC START/END event, and at the start and end of the time window.
     */
    abstract fun createHeapEvolutionAnalysisUpdateListeners(): List<HeapEvolutionUpdateListener>

    /*
    Initialize the [info] field
     */
    abstract fun initInfo()

    /**
     * The tabs that display the analysis results of the respective [HeapEvolutionAnalysisMethod]
     */
    abstract fun createTabs(): List<ApplicationBaseTab>

    /**
     * This function is called before parsing starts; it should parse the current state of the settings pane (such that settings remain unchanged during parsing).
     * After parsing the settings the returned boolean should tell whether the selected settings are valid (i.e. whether parsing can start!)
     */
    abstract fun parseAndCheckSettings(): Boolean
}