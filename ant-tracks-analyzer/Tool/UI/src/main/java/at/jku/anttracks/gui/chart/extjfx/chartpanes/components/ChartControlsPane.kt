package at.jku.anttracks.gui.chart.extjfx.chartpanes.components

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.chart.extjfx.plugins.XScrollZoomer
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.ImageUtil
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleBooleanProperty
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.Tooltip
import javafx.scene.image.ImageView
import javafx.scene.layout.AnchorPane

class ChartControlsPane : AnchorPane() {

    @FXML
    private lateinit var zoomInControl: ImageView
    @FXML
    private lateinit var zoomOutControl: ImageView
    @FXML
    private lateinit var resetZoomControl: ImageView
    @FXML
    private lateinit var chartOptionsControl: ImageView

    val chartOptionsPopup = ChartOptionsPopup()

    init {
        FXMLUtil.load(this, ChartControlsPane::class.java)
    }

    fun init(zoomer: XScrollZoomer<*>,
             chartYUnits: List<ReducedXYChartPane.Companion.Unit>,
             chartOptionsNodes: List<Node>,
             automaticallyAdjustZoomToDataset: SimpleBooleanProperty) {
        zoomInControl.image = ImageUtil.toFXImage(Consts.ZOOM_IN_IMAGE)
        zoomOutControl.image = ImageUtil.toFXImage(Consts.ZOOM_OUT_IMAGE)
        resetZoomControl.image = ImageUtil.toFXImage(Consts.RESET_ZOOM_IMAGE)
        chartOptionsControl.image = ImageUtil.toFXImage(Consts.CONFIGURE_CHART_IMAGE)

        Tooltip.install(zoomInControl, Tooltip("Zoom in"))
        Tooltip.install(zoomOutControl, Tooltip("Zoom out"))
        Tooltip.install(resetZoomControl, Tooltip("Reset zoom"))
        Tooltip.install(chartOptionsControl, Tooltip("Configure chart"))

        zoomInControl.setOnMouseClicked {
            zoomer.zoomIn()
            it.consume()
        }
        zoomOutControl.setOnMouseClicked {
            zoomer.zoomOut()
            it.consume()
        }
        resetZoomControl.setOnMouseClicked {
            zoomer.zoomOrigin()
            it.consume()
        }
        resetZoomControl.disableProperty().bind(automaticallyAdjustZoomToDataset)

        chartOptionsControl.setOnMouseClicked {
            if (!chartOptionsPopup.isShowing) {
                val chartOptionsControlPosition = chartOptionsControl.localToScreen(24.0, 0.0)
                chartOptionsPopup.show(chartOptionsControl,
                                       chartOptionsControlPosition.x,
                                       chartOptionsControlPosition.y)
            } else {
                chartOptionsPopup.hide()
            }
        }

        chartOptionsPopup.init(chartYUnits, chartOptionsNodes)

        // show chart options control only when options are available
        val chartOptionsAvailableProperty = Bindings.or(chartOptionsPopup.synchronizedCheckBox.visibleProperty(),
                                                        chartOptionsPopup.yUnitComboBox.visibleProperty()).or(SimpleBooleanProperty(chartOptionsNodes.isNotEmpty()))
        chartOptionsControl.visibleProperty().bind(chartOptionsAvailableProperty)
        chartOptionsControl.managedProperty().bind(chartOptionsAvailableProperty)
    }

    fun hideSynchronizationControl() {
        chartOptionsPopup.synchronizedCheckBox.isVisible = false
        chartOptionsPopup.synchronizedCheckBox.isManaged = false
    }

    fun showSynchronizationControl() {
        chartOptionsPopup.synchronizedCheckBox.isVisible = true
        chartOptionsPopup.synchronizedCheckBox.isManaged = true
    }
}