package cern.extjfx.chart.plugins

import at.jku.anttracks.gui.utils.toNiceNumberString
import at.jku.anttracks.heap.GarbageCollectionCause
import cern.extjfx.chart.Axes
import cern.extjfx.chart.XYChartPane
import javafx.animation.KeyFrame
import javafx.animation.KeyValue
import javafx.animation.Timeline
import javafx.geometry.BoundingBox
import javafx.scene.chart.ValueAxis
import javafx.scene.text.TextAlignment
import javafx.util.Duration

open class AntTracksRangeIndicator<Y>(lowerBound: Double, upperBound: Double, text: String) : AbstractRangeValueIndicator<Number, Y>(lowerBound, upperBound, text) {

    init {
        label.isVisible = false
        label.textAlignment = TextAlignment.CENTER

        rectangle.isMouseTransparent = false
        rectangle.setOnMouseClicked { it.consume() }

        rectangle.hoverProperty().addListener { obs, wasHovered, isHovered ->
            if (chartPane != null) {
                val resizeAnimation = Timeline()
                if (isHovered) {
                    resizeAnimation.keyFrames
                            .setAll(KeyFrame(Duration.ZERO,
                                             KeyValue(rectangle.heightProperty(), rectangle.height)),
                                    KeyFrame(Duration(100.0),
                                             KeyValue(rectangle.heightProperty(), chartPane.plotAreaBounds.height)))
                    resizeAnimation.setOnFinished {
                        layoutLabel(rectangle.layoutBounds, 0.5, 0.5)
                        label.isVisible = true
                    }
                } else {
                    resizeAnimation.keyFrames
                            .setAll(KeyFrame(Duration.ZERO,
                                             KeyValue(rectangle.heightProperty(), rectangle.height)),
                                    KeyFrame(Duration(100.0),
                                             KeyValue(rectangle.heightProperty(), chartPane.plotAreaBounds.height * 0.05)))
                    label.isVisible = false
                    resizeAnimation.setOnFinished {
                        label.isVisible = false
                    }
                }
                resizeAnimation.play()
            }
        }
    }

    override fun updateStyleClass() {
    }

    override fun layoutChildren() {
        if (chartPane != null) {
            val plotAreaBounds = chartPane.plotAreaBounds
            val minX = plotAreaBounds.minX
            val maxX = plotAreaBounds.maxX
            val minY = plotAreaBounds.minY
            val maxY = plotAreaBounds.minY + plotAreaBounds.height * 0.05
            val xAxis = chartPane.chart.xAxis
            val startX = Math.max(minX, minX + xAxis.getDisplayPosition(lowerBound))
            val endX = Math.min(maxX, minX + xAxis.getDisplayPosition(upperBound))
            this.layout(BoundingBox(startX, minY, endX - startX, maxY - minY))
        }
    }

    override fun getValueAxis(chartPane: XYChartPane<Number, Y>): ValueAxis<*> = Axes.toValueAxis<Number>(chartPane.chart.xAxis)

    fun pixelWidth(xAxis: ValueAxis<Number>) = AntTracksRangeIndicator.pixelWidth(lowerBound, upperBound, xAxis)

    companion object {
        fun pixelWidth(lowerBound: Double, upperBound: Double, xAxis: ValueAxis<Number>) =
                xAxis.getDisplayPosition(upperBound) - xAxis.getDisplayPosition(lowerBound)
    }
}

class GCRangeIndicator<Y>(lowerBound: Double,
                          upperBound: Double,
                          gcId: Short,
                          majorGC: Boolean,
                          gcCause: GarbageCollectionCause) :
        AntTracksRangeIndicator<Y>(lowerBound,
                                   upperBound,
                                   """GC #$gcId
                                    | took ${toNiceNumberString(upperBound.toLong() - lowerBound.toLong())}ms
                                    | was a ${if (majorGC) {
                                       "Major"
                                   } else {
                                       "Minor"
                                   }} GC
                                    | triggered by ${gcCause.name}""".trimMargin()) {
    init {
        rectangle.styleClass.setAll("gc-range-indicator-rect")
        label.styleClass.setAll("gc-range-indicator-label")
    }
}

class MutatorRangeIndicator<Y>(lowerBound: Double,
                               upperBound: Double) :
        AntTracksRangeIndicator<Y>(lowerBound,
                                   upperBound,
                                   """App running
                                       | for ${toNiceNumberString(upperBound.toLong() - lowerBound.toLong())}ms""".trimMargin()) {
    init {
        rectangle.styleClass.setAll("mutator-range-indicator-rect")
        label.styleClass.setAll("mutator-range-indicator-label")
    }
}

class ReducedRangeIndicator<Y>(lowerBound: Double,
                               upperBound: Double,
                               val hiddenGCs: Int) :
        AntTracksRangeIndicator<Y>(lowerBound,
                                   upperBound,
                                   """$hiddenGCs GCs occurred over this time frame
                                       |Zoom in to see more details""".trimMargin()) {

    init {
        rectangle.styleClass.setAll("reduced-range-indicator-rect")
        label.styleClass.setAll("reduced-range-indicator-label")
    }

}