package at.jku.anttracks.gui.frame.main.tab.application.model

import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.frame.main.tab.application.ApplicationTab
import at.jku.anttracks.gui.frame.main.tab.application.controller.ApplicationController
import at.jku.anttracks.gui.model.AppInfo
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.collections.ListChangeListener

class ApplicationTabModel {

    lateinit var view: ApplicationTab
    lateinit var appInfo: AppInfo

    private val areHeapStateOperationsEnabled: BooleanProperty = SimpleBooleanProperty(false)
    private val areHeapEvolutionOperationsEnabled: BooleanProperty = SimpleBooleanProperty(false)

    val longDescription = SimpleStringProperty("(1) click at a single point in time on a chart to select it. You can then examine the heap state at that time.\n" +
                                                       "(2) click and drag to select a time window. You can the perform heap evolution analyses over the selected window.\n\n" +
                                                       "Charts can be zoomed by using the scroll wheel while pressing the CTRL key.\n" +
                                                       "Charts can be panned by clicking and dragging the mouse while pressing the CTRL key.")

    val actions = listOf(
            ActionTabAction("Heap evolution analysis",
                            "Perform different analyses on the heap evolution in the selected time window",
                            "Operations",
                            areHeapEvolutionOperationsEnabled) {
                val selection = view.overviewTab.simplifiedMemoryChartPane.xSelector!!.selectedXValues
                ApplicationController.heapEvolutionAnalysis(view,
                                                            appInfo,
                                                            selection.min()!!.toLong()..selection.max()!!.toLong(),
                                                            startHeapEvolutionAnalysisAutomatically = false)
            },
            ActionTabAction("Heap state analysis",
                            "Classify, group and inspect the live heap objects at the selected point in time",
                            "Operations",
                            areHeapStateOperationsEnabled) {
                ApplicationController.heapStateAnalysis(view,
                                                        appInfo,
                                                        view.overviewTab.simplifiedMemoryChartPane.xSelector!!.selectedXValues.first().toLong())
            }
    )

    fun init(view: ApplicationTab, appInfo: AppInfo) {
        this.view = view
        this.appInfo = appInfo

        view.overviewTab.simplifiedMemoryChartPane.xSelector!!.selectedXValues.addListener(ListChangeListener<Double> {
            when {
                it.list.size == 1 -> {
                    areHeapStateOperationsEnabled.set(true)
                    areHeapEvolutionOperationsEnabled.set(false)
                }
                it.list.size > 1 -> {
                    areHeapStateOperationsEnabled.set(false)
                    areHeapEvolutionOperationsEnabled.set(true)
                }
                else -> {
                    areHeapStateOperationsEnabled.set(false)
                    areHeapEvolutionOperationsEnabled.set(false)
                }
            }
        })
    }
}