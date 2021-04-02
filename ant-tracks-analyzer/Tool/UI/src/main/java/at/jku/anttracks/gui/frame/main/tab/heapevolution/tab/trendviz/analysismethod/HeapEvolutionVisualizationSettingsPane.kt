package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.analysismethod

import at.jku.anttracks.gui.classification.CombinationType
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.HeapParsingPolicy
import at.jku.anttracks.gui.frame.main.tab.heapstate.component.combinationselectionlistview.CombinationSelectionPane
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.util.safe
import javafx.fxml.FXML
import javafx.scene.control.CheckBox
import javafx.scene.control.RadioButton
import javafx.scene.control.Spinner
import javafx.scene.layout.VBox

class HeapEvolutionVisualizationSettingsPane : VBox() {
    @FXML
    lateinit var combinationSelectionPane: CombinationSelectionPane
    @FXML
    lateinit var calculateClosuresCB: CheckBox
    @FXML
    lateinit var parseAllHeapsRB: RadioButton
    @FXML
    lateinit var parseEveryNthHeapRB: RadioButton
    @FXML
    lateinit var parseHeapEveryNSecsRB: RadioButton
    @FXML
    lateinit var parseEveryNthHeapSP: Spinner<Int>
    @FXML
    lateinit var parseHeapEveryNSecsSP: Spinner<Int>
    @FXML
    lateinit var exportAsJsonCheckBox: CheckBox

    init {
        FXMLUtil.load(this, HeapEvolutionVisualizationSettingsPane::class.java)
    }

    fun init(info: HeapEvolutionInfo) {
        combinationSelectionPane.init(info.appInfo, info, CombinationType.HEAP_EVOLUTION)

        parseEveryNthHeapRB.selectedProperty().addListener { obs, wasSelected, isSelected ->
            parseEveryNthHeapSP.isDisable = !isSelected
        }
        parseHeapEveryNSecsRB.selectedProperty().addListener { obs, wasSelected, isSelected ->
            parseHeapEveryNSecsSP.isDisable = !isSelected
        }
        parseEveryNthHeapSP.isDisable = true
        parseHeapEveryNSecsSP.isDisable = true

        parseEveryNthHeapSP.valueFactory.value = Math.max((info.spanGCs / 20.0).toInt(), 1)
        parseHeapEveryNSecsSP.valueFactory.value = Math.max((info.spanMilliseconds / 20_000.0).toInt(), 1)

        parseEveryNthHeapRB.isSelected = true
    }

    fun getHeapParsingPolicy(): HeapParsingPolicy = when {
        parseAllHeapsRB.isSelected -> HeapParsingPolicy.ALL_HEAPS
        parseEveryNthHeapRB.isSelected -> HeapParsingPolicy.EVERY_NTH_HEAP
        parseHeapEveryNSecsRB.isSelected -> HeapParsingPolicy.HEAP_EVERY_N_SECS
        else -> throw IllegalStateException("One radio button must be selected by default!")
    }.safe
}