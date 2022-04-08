
package at.jku.anttracks.gui.classification.filter

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.annotations.F

@F(name = NoDataStructureHeadsFilter.NAME,
   desc = "This filter removes every object that is a data structure head",
   collection = at.jku.anttracks.classification.enumerations.ClassifierSourceCollection.FASTHEAP)
class NoDataStructureHeadsFilter : Filter() {
    companion object {
        const val NAME = "No data structure head objects"
    }

    public override fun classify(): Boolean {
        val dataStructure = fastHeap()!!.getHeadedDataStructure(index())
        return dataStructure == null
    }
}
