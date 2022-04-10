
package at.jku.anttracks.heap

class GarbageCollectionCauses {

    private val causes: MutableMap<Int, GarbageCollectionCause> = mutableMapOf()

    val all: Array<GarbageCollectionCause>
        get() = causes.values.toTypedArray()

    init {
        causes[-1] = ARTIFICIAL_GARBAGE_COLLECTION
    }

    fun add(id: Int, cause: String, ok: Boolean) {
        causes[id] = GarbageCollectionCause(id, cause, ok)
    }

    operator fun get(id: Int): GarbageCollectionCause? {
        return causes[id]
    }

    fun getByName(searchName: String): GarbageCollectionCause? {
        return all.find { (id, name, common) -> name == searchName }
    }

}
