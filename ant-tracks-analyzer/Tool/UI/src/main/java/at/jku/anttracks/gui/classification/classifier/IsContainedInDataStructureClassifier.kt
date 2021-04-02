
package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.annotations.C
import java.util.*

@C(name = IsContainedInDataStructureClassifier.NAME,
   desc = "This classifier distinguishes objects based whether they are contained in a data structure or not",
   example = "Not contained in any data structure",
   type = at.jku.anttracks.classification.enumerations.ClassifierType.ONE,
   collection = at.jku.anttracks.classification.enumerations.ClassifierSourceCollection.FASTHEAP)
class IsContainedInDataStructureClassifier : Classifier<String>() {

    @at.jku.anttracks.classification.annotations.ClassifierProperty(overviewLevel = 10)
    var considerIndirectlyContainedObjects = false

    public override fun classify(): String {
        if (considerIndirectlyContainedObjects) {
            val closedSet: BitSet = BitSet()
            val toProcess: BitSet = BitSet()
            closedSet.set(index())
            toProcess.set(index())

            while (!toProcess.isEmpty) {
                var idx = toProcess.nextSetBit(0)
                while (idx != -1) {
                    toProcess.clear(idx)

                    if (fastHeap().getDataStructures(idx, false, false) == null) {
                        // idx is not contained in any data structure
                        fastHeap().getFromPointers(idx)
                                .filter {
                                    it != at.jku.anttracks.heap.IndexBasedHeap.NULL_INDEX && !closedSet.get(it)
                                }
                                .forEach {
                                    toProcess.set(it)
                                    closedSet.set(it)
                                }
                    } else {
                        // idx is contained in a data structure!
                        // now, if idx corresponds to the index of the classified object, it is actually directly contained in a DS, otherwise only indirectly
                        return if (idx == index()) YES_DIRECTLY else YES_INDIRECTLY
                    }

                    idx = toProcess.nextSetBit(idx + 1)
                }
            }

            // following the from pointer of index() we couldn't reach any object that is contained in a data structure
            return NO_INDIRECTLY

        } else {
            return if (fastHeap()!!.isContainedInDataStructure(index())) YES_DIRECTLY else NO_DIRECTLY
        }
    }

    companion object {
        const val NAME = "Is contained in data structure?"
        const val YES_DIRECTLY = "Is directly contained in data structure(s)"
        const val YES_INDIRECTLY = "Is only indirectly contained in data structure(s)"
        const val NO_DIRECTLY = "Is not directly contained in any data structure"
        const val NO_INDIRECTLY = "Is neither directly nor indirectly contained in any data structure"
    }
}
