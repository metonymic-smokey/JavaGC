package at.jku.anttracks.gui.chart.base

import at.jku.anttracks.gui.model.ChartSelection
import at.jku.anttracks.gui.utils.ColorBrewer
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.application.Platform
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.CheckBox
import javafx.scene.layout.BorderPane
import java.awt.Color
import java.util.logging.Logger
import javax.naming.OperationNotSupportedException

abstract class AntChartPane<DS, DATA> : BorderPane() {
    protected var LOGGER = Logger.getLogger(this.javaClass.simpleName)

    @FXML
    private lateinit var zoomSynchronizedCheckbox: CheckBox
    @FXML
    private lateinit var configureButton: Button

    lateinit var chartSynchronizer: ChartSynchronizer
    var chartSynchronizerAvailable: Boolean = false

    val isZoomSynchronizedProperty: BooleanProperty = SimpleBooleanProperty(true)

    var isZoomSynchronized: Boolean
        get() = isZoomSynchronizedProperty.get()
        set(b) = isZoomSynchronizedProperty.set(b)

    open val topMarginPercent = 0.05f

    open val seriesColors: Array<Color>
        get() = ColorBrewer.Set1.getColorPalette(ColorBrewer.Set1.maximumColorCount).map { c -> Color(c.red, c.green, c.blue, 210) }.toTypedArray()

    protected abstract val onConfigureAction: EventHandler<ActionEvent>?

    var dataset: DS? = null

    // -----------------------------------------------
    // ---------- ScaleAdjustable --------------------
    // -----------------------------------------------

    open val scaler: AxisScaler<DS>
        get() = NoAxisScaler()

    val paneId: String
        get() = hashCode().toString()

    private enum class ClickAction {
        ZOOMING,
        DRAGGING,
        SELECTING
    }

    init {
        FXMLUtil.load(this, at.jku.anttracks.gui.chart.base.AntChartPane::class.java)
        /*
        String color = "";
        // create random object - reuse this as often as possible
        Random random = new Random();

        // create a big random number - maximum is ffffff (hex) = 16777215 (dez)
        int nextInt = random.nextInt(256*256*256);

        // format it as hexadecimal string (with hashtag and leading zeros)
        String colorCode = String.format("#%06x", nextInt);
        setStyle("-fx-background-color: " + colorCode);
        */
    }

    open fun init(chartSynchronizer: ChartSynchronizer, showZoomSyncCheckbox: Boolean = true) {
        this.chartSynchronizer = chartSynchronizer

        initializeChart()
        initializeZoomSynchronizedCheckBox(showZoomSyncCheckbox)
        initializeConfigureButton()
        initializeSynchronizerListeners()
    }

    private fun initializeSynchronizerListeners() {
        chartSynchronizer.addSelectionListener(object : ChartSynchronizer.SelectionListener {
            override fun selectionChanged(sender: ChartSynchronizer, selection: ChartSelection?) {
                updateMarker()
            }
        })
        chartSynchronizer.addZoomListener(object : ChartSynchronizer.ZoomListener {

            override fun zoomChanged(sender: ChartSynchronizer) {
                //System.out.println(String.format("Zoom: %10.3f to %10.3f", sender.getZoomXLowerBound(), sender.getZoomXUpperBound()));
                zoomX(sender.zoomXLowerBound, sender.zoomXUpperBound)
            }

            override fun domainChanged(sender: ChartSynchronizer) {}

        })
    }

    fun update(data: DATA) {
        //val measureUpdate = ApplicationStatistics.getInstance().createMeasurement("Charts: Update - " + javaClass.simpleName)
        if (createsNewDatasetOnUpdate()) {
            dataset = createDataSet(data)
            if (dataset != null) {
                dataset = scaler.postProcessDataset(dataset!!)
                Platform.runLater {
                    //val m = ApplicationStatistics.getInstance().createMeasurement("Charts: Update Chart On UI - " + javaClass.simpleName)
                    updateChartOnUIThread(dataset!!)
                    //m.end()
                }
            }

        } else {
            dataset = updateDataSet(data)
        }

        postDataSetUpdate()
        //measureUpdate.end()
    }

    protected abstract fun updateChartOnUIThread(dataset: DS)

    protected abstract fun createsNewDatasetOnUpdate(): Boolean

    @Throws(OperationNotSupportedException::class)
    protected abstract fun createDataSet(data: DATA): DS

    @Throws(OperationNotSupportedException::class)
    protected abstract fun updateDataSet(data: DATA): DS

    protected abstract fun postDataSetUpdate()

    abstract fun zoomX(lowerBound: Double, upperBound: Double)

    protected abstract fun initializeChart()

    abstract fun getMinYValue(dataset: DS): Double

    abstract fun getMaxYValue(dataset: DS): Double

    abstract fun setXLabel(label: String)

    abstract fun setYLabel(label: String)

    protected fun initializeConfigureButton() {
        configureButton.onAction = onConfigureAction
        configureButton.isVisible = configureButton.onAction != null
        configureButton.isManaged = configureButton.isVisible
    }

    protected fun initializeZoomSynchronizedCheckBox(show: Boolean) {
        if (show) {
            isZoomSynchronizedProperty.bind(zoomSynchronizedCheckbox.selectedProperty())
            isZoomSynchronizedProperty.addListener { observableValue, oldVal, newVal ->
                if (!oldVal && newVal!! && chartSynchronizerAvailable) {
                    // checked synchronize box -> zoom immediately to current synchronized level
                    zoomX(chartSynchronizer.zoomXLowerBound, chartSynchronizer.zoomXUpperBound)
                }
            }
        } else {
            zoomSynchronizedCheckbox.isVisible = false
            zoomSynchronizedCheckbox.isManaged = false
        }
    }

    // -----------------------------------------------
    // -----------------------------------------------
    // -----------------------------------------------

    /**
     * Update current selection, ... (called via syncer)
     */
    abstract fun updateMarker()
}
