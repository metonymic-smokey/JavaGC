package at.jku.anttracks.classification

import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.util.ClassifierUtil
import at.jku.anttracks.util.DirectoryClassLoader
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.function.Supplier
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

class ClassifierFactoryList @JvmOverloads constructor(private val symbolsSupplier: Supplier<Symbols?>,
                                                      private val fastHeapSupplier: Supplier<IndexBasedHeap?>,
                                                      additionalClassifierDirectory: String? = null,
                                                      sourceCollection: ClassifierSourceCollection? = null) : ArrayList<ClassifierFactory>() {

    private val LOGGER = Logger.getLogger(this.javaClass.simpleName)

    fun remove(filter: Classifier<*>) {
        find { f -> f.name == filter.getName() }?.also { remove(it) }
    }

    init {
        loadCompiledClassifiers(Thread.currentThread().contextClassLoader, sourceCollection)
        if (additionalClassifierDirectory != null) {
            loadCompiledClassifiers(DirectoryClassLoader(additionalClassifierDirectory), sourceCollection)
            loadSourceCodeClassifiers(additionalClassifierDirectory, sourceCollection)
        }
    }

    private fun loadSourceCodeClassifiers(directory: String, sourceCollection: ClassifierSourceCollection?) {
        try {
            Files.list(Paths.get(directory)).forEach { f ->
                if (f.toString().endsWith(ClassifierUtil.CUSTOM_CLASSIFIER_FILE_EXTENSION)) {
                    val classifierFactory: ClassifierFactory?
                    try {
                        classifierFactory = ClassifierUtil.loadClassifier(f.toFile(), fastHeapSupplier, symbolsSupplier)
                        if (classifierFactory != null && (classifierFactory.sourceCollection == sourceCollection || classifierFactory.sourceCollection == ClassifierSourceCollection.ALL)) {
                            add(classifierFactory.create())
                        }
                    } catch (e: InstantiationException) {
                        e.printStackTrace()
                    } catch (e: IllegalAccessException) {
                        e.printStackTrace()
                    } catch (e: IOException) {
                        e.printStackTrace()
                    }

                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    private fun loadCompiledClassifiers(cl: ClassLoader, sourceCollection: ClassifierSourceCollection?) {
        val serviceLoader = ServiceLoader.load(Classifier::class.java, cl)
        try {
            for (oc in serviceLoader) {
                if (sourceCollection == null || oc.getSourceCollection() == sourceCollection || oc.getSourceCollection() == ClassifierSourceCollection.ALL) {
                    add(oc)
                }
            }
        } catch (sce: ServiceConfigurationError) {
            LOGGER.warning("Loading object classifiers failed!\n$sce")
        }

    }

    operator fun get(name: String): Classifier<*>? {
        for (oc in this) {
            val classifier = oc.create()
            if (classifier.getName() == name) {
                return classifier
            }
        }
        return null
    }

    operator fun <T> get(clazz: Class<T>): Classifier<*>? {
        for (oc in this) {
            val classifier = oc.create()
            if (clazz.isAssignableFrom(classifier.javaClass)) {
                return classifier
            }
        }
        return null
    }

    operator fun get(kClass: KClass<*>): Classifier<*>? {
        for (oc in this) {
            val classifier = oc.create()
            if (kClass.isSuperclassOf(classifier::class)) {
                return classifier
            }
        }
        return null
    }

    fun add(oc: Classifier<*>) {
        for (i in size - 1 downTo 0) {
            if (get(i).name == oc.getName()) {
                removeAt(i)
            }
        }
        add(ClassifierFactory(symbolsSupplier, fastHeapSupplier, oc))
    }

    operator fun contains(name: String): Boolean {
        return get(name) != null
    }
}

