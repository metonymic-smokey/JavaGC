package at.jku.anttracks.gui.model

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierFactoryList
import at.jku.anttracks.classification.FilterFactoryList
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import java.util.*
import java.util.function.Supplier
import java.util.logging.Logger

interface IAvailableClassifierInfo {
    val availableClassifier: ClassifierFactoryList
    val availableFilter: FilterFactoryList
    val fastHeapSupplier: Supplier<IndexBasedHeap?>
    val symbolsSupplier: Supplier<Symbols?>
    fun getDummyClassifier(classifier: Class<out Classifier<*>>): Classifier<*>?
}

abstract class AvailableFastHeapClassifierInfo : IAvailableClassifierInfo {
    override val availableClassifier: ClassifierFactoryList by lazy {
        ClassifierFactoryList(symbolsSupplier,
                              fastHeapSupplier,
                              Consts.CLASSIFIERS_DIRECTORY,
                              ClassifierSourceCollection.FASTHEAP)
    }
    override val availableFilter: FilterFactoryList by lazy {
        FilterFactoryList(symbolsSupplier,
                          fastHeapSupplier,
                          Consts.FILTERS_DIRECTORY,
                          ClassifierSourceCollection.FASTHEAP)
    }

    abstract override val fastHeapSupplier: Supplier<IndexBasedHeap?>

    abstract override val symbolsSupplier: Supplier<Symbols?>

    override fun getDummyClassifier(classifier: Class<out Classifier<*>>): Classifier<*>? {
        var c: Classifier<*>? = dummyClassifiers[classifier]
        if (c == null) {
            try {
                classifier.constructors.forEach { it.isAccessible = true }
                dummyClassifiers[classifier] = classifier.newInstance()
            } catch (e: InstantiationException) {
                LOGGER.warning("Error on dummy classifier creation:\n$e")
            } catch (e: IllegalAccessException) {
                LOGGER.warning("Error on dummy classifier creation:\n$e")
            }

            c = dummyClassifiers[classifier]
        }

        return c
    }

    companion object {
        private val LOGGER = Logger.getLogger(AvailableFastHeapClassifierInfo::class.simpleName)
        private val dummyClassifiers = HashMap<Class<out Classifier<*>>, Classifier<*>>()
    }
}

abstract class AvailableDetailedHeapClassifierInfo : IAvailableClassifierInfo {
    override val availableClassifier: ClassifierFactoryList by lazy {
        ClassifierFactoryList(symbolsSupplier,
                              fastHeapSupplier,
                              Consts.CLASSIFIERS_DIRECTORY,
                              ClassifierSourceCollection.DETAILEDHEAP)
    }
    override val availableFilter: FilterFactoryList by lazy {
        FilterFactoryList(symbolsSupplier,
                          fastHeapSupplier,
                          Consts.FILTERS_DIRECTORY,
                          ClassifierSourceCollection.DETAILEDHEAP)
    }

    override val fastHeapSupplier: Supplier<IndexBasedHeap?> = Supplier { null }

    abstract fun getDetailedHeapSupplier(): Supplier<DetailedHeap>?

    override fun getDummyClassifier(classifier: Class<out Classifier<*>>): Classifier<*>? {
        var c: Classifier<*>? = dummyClassifiers[classifier]
        if (c == null) {
            try {
                classifier.constructors.forEach { it.isAccessible = true }
                dummyClassifiers[classifier] = classifier.newInstance()
            } catch (e: InstantiationException) {
                LOGGER.warning("Error on dummy classifier creation:\n$e")
            } catch (e: IllegalAccessException) {
                LOGGER.warning("Error on dummy classifier creation:\n$e")
            }

            c = dummyClassifiers[classifier]
        }

        return c
    }

    companion object {
        private val LOGGER = Logger.getLogger(AvailableFastHeapClassifierInfo::class.simpleName)
        private val dummyClassifiers = HashMap<Class<out Classifier<*>>, Classifier<*>>()
    }
}
