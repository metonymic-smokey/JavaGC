
package at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.cpuutilization.dialog

import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.cpuutilization.GCActivityStackedAreaJFreeChartPane
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.value.ChangeListener
import javafx.fxml.FXML
import javafx.scene.control.DialogPane
import javafx.scene.control.Slider

class GCActivityStackedAreaJFreeChartDialogPane : DialogPane() {
    @FXML
    lateinit var slider: Slider

    init {
        FXMLUtil.load(this, GCActivityStackedAreaJFreeChartDialogPane::class.java)
    }

    fun init(owner: GCActivityStackedAreaJFreeChartPane) {
        slider.value = owner.plateauWidth.toDouble()
        slider.valueProperty().addListener(ChangeListener { observable, oldValue, newValue -> owner.plateauWidth = newValue.toInt() })
    }
}
