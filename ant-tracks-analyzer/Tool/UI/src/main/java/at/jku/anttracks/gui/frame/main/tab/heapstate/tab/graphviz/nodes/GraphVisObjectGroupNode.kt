package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes

import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisActionItem
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisBridge
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.extensions.*
import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.util.andNew
import at.jku.anttracks.util.asSequence
import at.jku.anttracks.util.toString
import java.util.*

const val RESTRICT_TYPED_NODES_COUNT = false

class GraphVisObjectGroupNode(containedNodes: BitSet, bridge: GraphVisBridge) : GraphVisNode(bridge) {
    internal val containedNodes = containedNodes.clone() as BitSet

    override fun getIdentifier(): String {
        return containedNodes.hashCode().toString()
    }

    override fun getLabel(): String {
        val typeCounts = containedNodes.asSequence().map { bridge.heap.getObjectInfo(it) }
                .groupingBy { it.type.simpleName }.eachCount().toList().sortedByDescending { it.second }
        val othersCount = typeCounts.drop(2).map { it.second }.sum()
        if (othersCount > 0) {
            return typeCounts.take(2).joinToString(separator = "\n", postfix = "\nothers\n(${othersCount.toString("%,d")} obj.)", transform = { "${it.first} (${it.second.toString("%,d")} obj.)" })
        }
        return typeCounts.joinToString(separator = "\n", transform = { "${it.first} (${it.second.toString("%,d")} obj.)" })
    }

    override fun getLabelStyle(): String {
        return "font-family: monospace; font-weight: bold;"
    }

    override fun getStyle(): String {
        val rootPointed = containedNodes.asSequence().map { bridge.heap.getRoot(it) }
                .filter { it != null && it.isNotEmpty() }.flatten()
                .count() > 0
        //return if (rootPointed) "stroke: red" else ""
        return ""
    }

    override fun getActionItems(): Array<GraphVisActionItem> {
        return super.getActionItems()
                .plus(arrayOf(
                        /*
                        "To (shared)",
                        "From (shared)",
                        */
                        GraphVisActionItem(SHOW_KEEP_ALIVE_EDGES),
                        GraphVisActionItem(EXTRACT_GROUP,
                                           arrayOf(GraphVisActionItem(EXTRACT_FEW),
                                                   GraphVisActionItem(EXTRACT_ALL))),
                        GraphVisActionItem(NEIGHBORS_GROUP,
                                           arrayOf(GraphVisActionItem(TO_TYPED),
                                                   GraphVisActionItem(FROM_TYPED))),
                        GraphVisActionItem(ROOTS_GROUP,
                                           arrayOf(GraphVisActionItem(PATH_TO_CLOSEST_ROOT),
                                                   GraphVisActionItem(PATH_TO_STRONGEST_ROOT_TYPED),
                                                   GraphVisActionItem(PATH_TO_CLOSEST_ROOTS_TYPED),
                                                   GraphVisActionItem(PATH_TO_ROOTS_TYPED),
                                                   GraphVisActionItem(PATH_TO_MOST_INTERESTING_ROOTS_TYPED))),
                        GraphVisActionItem(CLOSURES_GROUP,
                                           arrayOf(GraphVisActionItem(TRANSITIVE_CLOSURE)))))
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
            EXTRACT_ALL -> run {
                val nodes = containedNodes.asSequence()
                        .map { GraphVisObjectNode(it, bridge) }
                        .onEach { containedNodes.clear(it.objectId) }
                        .toSet()
                update.addNodes(nodes)
            }
            TO_TYPED -> run {
                val groupNodeObjectIds = bridge.heap.getTypedToPointers(containedNodes).values.sortedByDescending { it.cardinality() }
                if (RESTRICT_TYPED_NODES_COUNT) {
                    val groupNodes = groupNodeObjectIds.take(GraphVisBridge.MAX_UNCOLLAPSED).map { GraphVisObjectGroupNode(it, bridge) }
                    val othersGroupNodeObjectIds = groupNodeObjectIds.drop(GraphVisBridge.MAX_UNCOLLAPSED).plus(BitSet()).reduce { b1, b2 ->
                        run {
                            b1.or(b2)
                            b1
                        }
                    }
                    if (!othersGroupNodeObjectIds.isEmpty) {
                        val othersGroupNode = GraphVisObjectGroupNode(othersGroupNodeObjectIds, bridge)
                        update.addNodes(othersGroupNode)
                    }
                    update.addNodes(groupNodes)
                } else {
                    update.addNodes(groupNodeObjectIds.map { GraphVisObjectGroupNode(it, bridge) })
                }
            }
            FROM_TYPED -> run {
                val groupNodeObjectIds = bridge.heap.getTypedFromPointers(containedNodes).values.sortedByDescending { it.cardinality() }
                val groupNodes = groupNodeObjectIds.take(GraphVisBridge.MAX_UNCOLLAPSED_TYPED_NODES).map { GraphVisObjectGroupNode(it, bridge) }
                val othersGroupNodeObjectIds = groupNodeObjectIds.drop(GraphVisBridge.MAX_UNCOLLAPSED_TYPED_NODES).plus(BitSet()).reduce { b1, b2 ->
                    run {
                        b1.or(b2)
                        b1
                    }
                }
                if (!othersGroupNodeObjectIds.isEmpty) {
                    val othersGroupNode = GraphVisObjectGroupNode(othersGroupNodeObjectIds, bridge)
                    update.addNodes(othersGroupNode)
                }
                update.addNodes(groupNodes)
            }
            PATH_TO_CLOSEST_ROOT -> run {
                val nodes = containedNodes.asSequence()
                        .map { bridge.heap.getPathToClosestRoot(it) }.filter { it.isNotEmpty() }.minBy { it.size }!!
                        .map { GraphVisObjectNode(it, bridge) }
                update.addNodes(nodes)
            }
            PATH_TO_STRONGEST_ROOT_TYPED -> run {
                update.addNodes(bridge.heap.getInterestingPathToRootTyped(containedNodes).map { GraphVisObjectGroupNode(it, bridge) })
            }
            PATH_TO_CLOSEST_ROOTS_TYPED -> run {
                update.addNodes(bridge.heap.getPathsToClosestRootsTyped(containedNodes).map { GraphVisObjectGroupNode(it, bridge) })
            }
            PATH_TO_ROOTS_TYPED -> run {
                update.addNodes(bridge.heap.getPathsToRootsTyped(containedNodes).map { GraphVisObjectGroupNode(it, bridge) })
            }
            PATH_TO_MOST_INTERESTING_ROOTS_TYPED -> run {
                update.addNodes(bridge.heap.getPathsToMostInterestingRootsTyped(containedNodes).map { GraphVisObjectGroupNode(it, bridge) })
            }
            SHOW_KEEP_ALIVE_EDGES -> run {
                update.labelsBasedOnExecutingNodeNode = true
            }
            TRANSITIVE_CLOSURE -> run {
                val typedTransitiveClosure = mutableMapOf<AllocatedType, BitSet>()

                bridge.heap.transitiveClosure(containedNodes, null).stream().forEach {
                    val type = bridge.heap.getType(it)
                    typedTransitiveClosure.getOrPut(type) { BitSet() }.set(it)
                }

                val othersNode = GraphVisObjectGroupNode(BitSet(), bridge)
                update.addNodes(othersNode)

                typedTransitiveClosure.forEach { _, bitset ->
                    if (bitset.cardinality() == 1) {
                        othersNode.containedNodes.or(bitset)
                    } else {
                        update.addNodes(GraphVisObjectGroupNode(bitset, bridge))
                    }
                }
            }
        }
        if (containedNodes.isEmpty) {
            update.deleteNodes(this)
        }
        return update
    }

    fun getCommonNodes(other: GraphVisObjectGroupNode) = containedNodes.andNew(other.containedNodes)
}
