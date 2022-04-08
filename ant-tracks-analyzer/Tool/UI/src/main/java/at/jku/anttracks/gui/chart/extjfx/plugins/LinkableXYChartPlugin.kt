package at.jku.anttracks.gui.chart.extjfx.plugins

import cern.extjfx.chart.XYChartPlugin
import javafx.event.EventHandler
import javafx.event.EventType
import javafx.geometry.Point2D
import javafx.scene.Node
import javafx.scene.input.ScrollEvent
import javafx.util.Pair

abstract class LinkableXYChartPlugin<X, Y> : XYChartPlugin<X, Y>() {

    private val linkedPlugins = mutableListOf<LinkableXYChartPlugin<X, Y>>()
    private val scrollEventHandlers = mutableListOf<Pair<EventType<ScrollEvent>, EventHandler<ScrollEvent>>>()

    init {
        this.chartPaneProperty().addListener { obs, oldChartPane, newChartPane ->
            this.removeScrollEventHandlers(oldChartPane)
            this.addScrollEventHandlers(newChartPane)
        }
    }

    private fun removeScrollEventHandlers(node: Node?) {
        if (node != null) {
            val iterator = scrollEventHandlers.iterator()

            while (iterator.hasNext()) {
                val pair = iterator.next()
                node.removeEventHandler(pair.key, pair.value)
            }

        }
    }

    private fun addScrollEventHandlers(node: Node?) {
        if (node != null) {
            val iterator = scrollEventHandlers.iterator()

            while (iterator.hasNext()) {
                val pair = iterator.next()
                node.addEventHandler(pair.key, pair.value)
            }

        }
    }

    protected fun registerScrollEventHandler(eventType: EventType<ScrollEvent>, handler: EventHandler<ScrollEvent>) {
        this.scrollEventHandlers.add(Pair(eventType, handler))
    }

    protected fun getLocationInPlotArea(event: ScrollEvent): Point2D {
        val mouseLocationInScene = Point2D(event.sceneX, event.sceneY)
        val xInAxis = this.chartPane.chart.xAxis.sceneToLocal(mouseLocationInScene).x
        val yInAxis = this.chartPane.chart.yAxis.sceneToLocal(mouseLocationInScene).y
        return Point2D(xInAxis, yInAxis)
    }

    fun <T : LinkableXYChartPlugin<X, Y>> T.doSynchronized(action: T.() -> Unit) {
        (linkedPlugins + this).forEach {
            action.invoke(it as T)  // works because linked plugins all have the same type!
        }
    }

    fun clearLinks() {
        linkedPlugins.clear()
    }

    companion object {
        fun link(plugins: List<LinkableXYChartPlugin<Number, Number>>) {
            plugins.forEach { plugin ->
                plugin.linkedPlugins.clear()
                // don't link plugins to themselves and only link plugins that have the same type
                plugin.linkedPlugins.addAll(plugins.filter { it != plugin && it.javaClass == plugin.javaClass })
            }
        }
    }

}