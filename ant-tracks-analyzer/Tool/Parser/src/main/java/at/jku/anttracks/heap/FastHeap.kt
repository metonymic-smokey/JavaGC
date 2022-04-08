package at.jku.anttracks.heap

import at.jku.anttracks.heap.labs.IndexHeapObject
import at.jku.anttracks.heap.objects.ObjectInfo
import at.jku.anttracks.heap.space.SpaceInfo
import at.jku.anttracks.heap.symbols.AllocatedTypes
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler
import at.jku.anttracks.util.ProgressListener
import java.util.*

/**
 * Provides the same information as the original [DetailedHeap] datastructure
 * but offers better performance by using index- instead of address-based access
 */
open class FastHeap : IndexBasedHeap {
    constructor(heap: DetailedHeap, initDataStructures: Boolean = true, progressListener: ProgressListener? = null)
            : super(heap, initDataStructures, progressListener)

    constructor(hprofParserHandler: HprofToFastHeapHandler, initDataStructures: Boolean = true)
            : super(hprofParserHandler, initDataStructures)

    //================================================================================
    // fields
    //================================================================================
    public lateinit var objectsArray: Array<IndexHeapObject>

    private lateinit var spaceInfos: Array<SpaceInfo>
    private lateinit var spaceStartAddresses: LongArray

    override fun store(spaceInfos: Array<SpaceInfo>,
                       spaceStartAddresses: LongArray,
                       objectsArray: Array<IndexHeapObject>) {
        this.spaceInfos = spaceInfos
        this.spaceStartAddresses = spaceStartAddresses
        this.objectsArray = objectsArray

        // TODO: This prevents that objects that have a reference to the class loader appear with 99% transitive size
        // Yet, it changes the real composition of the heap (the references exist in the original program, we just ignore it in the IndexBasedHeap)
        // Probably we can come up with a solution, e.g., a flag to the "getPointers" methods if we want to ignore things like class loaders etc. that are always alive
        // TODO mw disabled this again during Memory Cities work because only setting the toPointers to null but not adjusting the fromPointers leads to a broken symmetry,
        // i.e., objects of type A think they are pointed by objects of type B, but type B has set its pointers to null
        /*
        for (int i = 0; i < objectCount; i++) {
            List<RootPtr> rootPtrsToObject = rootPtrs.get(i);
            if (rootPtrsToObject != null
                    && rootPtrsToObject.size() > 0
                    && rootPtrsToObject.stream()
                                       .noneMatch(rp -> rp.getRootType() == RootPtr.RootType.STATIC_FIELD_ROOT || rp.getRootType() == RootPtr.RootType.LOCAL_VARIABLE_ROOT)) {
                Arrays.fill(getToPointers(i), NULL_INDEX);
            }
        }
         */
        // TODO mw this is here for better in the graph view, its hacky and has the same problems as above, fix in the future!

        // TODO: This prevents that objects that have a reference to the class loader appear with 99% transitive size
        // Yet, it changes the real composition of the heap (the references exist in the original program, we just ignore it in the IndexBasedHeap)
        // Probably we can come up with a solution, e.g., a flag to the "getPointers" methods if we want to ignore things like class loaders etc. that are always alive
        // TODO mw disabled this again during Memory Cities work because only setting the toPointers to null but not adjusting the fromPointers leads to a broken symmetry,
        // i.e., objects of type A think they are pointed by objects of type B, but type B has set its pointers to null
        /*
        for (int i = 0; i < objectCount; i++) {
            List<RootPtr> rootPtrsToObject = rootPtrs.get(i);
            if (rootPtrsToObject != null
                    && rootPtrsToObject.size() > 0
                    && rootPtrsToObject.stream()
                                       .noneMatch(rp -> rp.getRootType() == RootPtr.RootType.STATIC_FIELD_ROOT || rp.getRootType() == RootPtr.RootType.LOCAL_VARIABLE_ROOT)) {
                Arrays.fill(getToPointers(i), NULL_INDEX);
            }
        }
         */
        // TODO mw this is here for better in the graph view, its hacky and has the same problems as above, fix in the future!
        for (i in 0 until objectCount) {
            val obj = objectsArray[i]
            if (getType(i).getExternalName(false, false) == AllocatedTypes.MIRROR_CLASS_EXTERNAL_NAME) {
                Arrays.fill(obj.pointsTo, null)
                Arrays.fill(obj.pointsToIndices, NULL_INDEX)
            }
            var nSetNull = 0
            obj.pointedFrom.forEachIndexed { pIdx, pObj ->
                if (pObj!!.type.getExternalName(false, false) == AllocatedTypes.MIRROR_CLASS_EXTERNAL_NAME) {
                    nSetNull++
                }
            }
            if (nSetNull > 0) {
                val original = obj.pointedFrom
                val originalIndices = obj.pointedFromIndices

                obj.pointedFrom = arrayOfNulls(original.size - nSetNull)
                obj.pointedFromIndices = IntArray(originalIndices.size - nSetNull)

                var copyIdx = 0
                for (pIdx in original.indices) {
                    if (original[pIdx]!!.type.getExternalName(false, false) != AllocatedTypes.MIRROR_CLASS_EXTERNAL_NAME) {
                        obj.pointedFrom[copyIdx] = original[pIdx]
                        obj.pointedFromIndices[copyIdx] = originalIndices[pIdx]
                        copyIdx++
                    }
                }
            }
        }
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

    override fun getObjectInfo(objIndex: Int): ObjectInfo? {
        if (!valid(objIndex)) {
            return null
        }

        return objectsArray[objIndex].info
    }

    override fun toIndex(address: Long): Int = toObject(address, objectsArray).index

    override fun getBorn(objIndex: Int): Short {
        if (!valid(objIndex)) {
            return -1
        }

        return objectsArray[objIndex].bornAt
    }

    override fun getSpace(objIndex: Int): SpaceInfo {
        val address = getAddress(objIndex)
        var spaceIndex = Arrays.binarySearch(spaceStartAddresses, address)
        if (spaceIndex < 0) {
            // address is not the start of a space
            spaceIndex = Math.abs(spaceIndex)
            assert(spaceIndex >= 2)   // otherwise address < spaceStartAddresses[0]
            spaceIndex -= 2
        }

        return spaceInfos[spaceIndex]
    }
}
