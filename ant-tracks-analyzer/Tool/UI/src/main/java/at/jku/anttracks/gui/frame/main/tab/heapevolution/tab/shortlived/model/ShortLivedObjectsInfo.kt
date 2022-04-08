package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.model

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.trees.ClassificationTree
import at.jku.anttracks.gui.classification.classifier.AllocationSiteClassifier
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.IHeapEvolutionInfo
import at.jku.anttracks.gui.model.SelectedClassifierInfo
import at.jku.anttracks.heap.GarbageCollectionCause
import at.jku.anttracks.parser.heapevolution.HeapEvolutionData
import at.jku.anttracks.parser.heapevolution.ObjectAgeCollection
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap

class ShortLivedObjectsInfo(val heapEvolutionInfo: HeapEvolutionInfo) :
        SelectedClassifierInfo(),
        IHeapEvolutionInfo by heapEvolutionInfo {
    //================================================================================
    // GC metrics
    //================================================================================
    var latestGCTime: Long = -1
    val completedGCsCount: Int
        get() = completedMinorGCsCount + completedMajorGCsCount

    val runTime: Long
        get() = latestGCTime - heapEvolutionInfo.startTime

    val totalGCDuration: Long
        get() = minorGCDuration + majorGCDuration

    var minorGCDuration: Long = 0
    var completedMinorGCsCount: Int = 0

    var majorGCDuration: Long = 0
    var completedMajorGCsCount: Int = 0

    // portion of total run time spent on GCs
    val gcDurationPortion: Double
        get() = if (runTime > 0) {
            totalGCDuration / runTime.toDouble()
        } else {
            0.0
        }

    // portion of total GC time spent on minor GCs
    val minorGCDurationPortion: Double
        get() = if (totalGCDuration > 0) {
            minorGCDuration.toDouble() / totalGCDuration
        } else {
            0.0
        }

    // portion of total GC time spent on major GCs
    val majorGCDurationPortion: Double
        get() = if (totalGCDuration > 0) {
            majorGCDuration.toDouble() / totalGCDuration
        } else {
            0.0
        }

    // run time without GCs
    val adjustedRunTime: Long
        get() = runTime - totalGCDuration

    // N GCs every second pure run time
    val gcFrequency: Double
        get() = if (runTime > 0) {
            completedGCsCount / (runTime.toDouble() / 1000)
        } else {
            0.0
        }

    // how much time spent on GCs grouped by their cause
    val gcCauseDurations = Object2LongOpenHashMap<GarbageCollectionCause>()

    // how many GCs occurred grouped by their cause
    val gcCauseCount = Object2LongOpenHashMap<GarbageCollectionCause>()

    //================================================================================
    // Other
    //================================================================================

    lateinit var garbageObjectAgeCollection: ObjectAgeCollection
    lateinit var garbageGroupedByType: ClassificationTree
    lateinit var garbageGroupedByAllocSite: ClassificationTree

    val typeClassifierChain = ClassifierChain(listOf(heapEvolutionInfo.availableClassifier["Age"],
                                                     heapEvolutionInfo.availableClassifier["Type"],
                                                     heapEvolutionInfo.availableClassifier["Allocation Site"].also { (it as AllocationSiteClassifier).style = AllocationSiteClassifier.AllocationSiteStyle.Shortest },
                                                     heapEvolutionInfo.availableClassifier["Call Sites"]))

    val allocSiteClassifierChain = ClassifierChain(listOf(heapEvolutionInfo.availableClassifier["Age"],
                                                          heapEvolutionInfo.availableClassifier["Allocation Site"].also { (it as AllocationSiteClassifier).style = AllocationSiteClassifier.AllocationSiteStyle.Shortest },
                                                          heapEvolutionInfo.availableClassifier["Type"],
                                                          heapEvolutionInfo.availableClassifier["Call Sites"]))

    //================================================================================
    // update info
    //================================================================================
    fun updateAtGCStart(heapEvolutionData: HeapEvolutionData) {
        latestGCTime = heapEvolutionData.currentTime
    }

    fun updateAtGCEnd(heapEvolutionData: HeapEvolutionData) {
        latestGCTime = heapEvolutionData.currentTime
        // TODO: This .merge call destroys the integrity of heapEvolutionData.temp!
        // For example, before this call, heapEvolutionData.temp.objectCount returns 308,228
        // and heapEvolutionData.temp.classify(heapEvolutionData.detailedHeap, typeClassifierChain, null, null, true).root.objectCount returns 308,228
        // After this call, heapEvolutionData.temp.objectCount stilll returns 308,228
        // but heapEvolutionData.temp.classify(heapEvolutionData.detailedHeap, typeClassifierChain, null, null, true).root.objectCount returns more than 309,000 objects
        // It seems that the .merge() call adds objects to the internal maps of heapEvolutionData.temp, please fix this!
        println(heapEvolutionData.tempAgeCollection.classify(heapEvolutionData.detailedHeap, typeClassifierChain, arrayOf(), null, true).root.objectCount)
        println(heapEvolutionData.tempAgeCollection.classify(heapEvolutionData.detailedHeap, typeClassifierChain, arrayOf(), null, true).root.objectCount)
        garbageObjectAgeCollection = ObjectAgeCollection.merge(heapEvolutionData.tempAgeCollection, heapEvolutionData.diedAgeCollection)
        println(heapEvolutionData.tempAgeCollection.classify(heapEvolutionData.detailedHeap, typeClassifierChain, arrayOf(), null, true).root.objectCount)

        // nothing can be calculated at an initial GC END
        if (heapEvolutionData.latestGCStartTime >= 0) {
            val currentGCDuration = heapEvolutionData.currentTime - heapEvolutionData.latestGCStartTime

            if (heapEvolutionData.gcInfos.last().type.isFull) {
                majorGCDuration += currentGCDuration
                completedMajorGCsCount++
            } else {
                minorGCDuration += currentGCDuration
                completedMinorGCsCount++
            }

            gcCauseDurations.addTo(heapEvolutionData.gcInfos.last().cause, currentGCDuration)
            gcCauseCount.addTo(heapEvolutionData.gcInfos.last().cause, 1)
            garbageGroupedByType = garbageObjectAgeCollection.classify(heapEvolutionData.detailedHeap,
                                                                       typeClassifierChain,
                                                                       arrayOf(),
                                                                       null,
                                                                       true)
            garbageGroupedByAllocSite = garbageObjectAgeCollection.classify(heapEvolutionData.detailedHeap,
                                                                            allocSiteClassifierChain,
                                                                            arrayOf(),
                                                                            null,
                                                                            true)
        }
    }

    fun sampleDefaultTrees() {
        garbageGroupedByType.root.sampleTopDown(null)
        garbageGroupedByAllocSite.root.sampleTopDown(null)
    }

    fun reset() {
        minorGCDuration = 0
        completedMinorGCsCount = 0
        majorGCDuration = 0
        completedMajorGCsCount = 0

        latestGCTime = -1
    }
}