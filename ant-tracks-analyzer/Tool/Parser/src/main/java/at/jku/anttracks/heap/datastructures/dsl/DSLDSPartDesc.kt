package at.jku.anttracks.heap.datastructures.dsl

import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.heap.symbols.AllocatedTypes
import java.util.HashSet
import kotlin.collections.ArrayList
import kotlin.collections.List
import kotlin.collections.MutableList
import kotlin.collections.filter
import kotlin.collections.flatMap
import kotlin.collections.forEach
import kotlin.collections.joinToString
import kotlin.collections.sortedBy

class DSLDSPartDesc
/**
 * The default constructor used data structure parts
 *
 * @param fullyQualifiedName   The data structures part's fully qualified name (no placeholders allowed)
 * @param isHead `true` if the given type is a data structure head, `false` otherwise
 */
(val fullyQualifiedName: String, val isHead: Boolean) {

    // Will always contain the data read from the file
    val pointsToDescriptions = HashSet<DSLDSParsedReferenceInfo>()

    // This 4 fields will be initialized during resolving
    var type: AllocatedType? = null // The type this DSPart belongs to, or null if the type does not exist in the monitored application
        private set
    val flatResolvedDSParts: MutableList<DSLDSPartDesc> = ArrayList()
    val deepResolvedDSParts: MutableList<DSLDSPartDesc> = ArrayList()
    val isInternal: Boolean
        get() = !isHead

    fun resolve(types: AllocatedTypes, descriptions: List<DSLDSPartDesc>) {
        // Try to find matching type.
        // This may result in null, because we may have a description for a type that has not been allocated in the current application
        type = types.getByFullyQualifiedExternalName(fullyQualifiedName);

        pointsToDescriptions.forEach { ptt -> ptt.resolve(descriptions) }

        flatResolvedDSParts.clear()
        flatResolvedDSParts.addAll(pointsToDescriptions.filter { ptt -> ptt.isFlat }.flatMap { ptt -> ptt.resolvedDSParts })
        deepResolvedDSParts.clear()
        deepResolvedDSParts.addAll(pointsToDescriptions.filter { ptt -> ptt.isFollow }.flatMap { ptt -> ptt.resolvedDSParts })
    }

    override fun toString(): String {

        val sb = StringBuilder()
        sb.append(fullyQualifiedName)
        sb.append(" { \n")
        sb.append(pointsToDescriptions
                          .flatMap { it.resolvedDSParts }
                          .sortedBy { it.fullyQualifiedName }
                          .joinToString(";\n") { it.fullyQualifiedName })
        sb.append(" }")
        return sb.toString()
    }

    fun addPointsToDescription(subPart: DSLDSParsedReferenceInfo) {
        pointsToDescriptions.add(subPart)
    }

    /**
     * Data structure parts match an object of a certain type if they share the exact same name
     *
     * @param type The object's type to check against
     * @return `true` if the given type matched the data structure part
     */
    fun nameMatches(type: AllocatedType): Boolean {
        return this.fullyQualifiedName == type.getFullyQualifiedName(true)
    }
}