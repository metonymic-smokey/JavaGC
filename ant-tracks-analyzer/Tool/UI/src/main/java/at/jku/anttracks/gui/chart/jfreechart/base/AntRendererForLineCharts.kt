package at.jku.anttracks.gui.chart.jfreechart.base

import at.jku.anttracks.gui.chart.base.ChartSynchronizer
import at.jku.anttracks.gui.model.ChartSelection
import javafx.application.Platform
import org.jfree.chart.plot.Marker
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer
import java.awt.geom.Rectangle2D
import java.util.*

class AntRendererForLineCharts(
        synchronizer: ChartSynchronizer?,
        supportSelection: Boolean) : XYLineAndShapeRenderer(false, true) {
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

    override fun getDataBoundsIncludesVisibleSeriesOnly(): Boolean {
        return true
    }

    /* do not draw shapes */
    override fun getItemShapeVisible(series: Int, item: Int): Boolean {
        return false
    }
}