package at.jku.anttracks.heap.datastructures.auto

class AutoDS(val heapIdx: Int) {

    val content = mutableMapOf(heapIdx to Role.HeadOfContainer)

    fun traverse() {
        var current = heapIdx
        var currentRole = Role.HeadOfContainer

        when (currentRole) {
            Role.HeadOfContainer -> {

            }
            Role.HeadOfContained -> {

            }
            Role.Transition -> {

            }
            Role.CollectionImplDetail -> {

            }
            Role.ContainedImplDetail -> {

            }
            else -> {
                // Should not happen that we step from an object that has no role assigned
            }
        }
    }
}