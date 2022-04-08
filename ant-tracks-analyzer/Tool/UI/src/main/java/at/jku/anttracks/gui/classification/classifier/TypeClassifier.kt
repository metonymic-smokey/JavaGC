
package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.annotations.C
import at.jku.anttracks.classification.annotations.ClassifierProperty
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.classification.enumerations.ClassifierType
import at.jku.anttracks.gui.classification.classifier.TypeClassifier.Companion.DESC
import at.jku.anttracks.gui.classification.classifier.TypeClassifier.Companion.EX
import at.jku.anttracks.gui.classification.classifier.TypeClassifier.Companion.NAME
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.utils.ImageUtil
import at.jku.anttracks.util.ImagePack

@C(name = NAME, desc = DESC, example = EX, type = ClassifierType.ONE, collection = ClassifierSourceCollection.ALL)
class TypeClassifier : Classifier<Description>() {

    @ClassifierProperty(overviewLevel = 10)
    var showPackage = false

    @ClassifierProperty(overviewLevel = 10)
    var showMirrorType = false

    public override fun loadIcons(): Array<ImagePack>? {
        return arrayOf(ImageUtil.getResourceImagePack("Type", "type.png"))
    }

    public override fun classify(): Description {
        val typeName = type().getExternalName(!showPackage, showMirrorType)
        return if (type().isPossibleDomainType) {
            Description().appendEmphasized(typeName)
        } else {
            Description().appendDefault(typeName)
        }
    }

    companion object {
        const val NAME = "Type"
        const val DESC = "This classifier distinguishes objects based on their type"
        const val EX = "java.lang.String"
    }
}
