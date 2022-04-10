
package at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane

import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.fxml.FXML
import javafx.scene.control.CheckBox
import javafx.scene.control.DialogPane
import javafx.scene.layout.VBox

class FeatureChartConfigurationDialogPane : DialogPane() {

    @FXML
    private lateinit var vBox: VBox

    init {
        FXMLUtil.load(this, FeatureChartConfigurationDialogPane::class.java)
    }

    fun init(chartPane: ShowFeature) {
        for (i in 0 until chartPane.featureCount) {
            val checkbox =
                    CheckBox(ClientInfo.getCurrentAppInfo()?.symbols?.features?.getFeature(i)?.name)
                            .apply { isSelected = chartPane.displayedFeatures.contains(i) }
                            .apply { setOnAction { chartPane.updateDisplayFeature(isSelected, i) } }
            vBox.children.add(checkbox)
        }
    }
}
