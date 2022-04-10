package at.jku.anttracks.gui.classification.classifier

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.annotations.C
import at.jku.anttracks.classification.annotations.ClassifierProperty
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.classification.enumerations.ClassifierType
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.utils.ImageUtil
import at.jku.anttracks.heap.symbols.AllocationSite
import at.jku.anttracks.util.ImagePack
import java.util.*

@C(name = "Allocation Site",
   desc = "This classifier distinguishes objects based on their allocation site. The allocation site can be splitted up by packages.",
   example = "at -> jku -> anttracks -> classifier",
   type = ClassifierType.HIERARCHY,
   collection = ClassifierSourceCollection.ALL)
class AllocationSiteClassifier : Classifier<Array<out Description?>>() {

    @ClassifierProperty(overviewLevel = 10)
    var style = AllocationSiteStyle.Shortest

    @ClassifierProperty(overviewLevel = 10)
    var showByteCodeIndex = false

    enum class AllocationSiteStyle {
        Stacked,
        Shortest,
        OmitPackage,
        Full,
        Internal
    }

    @Throws(Exception::class)
    override fun classify(): Array<out Description?> = allocationSite().callSites[0].toClassification(style, showByteCodeIndex)

    public override fun loadIcons(): Array<ImagePack>? {
        return arrayOf(ImageUtil.getResourceImagePack("Allocation site", "allocation_site.png"))
    }

    companion object {
        fun AllocationSite.Location?.toClassification(style: AllocationSiteStyle, showByteCodeIndex: Boolean, fallback: Description? = null): Array<out Description?> {
            if (this == null) {
                return arrayOf(fallback)
            }
            return when (style) {
                AllocationSiteStyle.Full -> toDescriptionArray(showByteCodeIndex) { fullyQualified }
                AllocationSiteStyle.OmitPackage -> toDescriptionArray(showByteCodeIndex) { omitPackage }
                AllocationSiteStyle.Shortest -> toDescriptionArray(showByteCodeIndex) { shortest }
                AllocationSiteStyle.Internal -> toDescriptionArray(showByteCodeIndex) { signature }
                AllocationSiteStyle.Stacked -> {
                    val allocationSiteString = fullyQualified
                    val paramStart = allocationSiteString.indexOf('(')

                    if (paramStart < 0) {
                        // some special allocation sites such as "VM internal" do not contain parameters, return full string
                        arrayOf(Description(allocationSiteString))
                    } else {
                        val signature = allocationSiteString.substring(0, paramStart)
                        val declaringTypeIncludingPackage = signature.substring(0, signature.lastIndexOf('.'))
                        val method = signature.substring(declaringTypeIncludingPackage.length + 1)

                        var allocationSite = declaringTypeIncludingPackage.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        allocationSite = Arrays.copyOf(allocationSite, allocationSite.size + 1)
                        allocationSite[allocationSite.size - 1] = method

                        val descriptions = arrayOfNulls<Description>(allocationSite.size + if (showByteCodeIndex) 1 else 0)
                        for (i in allocationSite.indices) {
                            descriptions[i] = Description(allocationSite[i])
                        }
                        if (showByteCodeIndex) {
                            descriptions[descriptions.size - 1] = Description(" @ BCI ${bci}")
                        }
                        descriptions
                    }
                }
            }
        }

        private inline fun AllocationSite.Location.toDescriptionArray(showByteCodeIndex: Boolean, textExtractor: AllocationSite.Location.() -> String): Array<Description> {
            val desc = if (isPossibleDomainType) {
                Description().appendEmphasized(textExtractor())
            } else {
                Description().appendDefault(textExtractor())
            }

            if (showByteCodeIndex) {
                desc.appendDefault(" @ BCI ${bci}")
            }

            return arrayOf(desc)
        }
    }
}