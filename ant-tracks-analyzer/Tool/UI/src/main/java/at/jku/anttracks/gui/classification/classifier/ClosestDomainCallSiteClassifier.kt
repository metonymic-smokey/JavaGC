package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.annotations.C
import at.jku.anttracks.classification.annotations.ClassifierProperty
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.classification.enumerations.ClassifierType
import at.jku.anttracks.gui.classification.classifier.AllocationSiteClassifier.Companion.toClassification
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.utils.ImageUtil
import at.jku.anttracks.util.ImagePack

@C(name = "Closest Domain Call Site",
   desc = "This classifier distinguishes objects based on their closest domain call site. The call site can be splitted up by packages.",
   example = "at -> jku -> anttracks -> classifier",
   type = ClassifierType.HIERARCHY,
   collection = ClassifierSourceCollection.ALL)
class ClosestDomainCallSiteClassifier : Classifier<Array<out Description?>>() {

    @ClassifierProperty(overviewLevel = 10)
    var style = AllocationSiteClassifier.AllocationSiteStyle.Shortest

    @ClassifierProperty(overviewLevel = 10)
    var createNodeForObjectsWithoutClosestDomainCallsite = true

    @ClassifierProperty(overviewLevel = 10)
    var showByteCodeIndex = false

    @ClassifierProperty(overviewLevel = 10)
    var level = 0
        set(value) {
            field = value
            levelWord = when (field) {
                0 -> ""
                1 -> "2nd"
                2 -> "3rd"
                else -> "${value}th"
            }
        }

    private var levelWord: String = ""
        set(value) {
            field = value
            noDomainCallSiteFoundText = "No ${levelWord}-closest domain call site info available"
        }

    private var noDomainCallSiteFoundText = "No closest domain call site info available"
        set(value) {
            field = value
            noDomainCallSiteFoundDescription = if (createNodeForObjectsWithoutClosestDomainCallsite) Description(noDomainCallSiteFoundText) else null
        }

    private var noDomainCallSiteFoundDescription: Description? = Description(noDomainCallSiteFoundText)

    @Throws(Exception::class)
    override fun classify(): Array<out Description?> =
            allocationSite().callSites
                    .filter { it.isPossibleDomainType }
                    .drop(level)
                    .firstOrNull()
                    .toClassification(style, showByteCodeIndex, noDomainCallSiteFoundDescription)

    public override fun loadIcons(): Array<ImagePack>? {
        return arrayOf(ImageUtil.getResourceImagePack("Allocation site", "allocation_site.png"))
    }
}
