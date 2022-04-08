
package at.jku.anttracks.gui.classification.filter

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.annotations.F
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.heap.space.SpaceType

@F(name = "No objects in OLD spaces",
   desc = "This filter removes every object that resides in an OLD space",
   collection = ClassifierSourceCollection.ALL)
class NoOldSpaceFilter : Filter() {

    public override fun classify(): Boolean? {
        return space().type != SpaceType.OLD
    }

}
