package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes

import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisActionItem
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisBridge
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.extensions.*
import at.jku.anttracks.util.asSequence
import at.jku.anttracks.util.toBitSet

class GraphVisObjectNode(val objectId: Int, bridge: GraphVisBridge) : GraphVisNode(bridge) {
    private val rootPtr = bridge.heap.getRoots(objectId)

    override fun getIdentifier(): String {
        return objectId.toString()
    }

    override fun getLabel(): String {
        return bridge.heap.getObjectInfo(objectId).type.simpleName
    }

    override fun getStyle(): String {
        return ""
    }

    override fun getActionItems(): Array<GraphVisActionItem> {
        return super.getActionItems().plus(listOf(/*TO_GROUP_NODE,*/
                GraphVisActionItem(FROM_POINTERS),
                GraphVisActionItem(TO_POINTERS),
                /*GC_ROOTS,*/
                GraphVisActionItem(PATH_TO_CLOSEST_ROOT),
                GraphVisActionItem(PATH_TO_CLOSEST_ROOTS)).toTypedArray())
    }

    override fun onActionItemSelected(action: String): GraphVisBridge.GraphVisUpdate {
        val update = super.onActionItemSelected(action)
        when (action) {
            /*
            TO_GROUP_NODE -> {
                update.deleteNodes(this)
                update.addNodes(GraphVisObjectGroupNode(listOf(objectId).toBitSet(), bridge))
            }
            */
            TO_POINTERS -> {
                val nodeObjectIds = this.getToPointers().asSequence().asIterable()
                val nodes = nodeObjectIds.take(GraphVisBridge.MAX_UNCOLLAPSED).map { GraphVisObjectNode(it, bridge) }
                val collapsedNodes = nodeObjectIds.drop(GraphVisBridge.MAX_UNCOLLAPSED)
                if (collapsedNodes.isNotEmpty()) {
                    val collapseNode = GraphVisCollapseNode(collapsedNodes.toBitSet(), bridge)
                    update.addNodes(collapseNode)
                }
                update.addNodes(nodes)
            }
            FROM_POINTERS -> {
                val nodeObjectIds = this.getFromPointers().asSequence().asIterable()
                val nodes = nodeObjectIds.take(GraphVisBridge.MAX_UNCOLLAPSED).map { GraphVisObjectNode(it, bridge) }
                val collapsedNodes = nodeObjectIds.drop(GraphVisBridge.MAX_UNCOLLAPSED)
                if (collapsedNodes.isNotEmpty()) {
                    val collapseNode = GraphVisCollapseNode(collapsedNodes.toBitSet(), bridge)
                    update.addNodes(collapseNode)
                }
                update.addNodes(nodes)
            }
            /*
            GC_ROOTS -> {
                val roots = rootPtr.map { GraphVisRootNode(it, bridge) }
                update.addNodes(roots)
            }
            */
            PATH_TO_CLOSEST_ROOT -> {
                val nodes = bridge.heap.getPathToClosestRoot(objectId).map { GraphVisObjectNode(it, bridge) }
                update.addNodes(nodes)
            }
            PATH_TO_CLOSEST_ROOTS -> {
                val paths = bridge.heap.getPathsToClosestRoots(objectId)
                val nodes = paths.flatten().distinct().associateWith { objId -> GraphVisObjectNode(objId, bridge) }
                update.addNodes(nodes.values)
            }
        }
        return update
    }
}