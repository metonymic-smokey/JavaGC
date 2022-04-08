
package at.jku.anttracks.gui.classification.filter

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.annotations.F
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection

@F(name = "No primitive arrays",
   desc = "This filter removes every primitive array",
   collection = ClassifierSourceCollection.ALL)
class NoPrimitiveArrayFilter : Filter() {

    public override fun classify(): Boolean? {
        return !type().isPrimitiveArray
    }

}
