
package at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.cpuutilization.dialog

import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.cpuutilization.GCActivityStackedAreaJFreeChartPane
import javafx.scene.control.Dialog

class GCActivityStackedAreaJFreeChartDialog : Dialog<Void>() {
    private lateinit var pane: GCActivityStackedAreaJFreeChartDialogPane

    fun init(owner: GCActivityStackedAreaJFreeChartPane) {
        setResultConverter { null }
        title = "Configure CPU utilization chart"
        pane = GCActivityStackedAreaJFreeChartDialogPane()
        dialogPane = pane
        pane.init(owner)
    }
}
