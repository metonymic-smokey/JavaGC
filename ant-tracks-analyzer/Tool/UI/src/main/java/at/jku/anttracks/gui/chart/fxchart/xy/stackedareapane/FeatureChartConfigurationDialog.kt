
package at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane

import javafx.scene.control.Dialog

class FeatureChartConfigurationDialog : Dialog<Void>() {

    fun init(chartPane: ShowFeature) {
        setResultConverter { null }
        title = "Configure Feature Objects Chart"
        dialogPane = FeatureChartConfigurationDialogPane().apply { init(chartPane) }
    }
}
