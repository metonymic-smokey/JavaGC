package at.jku.anttracks.gui.chart.extjfx.plugins

import at.jku.anttracks.util.coerceIn
import cern.extjfx.chart.Axes
import javafx.beans.property.BooleanProperty
import javafx.event.EventHandler
import javafx.scene.chart.ValueAxis
import javafx.scene.input.ScrollEvent
import javafx.util.Duration
import java.util.function.Predicate

class XScrollZoomer<Y>(val automaticallyAdjustZoomLevelToDatasetProperty: BooleanProperty, val getDataRange: () -> ClosedRange<Double>) : LinkableXYChartPlugin<Number, Y>() {

    private val scrollEventFilter = Predicate<ScrollEvent> { it.isControlDown }
    private val zoomDuration = Duration.millis(500.0)

    init {
        this.registerScrollEventHandler(ScrollEvent.SCROLL, EventHandler { evt ->
            if (scrollEventFilter.test(evt)) {
                // note that delta values of all scroll events are the same thus we always zoom by a fixed amount
                val cursorXValue = toDataPoint(chartPane.chart.yAxis, getLocationInPlotArea(evt)).xValue.toDouble()
                if (evt.deltaY > 0) {
                    zoomIn(cursorXValue)
                } else {
                    zoomOut(cursorXValue)
                }
                evt.consume()
            }
        })
    }

    fun zoomIn(cursorXValue: Double = ((chartPane.chart.xAxis as ValueAxis).upperBound + (chartPane.chart.xAxis as ValueAxis).lowerBound) / 2.0) {
        val xAxis = chartPane.chart.xAxis as ValueAxis
        val leftPadding = cursorXValue - xAxis.lowerBound
        val rightPadding = xAxis.upperBound - cursorXValue
        val newLowerBound = cursorXValue - leftPadding * DEFAULT_ZOOM_IN_FACTOR
        val newUpperBound = cursorXValue + rightPadding * DEFAULT_ZOOM_IN_FACTOR
        zoomTo(newLowerBound..newUpperBound)
    }

    fun zoomOut(cursorXValue: Double = ((chartPane.chart.xAxis as ValueAxis).upperBound + (chartPane.chart.xAxis as ValueAxis).lowerBound) / 2.0) {
        val xAxis = chartPane.chart.xAxis as ValueAxis
        val leftPadding = cursorXValue - xAxis.lowerBound
        val rightPadding = xAxis.upperBound - cursorXValue
        val newLowerBound = cursorXValue - leftPadding * DEFAULT_ZOOM_OUT_FACTOR
        val newUpperBound = cursorXValue + rightPadding * DEFAULT_ZOOM_OUT_FACTOR
        zoomTo(newLowerBound..newUpperBound)
    }

    fun zoomOrigin() {
        val dataRange = getDataRange()
        zoomTo(dataRange.start..dataRange.endInclusive)
    }

    fun zoomTo(range: ClosedRange<Double>) {
        // prevent zooming out more than necessary
        val currentDataRange = getDataRange()
        val adjustedRange = range.coerceIn(currentDataRange)

        doSynchronized {
            // when zooming all the way out, enable automatic zoom adjustment on replots (note: simply enabling xaxis autoranging doesn't work for some reason...)
            automaticallyAdjustZoomLevelToDatasetProperty.set(adjustedRange == currentDataRange)

            // update all synchronized xaxes to the given range
            val xAxis = chartPane.chart.xAxis as ValueAxis
            xAxis.isAutoRanging = false
            if (!Axes.hasBoundedRange(xAxis)) {
                xAxis.upperBound = adjustedRange.endInclusive
                xAxis.lowerBound = adjustedRange.start
            }
        }
    }

    companion object {
        private const val DEFAULT_ZOOM_IN_FACTOR = 0.9   // i.e. zooming in by 10% on each scroll event
        private const val DEFAULT_ZOOM_OUT_FACTOR = 1.1   // i.e. zooming out by 10% on each scroll event
    }
}
