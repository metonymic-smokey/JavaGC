package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.extensions

import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes.*
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.util.andNew
import at.jku.anttracks.util.asSequence
import at.jku.anttracks.util.toBitSet
import java.util.*

private val fromPointerCache = mutableMapOf<GraphVisNode, BitSet>()
private val toPointerCache = mutableMapOf<GraphVisNode, BitSet>()

data class NodePair(val node1: GraphVisNode, val node2: GraphVisNode)
data class OverlapInfo(val objectsOfNode1OverlapWithPointedFromOfNode2: BitSet, val objectsOfNode2OverlapWithPointsToOfNode1: BitSet)
data class NodePairOverlapInfo(val nodePair: NodePair, val overlapInfo: OverlapInfo)

fun clearPointerCaches() {
    fromPointerCache.clear()
    toPointerCache.clear()
}

fun GraphVisNode.getCachedFromPointers(): BitSet {
    return fromPointerCache.getOrPut(this, { this.getFromPointers() })
}

fun GraphVisNode.getCachedToPointers(): BitSet {
    return toPointerCache.getOrPut(this, { this.getToPointers() })
}

fun Iterable<GraphVisNode>.getCombinationsWith(others: Iterable<GraphVisNode>): Iterable<NodePair> {
    return this.flatMap { node -> others.filter { other -> node != other }.flatMap { other -> setOf(NodePair(node, other), NodePair(other, node)) } }.toSet()
}

fun GraphVisNode.isRootNode(): Boolean {
    return when (this) {
        is GraphVisRootNode -> true
        is GraphVisRootGroupNode -> true
        else -> false
    }
}

fun GraphVisNode.containedObjects(): BitSet {
    return when (this) {
        is GraphVisObjectNode -> setOf(this.objectId).toBitSet()
        is GraphVisObjectGroupNode -> this.containedNodes
        is GraphVisCollapseNode -> this.containedNodes
        is GraphVisRootGroupNode -> this.containedNodes
        is GraphVisRootNode -> setOf(this.rootInfo.idx).toBitSet()
        else -> BitSet()
    }
}

fun GraphVisNode.containedObjects(objectIds: BitSet): BitSet {
    return objectIds.andNew(when (this) {
                                is GraphVisObjectNode -> setOf(this.objectId).toBitSet()
                                is GraphVisObjectGroupNode -> this.containedNodes
                                is GraphVisCollapseNode -> this.containedNodes
                                else -> BitSet()
                            })
}

fun GraphVisNode.getFromPointers(): BitSet {
    return when (this) {
        is GraphVisObjectNode -> bridge.heap.getFromPointerOnlyDataStructureHeads(this.objectId)
        is GraphVisObjectGroupNode -> bridge.heap.getFromPointerOnlyDataStructureHeads(this.containedNodes)
        is GraphVisCollapseNode -> bridge.heap.getFromPointerOnlyDataStructureHeads(this.containedNodes)
        else -> BitSet()
    }
}

fun GraphVisNode.getToPointers(): BitSet {
    return when (this) {
        is GraphVisObjectNode -> bridge.heap.getToPointerOnlyDataStructureHeads(this.objectId)
        is GraphVisObjectGroupNode -> bridge.heap.getToPointerOnlyDataStructureHeads(this.containedNodes)
        is GraphVisCollapseNode -> bridge.heap.getToPointerOnlyDataStructureHeads(this.containedNodes)
        is GraphVisRootNode -> {
            BitSet().also { it.set(rootInfo.idx) }
        }
        is GraphVisRootGroupNode -> {
            BitSet().also { bs ->
                rootInfos.map { ri -> ri.idx }.forEach { idx -> bs.set(idx) }
            }
        }
        else -> BitSet()
    }
}

fun GraphVisNode.getTypedRootPointers(): Map<RootPtr.RootType, Set<RootPtr>> {
    val result = mutableMapOf<RootPtr.RootType, Set<RootPtr>>()
    INTERESTING_ROOT_TYPES.forEach { result[it] = mutableSetOf() }
    getRootPointers().forEach { (result[it.rootType] as MutableSet<RootPtr>).add(it) }
    result.entries.removeIf { it.value.isEmpty() }
    return result
}

fun BitSet.transitiveClosure(heap: IndexBasedHeap) = heap.transitiveClosure(this, null)

private fun GraphVisNode.getRootPointers(): Set<RootPtr> {
    val roots = mutableSetOf<RootPtr>()
    when (this) {
        is GraphVisObjectNode -> roots.addAll(bridge.heap.getRoots(this.objectId))
        is GraphVisObjectGroupNode -> this.containedNodes.asSequence()
                .forEach { roots.addAll(bridge.heap.getRoots(it)) }
        is GraphVisCollapseNode -> this.containedNodes.asSequence()
                .forEach { roots.addAll(bridge.heap.getRoots(it)) }
        else -> {
        }
    }
    return roots
}

internal fun GraphVisNode.getNodesInTreeOf(): Collection<GraphVisNode> {
    val result = mutableSetOf<GraphVisNode>()
    val nodesToProcess = mutableListOf(this)
    while (nodesToProcess.isNotEmpty()) {
        val nodeToProcess = nodesToProcess.removeAt(0)
        if (result.add(nodeToProcess)) {
            nodesToProcess.addAll(getAdjacentNodes(nodeToProcess))
        }
    }
    return result
}

private fun GraphVisNode.getAdjacentNodes(node: GraphVisNode): Collection<GraphVisNode> {
    return bridge.getLinks().filter { it.source == node || it.target == node }.map { if (it.source == node) it.target else it.source }.toSet()
}