
package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierException
import at.jku.anttracks.classification.annotations.C
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.classification.enumerations.ClassifierType
import at.jku.anttracks.gui.utils.ImageUtil

@C(name = "Age",
   desc = "This classifier distinguishes objects based on their age (= # of survived garbage collections)",
   example = "5 GCs survived",
   type = ClassifierType.ONE,
   collection = ClassifierSourceCollection.ALL)
class AgeClassifier : Classifier<String>() {

    @Throws(ClassifierException::class)
    override fun classify() = age().toString() + " GCs survived"

    override fun loadIcons() = arrayOf(ImageUtil.getResourceImagePack("Age", "age.png"))
}
