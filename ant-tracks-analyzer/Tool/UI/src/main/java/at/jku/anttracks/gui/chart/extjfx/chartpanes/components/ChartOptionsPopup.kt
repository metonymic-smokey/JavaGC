package at.jku.anttracks.gui.chart.extjfx.chartpanes.components

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.control.ComboBox
import javafx.scene.layout.VBox
import javafx.stage.Popup
import javafx.util.StringConverter

class ChartOptionsPopup : Popup() {
    @FXML
    private lateinit var chartOptionsPane: VBox
    @FXML
    lateinit var synchronizedCheckBox: CheckBox
    @FXML
    lateinit var yUnitComboBox: ComboBox<ReducedXYChartPane.Companion.Unit>
    @FXML
    private lateinit var closeButton: Button

    init {
        FXMLUtil.load(this, ChartOptionsPopup::class.java)
    }

    fun init(yUnits: List<ReducedXYChartPane.Companion.Unit>, chartOptionsNodes: List<Node>) {
        chartOptionsPane.children.addAll(chartOptionsNodes)
        chartOptionsPane.requestFocus()
        closeButton.setOnAction {
            hide()
        }
        yUnitComboBox.items = FXCollections.observableArrayList(yUnits)
        yUnitComboBox.converter = object : StringConverter<ReducedXYChartPane.Companion.Unit>() {
            override fun toString(unit: ReducedXYChartPane.Companion.Unit) = unit.labelText

            override fun fromString(unitString: String) = ReducedXYChartPane.Companion.Unit.values().find { it.labelText == unitString }
        }
        if (yUnits.size <= 1) {
            yUnitComboBox.isVisible = false
            yUnitComboBox.isManaged = false
        }
    }
}