package at.jku.anttracks.heap

import at.jku.anttracks.heap.labs.IndexHeapObject
import at.jku.anttracks.heap.space.SpaceInfo
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler

open class FastHeapHPROF(handler: HprofToFastHeapHandler) : IndexBasedHeap(handler, true) {
    //================================================================================
    // fields
    //================================================================================

    protected lateinit var objectsArray: Array<IndexHeapObject>
    private lateinit var fakeSpaceInfo: SpaceInfo

    override fun store(spaceInfos: Array<SpaceInfo>,
                       spaceStartAdresses: LongArray,
                       objectsArray: Array<IndexHeapObject>) {
        this.fakeSpaceInfo = spaceInfos[0]
        this.objectsArray = objectsArray
    }

    //================================================================================
    // utilities
    //================================================================================
    override fun clear() {

    }

    //================================================================================
    // getters
    //================================================================================

    override fun getAddress(objIndex: Int): Long {
        return if (!valid(objIndex)) {
            IndexBasedHeap.NULL_INDEX.toLong()
        } else objectsArray[objIndex].address

    }

    override fun getToPointers(objIndex: Int): IntArray? {
        return if (!valid(objIndex)) {
            null
        } else objectsArray[objIndex].pointsToIndices

    }

    override fun getFromPointers(objIndex: Int): IntArray? {
        return if (!valid(objIndex)) {
            null
        } else objectsArray[objIndex].pointedFromIndices
    }

    override fun getObjectInfo(objIndex: Int): at.jku.anttracks.heap.objects.ObjectInfo? {
        if (!valid(objIndex)) {
            return null
        }

        return objectsArray[objIndex].info
    }

    override fun toIndex(address: Long): Int = toObject(address, objectsArray).index

    override fun getBorn(objIndex: Int): Short {
        return -1
    }

    override fun getSpace(objIndex: Int): SpaceInfo {
        return fakeSpaceInfo
    }
}
