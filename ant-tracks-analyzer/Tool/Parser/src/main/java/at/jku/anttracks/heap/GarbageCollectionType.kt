
package at.jku.anttracks.heap

enum class GarbageCollectionType(val id: Int, val isFull: Boolean) {
    MINOR(0, false),
    MAJOR(1, true),
    MAJOR_SYNC(2, true),
    MINOR_SYNC(3, false),
    INITIALIZE(4, true);
    // MUTATOR(-1, false);

    companion object {
        fun parse(id: Int): GarbageCollectionType = if (id in values().indices) values()[id] else throw ArrayIndexOutOfBoundsException(id)
    }
}
