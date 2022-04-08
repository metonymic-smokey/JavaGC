package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.treetrends.web

import at.jku.anttracks.gui.component.antwebview.AntWebViewBridge
import javafx.scene.web.WebEngine

class HeapEvolutionVisualizationWebTreeBridge() : AntWebViewBridge {
    override lateinit var webEngine: WebEngine
    override val bridgeJS: String
        get() = "window.bridge"

    override fun injectIntoEngine() = setGlobal("bridge", this)

    fun initTreeViz(treeString: String) {
        executeScript("window.loadData(${treeString.replace("'", "#")});")
    }

    fun getWindowWidth(): Double {
        return executeScript("window.innerWidth").toString().toDouble()
    }
}