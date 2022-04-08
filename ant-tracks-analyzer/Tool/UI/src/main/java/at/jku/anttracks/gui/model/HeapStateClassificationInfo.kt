
package at.jku.anttracks.gui.model

import at.jku.anttracks.classification.trees.ListClassificationTree

class HeapStateClassificationInfo(val heapStateInfo: FastHeapInfo,
                                  val selectedClassifierInfo: SelectedClassifierInfo) {
    var grouping: ListClassificationTree? = null
}
