
package at.jku.anttracks.gui.classification.filter

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.annotations.ClassifierProperty
import at.jku.anttracks.classification.annotations.F
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection

@F(name = OnlyDataStructureHeadsFilter.NAME,
   desc = "This filter removes every object that is not the head of a data structure",
   collection = ClassifierSourceCollection.FASTHEAP)
class OnlyDataStructureHeadsFilter : Filter() {

    @ClassifierProperty(overviewLevel = 10)
    var onlyTopLevelDataStructures = true

    public override fun classify(): Boolean? {
        val dataStructure = fastHeap()!!.getHeadedDataStructure(index())

        return dataStructure != null && (!onlyTopLevelDataStructures || dataStructure.isTopLevelDataStructure)
    }

    companion object {
        const val NAME = "Only (top-level) data structure heads"
    }

}
