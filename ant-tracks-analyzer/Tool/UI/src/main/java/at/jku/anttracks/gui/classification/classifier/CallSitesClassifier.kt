
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
import at.jku.anttracks.util.safe

@C(name = "Call Sites",
   desc = "This classifier distinguishes objects based on their call sites. An allocation site may consist of multiple call sites, representing the method call chain to the allocation site.",
   example = "HashMap.put() <- Main.main()",
   type = ClassifierType.HIERARCHY,
   collection = ClassifierSourceCollection.ALL)
class CallSitesClassifier : Classifier<Array<Description?>>() {

    @ClassifierProperty(overviewLevel = 10)
    var style = CallSiteStyle.Shortest

    @ClassifierProperty(overviewLevel = 10)
    var collapseInternalCallSites = true

    @ClassifierProperty(overviewLevel = 10)
    var showByteCodeIndex = false

    enum class CallSiteStyle {
        Shortest,
        OmitPackage,
        Full
    }

    public override fun loadIcons(): Array<ImagePack>? {
        return arrayOf(ImageUtil.getResourceImagePack("Call site", "callsite.png"))
    }

    public override fun classify(): Array<Description?> {
        // 0 is the allocation site which we exclude
        val startIndex = 1
        val allocationSite = allocationSite()

        if (collapseInternalCallSites) {
            // Check if we have call site information
            if (allocationSite.callSites.size > 1) {
                var callSiteArraySize = 0
                var consecutiveInternalCallSites = 0
                for (callSiteIdx in startIndex until allocationSite.callSites.size) {
                    if (allocationSite.callSites[callSiteIdx].isPossibleDomainType) {
                        // Every non-internal call site reserves a spot
                        callSiteArraySize++
                        consecutiveInternalCallSites = 0
                    } else {
                        consecutiveInternalCallSites++
                        if (consecutiveInternalCallSites == 1) {
                            // The first internal call site we see for a possible internal chain reserves a spot
                            callSiteArraySize++
                        }
                    }
                }

                val callSiteDescriptions = arrayOfNulls<Description>(callSiteArraySize)
                var resIdx = 0
                consecutiveInternalCallSites = 0
                for (callSiteIdx in startIndex until allocationSite.callSites.size) {
                    if (allocationSite.callSites[callSiteIdx].isPossibleDomainType) {
                        // Non-internal call site
                        if (consecutiveInternalCallSites == 1) {
                            // Before this non-internal call site, there has been exactly one internal one. Use its name
                            val loc = allocationSite.callSites[callSiteIdx - 1]
                            if (loc != null) {
                                callSiteDescriptions[resIdx] = toDescription(loc)
                            }
                            resIdx++
                        } else if (consecutiveInternalCallSites > 1) {
                            // Before this non-internal call site, there have been multiple internal ones. Use special term
                            callSiteDescriptions[resIdx] = Description("(hidden internal call sites)")
                            resIdx++
                        }
                        // Reset internal counter
                        consecutiveInternalCallSites = 0

                        // Now add the non-internal call site
                        val loc = allocationSite.callSites[callSiteIdx]
                        if (loc != null) {
                            callSiteDescriptions[resIdx] = toDescription(loc)
                        }
                        resIdx++

                    } else {
                        // Internal call site
                        consecutiveInternalCallSites++
                        if (callSiteIdx == allocationSite.callSites.size - 1) {
                            // We reached the last call site, and the last call site is internal
                            if (consecutiveInternalCallSites == 1) {
                                // If it is a single internal call site, use its normal name
                                val loc = allocationSite.callSites[callSiteIdx]
                                if (loc != null) {
                                    callSiteDescriptions[resIdx] = toDescription(loc)
                                }
                                resIdx++
                            } else {
                                if (resIdx >= callSiteDescriptions.size) {
                                    println("something is not right here")
                                }
                                // The call chain ends with multiple internal call sites, print special term
                                callSiteDescriptions[resIdx] = Description("(hidden internal call sites)")
                                resIdx++
                            }
                        }
                    }
                }

                return callSiteDescriptions
            } else {
                return arrayOfNulls(0)
            }
        } else {
            val callSiteDescriptions = arrayOfNulls<Description>(allocationSite.callSites.size - startIndex)

            var i = startIndex
            var j = 0
            while (i < allocationSite.callSites.size) {
                val loc = allocationSite.callSites[i]
                if (loc != null) {
                    callSiteDescriptions[j] = toDescription(loc)
                }
                i++
                j++
            }

            return callSiteDescriptions
        }
    }

    private fun toDescription(callSite: AllocationSite.Location): Description {
        val locationText = when (style) {
            CallSitesClassifier.CallSiteStyle.Full -> callSite.fullyQualified
            CallSitesClassifier.CallSiteStyle.OmitPackage -> callSite.omitPackage
            CallSitesClassifier.CallSiteStyle.Shortest -> callSite.shortest
        }.safe

        val locationDescription = if (callSite.isPossibleDomainType) {
            Description().appendEmphasized(locationText)
        } else {
            Description(locationText)
        }

        if (showByteCodeIndex) {
            locationDescription.appendDefault(" @ BCI ${callSite.bci}")
        }

        return locationDescription
    }
}
