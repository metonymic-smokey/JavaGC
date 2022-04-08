package at.jku.anttracks.gui.chart.jfreechart.base

import at.jku.anttracks.gui.model.ChartSelection
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import org.jfree.chart.entity.XYItemEntity
import org.jfree.chart.fx.ChartCanvas
import org.jfree.chart.fx.ChartViewer
import org.jfree.chart.fx.interaction.AbstractMouseHandlerFX
import org.jfree.chart.fx.interaction.PanHandlerFX
import org.jfree.chart.renderer.xy.StackedXYAreaRenderer2

/**
 * chartViewer represents the JavaFX container for a JFreeChart.
 * it contains the following parts:
 * chartViewer.getCanvas()                         ... painting canvas, used to add overlays
 * chartViewer.getChart()                          ... the JFreeChart
 * chartViewer.getChart().getXYPlot()              ... the chart's plot
 * chartViewer.getChart().getXYPlot().getDataset() ... the chart's dataset
 */
class AntChartViewer : ChartViewer() {
    private var alreadyAddedListeners = false

    private inner class AntChartSynchronizingMouseHandler : AbstractMouseHandlerFX("ClickListener", false, false, false, false) {
        private var isInDragMotion = false

        override fun handleMouseDragged(canvas: ChartCanvas?, e: MouseEvent?) {
            isInDragMotion = true
        }

        override fun handleMouseReleased(canvas: ChartCanvas?, e: MouseEvent?) {
            if (isInDragMotion) {
                // ignore click that happened after drag
                isInDragMotion = false
                return
            }

            if (e!!.button == MouseButton.PRIMARY && chart is AntJFreeChart && (chart as AntJFreeChart).synchronizer != null) {
                val info = canvas!!.renderingInfo ?: return
                val entities = info.entityCollection ?: return

                println("x ${e.x} y ${e.y} clicked")

                val entity = entities.getEntity(e.x, info.plotInfo.dataArea.maxY - 0.5) ?: return

                if (entity is XYItemEntity) {
                    val plot = chart.xyPlot

                    val series = entity.seriesIndex
                    val itemId = entity.item
                    val time = entity.dataset.getXValue(series, itemId)
                    var height = entity.dataset.getYValue(series, itemId)
                    // Check if we have stacked renderer. If so, we must
                    // calculate the summed height of all series below the selected
                    // one.
                    var stackedRenderer: StackedXYAreaRenderer2? = null
                    for (i in 0 until plot.rendererCount) {
                        if (plot.getRenderer(i) is StackedXYAreaRenderer2) {
                            stackedRenderer = plot.getRenderer(i) as StackedXYAreaRenderer2
                        }
                    }
                    if (stackedRenderer != null) {
                        for (seriesIndex in 0 until series) {
                            height += entity.dataset.getYValue(seriesIndex, itemId)
                        }
                    }

                    val selection = ChartSelection(series, itemId, time, height, (chart as AntJFreeChart).chartID)
                    (chart as AntJFreeChart).synchronizer.select(selection)
                }
            }
        }
    }

    fun setChart(chart: AntJFreeChart) {
        super.setChart(chart)

        if (!alreadyAddedListeners) {
            canvas.addAuxiliaryMouseHandler(AntChartSynchronizingMouseHandler())
            canvas.addMouseHandler(PanHandlerFX("Pan Mouse Listener", false, true, false, false))
            // Disable zoom via scrolling
            // A ScrollHandlerFX with the id "scroll" automatically gets added in the constructor of Canvas.
            canvas.removeAuxiliaryMouseHandler(canvas.getMouseHandler("scroll"))
            alreadyAddedListeners = true
        }
    }
}