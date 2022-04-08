package at.jku.anttracks.classification.trees

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.heap.Heap
import javafx.concurrent.Task
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean
import javax.naming.OperationNotSupportedException

abstract class ClassificationTree(var root: GroupingNode, val filters: Array<Filter>, val classifiers: ClassifierChain) {

    interface ClosureCalcListener {
        fun update(progress: Double)
    }

    // metrics
    @kotlin.jvm.JvmField
    protected var objectSum: Long = -1 // sum of object counts of ALL nodes
    @kotlin.jvm.JvmField
    protected var nNodes: Long = -1
    @kotlin.jvm.JvmField
    protected var nDataNodes: Long = -1 // nodes with at least one data entry
    @kotlin.jvm.JvmField
    protected var nDataCollectionParts: Long = -1
    var avgTreeNodeDataCollectionPartsPerNode = -1.0
        private set
    var avgObjectsPerNode = -1.0
        private set
    var avgTreeNodeDataCollectionPartsPerDataNode = -1.0
        private set
    var avgObjectsPerDataNode = -1.0
        private set
    var avgObjectsPerTreeNodeDataCollectionPart = -1.0
        private set
    @kotlin.jvm.JvmField
    protected var cancellationToken: AtomicBoolean? = null

    var pointedFromMap = mapOf<GroupingNode, MutableMap<GroupingNode, Long>>()
    var pointsToMap = mapOf<GroupingNode, MutableMap<GroupingNode, Long>>()

    val pointsToMapStringKeys: Map<String, Map<String, Long>>
        get() = pointsToMap.mapKeys { it.key.fullKeyAsString }.mapValues { it.value.mapKeys { it.key.fullKeyAsString } }

    val pointedFromMapStringKeys: Map<String, Map<String, Long>>
        get() = pointedFromMap.mapKeys { it.key.fullKeyAsString }.mapValues { it.value.mapKeys { it.key.fullKeyAsString } }

    open fun init(heap: Heap?,
                  calculateTransitiveClosure: Boolean,
                  calculateGCClosure: Boolean,
                  calculateDataStructureClosure: Boolean,
                  calculateDeepDataStructureClosure: Boolean) { // Try to save memory and time by calculating the whole tree's object and byte counts in one traversal
//ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("Init Classification Tree");
//ApplicationStatistics.Measurement m2 = ApplicationStatistics.getInstance().createMeasurement("Byte & Object Count calculation");
        calculateObjectAndByteCounts(heap)
        //m2.end();
//m2 = ApplicationStatistics.getInstance().createMeasurement("Fill root closure sizes");
        root.fillClosureSizes(heap,
                              calculateTransitiveClosure,
                              calculateGCClosure,
                              calculateDataStructureClosure,
                              calculateDeepDataStructureClosure)
        //m2.end();
//m.end();
// calculate additional metrics
        if (nNodes != -1L && nDataNodes != -1L && nDataCollectionParts != -1L) {
            avgTreeNodeDataCollectionPartsPerNode = 1.0 * nDataCollectionParts / nNodes
            avgObjectsPerNode = 1.0 * root.objectCount / nNodes
            avgTreeNodeDataCollectionPartsPerDataNode = 1.0 * nDataCollectionParts / nDataNodes
            avgObjectsPerDataNode = 1.0 * root.objectCount / nDataNodes
            avgObjectsPerTreeNodeDataCollectionPart = 1.0 * root.objectCount / nDataNodes
        }
    }

    val allNodes: List<GroupingNode> by lazy {
        val nodes: MutableList<GroupingNode> = ArrayList()
        nodes.add(root)
        var i = 0
        while (i < nodes.size) {
            nodes.addAll(nodes[i].children)
            i++
        }
        nodes
    }

    val allLeafNodes: List<GroupingNode> by lazy {
        // TODO Assumes that we do not have subtree levels in the tree
        allNodes.filter { it.children.isEmpty() }
    }

    @JvmOverloads
    fun initClosureTask(heap: Heap?,
                        calculateTransitiveClosure: Boolean,
                        calculateGCClosure: Boolean,
                        calculateDataStructureClosure: Boolean,
                        calculateDeepDataStructureClosure: Boolean,
                        completionCallback: Runnable = Runnable {}): Task<Void?> {
        cancellationToken = AtomicBoolean(false)
        return object : Task<Void?>() {
            @Throws(Exception::class)
            override fun call(): Void? {
                try {
                    updateTitle("Calculate closures")
                    updateMessage("Transitive closure, GC closure & DS closures")
                    //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("anttracks.classificationtree.calculateclosures");
                    calculateClosures(heap,
                                      calculateTransitiveClosure,
                                      calculateGCClosure,
                                      calculateDataStructureClosure,
                                      calculateDeepDataStructureClosure,
                                      object : ClosureCalcListener {
                                          override fun update(progress: Double) {
                                              updateProgress(progress, 1.0)
                                          }
                                      })
                    //m.end();
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                return null
            }

            override fun succeeded() {
                completionCallback.run()
            }

            override fun cancelled() {
                super.cancelled()
                cancellationToken!!.set(true)
            }
        }
    }

    @Throws(OperationNotSupportedException::class)
    abstract fun writeTreeTask(heap: Heap?, metaDir: String, postfix: String): Task<Void?>?

    @Throws(IOException::class)
    protected fun writeHeapMetrics(stream: DataOutputStream) {
        stream.writeLong(objectSum)
        stream.writeLong(nNodes)
        stream.writeLong(nDataNodes)
        stream.writeLong(nDataCollectionParts)
        stream.writeDouble(avgTreeNodeDataCollectionPartsPerNode)
        stream.writeDouble(avgObjectsPerNode)
        stream.writeDouble(avgTreeNodeDataCollectionPartsPerDataNode)
        stream.writeDouble(avgObjectsPerDataNode)
        stream.writeDouble(avgObjectsPerTreeNodeDataCollectionPart)
    }

    @Throws(IOException::class)
    protected fun readHeapMetrics(stream: DataInputStream) {
        objectSum = stream.readLong()
        nNodes = stream.readLong()
        nDataNodes = stream.readLong()
        nDataCollectionParts = stream.readLong()
        avgTreeNodeDataCollectionPartsPerNode = stream.readDouble()
        avgObjectsPerNode = stream.readDouble()
        avgTreeNodeDataCollectionPartsPerDataNode = stream.readDouble()
        avgObjectsPerDataNode = stream.readDouble()
        avgObjectsPerTreeNodeDataCollectionPart = stream.readDouble()
    }

    abstract fun calculateObjectAndByteCounts(heap: Heap?)
    @Throws(OperationNotSupportedException::class)
    abstract fun calculateClosures(heap: Heap?,
                                   calculateTransitiveClosure: Boolean,
                                   calculateGCClosure: Boolean,
                                   calculateDataStructureClosure: Boolean,
                                   calculateDeepDataStructureClosure: Boolean,
                                   listener: ClosureCalcListener?)

    fun closuresInitialized(): Boolean {
        val toVisit = Stack<GroupingNode>()
        toVisit.push(root)
        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("isClosuresInitialized");
        while (!toVisit.empty()) {
            val node = toVisit.pop()
            if (!node.isClosureSizeCalculated || !node.isGCSizeCalculated) {
                return false
            }
            for (child in node.children) {
                toVisit.push(child)
            }
        }
        //m.end();
        return true
    }

    fun getnNodes(): Long {
        return nNodes
    }

    fun getnDataNodes(): Long {
        return nDataNodes
    }

    fun getnDataCollectionParts(): Long {
        return nDataCollectionParts
    }
}