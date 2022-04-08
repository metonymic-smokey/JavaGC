package at.jku.anttracks.heap.datastructures.auto

import at.jku.anttracks.heap.IndexBasedHeap

enum class Role {
    HeadOfContainer {
        override fun matches(idx: Int, heap: IndexBasedHeap): Boolean = heap.getToPointers(idx)?.any { pointedObjectIdx ->
            heap.getType(pointedObjectIdx).isRecursiveType || heap.getType(pointedObjectIdx).isReferenceArray
        } ?: false
    },
    HeadOfContained {
        override fun matches(idx: Int, heap: IndexBasedHeap): Boolean = true // Node used
    },
    Transition {
        override fun matches(idx: Int, heap: IndexBasedHeap): Boolean =
                heap.getType(idx).isRecursiveType || (heap.getType(idx).isReferenceArray && !heap.symbols.types.getByInternalName(heap.getType(idx).internalName).isRecursiveType)
    },
    PointsToPrimitiveArray {
        override fun matches(idx: Int, heap: IndexBasedHeap): Boolean = heap.getToPointers(idx)?.any { pointedObjectIdx -> heap.getType(pointedObjectIdx).isPrimitiveArray } ?: false
    },
    CollectionImplDetail {
        override fun matches(idx: Int, heap: IndexBasedHeap): Boolean = idx.matches(Transition, heap)
    },
    ContainedImplDetail {
        override fun matches(idx: Int, heap: IndexBasedHeap): Boolean = true
    };

    fun Int.matches(role: Role, heap: IndexBasedHeap): Boolean = role.matches(this, heap)

    abstract fun matches(idx: Int, heap: IndexBasedHeap): Boolean
}