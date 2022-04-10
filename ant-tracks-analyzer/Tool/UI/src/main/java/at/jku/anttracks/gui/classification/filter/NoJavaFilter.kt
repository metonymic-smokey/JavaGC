
package at.jku.anttracks.gui.classification.filter

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.annotations.F
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection

@F(name = "No java.* objects",
   desc = "This filter removes all object withing the 'java' package, as well as primitive arrays",
   collection = ClassifierSourceCollection.ALL)
class NoJavaFilter : Filter() {
    public override fun classify(): Boolean? {
        return type().internalName.length > 2 && !type().internalName.contains("Ljava")
    }
}
