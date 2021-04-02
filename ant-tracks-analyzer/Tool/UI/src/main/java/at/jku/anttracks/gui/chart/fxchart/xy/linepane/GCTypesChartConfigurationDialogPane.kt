
package at.jku.anttracks.gui.chart.fxchart.xy.linepane

import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.fxml.FXML
import javafx.scene.control.CheckBox
import javafx.scene.control.DialogPane
import javafx.scene.layout.VBox

class GCTypesChartConfigurationDialogPane : DialogPane() {

    @FXML
    private lateinit var vBox: VBox

    private var causeShower: ShowGCCause? = null

    init {
        FXMLUtil.load(this, GCTypesChartConfigurationDialogPane::class.java)
    }

    fun init(causeShower: ShowGCCause) {
        this.causeShower = causeShower
        for (cause in ClientInfo.getCurrentAppInfo()?.symbols?.causes?.all ?: arrayOf()) {
            val checkbox = CheckBox(cause.name)
            checkbox.isSelected = causeShower.displayedCauses.contains(cause.id)
            checkbox.setOnAction { causeShower.updateDisplayCause(checkbox.isSelected, cause) }
            vBox.children.add(checkbox)
        }
    }
}
