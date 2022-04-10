
package at.jku.anttracks.heap.labs

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.ObjectVisitor
import at.jku.anttracks.heap.space.Space
import at.jku.anttracks.heap.space.SpaceInfo
import at.jku.anttracks.parser.heap.pointer.PtrUpdateVisitor
import at.jku.anttracks.util.Consts.AVERAGE_OBJECT_SIZE
import at.jku.anttracks.util.Consts.UNDEFINED_ADDR
import at.jku.anttracks.util.TraceException
import java.util.logging.Logger

class MultiObjectLab @JvmOverloads constructor(thread: String,
                                               kind: Kind,
                                               addr: Long,
                                               private var capacity: Int,
                                               private var objectsList: Array<AddressHO?> = arrayOfNulls(Math.max(4, capacity / AVERAGE_OBJECT_SIZE)),
                                               private var addressList: IntArray = IntArray(Math.max(4, capacity / AVERAGE_OBJECT_SIZE))) : Lab(thread, kind, addr) {

    private var position: Int = 0
    private var n: Int = 0
    //private var expansions = 0

    override fun capacity(): Int {
        return if (capacity != Lab.UNKNOWN_CAPACITY) capacity else position
    }

    override fun position(): Int {
        return position
    }

    override fun isFull(): Boolean {
        return capacity != Lab.UNKNOWN_CAPACITY && super.isFull()
    }

    override fun isExtendable(): Boolean {
        return capacity == Lab.UNKNOWN_CAPACITY
    }

    override fun getObjectCount(): Int {
        return n
    }

    override fun iterate(heap: DetailedHeap,
                         space: SpaceInfo,
                         filter: List<Filter>?,
                         visitor: ObjectVisitor,
                         visitorSettings: ObjectVisitor.Settings) {
        var addr = this.addr
        for (i in 0 until n) {
            val obj = objectsList[i]!!
            var accept = true
            if (filter != null) {
                for (f in filter) {
                    try {
                        if (!f.classify(obj,
                                        addr,
                                        obj.info,
                                        space,
                                        obj.type,
                                        obj.size,
                                        obj.isArray,
                                        obj.arrayLength,
                                        obj.site,
                                        LongArray(0),// TODO From pointers
                                        LongArray(0),// TODO Pointers
                                        obj.eventType,
                                        if (visitorSettings.rootPointerInfoNeeded) heap.rootPtrs.get(addr) else emptyList(),
                                        -1,// TODO Age
                                        obj.info.thread,
                                        heap.getExternalThreadName(obj.info.thread))) {
                            accept = false
                            break
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        accept = false
                        break
                    }

                }
            }
            if (accept) {
                visitor.visit(addr,
                              obj,
                              space,
                              if (visitorSettings.rootPointerInfoNeeded) heap.rootPtrs.get(addr) ?: emptyList() else emptyList())
            }
            addr += obj.size.toLong()
        }

    }

    @Throws(TraceException::class)
    override fun tryAllocate(objAddr: Long, `object`: AddressHO): Long {
        var assignedAddr = Lab.OBJECT_NOT_ASSIGNED.toLong()

        if (objAddr == UNDEFINED_ADDR) {
            assignedAddr = allocate(`object`) + addr
        } else if (isInLab(objAddr)) {
            errorOnWrongAddr(thread, objAddr, `object`)
            assignedAddr = allocate(`object`) + addr
        }
        return assignedAddr
    }

    private fun isInLab(objAddr: Long): Boolean {
        return if (capacity == Lab.UNKNOWN_CAPACITY) {
            objAddr == top()
        } else {
            objAddr >= addr && objAddr < addr + capacity
        }
    }

    @Throws(TraceException::class)
    private fun allocate(obj: AddressHO): Int {
        val offset = position

        if (n == objectsList.size) {
            val newSize = n * 2
            //expansions++

            objectsList = objectsList.copyOf(newSize)
            addressList = addressList.copyOf(newSize)
            //LOGGER.info("LAB had to be expanded from $n to ${objectsList.size} (${expansions}th expansion)")
        }

        objectsList[n] = obj
        addressList[n] = offset
        n++

        position += obj.size

        errorOnLabOverflow(offset + addr, obj)

        return offset
    }

    override fun resetCapacity() {
        assert(capacity == UNKNOWN_CAPACITY) { "Lab Capacity must not be set if capacity should be reset" }
        capacity = position
    }

    @Throws(TraceException::class)
    private fun errorOnLabOverflow(assignedAddr: Long, obj: AddressHO) {
        if (capacity != Lab.UNKNOWN_CAPACITY && position > capacity) {
            throw TraceException(String.format("LAB CAPACITY EXCEEDED\n" + "Object added to lab of kind %s of thread %s (Range = %,d - %,d, Size = %,d) which " +
                                                       "exceeded the " +
                                                       "LAB's size:\n" + "obj addr = %,d (Range = %,d - %,d, Size = %,d, Object = %s)\n" + "capacity = %,d, " +
                                                       "position now = %,d",
                                               kind,
                                               thread,
                                               bottom(),
                                               end(),
                                               capacity(),
                                               assignedAddr,
                                               assignedAddr,
                                               assignedAddr + obj.size,
                                               obj.size,
                                               obj,
                                               capacity,
                                               position))
        }
    }

    @Throws(TraceException::class)
    private fun errorOnWrongAddr(thread: String, objAddr: Long, obj: AddressHO) {
        if (objAddr != top()) {
            val expectedAddr = top()
            val offset = objAddr - expectedAddr
            throw TraceException(String.format("#CalculateAddrByTLAB, Lab of kind %s of thread %s (Range = %,d - %,d, Size = %,d) is not consecutive:\n" + "obj " +
                                                       "actual addr =" +
                                                       " %,d (Range = %,d - %,d, Size = %,d, Object = %s),\n" + "expected addr = %,d + %,d = %,d\n" + "offset = %,d",
                                               kind,
                                               thread,
                                               bottom(),
                                               end(),
                                               capacity(),
                                               objAddr,
                                               objAddr,
                                               objAddr + obj.size,
                                               obj.size,
                                               obj,
                                               addr,
                                               position,
                                               expectedAddr,
                                               offset))
        }
    }

    override fun resetForwardingAddresses() {
        objectsList.forEach { ho -> ho?.tag = Lab.UNSET_FORWARDING_ADDR }
    }

    override fun clone(): Lab {
        return MultiObjectLab(thread,
                              kind,
                              addr,
                              capacity,
                              objectsList.clone(),
                              addressList.clone()).also {
            it.position = this.position
            it.n = this.n
        }
    }

    override fun reduceSize() {
        objectsList = objectsList.copyOf(n);
        addressList = addressList.copyOf(n);
    }

    @Throws(TraceException::class)
    override fun getObject(objAddr: Long): AddressHO? {
        val index = getAddressIndex(objAddr)
        return objectsList[index]
    }

    override fun getObjectAtIndex(index: Int): AddressHO {
        return objectsList[index]!!
    }

    @Throws(TraceException::class)
    override fun getAddressIndex(objAddr: Long): Int {
        if (n == 0) {
            throw TraceException("No object in LAB!")
        }

        var l = 0;
        var r = n - 1
        val search = (objAddr - addr).toInt()

        while (l <= r) {
            val m = l + (r - l) / 2;

            // Check if x is present at mid
            if (addressList[m] == search) return m;

            // If x greater, ignore left half
            if (addressList[m] < search) l = m + 1;
            // If x is smaller, ignore right half
            else r = m - 1;
        }
        throw TraceException("No object found at address " + objAddr)
    }

    override fun variableCapacity() {
        capacity = Lab.UNKNOWN_CAPACITY
    }

    override fun iterateUpdatePointer(iterator: PtrUpdateVisitor, space: Space) {
        var curAddr = this.addr
        for (i in 0 until n) {
            val obj = objectsList[i]
            iterator.visit(space, this, curAddr, obj)
            curAddr += obj!!.size.toLong()
        }
    }

    companion object {
        private val LOGGER = Logger.getLogger(MultiObjectLab::class.java.name)
    }
}
