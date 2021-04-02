package at.jku.anttracks.heap.datastructures.dsl

import java.util.*
import java.util.regex.Pattern

/**
 * Represents a single "line" read from a data structure
 */
class DSLDSParsedReferenceInfo(val name: String, val isFollow: Boolean) {
    // create regex from given name, that matches all wildcards ('*') in the name with anything
    private val namePattern: Pattern = Pattern.compile(name.split(Pattern.quote("*").toRegex()).joinToString(".*") { Pattern.quote(it) })

    var resolvedDSParts: MutableList<DSLDSPartDesc> = ArrayList()

    val isFlat: Boolean
        get() = !isFollow

    fun resolve(descriptions: List<DSLDSPartDesc>) {
        resolvedDSParts.clear()
        if (name == "*") {
            resolvedDSParts.add(descriptions.find { it.fullyQualifiedName == "java.lang.Object" }!!)
        } else {
            resolvedDSParts.addAll(descriptions.filter { matches(it.fullyQualifiedName) })
        }
    }

    private fun matches(type: String): Boolean {
        return namePattern.matcher(type).matches()
    }
}
