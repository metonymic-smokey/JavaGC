package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.links

import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisBridge
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.extensions.containedObjects
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.extensions.getCachedToPointers
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.extensions.getGcClosureByteCount
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes.GraphVisNode

class GraphVisLink(val source: GraphVisNode,
                   val target: GraphVisNode,
                   val label: String,
                   val bridge: GraphVisBridge) {
    var weight: Double? = null
    var number: Double? = null

    fun getUniqueIdentifier(): String {
        return getUniqueIdentifier(source, target)
    }

    override fun equals(other: Any?): Boolean {
        if (other is GraphVisLink) {
            return this.getUniqueIdentifier() == other.getUniqueIdentifier()
        }
        return false
    }

    override fun hashCode(): Int {
        return getUniqueIdentifier().hashCode()
    }

    fun toDotGraphLink(): String {
        // TODO switch to html labelType once multi-line has been figured out
        //return "\"${source.id}\"->\"${target.id}\"[labelType=\"html\" label=\"$label\", arrowhead=\"vee\"]"
        return "\"${source.id}\"->\"${target.id}\"[label=\"$label\", arrowhead=\"vee\", labelStyle=\"font-weight: bold;\"]"
    }

    fun calculateWeight(weightBasedOn: String?): Double {
        val reference = if (weightBasedOn == null) {
            bridge.heap.byteCount.toDouble()
        } else {
            // TODO No list lookup
            bridge.getNodes().find { it.getUniqueIdentifier() == weightBasedOn }!!.containedObjects().cardinality().toDouble()
        }

        val linkValue = if (weightBasedOn == null) {
            bridge.heap.getGcClosureByteCount(target.containedObjects(source.getCachedToPointers())).toDouble()
        } else {
            val reachableObjects = bridge.heap.transitiveClosure(target.containedObjects(source.getCachedToPointers()), null)
            reachableObjects.and(bridge.getNodes().find { it.getUniqueIdentifier() == weightBasedOn }!!.containedObjects())
            reachableObjects.cardinality().toDouble()
        }

        if (weight == null) {
            weight = 1 + 5 * linkValue / reference
            number = linkValue
        }
        return weight!!
    }

    companion object {
        fun getUniqueIdentifier(source: GraphVisNode, target: GraphVisNode): String {
            return "${source.getUniqueIdentifier()}->${target.getUniqueIdentifier()}"
        }
    }
}