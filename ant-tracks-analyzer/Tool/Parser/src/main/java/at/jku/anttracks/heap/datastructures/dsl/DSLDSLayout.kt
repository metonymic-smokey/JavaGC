package at.jku.anttracks.heap.datastructures.dsl

import at.jku.anttracks.heap.symbols.AllocatedType

open class DSLDSLayout(val type: AllocatedType,
                       val followTypes: Array<AllocatedType> = arrayOf(),
                       val flatTypes: Array<AllocatedType> = arrayOf(),
                       val isHead: Boolean = false) {

    fun hasFollow(follower: AllocatedType): Boolean {
        return followTypes.any { follower.isAssignableTo(it) }
    }

    fun hasFlat(flatty: AllocatedType): Boolean {
        return flatTypes.any { flatty.isAssignableTo(it) }
    }

    fun isEmpty() = followTypes.isEmpty() && flatTypes.isEmpty()
}
