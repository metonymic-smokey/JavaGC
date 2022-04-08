package at.jku.anttracks.gui.chart.extjfx.plugins

import cern.extjfx.chart.plugins.XRangeIndicator
import cern.extjfx.chart.plugins.XValueIndicator
import javafx.collections.FXCollections
import javafx.scene.chart.XYChart
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.shape.Rectangle
import java.util.function.Predicate
import kotlin.math.absoluteValue
import kotlin.math.max
import kotlin.math.min

class XSelector<Y>(private val valueLabelGenerator: ((x: Double) -> String)? = null,
                   private val rangeLabelGenerator: ((fromX: Double, toX: Double) -> String)? =
                           if (valueLabelGenerator == null)
                               null
                           else
                               { fromX, toX ->
                                   "From ${valueLabelGenerator.invoke(fromX)}\nTo ${valueLabelGenerator.invoke(toX)}"
                               }) : LinkableXYChartPlugin<Number, Y>() {

    private val mouseEventFilter = Predicate<MouseEvent> { evt ->
        !evt.isControlDown && // to avoid selection while panning
                evt.isPrimaryButtonDown &&
                evt.target !is ImageView && // to avoid selection when clicking chart controls
                evt.target !is Rectangle    // to avoid selection when clicking gc range indicators
    }

    var selectedXValues = FXCollections.observableArrayList<Double>()
        private set
    private var currentDataPointSelection: XValueIndicator<Y>? = null
    private var currentDataRangeSelection: XRangeIndicator<Y>? = null
    private var dragOrigin = -1.0
    private var ongoingDragSelection = false

    init {
        registerMouseEventHandler(MouseEvent.MOUSE_PRESSED) { evt ->
            if (mouseEventFilter.test(evt)) {
                // select data point with x value closest to cursor
                chartPane.chart.data.flatMap { it.data }.minBy { xDistanceBetween(evt, it) }?.also { dataPointClosestToCursor ->
                    val x = dataPointClosestToCursor.xValue.toDouble()
                    doSynchronized {
                        selectedXValues.setAll(x)
                        dragOrigin = x
                        drawSelection(x, valueLabelGenerator?.invoke(x))
                    }
                }
                // TODO add functionality to extend an existing selection to either side (like in audacity - should use custom cursor symbol!)
                evt.consume()
            }
        }

        registerMouseEventHandler(MouseEvent.MOUSE_DRAGGED) { evt ->
            if (mouseEventFilter.test(evt)) {
                // select dragged range
                val dragTarget = toDataPoint(chartPane.chart.yAxis, getLocationInPlotArea(evt)).xValue.toDouble()
                val xValuesWithinDraggedRange = getXValues(min(dragOrigin, dragTarget)..max(dragOrigin, dragTarget))
                doSynchronized {
                    selectedXValues.setAll(xValuesWithinDraggedRange)
                    drawSelection(min(dragOrigin, dragTarget),
                                  max(dragOrigin, dragTarget),
                                  if (selectedXValues.isNotEmpty())
                                      rangeLabelGenerator?.invoke(selectedXValues.min()!!, selectedXValues.max()!!)
                                  else null)
                }
                ongoingDragSelection = true
                evt.consume()
            }
        }

        registerMouseEventHandler(MouseEvent.MOUSE_RELEASED) { evt ->
            if (ongoingDragSelection) {
                // adjust selection to fit data points captured by the dragged range
                val distinctSelectedXValues = selectedXValues.distinctBy { it.toLong() }    // otherwise there have been problems due to rounding errors...
                doSynchronized {
                    // synchronize final selection
                    when (distinctSelectedXValues.size) {
                        0 -> cleanSelection()
                        1 -> drawSelection(distinctSelectedXValues.first(), valueLabelGenerator?.invoke(distinctSelectedXValues.first()))
                        else -> {
                            val minX = distinctSelectedXValues.min()!!
                            val maxX = distinctSelectedXValues.max()!!
                            drawSelection(minX, maxX, rangeLabelGenerator?.invoke(minX, maxX))
                        }
                    }
                }
                ongoingDragSelection = false
                evt.consume()
            }
        }
    }

    private fun drawSelection(x: Double, label: String?) {
        cleanSelection()
        currentDataPointSelection = XValueIndicator(x, label)
        chartPane.plugins.add(currentDataPointSelection)
    }

    private fun drawSelection(fromX: Double, toX: Double, label: String?) {
        cleanSelection()
        currentDataRangeSelection = XRangeIndicator(fromX, toX, label)
        chartPane.plugins.add(currentDataRangeSelection)
    }

    private fun cleanSelection() {
        chartPane.plugins.remove(currentDataPointSelection)
        chartPane.plugins.remove(currentDataRangeSelection)
        currentDataPointSelection = null
        currentDataRangeSelection = null
    }

    private fun xDistanceBetween(evt: MouseEvent, dataPoint: XYChart.Data<Number, Y>?) =
            if (dataPoint == null) {
                Double.MAX_VALUE
            } else {
                val evtDataPoint = toDataPoint(chartPane.chart.yAxis, getLocationInPlotArea(evt))
                (evtDataPoint.xValue.toDouble() - dataPoint.xValue.toDouble()).absoluteValue
            }

    private fun getXValues(range: ClosedRange<Double>) = chartPane.chart.data.flatMap { series -> series.data.map { it.xValue.toDouble() } }.filter { it in range }

    // for programmatic value selections
    fun select(x: Double) {
        doSynchronized {
            selectedXValues.setAll(x)
            drawSelection(x, valueLabelGenerator?.invoke(x))
        }
    }

    // for programmatic range selections
    fun select(fromX: Double, toX: Double) {
        val selection = getXValues(fromX..toX).distinct()
        doSynchronized {
            selectedXValues.setAll(selection)
            drawSelection(fromX, toX, rangeLabelGenerator?.invoke(fromX, toX))
        }
    }

    // for programmatic selection removal
    fun clearSelection() {
        doSynchronized {
            selectedXValues.clear()
            cleanSelection()
        }
    }
}