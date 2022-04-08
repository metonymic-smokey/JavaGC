package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.analysismethod

import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.analysismethod.HeapEvolutionAnalysisMethod
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.HeapEvolutionVisualizationMainTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.model.HeapEvolutionVisualizationInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.HeapEvolutionVisualizationTimeSeriesUpdateListener
import at.jku.anttracks.parser.heapevolution.HeapEvolutionAction
import at.jku.anttracks.parser.heapevolution.HeapEvolutionData
import at.jku.anttracks.parser.heapevolution.HeapEvolutionUpdateListener
import at.jku.anttracks.util.safe
import org.controlsfx.control.Notifications

class HeapEvolutionVisualizationAnalysisMethod(val heapEvolutionInfo: HeapEvolutionInfo) : HeapEvolutionAnalysisMethod() {
    override val name: String = "Heap evolution visualization"
    override val description: String = "Inspect the development of object groups, e.g., which types grow the most"

    //================================================================================
    // INFO
    //================================================================================
    override lateinit var info: HeapEvolutionVisualizationInfo

    //================================================================================
    // TABS
    //================================================================================
    private lateinit var heapEvolutionMainTab: HeapEvolutionVisualizationMainTab

    //================================================================================
    // SETTINGS
    //================================================================================
    lateinit var settings: HeapEvolutionVisualizationSettings
    override val settingsPane: HeapEvolutionVisualizationSettingsPane = HeapEvolutionVisualizationSettingsPane()

    init {
        settingsPane.init(heapEvolutionInfo)
    }

    //================================================================================
    // PROCESS
    //================================================================================
    // 1
    override fun parseAndCheckSettings(): Boolean =
            when {
                settingsPane.combinationSelectionPane.selectedClassifierInfo.selectedClassifiers.list.isEmpty() -> {
                    Notifications.create().text("You have to select at least one classifier!").showError()
                    false
                }
                else -> true
            }.safe

    // 2
    override fun initInfo() {
        info = HeapEvolutionVisualizationInfo(
                heapEvolutionInfo,
                HeapEvolutionVisualizationSettings(
                        settingsPane.calculateClosuresCB.isSelected,
                        settingsPane.getHeapParsingPolicy(),
                        settingsPane.parseEveryNthHeapSP.value - 1,
                        settingsPane.parseEveryNthHeapSP.value,
                        settingsPane.exportAsJsonCheckBox.isSelected),
                settingsPane.combinationSelectionPane.selectedClassifierInfo.selectedClassifiers,
                settingsPane.combinationSelectionPane.selectedClassifierInfo.selectedFilters)
    }

    // 3
    override fun createTabs(): List<ApplicationBaseTab> {
        heapEvolutionMainTab = HeapEvolutionVisualizationMainTab()
        heapEvolutionMainTab.init(info)

        return listOf(heapEvolutionMainTab)
    }

    //================================================================================
    // ACTIONS
    //================================================================================
    override val parserActions: Set<HeapEvolutionAction> = setOf()

    override fun createHeapEvolutionAnalysisUpdateListeners(): List<HeapEvolutionUpdateListener> =
            listOf(HeapEvolutionVisualizationTimeSeriesUpdateListener(heapEvolutionMainTab.timeSeriesTab, info),
                   object : HeapEvolutionUpdateListener {
                       override fun timeWindowEnd(heapEvolutionData: HeapEvolutionData) {
                           heapEvolutionMainTab.webTreeVizTab.notifyDataFinished()
                       }
                   })
}