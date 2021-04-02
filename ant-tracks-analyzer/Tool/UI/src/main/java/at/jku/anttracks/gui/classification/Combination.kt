package at.jku.anttracks.gui.classification

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.gui.classification.classifier.AllocationSiteClassifier
import at.jku.anttracks.gui.classification.classifier.CallSitesClassifier
import at.jku.anttracks.gui.classification.classifier.DirectGCRootsClassifier
import at.jku.anttracks.gui.classification.classifier.TypeClassifier
import at.jku.anttracks.gui.classification.filter.OnlyDomainObjectFilter
import at.jku.anttracks.gui.model.IAvailableClassifierInfo
import at.jku.anttracks.util.safe
import kotlin.reflect.KClass

val DEFAULT_CLASSIFIERS = listOf<KClass<*>>(TypeClassifier::class, AllocationSiteClassifier::class, CallSitesClassifier::class)

enum class CombinationType {
    BOTTOM_UP,
    TOP_DOWN,
    HEAP_EVOLUTION
}

class DefaultCombinations(val availableClassifiers: IAvailableClassifierInfo) {
    private val bottomUp = Combination("Bottom Up: Default",
                                       "Often, objects of the same type accumulate.\n" +
                                               "Thus, this combination groups all heap objects first by their types, " +
                                               "then by their allocation sites (i.e., the code location where an object has been created) " +
                                               "and then by their call sites (i.e., from where the allocating method has been called)",
                                       ClassifierChain(
                                               availableClassifiers.availableClassifier[TypeClassifier::class] ?: error("Classifier not found"),
                                               availableClassifiers.availableClassifier[AllocationSiteClassifier::class] ?: error("Classifier not found"),
                                               availableClassifiers.availableClassifier[CallSitesClassifier::class] ?: error("Classifier not found")
                                       ),
                                       listOf(),
                                       false)

    private val bottomUpDomainObjects = Combination("Bottom Up: Domain Objects",
                                                    "Often, we are not interested in \"internal\" types (such as object from the java.lang package), but only in the monitored application's domain objects.\n" +
                                                            "This combination filters out typical internal objects, and then applies the default bottom-up grouping (by type, then by allocation site, followed by " +
                                                            "call sites).",
                                                    ClassifierChain(
                                                            availableClassifiers.availableClassifier[TypeClassifier::class] ?: error("Classifier not found"),
                                                            availableClassifiers.availableClassifier[AllocationSiteClassifier::class] ?: error("Classifier not found"),
                                                            availableClassifiers.availableClassifier[CallSitesClassifier::class] ?: error("Classifier not found")
                                                    ),
                                                    listOf(
                                                            availableClassifiers.availableFilter[OnlyDomainObjectFilter::class] ?: error("Filter not found")
                                                    ),
                                                    false)

    private val topDownGCRoots = Combination("Top Down: GC Roots",
                                             "GC roots (for example static fields) keep their referenced objects alive, and these objects in turn keep their referenced objects " +
                                                     "alive, and so on.\n" +
                                                     "This combination displays all GC roots, which can be inspected in a top-down fashion for large reachability (i.e., deep size) and large ownership (i.e., retained size).",
                                             ClassifierChain(
                                                     availableClassifiers.availableClassifier[DirectGCRootsClassifier::class] ?: error("Classifier not found"),
                                                     availableClassifiers.availableClassifier[TypeClassifier::class] ?: error("Classifier not found"),
                                                     availableClassifiers.availableClassifier[AllocationSiteClassifier::class] ?: error("Classifier not found")
                                             ),
                                             listOf(),
                                             false)

    private val topDownDataStructures = Combination("Top Down: Data Structures",
                                                    "Data structures that (accidentally) grow over time are a common root cause for memory leaks.\n" +
                                                            "This combination filters out all objects that are not heads of data structures." +
                                                            "The data structure heads can be inspected in a top-down fashion for large reachability (i.e., deep size) and large ownership " +
                                                            "(i.e., retained size).",
                                                    ClassifierChain(
                                                            availableClassifiers.availableClassifier[TypeClassifier::class] ?: error("Classifier not found"),
                                                            availableClassifiers.availableClassifier[AllocationSiteClassifier::class] ?: error("Classifier not found"),
                                                            availableClassifiers.availableClassifier[CallSitesClassifier::class] ?: error("Classifier not found")
                                                    ),
                                                    listOf(),
                                                    true)

    operator fun get(type: CombinationType): List<Combination> =
            when (type) {
                CombinationType.BOTTOM_UP -> listOf(bottomUp, bottomUpDomainObjects)
                CombinationType.TOP_DOWN -> listOf(topDownGCRoots, topDownDataStructures)
                CombinationType.HEAP_EVOLUTION -> listOf(bottomUpDomainObjects, bottomUp).onEach { combination ->
                    combination.classifiers.forEach { classifier ->
                        when (classifier) {
                            is TypeClassifier -> classifier.showPackage = false
                            is AllocationSiteClassifier -> classifier.style = AllocationSiteClassifier.AllocationSiteStyle.Shortest
                            is CallSitesClassifier -> classifier.style = CallSitesClassifier.CallSiteStyle.Shortest
                        }
                    }
                }
            }.safe
}

data class Combination(val name: String, val description: String, val classifiers: ClassifierChain, val filters: List<Filter>, val dataStructureAnalysis: Boolean)