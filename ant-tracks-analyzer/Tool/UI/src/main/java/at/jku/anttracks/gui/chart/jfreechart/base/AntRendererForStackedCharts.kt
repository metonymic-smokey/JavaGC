package at.jku.anttracks.gui.chart.jfreechart.base

import at.jku.anttracks.gui.chart.base.ChartSynchronizer
import at.jku.anttracks.gui.model.ChartSelection
import javafx.application.Platform
import org.jfree.chart.plot.IntervalMarker
import org.jfree.chart.plot.Marker
import org.jfree.chart.plot.ValueMarker
import org.jfree.chart.plot.XYPlot
import org.jfree.chart.renderer.xy.StackedXYAreaRenderer
import org.jfree.chart.renderer.xy.XYAreaRenderer
import org.jfree.data.Range
import org.jfree.data.xy.TableXYDataset
import org.jfree.data.xy.XYDataset
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Shape
import java.awt.geom.Rectangle2D
import java.util.*

fun updateMarkers(sender: ChartSynchronizer, plot: XYPlot, markers: ArrayList<Marker>) {
    markers.forEach { m -> plot.removeDomainMarker(m) }
    markers.clear()

    if (sender.selection.size() == 0) {
        return
    }
    val marker: Marker
    if (sender.selection.size() == 1) {
        marker = ValueMarker(sender.selection[0].x, Color.BLACK, BasicStroke(2.5f))
    } else {
        val x_0 = sender.selection[0].x
        val x_1 = sender.selection[1].x

        val x_min = Math.min(x_0, x_1)
        val x_max = Math.max(x_0, x_1)

        marker = IntervalMarker(x_min, x_max, Color.YELLOW, BasicStroke(1f), Color.BLACK, BasicStroke(2.5f), 0.4f)
    }
    markers.add(marker)
    markers.forEach { m -> plot.addDomainMarker(m) }
}

class AntRendererForStackedCharts(synchronizer: ChartSynchronizer?,
                                  supportSelection: Boolean = true) : StackedXYAreaRenderer(XYAreaRenderer.SHAPES) {
    private val nullRectangle = Rectangle2D.Float(0f, 0f, 0f, 0f)

    private val markers = ArrayList<Marker>()

    init {
        if (supportSelection) {
            synchronizer?.addSelectionListener(object : ChartSynchronizer.SelectionListener {
                override fun selectionChanged(sender: ChartSynchronizer, selection: ChartSelection?) {
                    Platform.runLater {
                        updateMarkers(sender, plot, markers)
                    }
                }
            })
        }
    }

    /* Do not draw any shape */
    override fun getItemShape(row: Int, column: Int): Shape {
        return nullRectangle
    }

    override fun getDataBoundsIncludesVisibleSeriesOnly(): Boolean {
        return true
    }

    override fun findRangeBounds(dataset: XYDataset?): Range? {
        if (dataset == null) {
            return null
        }
        var min = java.lang.Double.POSITIVE_INFINITY
        var max = java.lang.Double.NEGATIVE_INFINITY
        val d = dataset as TableXYDataset?
        val itemCount = d!!.itemCount
        for (i in 0 until itemCount) {
            val x = dataset.getXValue(0, i)
            val domainAxis = plot.domainAxis
            if (x >= domainAxis.lowerBound && x <= domainAxis.upperBound) {
                val stackValues = getStackValues(dataset, d.seriesCount, i)
                min = Math.min(min, stackValues[0])
                max = Math.max(max, stackValues[1])
            }
        }
        return if (min == java.lang.Double.POSITIVE_INFINITY) {
            null
        } else Range(min, max)
    }

    private fun getStackValues(dataset: TableXYDataset, series: Int, index: Int): DoubleArray {
        val result = DoubleArray(2)
        for (i in 0 until series) {
            val v = dataset.getYValue(i, index)
            if (!java.lang.Double.isNaN(v)) {
                if (v >= 0.0) {
                    result[1] += v
                } else {
                    result[0] += v
                }
            }
        }
        return result
    }
}