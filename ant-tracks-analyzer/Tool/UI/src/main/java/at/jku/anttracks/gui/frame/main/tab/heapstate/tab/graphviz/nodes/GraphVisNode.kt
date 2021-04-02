package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes

import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisActionItem
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.GraphVisBridge
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.extensions.getNodesInTreeOf
import at.jku.anttracks.util.safe

const val HIDE_GROUP = "Hide..."
const val HIDE_NODE = "Hide this node"
const val HIDE_TREE = "Hide tree"
const val HIDE_OTHERS = "Hide other nodes"
const val HIDE_OTHER_TREES = "Hide other trees"

const val ROOTS_GROUP = "GC roots..."
const val PATH_TO_CLOSEST_ROOT = "Path to single closest GC root"
const val PATH_TO_CLOSEST_ROOTS = "Path to all closest GC roots"
const val PATH_TO_STRONGEST_ROOT_TYPED = "Paths to strongest  GC roots \n(group path objects by type)"
const val PATH_TO_CLOSEST_ROOTS_TYPED = "Paths to all closest GC roots \n(group path objects by type)"
const val PATH_TO_ROOTS_TYPED = "Paths to all GC roots \n(group path objects by type)"
const val PATH_TO_MOST_INTERESTING_ROOTS_TYPED = "Paths to most interesting GC roots \n(group path objects by type)"

const val CLOSURES_GROUP = "Closures..."
const val TRANSITIVE_CLOSURE = "Transitive closures"

const val EXTRACT_GROUP = "Extract objects..."
const val EXTRACT_FEW = "Extract ${GraphVisBridge.MAX_UNCOLLAPSED} objects"
const val EXTRACT_ALL = "Extract all objects"

const val NEIGHBORS_GROUP = "Neighbors..."
const val TO_POINTERS = "Points to \n(separate object)"
const val FROM_POINTERS = "Pointed from \n(separate objects)"
const val TO_TYPED = "Points to \n(group by type)"
const val FROM_TYPED = "Pointed from \n(group by type)"

const val SHOW_KEEP_ALIVE_EDGES = "Show keep-alive\nedges"

abstract class GraphVisNode(val bridge: GraphVisBridge) {
    val id: Int = GraphVisBridge.NEXT_ID++

    open fun getStyle(): String {
        return ""
    }

    open fun getLabel(): String {
        return ""
    }

    open fun getLabelStyle(): String {
        return ""
    }

    open fun getLabelType(): String {
        return ""
    }

    open fun getActionItems(): Array<GraphVisActionItem> {
        return arrayOf(GraphVisActionItem(HIDE_GROUP,
                                          arrayOf(GraphVisActionItem(HIDE_NODE),
                                                  GraphVisActionItem(HIDE_TREE),
                                                  GraphVisActionItem(HIDE_OTHERS),
                                                  GraphVisActionItem(HIDE_OTHER_TREES))))
    }

    open fun onActionItemSelected(action: String): GraphVisBridge.GraphVisUpdate {
        val update = bridge.createUpdate()
        when (action) {
            HIDE_NODE -> update.deleteNodes(this)
            HIDE_TREE -> update.deleteNodes(getNodesInTreeOf())
            HIDE_OTHERS -> update.deleteNodes(bridge.getNodes().filter { it != this })
            HIDE_OTHER_TREES -> {
                val tree = getNodesInTreeOf()
                update.deleteNodes(bridge.getNodes().filter { !tree.contains(it) })
            }
        }
        return update
    }

    @Suppress("unused")
    fun getShape(): String {
        return when (this) {
            is GraphVisObjectNode -> "rect"
            is GraphVisObjectGroupNode -> "ellipse"
            is GraphVisRootNode -> "rect"
            is GraphVisRootGroupNode -> "ellipse"
            is GraphVisCollapseNode -> "rect"
            else -> "rect"
        }.safe
    }

    override fun equals(other: Any?): Boolean {
        if (super.equals(other)) {
            return true
        }
        if (other is GraphVisNode) {
            return this.getUniqueIdentifier() == other.getUniqueIdentifier()
        }
        return false
    }

    override fun hashCode(): Int {
        return this.getUniqueIdentifier().hashCode()
    }

    fun toDotGraphNode(): String {
        return listOf(Pair("shape", getShape()),
                      Pair("label", if (getLabelType() == "html") "<center><div>${getLabel().replace("\n", "</div><div>")}</div></center>" else getLabel()),
                      Pair("labelStyle", getLabelStyle()),
                      Pair("style", getStyle()),
                      Pair("labelType", getLabelType()))
                .filter { it.second.isNotEmpty() }.joinToString(prefix = "\"$id\"[", postfix = "]", separator = ",", transform = { "${it.first}=\"${it.second}\"" })
    }

    fun getUniqueIdentifier(): String {
        return this.javaClass.simpleName + getIdentifier()
    }

    protected abstract fun getIdentifier(): String
}