
package at.jku.anttracks.gui.classification.filter

import at.jku.anttracks.classification.ClassifierException
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.annotations.F
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection

@F(name = "No objects without pointers",
   desc = "This filter removes every object that has no pointer / reference field",
   collection = ClassifierSourceCollection.ALL)
class NoPointersFilter : Filter() {

    public override fun classify(): Boolean? {
        try {
            val pointsTo = pointsToIndices()
            return pointsTo != null && pointsTo.size > 0
        } catch (ex: ClassifierException) {
            try {
                val pointsTo = pointsTo()
                return pointsTo != null && pointsTo.size > 0
            } catch (e: ClassifierException) {
                e.printStackTrace()
                return false
            }

        }

    }
}
