package at.jku.anttracks.classification.trees

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.nodes.MapGroupingNode
import at.jku.anttracks.heap.Heap
import javafx.concurrent.Task
import javax.naming.OperationNotSupportedException

class MapClassificationTree(root: MapGroupingNode, filters: Array<Filter>, classifiers: ClassifierChain) : ClassificationTree(root, filters, classifiers) {
    @Throws(OperationNotSupportedException::class)
    override fun writeTreeTask(heap: Heap?, metaDir: String, postfix: String): Task<Void?>? {
        throw OperationNotSupportedException("Tree writing not yet implemented")
    }

    override fun calculateObjectAndByteCounts(heap: Heap?) {
        root.objectCount
        root.getByteCount(heap)
    }

    @Throws(OperationNotSupportedException::class)
    override fun calculateClosures(heap: Heap?,
                                   calculateTransitiveClosure: Boolean,
                                   calculateGCClosure: Boolean,
                                   calculateDataStructureClosure: Boolean,
                                   calculateDeepDataStructureClosure: Boolean,
                                   listener: ClosureCalcListener?) {
        throw OperationNotSupportedException("Map Classification does not support closures")
    }
}