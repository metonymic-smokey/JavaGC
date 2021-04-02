package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.annotations.C
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.classification.enumerations.ClassifierType
import at.jku.anttracks.gui.utils.ImageUtil
import at.jku.anttracks.util.ImagePack

@C(name = "Closest Domain Call Site Available",
   desc = "This classifier distinguishes objects based on their closest domain call site. The call site can be splitted up by packages.",
   example = "Either \"Closest domain call site info available\" or \"No closest domain call site info available\"",
   type = ClassifierType.ONE,
   collection = ClassifierSourceCollection.ALL)
class ClosestDomainCallSiteAvailableClassifier : Classifier<String>() {
    override fun classify(): String =
            if (allocationSite().callSites.firstOrNull { it.isPossibleDomainType } == null)
                "No closest domain call site info available"
            else
                "Closest domain call site info available"

    public override fun loadIcons(): Array<ImagePack>? {
        return arrayOf(ImageUtil.getResourceImagePack("Allocation site", "allocation_site.png"))
    }
}
