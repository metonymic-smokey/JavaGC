
package at.jku.anttracks.gui.classification.filter

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.annotations.F
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection

@F(name = "No java.lang.* objects",
   desc = "This filter removes every object from the java.lang package (including arrays of these types)",
   collection = ClassifierSourceCollection.ALL)
class NoJavaLangFilter : Filter() {
    public override fun classify(): Boolean? {
        return !type().internalName.contains("Ljava/lang")
    }

}
