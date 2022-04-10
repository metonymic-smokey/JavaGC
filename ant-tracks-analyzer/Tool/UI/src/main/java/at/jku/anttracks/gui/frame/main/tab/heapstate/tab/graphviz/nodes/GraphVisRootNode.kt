package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes

import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisActionItem
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisBridge
import at.jku.anttracks.heap.roots.RootPtr

class GraphVisRootNode(val rootInfo: RootPtr, bridge: GraphVisBridge) : GraphVisNode(bridge) {
    override fun getIdentifier(): String {
        return "${rootInfo.rootType}-${rootInfo.idx}"
    }

    override fun getActionItems(): Array<GraphVisActionItem> {
        return arrayOf()
    }

    override fun getLabel(): String {
        return rootInfo.toGraphString()
    }

    override fun getStyle(): String {
        return if (rootInfo.rootType == RootPtr.RootType.STATIC_FIELD_ROOT) "fill: orange;" else "fill: yellow;"
    }
}