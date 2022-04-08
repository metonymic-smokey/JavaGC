package at.jku.anttracks.classification

import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.util.DirectoryClassLoader
import at.jku.anttracks.util.FilterUtil
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.function.Supplier
import java.util.logging.Logger
import kotlin.reflect.KClass
import kotlin.reflect.full.isSuperclassOf

class FilterFactoryList @JvmOverloads constructor(private val symbolsSupplier: Supplier<Symbols?>,
                                                  private val fastHeapSupplier: Supplier<IndexBasedHeap?>,
                                                  additionalFilterDirectory: String? = null,
                                                  sourceCollection: ClassifierSourceCollection? = null) : ArrayList<FilterFactory>() {

    protected val LOGGER = Logger.getLogger(this.javaClass.simpleName)

    fun remove(filter: Filter) {
        find { f -> f.name == filter.getName() }?.also { remove(it) }
    }

    init {

        loadCompiledFilters(Thread.currentThread().contextClassLoader, sourceCollection)

        if (additionalFilterDirectory != null) {
            loadCompiledFilters(DirectoryClassLoader(additionalFilterDirectory), sourceCollection)
            loadSourceCodeFilters(additionalFilterDirectory, sourceCollection)
        }
    }

    private fun loadSourceCodeFilters(directory: String, sourceCollection: ClassifierSourceCollection?) {
        try {
            Files.list(Paths.get(directory)).forEach { f ->
                if (f.toString().endsWith(FilterUtil.CUSTOM_FILTER_FILE_EXTENSION)) {
                    var filterFactory: FilterFactory? = null
                    try {
                        filterFactory = FilterUtil.loadFilter(f.toFile(), fastHeapSupplier, symbolsSupplier)
                        if (filterFactory != null && (filterFactory.sourceCollection == sourceCollection || filterFactory.sourceCollection == ClassifierSourceCollection.ALL)) {
                            add(filterFactory.create())
                        }
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } catch (e: InstantiationException) {
                        e.printStackTrace()
                    } catch (e: IllegalAccessException) {
                        e.printStackTrace()
                    }

                }
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    private fun loadCompiledFilters(cl: ClassLoader, sourceCollection: ClassifierSourceCollection?) {
        val serviceLoader = ServiceLoader.load(Filter::class.java, cl)
        try {
            for (filter in serviceLoader) {
                if (sourceCollection == null || filter.getSourceCollection() == sourceCollection || filter.getSourceCollection() == ClassifierSourceCollection.ALL) {
                    add(filter)
                }
            }
        } catch (sce: ServiceConfigurationError) {
            LOGGER.warning("Loading object classifiers failed!\n$sce")
        }

    }

    operator fun get(name: String): Filter? {
        for (oc in this) {
            val filter = oc.create()
            if (filter.getName() == name) {
                return filter
            }
        }
        return null
    }

    operator fun <T> get(clazz: Class<T>): Filter? {
        for (oc in this) {
            val filter = oc.create()
            if (clazz.isAssignableFrom(filter.javaClass)) {
                return filter
            }
        }
        return null
    }

    operator fun get(kClass: KClass<*>): Filter? {
        for (oc in this) {
            val filter = oc.create()
            if (kClass.isSuperclassOf(filter::class)) {
                return filter
            }
        }
        return null
    }

    fun add(oc: Filter) {
        for (i in size - 1 downTo 0) {
            if (get(i).name == oc.getName()) {
                removeAt(i)
            }
        }
        add(FilterFactory(symbolsSupplier, fastHeapSupplier, oc))
    }

    operator fun contains(name: String): Boolean {
        return get(name) != null
    }
}

