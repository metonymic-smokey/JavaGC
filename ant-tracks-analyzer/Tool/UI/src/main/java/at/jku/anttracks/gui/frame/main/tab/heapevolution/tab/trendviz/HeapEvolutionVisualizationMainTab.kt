
package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz

import at.jku.anttracks.gui.component.actiontab.tab.ActionTab
import at.jku.anttracks.gui.frame.main.component.applicationbase.NonSelectableApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.application.ApplicationTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.model.HeapEvolutionVisualizationInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.HeapEvolutionVisualizationTimeSeriesTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.treetrends.HeapEvolutionVisualizationWebTreeTab
import at.jku.anttracks.gui.model.IAppInfo
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.SimpleStringProperty

class HeapEvolutionVisualizationMainTab : NonSelectableApplicationBaseTab() {
    private lateinit var info: HeapEvolutionVisualizationInfo

    val timeSeriesTab: HeapEvolutionVisualizationTimeSeriesTab by lazy {
        HeapEvolutionVisualizationTimeSeriesTab().also { it.init(info) }
    }
    val webTreeVizTab: HeapEvolutionVisualizationWebTreeTab by lazy {
        HeapEvolutionVisualizationWebTreeTab().also { it.init(info) }
    }

    override val autoSelectChildTab: ActionTab
        get() = timeSeriesTab

    init {
        FXMLUtil.load(this, ApplicationTab::class.java)
    }

    fun init(info: HeapEvolutionVisualizationInfo) {
        // This must be performed before the super call because we access autoSelectedChildTab there, which needs this data to initialize
        this.info = info

        super.init(info.appInfo,
                   SimpleStringProperty("Heap Evolution Visualization"),
                //SimpleStringProperty("Overview on the heap memory evolution reconstructed from trace file."),
                   SimpleStringProperty("Displays the application's evolution by visual means"),
                   SimpleStringProperty("Long description not needed for non-selectable application"),
                   Consts.HEAP_TREND_ICON,
                   listOf(),
                   true)

        // trendTab is automatically added because it is the #autoSelectChildTab
        childTabs.add(webTreeVizTab)
    }

    override fun cleanupOnClose() {
    }

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {

    }
}
