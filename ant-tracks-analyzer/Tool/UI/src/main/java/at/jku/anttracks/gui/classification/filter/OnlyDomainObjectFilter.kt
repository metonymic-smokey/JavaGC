
package at.jku.anttracks.gui.classification.filter

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.annotations.F
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection

@F(name = "Only domain objects",
   desc = "This filter removes every object that does not seem to be a domain object (e.g., objects wich a package starting with java)",
   collection = ClassifierSourceCollection.ALL)
class OnlyDomainObjectFilter : Filter() {
    public override fun classify(): Boolean? {
        return type().isPossibleDomainType
    }
}