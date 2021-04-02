
package at.jku.anttracks.gui.chart.fxchart.xy.linepane

import javafx.scene.control.Dialog

class GCTypesChartConfigurationDialog : Dialog<Void>() {
    private lateinit var pane: GCTypesChartConfigurationDialogPane

    fun init(gcCauseShower: ShowGCCause) {
        setResultConverter { null }
        title = "Configure GC Chart"
        pane = GCTypesChartConfigurationDialogPane()
        dialogPane = pane
        pane.init(gcCauseShower)
    }
}
