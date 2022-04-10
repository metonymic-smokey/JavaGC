package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes

import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisActionItem
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisBridge
import at.jku.anttracks.util.asSequence
import java.util.*

class GraphVisCollapseNode(containedNodes: BitSet, bridge: GraphVisBridge) : GraphVisNode(bridge) {
    internal val containedNodes = containedNodes.clone() as BitSet

    override fun getIdentifier(): String {
        return containedNodes.hashCode().toString()
    }

    override fun getLabel(): String {
        return "•••\n(${containedNodes.cardinality()} obj. hidden)"
    }

    override fun getActionItems(): Array<GraphVisActionItem> {
        return super.getActionItems().plus(arrayOf(GraphVisActionItem(EXTRACT_FEW) /*, EXTRACT_ALL*/))
    }

    override fun onActionItemSelected(action: String): GraphVisBridge.GraphVisUpdate {
        val update = super.onActionItemSelected(action)
        when (action) {
            EXTRACT_FEW -> run {
                val nodes = containedNodes.asSequence().take(GraphVisBridge.MAX_UNCOLLAPSED)
                        .map { GraphVisObjectNode(it, bridge) }
                        .onEach { containedNodes.clear(it.objectId) }
                        .toSet()
                update.addNodes(nodes)
            }
            /*
            EXTRACT_ALL -> run {
                val nodes = containedNodes.toIterable()
                        .map { GraphVisObjectNode(it, bridge) }
                        .onEach { containedNodes.clear(it.objectId) }
                update.addNodes(nodes)
            }
            */
        }

        if (containedNodes.isEmpty) {
            update.deleteNodes(this)
        }
        return update
    }
}