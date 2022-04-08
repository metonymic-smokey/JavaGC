
package at.jku.anttracks.heap

data class GarbageCollectionCause(val id: Int, val name: String?, val common: Boolean)

val ARTIFICIAL_GARBAGE_COLLECTION = GarbageCollectionCause(-1, "Artificial AntTracks Analyzer Event", false)
