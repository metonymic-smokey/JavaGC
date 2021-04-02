package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes

import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisActionItem
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisBridge
import at.jku.anttracks.util.toBitSet
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.util.toString

class GraphVisRootGroupNode(val rootType: RootPtr.RootType, val rootInfos: Set<RootPtr>, bridge: GraphVisBridge) : GraphVisNode(bridge) {
    val containedNodes = rootInfos.map { it.idx }.toBitSet()

    override fun getIdentifier(): String {
        return "${rootType.stringRep}-${containedNodes.hashCode()}"
    }

    override fun getActionItems(): Array<GraphVisActionItem> {
        return arrayOf()
    }

    override fun getLabel(): String {
        return "${rootType.stringRep} (${rootInfos.size.toString("%,d")} roots)"
    }

    override fun getLabelStyle(): String {
        return "font-weight: bold;"
    }

    override fun getStyle(): String {
        return if (rootType == RootPtr.RootType.STATIC_FIELD_ROOT) "fill: orange;" else "fill: yellow;"
    }
}