package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz

import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.extensions.*
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.links.GraphVisLink
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes.GraphVisNode
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes.GraphVisObjectGroupNode
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes.GraphVisObjectNode
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes.GraphVisRootGroupNode
import at.jku.anttracks.gui.utils.AntTask
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.util.ParallelizationUtil
import at.jku.anttracks.util.toBitSet
import at.jku.anttracks.util.toString
import javafx.application.Platform
import javafx.scene.web.WebEngine
import netscape.javascript.JSObject
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class GraphVisBridge(private val webEngine: WebEngine, val heap: IndexBasedHeap) {
    private val nodes = mutableMapOf<String, GraphVisNode>() as Map<String, GraphVisNode>
    private val links = mutableMapOf<String, GraphVisLink>() as Map<String, GraphVisLink>

    private var onMouseEntered: (GraphVisNode) -> Unit = { _ -> }
    private var onMouseLeave: (GraphVisNode) -> Unit = { _ -> }

    private val bridge = (webEngine.executeScript("window.bridge") as JSObject)

    private var actionInProgress = false

    init {
        bridge.call("setDelegate", this)
        clearPointerCaches()
    }

    fun setOnMouseEntered(onMouseEntered: (GraphVisNode) -> Unit = { _ -> }) {
        this.onMouseEntered = onMouseEntered
    }

    fun setOnMouseLeave(onMouseLeave: (GraphVisNode) -> Unit = { _ -> }) {
        this.onMouseLeave = onMouseLeave
    }

    fun getNodes(): Collection<GraphVisNode> {
        return nodes.values
    }

    fun getLinks(): Collection<GraphVisLink> {
        return links.values
    }

    fun createUpdate(): GraphVisUpdate {
        return GraphVisUpdate()
    }

    private fun addNode(node: GraphVisNode) {
        if (nodes[node.getUniqueIdentifier()] == null) {
            (nodes as MutableMap)[node.getUniqueIdentifier()] = node
        }
    }

    private fun removeNodes(nodes: Iterable<GraphVisNode>) {
        val toRemove = nodes.toMutableSet()
        (this.nodes as MutableMap).entries.removeIf { toRemove.remove(it.value) }
    }

    private fun resetRootPointers(labelsBasedOn: String?) {
        val mutableNodes = this.nodes as MutableMap
        val mutableLinks = links as MutableMap

        val oldRootNodes = this.nodes.filter { it.value.isRootNode() }

        mutableNodes.entries.removeIf { it.value.isRootNode() }

        this.nodes.map { it.value }.forEach { node ->
            node.getTypedRootPointers().forEach { (type, rootPtrs) ->
                // TODO Store root pointers as list, not as set
                var rootNode: GraphVisNode = GraphVisRootGroupNode(type, rootPtrs, this)
                val oldRootNode = oldRootNodes[rootNode.getUniqueIdentifier()]
                if (oldRootNode != null) {
                    rootNode = oldRootNode
                }
                val linkLabel = if (labelsBasedOn != null) {
                    val reachableObjects = rootPtrs.map { rp -> rp.idx }.toBitSet().transitiveClosure(heap)
                    reachableObjects.and(nodes.getValue(labelsBasedOn).containedObjects())
                    "${reachableObjects.cardinality().toString("%,d")} ${nodes.getValue(labelsBasedOn).getLabel().substringBefore("(")}\n" +
                            "object${if (reachableObjects.cardinality() > 1) "s" else ""} reachable"
                } else {
                    "${rootPtrs.size.toString("%,d")} root pointer${if (rootPtrs.size > 1) "s" else ""}\n" +
                            "reference${if (rootPtrs.size == 1) "s" else ""} ${rootNode.containedObjects().cardinality().toString("%,d")} obj."
                }
                val link = GraphVisLink(rootNode, node, linkLabel, this)
                mutableNodes[rootNode.getUniqueIdentifier()] = rootNode
                mutableLinks[link.getUniqueIdentifier()] = link
            }
        }
    }

    private fun resetLinks(updatedNodes: Set<GraphVisNode>, labelsBasedOn: String?, updateOperation: (Long?, String?) -> Unit) {
        val mutableLinks = links as MutableMap

        val pairs = updatedNodes.filter { nodes[it.getUniqueIdentifier()] != null }.getCombinationsWith(nodes.values).toList()

        mutableLinks.entries.removeIf {
            updatedNodes.contains(it.value.source) || updatedNodes.contains(it.value.target)
        }

        var i = 0
        pairs.asSequence()
                .onEach { updateOperation((50.0 + (30.0 * i++ / pairs.size)).roundToInt().toLong(), null) }
                .map { nodePair ->
                    NodePairOverlapInfo(nodePair,
                                        OverlapInfo(
                                                nodePair.node1.containedObjects(nodePair.node2.getCachedFromPointers()),
                                                nodePair.node2.containedObjects(nodePair.node1.getCachedToPointers())))
                }
                .filter { (_, overlapInfo) -> !overlapInfo.objectsOfNode1OverlapWithPointedFromOfNode2.isEmpty && !overlapInfo.objectsOfNode2OverlapWithPointsToOfNode1.isEmpty }
                .forEach { (nodePair, overlapInfo) ->
                    val linkLabel = if (labelsBasedOn != null) {
                        val reachableObjects = overlapInfo.objectsOfNode2OverlapWithPointsToOfNode1.transitiveClosure(heap)
                        reachableObjects.and(nodes.getValue(labelsBasedOn).containedObjects())
                        "${reachableObjects.cardinality().toString("%,d")} ${nodes.getValue(labelsBasedOn).getLabel().substringBefore("(")}\n" +
                                "object${if (reachableObjects.cardinality() > 1) "s" else ""} reachable"
                    } else {
                        "${overlapInfo.objectsOfNode1OverlapWithPointedFromOfNode2.cardinality().toString("%,d")} obj.\n" +
                                "reference${if (overlapInfo.objectsOfNode1OverlapWithPointedFromOfNode2.cardinality() == 1) "s" else ""}\n" +
                                "${overlapInfo.objectsOfNode2OverlapWithPointsToOfNode1.cardinality().toString("%,d")} obj."
                    }

                    val link = GraphVisLink(nodePair.node1, nodePair.node2, linkLabel, this)
                    mutableLinks[link.getUniqueIdentifier()] = link

                }
    }

    private fun getDotNodes(): String {
        return nodes.values.sortedWith(compareBy { it.id }).joinToString(separator = "\n", transform = GraphVisNode::toDotGraphNode)
    }

    private fun getDotLinks(): String {
        return links.values.sortedWith(compareBy({ it.source.id }, { it.target.id })).joinToString(separator = " ", transform = GraphVisLink::toDotGraphLink)
    }

    /*
     * CALLED FROM JAVASCRIPT
     */

    @Suppress("unused")
    fun getActionItems(id: Int): Array<GraphVisActionItem> {
        if (actionInProgress) {
            return arrayOf()
        }
        return nodes.values.find { it.id == id }?.getActionItems() ?: arrayOf()
    }

    @Suppress("unused")
    fun actionItemSelected(id: Int, action: String) {
        ParallelizationUtil.submitTask(object : AntTask<Unit>() {
            override fun backgroundWork() {
                actionInProgress = true
                updateTitle(action)
                updateMessage("Calculating nodes to update...")
                var actionOperation: GraphVisUpdate? = null
                val actionTime = measureTimeMillis {
                    actionOperation = nodes.values.find { it.id == id }?.onActionItemSelected(action)
                }
                updateProgress(20, 100)
                println("Action Metrics (in ms):")
                println("$action took $actionTime")
                actionOperation?.execute(id) { progress, message ->
                    run {
                        if (progress != null) {
                            updateProgress(progress, 100)
                        }
                        if (message != null) {
                            updateMessage(message)
                        }
                    }
                }
            }

            override fun finished() {
                actionInProgress = false
            }
        })
    }

    @Suppress("unused")
    fun mouseEntered(id: Int) {
        val node = nodes.values.find { it.id == id }
        if (node != null) {
            onMouseEntered(node)
        }
    }

    @Suppress("unused")
    fun mouseLeft(id: Int) {
        val node = nodes.values.find { it.id == id }
        if (node != null) {
            onMouseLeave(node)
        }
    }

    @Suppress("unused")
    fun getDotGraph(): String {
        return "digraph{" + getDotNodes() + getDotLinks() + "}"
    }

    /**
     * [weightBasedOn] should be set to show keep-alive edges for the given node
     */
    @Suppress("unused")
    fun recalculateAndThenRepaintLinkWeights(weightBasedOn: String?) {
        recalculateAndThenRepaintLinkWeights(weightBasedOn, false)
    }

    /**
     * [weightBasedOn] should be set to show keep-alive edges for the given node
     * [waitForTaskFinish] should be set to true if this method should block until all link weights are calculated. Otherwise, this happens in parallel.
     */
    fun recalculateAndThenRepaintLinkWeights(weightBasedOn: String?, waitForTaskFinish: Boolean) {
        ParallelizationUtil.submitTask(object : AntTask<Unit>() {
            override fun backgroundWork() {
                updateTitle("Adjusting link weights...")
                val param =
                        links.values.mapIndexed { index, graphVisLink ->
                            updateProgress(index.toLong(), links.values.size.toLong())
                            Triple(graphVisLink.source.id, graphVisLink.target.id, graphVisLink.calculateWeight(weightBasedOn))
                        }.filter {
                            it.third >= 2
                        }.joinToString(separator = ",", prefix = "[", postfix = "]") {
                            "{v:\"${it.first}\",w:\"${it.second}\",value:\"${it.third}\"}"
                        }
                Platform.runLater {
                    webEngine.executeScript("window.bridge.repaintLinkWeights($param)")
                }
            }

            override fun finished() {
            }
        }, waitForTaskFinish)
    }

    // Currently not used, could be used to set the link labels lazyly
    /*
    fun adjustLinkLabels(id: String?) {
        if (id != null) {
            val defaultLinkLables = links.values.joinToString(separator = ",", prefix = "[", postfix = "]") {
                val linkObjects = it.target.containedObjects(it.source.getCachedToPointers())
                val linkTransClosure = heap.transitiveClosure(linkObjects.toIntArray(), null)
                linkTransClosure.and(nodes.getValue(id).containedObjects())
                "{v:\"${it.source.id}\",w:\"${it.target.id}\",value:\"${linkTransClosure.cardinality()}\"}"
            }
            Platform.runLater {
                webEngine.executeScript("window.bridge.adjustLinkLabels($defaultLinkLables)")
            }
        } else {
            val defaultLinkLables = links.values.joinToString(separator = ",", prefix = "[", postfix = "]") {
                "{v:\"${it.source.id}\",w:\"${it.target.id}\",value:\"${it.label}\"}"
            }
            Platform.runLater {
                webEngine.executeScript("window.bridge.adjustLinkLabels($defaultLinkLables)")
            }
        }
    }
    */

    inner class GraphVisUpdate {
        private val nodesToAdd = mutableSetOf<GraphVisNode>()
        private val nodesToDelete = mutableSetOf<GraphVisNode>()
        var labelsBasedOnExecutingNodeNode: Boolean = false

        fun addNodes(vararg nodes: GraphVisNode) {
            nodesToAdd.addAll(nodes)
        }

        fun addNodes(nodes: Collection<GraphVisNode>) {
            nodesToAdd.addAll(nodes)
        }

        fun deleteNodes(vararg nodes: GraphVisNode) {
            nodesToDelete.addAll(nodes)
        }

        fun deleteNodes(nodes: Collection<GraphVisNode>) {
            nodesToDelete.addAll(nodes)
        }

        fun execute() {
            execute(-1)
        }

        fun execute(executingNode: Int) {
            execute(executingNode) { _: Long?, _: String? ->

            }
        }

        fun execute(updateOperation: (Long?, String?) -> Unit) {
            execute(-1, updateOperation)
        }

        fun execute(executingNodeId: Int, progressListener: (Long?, String?) -> Unit) {
            progressListener(40, "Updating nodes...")
            nodesToAdd.removeIf { nodes[it.getUniqueIdentifier()] != null }

            if (nodesToDelete.isNotEmpty() || nodesToAdd.isNotEmpty()) {
                removeNodes(nodesToDelete)

                nodesToAdd.forEach { addNode(it) }

                val groupNodesToUpdate = mutableSetOf<GraphVisObjectGroupNode>()

                println("Graph Update Metrics (in ms):")
                print("splitting took ")
                println(measureTimeMillis {
                    for (groupNodeExisting in nodes.values.filterIsInstance<GraphVisObjectGroupNode>()) {
                        for (nodeAdded in nodesToAdd) {
                            if (nodeAdded is GraphVisObjectNode) {
                                if (groupNodeExisting.containedNodes.get(nodeAdded.objectId)) {
                                    groupNodeExisting.containedNodes.clear(nodeAdded.objectId)
                                    groupNodesToUpdate.add(groupNodeExisting)
                                }
                            } else if (nodeAdded is GraphVisObjectGroupNode && nodeAdded != groupNodeExisting) {
                                val commonNodes = nodeAdded.getCommonNodes(groupNodeExisting)
                                if (!commonNodes.isEmpty) {
                                    nodeAdded.containedNodes.andNot(commonNodes)
                                    groupNodeExisting.containedNodes.andNot(commonNodes)
                                    groupNodesToUpdate.add(nodeAdded)
                                    groupNodesToUpdate.add(groupNodeExisting)
                                    when {
                                        groupNodeExisting.containedNodes.isEmpty -> groupNodeExisting.containedNodes.or(commonNodes)
                                        nodeAdded.containedNodes.isEmpty -> nodeAdded.containedNodes.or(commonNodes)
                                        else -> groupNodesToUpdate.add(GraphVisObjectGroupNode(commonNodes, this@GraphVisBridge))
                                    }
                                }
                            }
                        }
                    }
                })

                removeNodes(groupNodesToUpdate)
                groupNodesToUpdate.filter { !it.containedNodes.isEmpty }.forEach { addNode(it) }

                progressListener(50, "Calculating links...")
                print("resetLinks took ")
                println(measureTimeMillis {
                    resetLinks(nodesToAdd.plus(nodesToDelete).plus(groupNodesToUpdate), null, progressListener)
                })

                progressListener(80, "Updating root pointer information...")
                print("resetRootPointers took ")
                println(measureTimeMillis {
                    resetRootPointers(null)
                })

                println("Graph new contains following nodes:")
                nodes.values.groupBy { node -> node.javaClass }.forEach { (clazz, nodeList) -> println("${clazz.simpleName}: ${nodeList.size}") }
                println("Links: ${links.size}")
            }

            var executingNodeIdentifier: String? = null
            if (labelsBasedOnExecutingNodeNode) {
                // TODO Do not build map on unique identifier but ID
                // TODO Some stuff is executed twice if nodes are added at the same time (e.g., reset links is called some line above and here)
                val executingNode = nodes.values.find { n -> n.id == executingNodeId }!!
                executingNodeIdentifier = executingNode.getUniqueIdentifier()
                resetLinks(nodes.values.toSet(), executingNodeIdentifier, progressListener)
                resetRootPointers(executingNodeIdentifier)
            }

            Platform.runLater {
                progressListener(null, "Updating graph...")
                if (executingNodeIdentifier == null) {
                    webEngine.executeScript("window.bridge.repaintGraph(\"$executingNodeId\", null)")
                } else {
                    webEngine.executeScript("window.bridge.repaintGraph(\"$executingNodeId\", \"${executingNodeIdentifier}\")")
                }
            }
        }
    }

    companion object {
        const val MAX_UNCOLLAPSED_TYPED_NODES = 10
        const val MAX_UNCOLLAPSED = 3
        var NEXT_ID: Int = 0
    }
}