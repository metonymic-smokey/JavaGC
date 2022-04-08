package at.jku.anttracks.heap.datastructures.dsl

import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.heap.symbols.AllocatedTypes
import at.jku.anttracks.util.ClosureUtil
import at.jku.anttracks.util.Counter
import at.jku.anttracks.util.ProgressListener
import java.io.ByteArrayInputStream
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.*
import java.util.logging.Logger
import java.util.stream.Collectors
import kotlin.collections.ArrayList

object DataStructureUtil {

    private val LOGGER = Logger.getLogger(DataStructureUtil::class.java.simpleName)
    private const val DEFAULT_DATA_STRUCTURE_DEFINITIONS_RESOURCE_FOLDER = "/datastructures"

    /**
     * Retrieves the default data structure definition files
     *
     * @return a [Set] of URIs that represent the default data structure definition files
     */
// JAR files are zip files and need some special handling in Java nio
    val defaultDataStructureDefinitionFiles: Set<URI>
        get() {
            val defaultDataStructureDefinitionsFolderUrl = DSLDataStructure::class.java.getResource(DEFAULT_DATA_STRUCTURE_DEFINITIONS_RESOURCE_FOLDER)
            val defaultDataStructureDefinitionsFolderUri: URI
            var fs: FileSystem? = null
            try {
                defaultDataStructureDefinitionsFolderUri = defaultDataStructureDefinitionsFolderUrl.toURI()
                if (defaultDataStructureDefinitionsFolderUri.scheme == "jar") {
                    fs = FileSystems.newFileSystem(defaultDataStructureDefinitionsFolderUri, emptyMap<String, String>())
                }

                val fileUris =
                        Files.list(Paths.get(defaultDataStructureDefinitionsFolderUri))
                                .filter { path -> path.toString().endsWith(".ds") }
                                .map<URI> { it.toUri() }
                                .collect(Collectors.toSet())
                fs?.close()
                return fileUris
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: URISyntaxException) {
                e.printStackTrace()
            }

            return HashSet()
        }

    /**
     * Parses the given data structure definition files
     *
     * @param dataStructureDefinitionFiles URI of data structure definition files to be parsed
     * @return a list of [DSLDSPartDesc] instances defined in the given .ds files or empty [List] if no files or
     * definitions present
     */
    @JvmOverloads
    fun parseDataStructureDefinitionFiles(dataStructureDefinitionFiles: Collection<URI>? = defaultDataStructureDefinitionFiles): List<DSLDSPartDesc> {
        // parse data structure definitions out of all selected data structure definition files
        if (dataStructureDefinitionFiles == null || dataStructureDefinitionFiles.isEmpty()) {
            return ArrayList()
        }
        LOGGER.info("Merging data structure definition files: ${dataStructureDefinitionFiles.joinToString(", ")}")
        val fileSystems = HashSet<FileSystem>()
        dataStructureDefinitionFiles
                .filter { uri -> uri.scheme == "jar" }
                .forEach { fileUri ->
                    try {
                        fileSystems.add(FileSystems.getFileSystem(fileUri))
                    } catch (e: FileSystemNotFoundException) {
                        try {
                            fileSystems.add(FileSystems.newFileSystem(fileUri, emptyMap<String, String>()))
                        } catch (e1: IOException) {
                            e1.printStackTrace()
                        }

                    }
                }

        val mergedFiles: String = dataStructureDefinitionFiles
                .flatMap { uri ->
                    try {
                        Files.readAllLines(Paths.get(uri))
                    } catch (e: IOException) {
                        e.printStackTrace()
                        listOf<String>()
                    }
                }
                .joinToString("\n")

        fileSystems.forEach { fs ->
            try {
                fs.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }

        val parser = Parser(Scanner(ByteArrayInputStream(mergedFiles.toByteArray(StandardCharsets.UTF_8))))
        parser.Parse()
        parser.allDataStructurePartDescriptions.forEach { dsPart -> LOGGER.info(String.format("Parsed data structure description: %s", dsPart.fullyQualifiedName)) }
        return parser.allDataStructurePartDescriptions
    }

    fun resolveDescriptionsAndStoreDefinitionsInTypes(allocatedTypes: AllocatedTypes, descriptions: MutableList<DSLDSPartDesc>) {
        // For each type that has no matching DataStructurePartDescription, create it
        val typeHasDescription = allocatedTypes.associateBy({ it.getFullyQualifiedName(true) }, { false }).toMutableMap()
        descriptions.forEach { if (typeHasDescription.contains(it.fullyQualifiedName)) typeHasDescription[it.fullyQualifiedName] = true }

        typeHasDescription.filter { !it.value }.map { it.key }.forEach { fullyQualifiedName ->
            val allocatedType = allocatedTypes.getByFullyQualifiedExternalName(fullyQualifiedName) ?: throw (IllegalStateException("Type must be found for name $fullyQualifiedName"))
            val fallbackDescForTypeWithoutUserDesc = DSLDSPartDesc(allocatedType.getFullyQualifiedName(true), false)
            if (allocatedType.isArray) {
                // Arrays follow everything in them
                fallbackDescForTypeWithoutUserDesc.addPointsToDescription(DSLDSParsedReferenceInfo("*", true))
            }
            descriptions.add(fallbackDescForTypeWithoutUserDesc)
            if (fallbackDescForTypeWithoutUserDesc.pointsToDescriptions.isEmpty()) {
                LOGGER.fine(String.format("Created empty DataStructurePartDescription for type %s", allocatedType.getExternalName(false, true)))
            } else {
                LOGGER.fine(String.format("Created 'follow-all' DataStructurePartDescription for type %s", allocatedType.getExternalName(false, true)))
            }
        }

        // Now, resolve the DataStructureParts
        // This matches all their PointsToTypes to DataStructureDescriptions
        descriptions.forEach { dsp -> dsp.resolve(allocatedTypes, descriptions) }

        // Currently, we may have more DataStructureDescriptions than types
        // This may be due to the fact that we have data structure definition in files for data structures that do not exist in our application

        // As a next step, to reduce usage complexity, convert all DSDesc to DSLayout
        // There is exactly one DSLayout per AllocatedType, and one AllocatedType per DSLayout
        descriptions
                .filter { desc -> desc.type != null }
                .forEach { desc ->
                    // Collect data for new definition
                    val type = desc.type!!
                    val followTypes: Array<AllocatedType> =
                            desc.deepResolvedDSParts.mapNotNull { it.type }.toTypedArray()
                    val flatTypes: Array<AllocatedType> =
                            desc.flatResolvedDSParts.mapNotNull { it.type }.toTypedArray()
                    val isHeap = desc.isHead

                    val layout = DSLDSLayout(type,
                                             followTypes,
                                             flatTypes,
                                             isHeap)
                    type.dataStructureLayout = layout
                }
        // Assertion: Every type has a data structure definition
        allocatedTypes.forEach {
            assert(it.dataStructureLayout != null) {
                "Type $it is expected to have a data structure definition, but does not have one!"
            }
        }
    }

    @JvmStatic
    fun calculateDataStructureComposition(heap: IndexBasedHeap, dataStructures: Array<DSLDataStructure>, objectCount: Int, progressListener: ProgressListener? = null): Array<IntArray?> {
        //ApplicationStatistics.Measurement mBuildDataStructureComposition = ApplicationStatistics.getInstance().createMeasurement("Build data structure composition");
        //mCreateDataStructures.end();
//ApplicationStatistics.Measurement mBuildDataStructureComposition = ApplicationStatistics.getInstance().createMeasurement("Build data structure composition");
        val dataStructureComposition = arrayOfNulls<IntArray>(objectCount)
        // build data structure composition array...
        val visited = BitSet(heap.objectCount)
        for ((i, ds) in dataStructures.withIndex()) {
            val dataStructureIndex: Int = i
            if (progressListener != null && i % 100 == 0) {
                progressListener.fire(0.2 + 0.6 / dataStructures.size * i, String.format("Calculate data structure composition for data structure #%,d of %,d", i, dataStructures.size))
            }
            ds.visitAllObjects(heap, visited) { parentObjIndex: Int, objIndex: Int ->
                var dataStructuresOfObjIndex: IntArray? = dataStructureComposition[objIndex]
                // update data structures for object at given index...
                if (dataStructuresOfObjIndex == null) { // create new array with first entry
                    dataStructuresOfObjIndex = intArrayOf(dataStructureIndex)
                    arrayCreationCounter.inc()
                } else { // append to existing array
                    dataStructuresOfObjIndex = Arrays.copyOf(dataStructuresOfObjIndex, dataStructuresOfObjIndex.size + 1)
                    dataStructuresOfObjIndex[dataStructuresOfObjIndex.size - 1] = dataStructureIndex
                    arrayIncreaseCounter.inc()
                }
                // update data structures array of object
                dataStructureComposition[objIndex] = dataStructuresOfObjIndex
            }

            visited.clear()
        }
        return dataStructureComposition
    }

    /*
    @JvmStatic
    fun findDataStructuresNew(heap: IndexBasedHeap) {
        val rootPointedIndices = heap.rootPointerMap.keys.filter { rootPoinedIndex -> rootPoinedIndex >= 0 }

        val visited = BitSet()

        for (rootPointedIndex in rootPointedIndices) {
            findDataStructuresNew(heap, null, rootPointedIndex, visited)
        }
    }
     */

    /*
    @JvmStatic
    private fun findDataStructuresNew(heap: IndexBasedHeap, currentDataStructure: DataStructure?, currentObject: Int, visited: BitSet) {
        val stack = Stack<Int>()
        stack.push(currentObject)
        visited.set(currentObject)

        var objIndex = toProcess.nextSetBit(0)

        while (objIndex != -1) {
            // Step 1: Check if current object is data structure
            val dataStructureHead: DataStructure? = tryCreateDataStructure(dsCounter.getAndInc().toInt(), objIndex, heap)
            if (dataStructureHead == null) {
                // No, current object is no data structure head
            } else {
                // Yes, current object is data structure head
            }
            objIndex = toProcess.nextSetBit(objIndex + 1)
        }
    }
     */

    @JvmStatic
    fun detectTopLevelDataStructures(dataStructures: Array<DSLDataStructure>, progressListener: ProgressListener? = null) {
        //mBuildDataStructureComposition.end();
// Detect top-level data structures
        for (i in dataStructures.indices) {
            val currentDataStructure: DSLDataStructure = dataStructures.get(i)
            if (progressListener != null && i % 100 == 0) {
                progressListener.fire(0.8 + 0.2 / dataStructures.size * i,
                                      String.format("Detect top-level data structures. Currently inspecting data structure #%,d of %,d", i, dataStructures.size))
            }
            for (pointedDS in currentDataStructure.pointedDataStructures) {
                if (!pointedDS.isTopLevelDataStructure) { // pointed DS has already been marked as non-top-level by another DS
                    continue
                }
                val transitiveDSClosure = ClosureUtil.transitiveClosure(intArrayOf(pointedDS.id),
                                                                        null,
                                                                        { BitSet() },
                                                                        { id -> dataStructures[id].pointedDataStructures.map { it.id }.toIntArray() },
                                                                        { true })
                if (!transitiveDSClosure[currentDataStructure.id]) { // this ds is not in its pointed ds's transitive closure => all objects in the closure are owned by this ds (i.e. not top level)
                    transitiveDSClosure.stream().forEach { reachableDSIndex: Int -> dataStructures.get(reachableDSIndex).isTopLevelDataStructure = false }
                }
            }
        }
    }

    @JvmStatic
    val arrayCreationCounter = Counter()

    @JvmStatic
    val arrayIncreaseCounter = Counter()

    @JvmStatic
    val dsCounter = Counter()
}