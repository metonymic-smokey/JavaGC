package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes

import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisBridge

class GraphVisDefaultNode(bridge: GraphVisBridge) : GraphVisNode(bridge) {

    override fun getIdentifier(): String {
        return "default"
    }
}