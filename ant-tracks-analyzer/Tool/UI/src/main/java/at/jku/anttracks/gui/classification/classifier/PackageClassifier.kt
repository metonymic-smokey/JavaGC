
package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.annotations.C
import at.jku.anttracks.classification.annotations.ClassifierProperty
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.classification.enumerations.ClassifierType
import at.jku.anttracks.gui.classification.classifier.PackageClassifier.Companion.DESC
import at.jku.anttracks.gui.classification.classifier.PackageClassifier.Companion.EX
import at.jku.anttracks.gui.classification.classifier.PackageClassifier.Companion.NAME
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.utils.ImageUtil
import at.jku.anttracks.util.ImagePack

@C(name = NAME, desc = DESC, example = EX, type = ClassifierType.HIERARCHY, collection = ClassifierSourceCollection.ALL)
class PackageClassifier : Classifier<Array<Description>>() {

    @ClassifierProperty(overviewLevel = 10)
    var stacked = false

    public override fun loadIcons(): Array<ImagePack>? {
        return arrayOf(ImageUtil.getResourceImagePack("Type", "type.png"))
    }

    public override fun classify(): Array<Description> {
        return if (!stacked) {
            if (type().isPossibleDomainType()) {
                Array(1) { Description().appendEmphasized(type().getPackage()) }
            } else {
                Array(1) { Description().appendDefault(type().getPackage()) }
            }
        } else {
            type().`package`.split(".").map { packagePart -> Description(packagePart) }.toTypedArray()
        }
    }

    companion object {
        const val NAME = "Package"
        const val DESC = "This classifier distinguishes objects based on their package"
        const val EX = "java.lang"
    }
}
