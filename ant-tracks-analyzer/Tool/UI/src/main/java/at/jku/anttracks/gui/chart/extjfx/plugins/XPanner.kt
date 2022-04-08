package at.jku.anttracks.gui.chart.extjfx.plugins

import at.jku.anttracks.util.contains
import at.jku.anttracks.util.plus
import javafx.beans.property.BooleanProperty
import javafx.geometry.Point2D
import javafx.scene.chart.ValueAxis
import javafx.scene.input.MouseEvent
import java.util.function.Predicate

class XPanner<Y>(val automaticallyAdjustZoomLevelToDataset: BooleanProperty, val getDataRange: () -> ClosedRange<Double>) : LinkableXYChartPlugin<Number, Y>() {

    private val mouseEventFilter = Predicate<MouseEvent> { evt -> evt.isPrimaryButtonDown && evt.isControlDown || evt.isMiddleButtonDown }
    private var previousCursorPosition: Point2D? = null

    init {
        registerMouseEventHandler(MouseEvent.MOUSE_PRESSED) { evt ->
            if (mouseEventFilter.test(evt)) {
                doSynchronized {
                    previousCursorPosition = this.getLocationInPlotArea(evt)
                }
                evt.consume()
            }
        }

        registerMouseEventHandler(MouseEvent.MOUSE_DRAGGED) { evt ->
            if (mouseEventFilter.test(evt)) {
                val cursorDataPoint = toDataPoint(chartPane.chart.yAxis, getLocationInPlotArea(evt))
                val previousCursorDataPoint = toDataPoint(chartPane.chart.yAxis, previousCursorPosition)
                val deltaX = previousCursorDataPoint!!.xValue.toDouble() - cursorDataPoint.xValue.toDouble()
                val xAxis = chartPane.chart.xAxis as ValueAxis
                val newXRange = (xAxis.lowerBound..xAxis.upperBound) + deltaX

                if (getDataRange().contains(newXRange))
                    doSynchronized {
                        automaticallyAdjustZoomLevelToDataset.set(false)
                        val xAxis = chartPane.chart.xAxis as ValueAxis
                        xAxis.isAutoRanging = false
                        xAxis.upperBound = newXRange.endInclusive
                        xAxis.lowerBound = newXRange.start
                    }

                previousCursorPosition = getLocationInPlotArea(evt)
                evt.consume()
            }
        }
    }
}