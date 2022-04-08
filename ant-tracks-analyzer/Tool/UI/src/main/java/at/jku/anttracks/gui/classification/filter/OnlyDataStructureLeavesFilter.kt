
package at.jku.anttracks.gui.classification.filter

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.annotations.F
import java.util.*

@F(name = OnlyDataStructureLeavesFilter.NAME,
   desc = "This filter filters every object that is not a data structure leaf",
   collection = at.jku.anttracks.classification.enumerations.ClassifierSourceCollection.FASTHEAP)
class OnlyDataStructureLeavesFilter : Filter() {
    companion object {
        const val NAME = "Only data structure leaf objects"
    }

    private val knownToBeLeaf = BitSet()
    private val knownToBeInternal = BitSet()

    override fun classify(): Boolean {
        if (knownToBeInternal.get(index())) {
            return false
        }
        if (knownToBeLeaf.get(index())) {
            return true
        }

        fastHeap()!!.getDataStructures(index(), false, false).forEach {
            knownToBeLeaf.or(it.getLeafObjects(fastHeap(), true))
            knownToBeInternal.or(it.getInternalObjects(fastHeap()))
        }
        return knownToBeLeaf.get(index())
    }
}
