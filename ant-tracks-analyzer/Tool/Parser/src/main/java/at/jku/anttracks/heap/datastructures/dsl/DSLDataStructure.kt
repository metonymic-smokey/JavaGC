package at.jku.anttracks.heap.datastructures.dsl

import at.jku.anttracks.heap.IndexBasedHeap
import java.util.*

class DSLDataStructure(val id: Int, val headIdx: Int) {
    /**
     * All objects that are part of this data structure according to its DSL definition, NOT the number of data elements in the data structure
     */
    var objectCount = 0
    /**
     * All data structures that are reachable from this data structure (i.e. their head objects are encountered during visitAllObjects())
     */
    val pointedDataStructures: MutableList<DSLDataStructure> = ArrayList()
    var isTopLevelDataStructure = true
    /*
    void visit(current_object) {
        add current_object to current data structure
        mark current_object as visited
        for each not visited ptr_object of current_object {
            if(type of ptr_object is leaf type in current_object's type's data structure definiton) {
                // Just visit ptr_object, but do not continue recursive descent
                add ptr to current data structure
            } else if (type of ptr_object is fllow type in current_object's type's data structure definiton) {
                if(dsLayout(ptr).isHead) {
                    // handle other data structe heads as leaves of current data structure
                    // Just visit ptr_object, but do not continue recursive descent
                    add ptr to current data structure
                } else {
                    // recursive descent
                    visit(ptr)
                }
            }
        }
    }
    */
    /**
     * Will call the accept method of the given visitor, passing the object indices of all objects that form this data structure.
     * Objects pointed by the data elements in the data structure are not visited.
     * Null pointers will not be visited.
     * Duplicate data elements will not be visited twice.
     * No order of visit guaranteed.
     *
     * @param visitor the accept method is called for each object that is part of this data structure according to the definition, passing 1) the pointing object
     * and 2) the object itself. Note that the pointing object passed when visiting the head object of the data structure is NULL_INDEX i.e. -1.
     * @param heap    fast heap needed for getType and getToPointers calls
     */
    inline fun visitAllObjects(heap: IndexBasedHeap, visited : BitSet, visitor: (Int, Int) -> Unit) {
        objectCount = 1
        val stack = Stack<Int>()
        stack.push(headIdx)
        // visit head
        visitor(IndexBasedHeap.NULL_INDEX, headIdx)
        visited.set(headIdx)
        // visit the rest
        while (!stack.isEmpty()) {
            val idx = stack.pop()
            val type = heap.getType(idx)
            val dsLayout = type.dataStructureLayout
            val ptrs = heap.getToPointers(idx)
            if (ptrs != null) {
                for (i in ptrs.indices) {
                    val ptr = ptrs[i]
                    if (ptr != IndexBasedHeap.NULL_INDEX && !visited[ptr]) {
                        // ptr may be a part of the data structure...
                        val ptrType = heap.getType(ptr)
                        val ptrdsLayout = ptrType.dataStructureLayout
                        if (dsLayout.hasFollow(ptrType)) {
                            // 1) The ptr is covered by a data structure definition that should be followed => visit ptr
                            visitor(idx, ptr)
                            visited.set(ptr)
                            objectCount++
                            if (!ptrdsLayout.isHead) {
                                // ... and follow it (do recursive descend)
                                stack.push(ptr)
                            } else {
                                // ... and don't follow but remember the data structure headed by ptr as reachable from this data structure
                                pointedDataStructures.add(heap.dataStructuresByHeadObjectIndexMap[ptr]!!)
                            }
                        } else if (dsLayout.hasFlat(ptrType)) {
                            // 2) The ptr is ONLY covered by a data structure definition that should not be followed => visit ptr without following it
                            visitor(idx, ptr)
                            visited.set(ptr)
                            objectCount++
                            if (ptrdsLayout.isHead) {
                                // ... also remember the data structure headed by ptr as reachable from this data structure
                                pointedDataStructures.add(heap.dataStructuresByHeadObjectIndexMap[ptr]!!)
                            }
                        }
                        // 3) The pointer is not covered by any data structure definition => ignore
                    }
                }
            }
        }
    }

    /**
     * Gather all leaf objects of this data structure, i.e. the objects that represent the 'data' in this data structure
     *
     * @param heap
     * @param onlyDeepLeaves if this flag is false, heads of other data structures within this data structure are treated as leaves, i.e., included in the result and
     * not followed.
     * if this flag is true, heads of other data structures are not included in the result but followed instead followed recursively to find
     * their leaves,
     * which are then included in the result
     * @return a bit set where the object indices of all leaf objects of this data structure are set
     */
    fun getLeafObjects(heap: IndexBasedHeap, onlyDeepLeaves: Boolean): BitSet {
        var closedSet: HashSet<Int?>? = null
        if (onlyDeepLeaves) { // recursive traversal of data structures => endless recursion might happen due to bidirectional links between data structures => use closed set
            closedSet = HashSet()
            closedSet.add(headIdx)
        }
        return getLeafObjects(heap, onlyDeepLeaves, closedSet)
    }

    private fun getLeafObjects(heap: IndexBasedHeap, onlyDeepLeaves: Boolean, closedSet: HashSet<Int?>?): BitSet {
        val leafObjects = BitSet(heap.objectCount)
        visitAllObjects(heap, BitSet()) { parentObjIndex: Int?, objIndex: Int ->
            if (objIndex == headIdx) {
                return@visitAllObjects
            }
            val objType = heap.getType(objIndex)
            val parentdsLayout = heap.getType(parentObjIndex!!).dataStructureLayout
            if (onlyDeepLeaves && objType.dataStructureLayout.isHead) { // we are interested in deep leaves and the visited object is another data structure head contained in this data structure
                if (closedSet!!.add(objIndex)) { // the other data structure was not yet visited to gather leaves
                    leafObjects.or(heap.getHeadedDataStructure(objIndex).getLeafObjects(heap, true, closedSet))
                }
            } else if (!onlyDeepLeaves && objType.dataStructureLayout.isHead ||
                    parentdsLayout.hasFlat(objType) && !parentdsLayout.hasFollow(objType) ||
                    parentdsLayout.hasFollow(objType) && (objType.dataStructureLayout.isEmpty() || heap.getToPointers(objIndex) == null ||
                            Arrays.stream(heap.getToPointers(objIndex)).allMatch { ptr: Int -> ptr == IndexBasedHeap.NULL_INDEX })) { // the visited object is a leaf
                leafObjects.set(objIndex)
            }
        }
        return leafObjects
    }

    fun getInternalObjects(heap: IndexBasedHeap): BitSet {
        val internalObjects = BitSet(heap.objectCount)
        visitAllObjects(heap, BitSet()) { parentObjIndex: Int?, objIndex: Int ->
            if (objIndex == headIdx) {
                return@visitAllObjects
            }
            val objType = heap.getType(objIndex)
            val parentdsLayout = heap.getType(parentObjIndex!!).dataStructureLayout
            if (!objType.dataStructureLayout.isHead &&
                    parentdsLayout.hasFollow(objType) &&
                    !objType.dataStructureLayout.isEmpty() && heap.getToPointers(objIndex) != null &&
                    Arrays.stream(heap.getToPointers(objIndex)).anyMatch { ptr: Int -> ptr != IndexBasedHeap.NULL_INDEX }) { // visited object is neither THE ds head nor a leaf
                internalObjects.set(objIndex)
            }
        }
        return internalObjects
    }

    companion object {
        /**
         * Creates [DSLDataStructure] if given index is a data structure head. Do not call twice for the same objIndex
         *
         * @param objIndex
         * @param heap
         * @return
         */
        @JvmStatic
        fun tryCreateDataStructure(id: Int, objIndex: Int, heap: IndexBasedHeap): DSLDataStructure? {
            return if (isDataStructureHead(objIndex, heap)) {
                DSLDataStructure(id, objIndex)
            } else null
        }

        @JvmStatic
        fun isDataStructureHead(objIndex: Int, heap: IndexBasedHeap): Boolean {
            val dataStructureDefinition = getDataStructureDefinition(objIndex, heap)
            return dataStructureDefinition.isHead
        }

        fun getDataStructureDefinition(objIndex: Int, heap: IndexBasedHeap): DSLDSLayout {
            val type = heap.getType(objIndex)
            return type.dataStructureLayout
        }
    }

}