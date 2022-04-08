package at.jku.anttracks.classification.trees

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.classification.nodes.IndexCollection
import at.jku.anttracks.classification.nodes.ListGroupingNode
import at.jku.anttracks.heap.Closures
import at.jku.anttracks.heap.Heap
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.util.Consts.LIST_TREE_EXTENSION
import at.jku.anttracks.util.Counter
import at.jku.anttracks.util.ParallelizationUtil
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import javafx.concurrent.Task
import java.io.*
import java.util.*
import java.util.stream.Collectors

val Map<String, Map<String, Long>>?.asJSON: JsonObject?
    get() = if (this == null) {
        null
    } else {
        JsonObject().also { json ->
            forEach { left, rightMap ->
                json.add(left, JsonObject().apply {
                    rightMap.forEach { right, amount ->
                        addProperty(right, amount)
                    }
                })
            }
        }
    }

val Pair<Long, Map<String, Map<String, Long>>?>.asJSON
    get() = JsonObject().apply {
        addProperty("time", first)
        add("pointerMap", second.asJSON)
    }

val Map<Long, Map<String, Map<String, Long>>?>.asJSON
    get() = map { (time, pointerMap) ->
        (time to pointerMap).asJSON
    }.fold(JsonArray()) { array: JsonArray, tree: JsonElement -> array.apply { add(tree) } }

class ListClassificationTree(grouping: ListGroupingNode, filters: Array<Filter>, classifiers: ClassifierChain) : ClassificationTree(grouping, filters, classifiers) {
    private class ClosureInitInfo(val data: IndexCollection, val closures: Closures)

    var isClosuresCalculated = false
        private set
    private var progressListener: ClosureCalcListener? = null
    private var progressCounter: Counter? = null

    override fun init(heap: Heap?, calculateTransitiveClosure: Boolean, calculateGCClosure: Boolean, calculateDataStructureClosure: Boolean, calculateDeepDataStructureClosure: Boolean) {
        super.init(heap, calculateTransitiveClosure, calculateGCClosure, calculateDataStructureClosure, calculateDeepDataStructureClosure)
        fillPointerMaps(heap as IndexBasedHeap)
    }

    override fun calculateObjectAndByteCounts(heap: Heap?) {
        fillObjectAndByteCounts(heap as IndexBasedHeap?, root as ListGroupingNode)
    }

    override fun calculateClosures(heap: Heap?,
                                   calculateTransitiveClosure: Boolean,
                                   calculateGCClosure: Boolean,
                                   calculateDataStructureClosure: Boolean,
                                   calculateDeepDataStructureClosure: Boolean,
                                   listener: ClosureCalcListener?) {
        if (nNodes < 0) {
            calculateObjectAndByteCounts(heap)
        }
        progressListener = listener
        progressCounter = Counter()
        isClosuresCalculated = fillClosures(heap,
                                            calculateTransitiveClosure,
                                            calculateGCClosure,
                                            calculateDataStructureClosure,
                                            calculateDeepDataStructureClosure,
                                            root as ListGroupingNode,
                                            true) != null
        progressListener = null
        progressCounter = null
    }

    private fun fillClosures(heap: Heap?,
                             calculateTransitiveClosure: Boolean,
                             calculateGCClosure: Boolean,
                             calculateDataStructureClosure: Boolean,
                             calculateDeepDataStructureClosure: Boolean,
                             node: ListGroupingNode,
                             multiThread: Boolean): ClosureInitInfo? {
        val ret: ClosureInitInfo
        val childrenOnSameSubtreeLevel = node.firstChildrenOnSameSubtreeLevel
        if (node.children.isEmpty()) { //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("anttracks.closures.leaf");
// Init leafs, no big deal
            val closures = node.data.calculateClosures(heap as IndexBasedHeap?,
                                                       calculateTransitiveClosure,
                                                       calculateGCClosure,
                                                       calculateDataStructureClosure,
                                                       calculateDeepDataStructureClosure)
            //ApplicationStatistics.Measurement m2 = ApplicationStatistics.getInstance().createMeasurement("anttracks.closures.leaf.calcbytes");
            node.setClosureSize(closures.transitiveClosureByteCount)
            node.setGCSize(closures.gcClosureByteCount)
            node.setDataStructureSize(closures.dataStructureClosureByteCount)
            node.setDeepDataStructureSize(closures.deepDataStructureClosureByteCount)
            //m2.end();
            closureCalculationProgressed()
            //m.end();
            ret = ClosureInitInfo(node.data, closures)
        } else { // Return my data plus the data of all children
            if (cancellationToken != null && cancellationToken!!.get()) { // avoid all recursion to end algorithm as quickly as possible
                return ClosureInitInfo(node.getData(false) as IndexCollection, Closures(null, IntArray(0), BitSet(), BitSet(), BitSet(), BitSet()))
            }
            childrenOnSameSubtreeLevel.sortWith(Comparator { o1: GroupingNode, o2: GroupingNode -> (o2.objectCount - o1.objectCount).toInt() })
            var singleChildClosuresInitInfo: ClosureInitInfo? = null
            val combinedChildClosure = BitSet()
            val childData = arrayOfNulls<IndexCollection>(childrenOnSameSubtreeLevel.size)
            val sameSubtreeLevelChildId = Counter()
            if (multiThread && childrenOnSameSubtreeLevel.size > 1) {
                ParallelizationUtil.temporaryExecutorServiceBlocking { threadId: Int, threadCount: Int ->
                    var childId = threadId
                    while (childId < node.children.size) {
                        val childClosuresInfo = fillClosures(heap,
                                                             calculateTransitiveClosure,
                                                             calculateGCClosure,
                                                             calculateDataStructureClosure,
                                                             calculateDeepDataStructureClosure,
                                                             node.children[childId] as ListGroupingNode,
                                                             false)
                        if (childrenOnSameSubtreeLevel.contains(node.children[childId])) {
                            synchronized(combinedChildClosure
                            ) { combinedChildClosure.or(childClosuresInfo!!.closures.transitiveClosure) }
                            synchronized(childData) {
                                childData[sameSubtreeLevelChildId.get().toInt()] = childClosuresInfo!!.data
                                sameSubtreeLevelChildId.inc()
                            }
                        }
                        childId += threadCount
                    }
                }
            } else {
                for (childId in node.children.indices) {
                    val childClosuresInfo = fillClosures(heap,
                                                         calculateTransitiveClosure,
                                                         calculateGCClosure,
                                                         calculateDataStructureClosure,
                                                         calculateDeepDataStructureClosure,
                                                         node.children[childId] as ListGroupingNode, false)
                    if (childrenOnSameSubtreeLevel.contains(node.children[childId])) {
                        combinedChildClosure.or(childClosuresInfo!!.closures.transitiveClosure)
                        childData[childId] = childClosuresInfo.data
                        if (childrenOnSameSubtreeLevel.size == 1) {
                            singleChildClosuresInitInfo = childClosuresInfo
                        }
                    }
                }
            }
            // ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("anttracks.closures.parent");
            val closures: Closures
            var data = (node.getData(false) as IndexCollection).clone()
            if (data.objectCount == 0 && singleChildClosuresInitInfo != null) { // Special case: We can use the child's data directly
                data = singleChildClosuresInitInfo.data
                closures = singleChildClosuresInitInfo.closures
            } else { // ApplicationStatistics.Measurement m2 = ApplicationStatistics.getInstance().createMeasurement("anttracks.closures.parent.dataunion");
                data.unionWith(childData)
                //m2.end();
                closures = data.calculateClosures(heap as IndexBasedHeap?,
                                                  calculateTransitiveClosure,
                                                  calculateGCClosure,
                                                  calculateDataStructureClosure,
                                                  calculateDeepDataStructureClosure,
                                                  combinedChildClosure)
            }
            //ApplicationStatistics.Measurement m3 = ApplicationStatistics.getInstance().createMeasurement("anttracks.closures.parent.calcbytes");
            node.setClosureSize(closures.transitiveClosureByteCount)
            node.setGCSize(closures.gcClosureByteCount)
            node.setDataStructureSize(closures.dataStructureClosureByteCount)
            node.setDeepDataStructureSize(closures.deepDataStructureClosureByteCount)
            //m3.end();
//m.end();
            closureCalculationProgressed()
            ret = ClosureInitInfo(data, closures)
        }
        return ret
    }

    @Synchronized
    fun closureCalculationProgressed() {
        assert(nNodes >= 0) { "Classification tree must be initialized! Probably missing call to init()" }
        if (progressListener != null && progressCounter != null) {
            progressCounter!!.inc()
            progressListener!!.update(1.0 * progressCounter!!.get() / nNodes)
        }
    }

    private fun fillObjectAndByteCounts(heap: IndexBasedHeap?,
                                        node: ListGroupingNode): IndexCollection {
        nNodes++
        nDataNodes += Math.min(1, node.data.objectCount).toLong()
        nDataCollectionParts += node.data.objectCount.toLong()
        return if (node.children.isEmpty()) { // Init children, no big deal
            objectSum += node.data.objectCount.toLong()
            node.getByteCount(heap)
            // Add child objects to accumulator
            node.getData(false) as IndexCollection
        } else { // Return my data plus the data of all children
            val childrenOnSameSubtreeLevel = node.firstChildrenOnSameSubtreeLevel
            val childData = arrayOfNulls<IndexCollection>(childrenOnSameSubtreeLevel.size)
            for (i in childrenOnSameSubtreeLevel.indices) {
                childData[i] = fillObjectAndByteCounts(heap, childrenOnSameSubtreeLevel[i] as ListGroupingNode)
            }
            val ret = (node.getData(false) as IndexCollection).clone()
            ret.unionWith(childData)
            node.setObjectCount(ret.objectCount.toDouble())
            objectSum += ret.objectCount.toLong()
            node.setByteCount(ret.getBytes(heap))
            node.children
                    .stream()
                    .filter { child: GroupingNode? -> !childrenOnSameSubtreeLevel.contains(child) }
                    .map { child: GroupingNode? -> child as ListGroupingNode }
                    .forEach({ n: ListGroupingNode -> fillObjectAndByteCounts(heap, n) })
            ret
        }
    }

    override fun writeTreeTask(heap: Heap?, metaDir: String, postfix: String): Task<Void?>? {
        return object : Task<Void?>() {
            @Throws(Exception::class)
            override fun call(): Void? {
                updateTitle("Classification tree")
                updateMessage("Write to file")
                try {
                    val file = getTreeFile(metaDir, classifiers, postfix)
                    if (!file.exists()) { // System.out.println(file.getAbsolutePath());
                        file.createNewFile()
                    }
                    val stream = DataOutputStream(FileOutputStream(file, false))
                    ListGroupingNode.writeTree(stream,
                                               heap,
                                               (root as ListGroupingNode),
                                               Counter(),
                                               nNodes
                    ) { progress: Double, newText: String? -> updateProgress(progress, 1.0) }
                    writeHeapMetrics(stream)
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                return null
            }
        }
    }

    private fun fillPointerMaps(indexBasedHeap: IndexBasedHeap) {
        val nodes = allNodes.filterIsInstance(ListGroupingNode::class.java)

        val objectIndexToNodes = Array<MutableList<ListGroupingNode>>(indexBasedHeap.objectCount) { mutableListOf() }
        for (node in nodes) {
            for (i in 0 until node.data.objectCount) {
                val objIdx = node.data.get(i)
                objectIndexToNodes[objIdx].add(node)
            }
        }

        this.pointsToMap = nodes.associate { node ->
            Pair(node as GroupingNode,
                 indexBasedHeap.getToPointers(node.data.asBitset())
                         .stream()
                         .boxed()
                         .flatMap { ptr -> objectIndexToNodes[ptr].stream() }
                         .collect(Collectors.groupingBy({ n: ListGroupingNode -> n as GroupingNode }, Collectors.counting()))
            )
        }

        this.pointedFromMap = nodes.associate { node ->
            Pair(node as GroupingNode,
                 indexBasedHeap.getFromPointers(node.data.asBitset())
                         .stream()
                         .boxed()
                         .flatMap { ptr -> objectIndexToNodes[ptr].stream() }
                         .collect(Collectors.groupingBy({ n: ListGroupingNode -> n as GroupingNode }, Collectors.counting()))
            )
        }
    }

    fun getPointsToMapOf(fullKeyAsString: String): Map<GroupingNode, Long>? {
        return pointsToMap[allNodes.find { it.fullKeyAsString == fullKeyAsString }]
    }

    fun getPointedFromMapOf(fullKeyAsString: String): Map<GroupingNode, Long>? {
        return pointedFromMap[allNodes.find { it.fullKeyAsString == fullKeyAsString }]
    }

    fun calculatePointerMapOf(fullKeyAsString: String, indexBasedHeap: IndexBasedHeap): Map<GroupingNode, Int> {
        val nodes = allLeafNodes.filterIsInstance(ListGroupingNode::class.java)
        val node = allLeafNodes.find { it.fullKeyAsString == fullKeyAsString } as ListGroupingNode
        val pointedObjects = indexBasedHeap.getToPointers(indexBasedHeap.getToPointers(node.data.asBitset()))

        return nodes.associate { potentialPointeeNode ->
            potentialPointeeNode as GroupingNode to node.data.overlap(potentialPointeeNode.data)
        }.filter { (pointedFullKeyAsString, overlappingObjects) ->
            overlappingObjects > 0
        }
    }

    companion object {
        fun readTree(metaDir: String,
                     filters: Array<Filter>,
                     classifiers: ClassifierChain,
                     postfix: String,
                     classifierMap: HashMap<String, Classifier<*>>): ClassificationTree? {
            try {
                val file = getTreeFile(metaDir, classifiers, postfix)
                if (file.exists() && file.isFile) {
                    val stream = DataInputStream(FileInputStream(file))
                    val tree = ListClassificationTree(ListGroupingNode.readTree(stream, null, classifierMap), filters, classifiers)
                    tree.readHeapMetrics(stream)
                    return tree
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }

        private fun getTreeFile(metaDir: String, classifiers: ClassifierChain, postfix: String): File {
            return File(metaDir + File.separator + classifiers.toString()
                    .replace(" ", "_")
                    .replace("(", "")
                    .replace(")", "")
                    .replace("[", "")
                    .replace("]", "")
                    .replace(">", "-")
                    .replace("<", "-") + "_" + postfix + LIST_TREE_EXTENSION)
        }
    }
}