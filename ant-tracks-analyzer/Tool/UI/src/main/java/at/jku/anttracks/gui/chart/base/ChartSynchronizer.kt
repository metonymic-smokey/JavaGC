
package at.jku.anttracks.gui.chart.base

import at.jku.anttracks.gui.chart.jfreechart.base.AntJFreeChart
import at.jku.anttracks.gui.chart.jfreechart.xy.base.XYJFreeChartPane
import at.jku.anttracks.gui.model.ChartSelection
import javafx.application.Platform
import java.util.concurrent.CopyOnWriteArrayList

class ChartSynchronizer() {
    private val lock = Any()
    private val zoomListeners = CopyOnWriteArrayList<ZoomListener>()
    private val selectionListeners = CopyOnWriteArrayList<SelectionListener>()

    var zoomXUpperBound = java.lang.Double.MAX_VALUE
        private set
    var zoomXLowerBound = java.lang.Double.MIN_VALUE
        private set

    // lastSelection is set during .sync() if a new selection has been added. If
    // an existing selection has been removed, this will be null. If the
    // selection state has not been changed (nothing removed, nothing added)
    // lastSelection does not change
    var lastChartSelection: ChartSelection? = null
        private set

    private var selections = SelectedItemsList()
    val selection: SelectedItemsList
        get() = synchronized(lock) {
            return selections
        }

    fun setZoomXBounds(lowerBound: Double, upperBound: Double) {
        //val m = ApplicationStatistics.getInstance().createMeasurement("Chart Sync: Sync Zoom")
        synchronized(lock) {
            zoomXLowerBound = lowerBound
            zoomXUpperBound = upperBound
        }
        Platform.runLater {
            zoomListeners.forEach { l -> l.zoomChanged(this) }
        }
        //m.end()
    }

    fun select(selection: ChartSelection) {
        synchronized(lock) {
            //val m = ApplicationStatistics.getInstance().createMeasurement("Chart Sync: Sync Selection")
            // Clear selection if two points have been selected before
            if (selections.size() == 2) {
                selections.clear()
            }

            if (selections.containsItemInAnySeries(selection)) {
                // Clicked on already existing selection, do nothing
                return
            }

            selections.add(selection)
            lastChartSelection = selection
            selections.sort()

            Platform.runLater {
                selectionListeners.forEach { l -> l.selectionChanged(this, selection) }
            }
            //m.end()
        }
    }

    fun addZoomListener(zoomListener: ZoomListener?) {
        Platform.runLater {
            if (zoomListener != null) {
                zoomListeners.add(zoomListener)
                zoomListener.zoomChanged(this)
                zoomListener.domainChanged(this)
            }
        }
    }

    fun addSelectionListener(selectionListener: SelectionListener?) {
        Platform.runLater {
            if (selectionListener != null) {
                selectionListeners.add(selectionListener)
                selectionListener.selectionChanged(this, null)
            }
        }
    }

    fun clear() {
        synchronized(lock) {
            selections.clear()
            lastChartSelection = null
            Platform.runLater {
                zoomListeners.forEach { l -> l.zoomChanged(this) }
                zoomListeners.forEach { l -> l.domainChanged(this) }
                selectionListeners.forEach { l -> l.selectionChanged(this, null) }
            }
        }
    }

    fun select(chart: XYJFreeChartPane<*, *>, fromX: Long, toX: Long) {
        clear()
        // TODO selection should set values for series, item and y
        select(ChartSelection(0, 0, fromX.toDouble(), -1.0, (chart.chartViewer.chart as AntJFreeChart).chartID))
        select(ChartSelection(0, 1, toX.toDouble(), -1.0, (chart.chartViewer.chart as AntJFreeChart).chartID))
    }

    interface ZoomListener {
        fun zoomChanged(sender: ChartSynchronizer)

        fun domainChanged(sender: ChartSynchronizer)
    }

    interface SelectionListener {
        fun selectionChanged(sender: ChartSynchronizer, selection: ChartSelection?)
    }

}