
package at.jku.anttracks.gui.classification.filter

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.annotations.F
import java.util.*

@F(name = NoDataStructureInternalsFilter.NAME,
   desc = "This filter removes every object that is a data structure internal",
   collection = at.jku.anttracks.classification.enumerations.ClassifierSourceCollection.FASTHEAP)
class NoDataStructureInternalsFilter : Filter() {
    companion object {
        const val NAME = "No data structure internal objects"
    }

    private val knownToBeLeaf = BitSet()
    private val knownToBeInternal = BitSet()

    public override fun classify(): Boolean {
        if (knownToBeInternal.get(index())) {
            return false
        }
        if (knownToBeLeaf.get(index())) {
            return true
        }

        fastHeap()!!.getDataStructures(index(), false, false)?.forEach {
            knownToBeLeaf.or(it.getLeafObjects(fastHeap(), false))
            knownToBeInternal.or(it.getInternalObjects(fastHeap()))
        }
        return !knownToBeInternal.get(index())
    }
}
