package at.jku.anttracks.heap

import at.jku.anttracks.util.ProgressListener

object IndexBasedHeapFactory {
    enum class HeapType {
        MMAP,
        FAST,
        NEO4J
    }

    var type = HeapType.FAST

    fun create(source: DetailedHeap, initDataStructures: Boolean, progressListener: ProgressListener?): IndexBasedHeap {
        source.shrinkObjectInfoCache()
        return when (type) {
            HeapType.MMAP -> MemoryMappedFastHeap(source)
            HeapType.FAST -> FastHeap(source, initDataStructures, progressListener)
            // HeapType.NEO4J -> Neo4JHeap(source)
            HeapType.NEO4J -> error("Neo4J currently not supported")
        }
    }
}
