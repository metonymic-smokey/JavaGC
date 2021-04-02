package at.jku.anttracks.util

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.nodes.FastHeapGroupingNode
import at.jku.anttracks.heap.IndexBasedHeap

fun Int.classifyInto(root: FastHeapGroupingNode,
                     classifiers: ClassifierChain,
                     filters: Array<Filter>,
                     addFilterNodeInTree: Boolean,
                     heap: IndexBasedHeap) {
    classifiers.forEach { it?.setup({ heap.symbols }, { heap }) }
    filters.forEach { it.setup({ heap.symbols }, { heap }) }
    root.classify(heap, this, classifiers, filters, addFilterNodeInTree)
}

fun Int.toString(format: String): String = String.format(format, this)