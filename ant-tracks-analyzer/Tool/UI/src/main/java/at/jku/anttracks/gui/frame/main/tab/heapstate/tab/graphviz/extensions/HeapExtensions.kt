package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.extensions

import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.util.andNew
import at.jku.anttracks.util.asSequence
import at.jku.anttracks.util.toBitSet
import at.jku.anttracks.util.toIntArray
import java.util.*

val MOST_INTERESTING_THRESHOLD = 0.05
val INTERESTING_ROOT_TYPES = arrayOf(RootPtr.RootType.STATIC_FIELD_ROOT, RootPtr.RootType.LOCAL_VARIABLE_ROOT)

fun IndexBasedHeap.getRoots(objIndex: Int): List<RootPtr> {
    return this.getRoot(objIndex)?.filter { INTERESTING_ROOT_TYPES.contains(it.rootType) } ?: listOf()
}

private fun IndexBasedHeap.getFromPointerOnlyDataStructureHeadsAsIterable(objIndex: Int): Sequence<Int> {
    val dataStructures = getDataStructures(objIndex, true, false)

    if (dataStructures == null || dataStructures.isEmpty()) {
        // I am not contained in a data structure, so I cannot be referenced from a DS head or DS internal but at max from a DS leaf.
        return getFromPointers(objIndex)?.asSequence() ?: sequenceOf()
    } else {
        val fromPtrs = getFromPointers(objIndex)
        val fromPtrSet = mutableSetOf<Int>()
        if (fromPtrs != null) {
            for (fromPtr in fromPtrs) {
                val fromPtrDataStructures = getDataStructures(fromPtr, true, false)
                if (fromPtrDataStructures == null || fromPtrDataStructures.isEmpty()) {
                    fromPtrSet += fromPtr
                } else {
                    for (fromPtrDataStructure in fromPtrDataStructures) {
                        if (dataStructures.contains(fromPtrDataStructure)) {
                            // My pointee and I share the same data structure -> take the DS's head
                            fromPtrSet += fromPtrDataStructure.headIdx
                        } else {
                            // My pointee and I do not share the same data structure -> The pointee must be a leaf of the DS and thus will be shown
                            fromPtrSet += fromPtr
                        }
                    }
                }
            }
        }
        return fromPtrSet.asSequence()
    }
}

private fun IndexBasedHeap.getToPointerOnlyDataStructureHeadsAsIterable(objIndex: Int): Sequence<Int> {
    return this.getHeadedDataStructure(objIndex)?.getLeafObjects(this, false)?.asSequence() ?: this.getToPointers(objIndex).asSequence()
}

fun IndexBasedHeap.getFromPointerOnlyDataStructureHeads(objIndex: Int): BitSet {
    val result = this.getFromPointerOnlyDataStructureHeadsAsIterable(objIndex).toBitSet()
    result.and(this.getIndirectlyReachableObjectsByTypes(*INTERESTING_ROOT_TYPES))
    return result
}

fun IndexBasedHeap.getToPointerOnlyDataStructureHeads(objIndex: Int): BitSet {
    val result = this.getToPointerOnlyDataStructureHeadsAsIterable(objIndex).toBitSet()
    result.and(this.getIndirectlyReachableObjectsByTypes(*INTERESTING_ROOT_TYPES))
    return result
}

fun IndexBasedHeap.getFromPointerOnlyDataStructureHeads(objIndices: BitSet): BitSet {
    val result = objIndices.asSequence().flatMapTo(mutableSetOf()) { this.getFromPointerOnlyDataStructureHeadsAsIterable(it) }.toBitSet()
    result.and(this.getIndirectlyReachableObjectsByTypes(*INTERESTING_ROOT_TYPES))
    return result
}

fun IndexBasedHeap.getToPointerOnlyDataStructureHeads(objIndices: BitSet): BitSet {
    val result = objIndices.asSequence().flatMapTo(mutableSetOf()) { this.getToPointerOnlyDataStructureHeadsAsIterable(it) }.toBitSet()
    result.and(this.getIndirectlyReachableObjectsByTypes(*INTERESTING_ROOT_TYPES))
    return result
}

fun IndexBasedHeap.getTypedFromPointers(objIndices: BitSet): Map<Int, BitSet> {
    return objIndices.asSequence()
            .flatMapTo(mutableSetOf()) { this.getFromPointerOnlyDataStructureHeadsAsIterable(it) }
            .groupBy { getObjectInfo(it)?.type?.id ?: -1 }
            .mapValues {
                val result = it.value.toBitSet()
                result.and(this.getIndirectlyReachableObjectsByTypes(*INTERESTING_ROOT_TYPES))
                result
            }
            .filter { e -> !e.value.isEmpty }
}

fun IndexBasedHeap.getTypedToPointers(objIndices: BitSet): Map<Int, BitSet> {
    return objIndices.asSequence()
            .flatMapTo(mutableSetOf()) { this.getToPointerOnlyDataStructureHeadsAsIterable(it) }
            .groupBy { getObjectInfo(it)?.type?.id ?: -1 }
            .mapValues {
                val result = it.value.toBitSet()
                result.and(this.getIndirectlyReachableObjectsByTypes(*INTERESTING_ROOT_TYPES))
                result
            }
            .filter { e -> !e.value.isEmpty }
}

fun IndexBasedHeap.getPathToClosestRoot(objectId: Int): List<Int> {
    val paths = mutableListOf(listOf(objectId))

    while (paths.isNotEmpty()) {
        val path = paths.removeAt(0)
        val roots = this.getRoots(path[0])
        if (roots.isNotEmpty()) {
            return path
        } else {
            paths.addAll(this.getFromPointerOnlyDataStructureHeads(path[0]).asSequence()
                                 .filter { !path.contains(it) }
                                 .map { listOf(it).plus(path) })
        }
    }
    return listOf()
}

fun IndexBasedHeap.getPathsToClosestRoots(objectId: Int): List<List<Int>> {
    return getPathsToClosestRoots(listOf(objectId).toBitSet())
}

fun IndexBasedHeap.getPathsToClosestRoots(objectIds: BitSet): List<List<Int>> {
    val finalizedPaths = mutableListOf<List<Int>>()
    val processPaths = objectIds.asSequence().map { listOf(it) }.toMutableList()
    val finalizedNodes = BitSet()

    while (processPaths.isNotEmpty()) {
        val path = processPaths.removeAt(0)
        val roots = this.getRoots(path[0])
        if (roots.isNotEmpty() || finalizedNodes.get(path[0])) {
            finalizedPaths.add(path)
            path.forEach { finalizedNodes.set(it) }
        } else {
            this.getFromPointerOnlyDataStructureHeads(path[0]).asSequence().filter { !path.contains(it) }.map { listOf(it).plus(path) }.forEach { processPaths.add(0, it) }
        }
    }

    return finalizedPaths
}

fun IndexBasedHeap.getInterestingPathToRootTyped(objectIds: BitSet): Collection<BitSet> {
    val finalObjectGroups = mutableSetOf<BitSet>()

    var currentObjectGroup = objectIds.clone() as BitSet?

    while (currentObjectGroup != null) {
        currentObjectGroup.let { objs -> finalObjectGroups.forEach { objs.andNot(it) } }
        if (!currentObjectGroup.isEmpty) {
            finalObjectGroups.add(currentObjectGroup)
            if (currentObjectGroup.asSequence().any { getRoots(it).isEmpty() }) {
                currentObjectGroup = currentObjectGroup.let { objs ->
                    getTypedFromPointers(objs).values.maxBy {
                        getTransitiveClosureByteCount(objs.andNew(getToPointerOnlyDataStructureHeads(it)))
                    }
                }
            }
        }
    }
    return finalObjectGroups
}

fun IndexBasedHeap.getPathsToRootsTyped(objectIds: BitSet): Collection<BitSet> {
    val finalObjectGroups = mutableSetOf<BitSet>()
    val currentObjectGroups: Queue<BitSet> = ArrayDeque(setOf(objectIds.clone() as BitSet))

    while (currentObjectGroups.isNotEmpty()) {
        val currentObjectGroup = currentObjectGroups.poll()
        finalObjectGroups.forEach { currentObjectGroup.andNot(it) }
        if (!currentObjectGroup.isEmpty) {
            finalObjectGroups.add(currentObjectGroup)
            currentObjectGroups.addAll(getTypedFromPointers(currentObjectGroup).values)
        }
    }
    return finalObjectGroups
}

fun IndexBasedHeap.getPathsToMostInterestingRootsTyped(objectIds: BitSet): Collection<BitSet> {
    val objectsInQuestion = objectIds.cardinality()

    val finalObjectGroups = mutableSetOf<BitSet>()
    val currentObjectGroups: Queue<BitSet> = ArrayDeque(setOf(objectIds.clone() as BitSet))

    while (currentObjectGroups.isNotEmpty()) {
        val currentObjectGroup = currentObjectGroups.poll()

        finalObjectGroups.forEach { currentObjectGroup.andNot(it) }
        if (!currentObjectGroup.isEmpty) {
            finalObjectGroups.add(currentObjectGroup)
            for (pointedFromByType in getTypedFromPointers(currentObjectGroup).values) {
                val type = getType(pointedFromByType.nextSetBit(0))
                finalObjectGroups.forEach { pointedFromByType.andNot(it) }
                //println("... $type reduced to ${pointedFromByType.cardinality().toString("%,d")} pointer obj.")
                if (pointedFromByType.cardinality() == 0) continue
                val reachableObjectsSet = pointedFromByType.transitiveClosure(this)
                reachableObjectsSet.and(objectIds)
                val reachableObjects = reachableObjectsSet.cardinality()
                val interesting = reachableObjects.toDouble() / objectsInQuestion >= MOST_INTERESTING_THRESHOLD
                // println("${getType(currentObjectGroup.nextSetBit(0))} pointed by ${pointedFromByType.cardinality().toString("%,d")} $type: " +
                //                 "$interesting (${reachableObjects.toString("%,d")} of ${objectsInQuestion.toString("%,d")} - " +
                //                 "${(reachableObjects.toDouble() * 100.0 / objectsInQuestion).toString("%,.2f")}%)")
                if (interesting) {
                    currentObjectGroups.add(pointedFromByType)
                }
            }
        }
    }
    return finalObjectGroups

    /*
    val objectsInQuestion = objectIds.cardinality()

    val finalObjectGroups = mutableSetOf<BitSet>()
    val currentObjectGroups: Queue<BitSet> = ArrayDeque(setOf(objectIds.clone() as BitSet))

    while (currentObjectGroups.isNotEmpty()) {
        val currentObjectGroup = currentObjectGroups.poll()

        finalObjectGroups.forEach { currentObjectGroup.andNot(it) }
        if (!currentObjectGroup.isEmpty) {
            finalObjectGroups.add(currentObjectGroup)

            var max = 0
            var maxGroup: BitSet? = null

            for (pointedFromByType in getTypedFromPointers(currentObjectGroup).values) {
                val type = getType(pointedFromByType.nextSetBit(0))
                finalObjectGroups.forEach { pointedFromByType.andNot(it) }
                println("... $type reduced to ${pointedFromByType.cardinality().toString("%,d")} pointer obj.")
                if (pointedFromByType.cardinality() == 0) continue
                val reachableObjectsSet = pointedFromByType.transitiveClosure(this)
                reachableObjectsSet.and(objectIds)
                val reachableObjects = reachableObjectsSet.cardinality()
                val interesting = reachableObjects.toDouble() / objectsInQuestion >= MOST_INTERESTING_THRESHOLD
                println("${getType(currentObjectGroup.nextSetBit(0))} pointed by ${pointedFromByType.cardinality().toString("%,d")} $type: " +
                                "$interesting (${reachableObjects.toString("%,d")} of ${objectsInQuestion.toString("%,d")} - " +
                                "${(reachableObjects.toDouble() * 100.0 / objectsInQuestion).toString("%,.2f")}%)")

                if (reachableObjects > max) {
                    max = reachableObjects
                    maxGroup = pointedFromByType
                }
            }
            if (maxGroup != null) {
                currentObjectGroups.add(maxGroup)
            }
        }
    }
    return finalObjectGroups
     */
}

fun IndexBasedHeap.getPathsToClosestRootsTyped(objectIds: BitSet): Collection<BitSet> {
    val finalObjectGroups = mutableSetOf<BitSet>()
    val currentObjectGroups: Queue<BitSet> = ArrayDeque(setOf(objectIds.clone() as BitSet))

    while (currentObjectGroups.isNotEmpty()) {
        val currentObjectGroup = currentObjectGroups.poll()
        finalObjectGroups.forEach { currentObjectGroup.andNot(it) }
        if (!currentObjectGroup.isEmpty) {
            finalObjectGroups.add(currentObjectGroup)
            if (currentObjectGroup.asSequence().any { getRoots(it).isEmpty() }) {
                currentObjectGroups.addAll(getTypedFromPointers(currentObjectGroup).values)
            }
        }
    }
    return finalObjectGroups
}

fun IndexBasedHeap.getGcClosureByteCount(objectIds: BitSet): Long {
    return this.getClosures(true, true, false, false, objectIds.toIntArray()).gcClosureByteCount
}

fun IndexBasedHeap.getTransitiveClosureByteCount(objectIds: BitSet): Long {
    return this.getClosures(true, false, false, false, objectIds.toIntArray()).transitiveClosureByteCount
}