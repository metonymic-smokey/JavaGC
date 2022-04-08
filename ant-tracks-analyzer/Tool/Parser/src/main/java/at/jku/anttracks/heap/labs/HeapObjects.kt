package at.jku.anttracks.heap.labs

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.labs.Lab.UNSET_FORWARDING_ADDR
import at.jku.anttracks.heap.objects.ObjectInfo
import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.heap.symbols.AllocationSite
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.parser.EventType
import at.jku.anttracks.parser.TraceSlaveParser
import java.util.*

interface AddressHO {
    val info: ObjectInfo
    val bornAt: Short
    var lastMovedAt: Short
    // This field can be used to attach tags to objects (for example to track forwarding addresses)
    var tag: Long

    val pointerCount: Int
    val type: AllocatedType
        get() = info.type
    val site: AllocationSite
        get() = info.allocationSite
    val eventType: EventType
        get() = info.eventType
    val size: Int
        get() = info.size
    val isArray: Boolean
        get() = info.isArray
    val arrayLength: Int
        get() = info.arrayLength

    fun getPointer(index: Int): Long
    fun fillPointers(ptrs: LongArray) {
        if (ptrs.size > pointerCount) {
            throw IllegalArgumentException(
                    """fillPointers() called with invalid parameter.
                      |Pointer array contains more pointers than the heap object can contain.
                      |Heap object pointer count: $pointerCount, pointer array length: ${ptrs.size}
                      |Object: $this""".trimMargin())
        }
    }

    fun setPointer(ptrNr: Int, ptr: Long) {
        if (ptrNr > pointerCount) {
            throw IndexOutOfBoundsException("Pointer index is out of bounds: $ptrNr of $pointerCount")
        }
    }

    companion object {
        fun pointerCountOf(info: ObjectInfo, symbols: Symbols, isPossibleFiller: Boolean): Int {
            if (!symbols.expectPointers) {
                // If no pointers have been recorded, expect none.
                return 0
            }

            if (isPossibleFiller) {
                // Filler objects do (hopefully) not have pointers, they are int[]
                return 0
            }

            // TODO Check if this is slow, it may execute a lot of String compares
            if (info.type.hasUnknownPointerCount) {
                return -1
            }

            // Arrays
            return if (info.isArray) {
                if (info.type.isPrimitiveArray) 0 else info.arrayLength
            } else {
                info.type.pointersPerObject
            }
        }

        fun createObject(objInfo: ObjectInfo, gcId: Short, symbols: Symbols, mayBeFiller: Boolean): AddressHO {
            val ptrCount = pointerCountOf(objInfo, symbols, mayBeFiller)
            when (ptrCount) {
                -1 -> return AddressHeapObjectUnknown(objInfo, gcId)
                0 -> return AddressHeapObject0(objInfo, gcId)
                1 -> return AddressHeapObject1(objInfo, gcId)
                2 -> return AddressHeapObject2(objInfo, gcId)
                3 -> return AddressHeapObject3(objInfo, gcId)
                4 -> return AddressHeapObject4(objInfo, gcId)
                5 -> return AddressHeapObject5(objInfo, gcId)
                6 -> return AddressHeapObject6(objInfo, gcId)
                7 -> return AddressHeapObject7(objInfo, gcId)
                8 -> return AddressHeapObject8(objInfo, gcId)
                9 -> return AddressHeapObject9(objInfo, gcId)
                10 -> return AddressHeapObject10(objInfo, gcId)
                11 -> return AddressHeapObject11(objInfo, gcId)
                12 -> return AddressHeapObject12(objInfo, gcId)
                else -> return AddressHeapObject12Plus(objInfo, gcId, -1, UNSET_FORWARDING_ADDR, LongArray(ptrCount))
            }
        }

        fun createObject(objInfo: ObjectInfo, heap: DetailedHeap, mayBeFiller: Boolean): AddressHO =
                createObject(objInfo, heap.gc.id, heap.symbols, mayBeFiller)
    }
}

data class AddressHeapObject0(override val info: ObjectInfo, override val bornAt: Short,
                              override var lastMovedAt: Short = -1,
                              override var tag: Long = UNSET_FORWARDING_ADDR) : AddressHO {

    override val pointerCount: Int
        get() = 0

    override fun getPointer(index: Int): Long = throw IndexOutOfBoundsException()
}

data class AddressHeapObject1(override val info: ObjectInfo, override val bornAt: Short,
                              override var lastMovedAt: Short = -1,
                              override var tag: Long = UNSET_FORWARDING_ADDR,
                              var p0: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 1

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
        }
    }
}

data class AddressHeapObject2(override val info: ObjectInfo,
                              override val bornAt: Short,
                              override var lastMovedAt: Short = -1,
                              override var tag: Long = UNSET_FORWARDING_ADDR,
                              var p0: Long = TraceSlaveParser.NULL_PTR,
                              var p1: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 2

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        1 -> p1
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
                1 -> p1 = ptrs[1]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
            1 -> p1 = ptr
        }
    }
}

data class AddressHeapObject3(override val info: ObjectInfo,
                              override val bornAt: Short,
                              override var lastMovedAt: Short = -1,
                              override var tag: Long = UNSET_FORWARDING_ADDR,
                              var p0: Long = TraceSlaveParser.NULL_PTR,
                              var p1: Long = TraceSlaveParser.NULL_PTR,
                              var p2: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 3

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        1 -> p1
        2 -> p2
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
                1 -> p1 = ptrs[1]
                2 -> p2 = ptrs[2]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
            1 -> p1 = ptr
            2 -> p2 = ptr
        }
    }
}

data class AddressHeapObject4(override val info: ObjectInfo,
                              override val bornAt: Short,
                              override var lastMovedAt: Short = -1,
                              override var tag: Long = UNSET_FORWARDING_ADDR,
                              var p0: Long = TraceSlaveParser.NULL_PTR,
                              var p1: Long = TraceSlaveParser.NULL_PTR,
                              var p2: Long = TraceSlaveParser.NULL_PTR,
                              var p3: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 4

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        1 -> p1
        2 -> p2
        3 -> p3
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
                1 -> p1 = ptrs[1]
                2 -> p2 = ptrs[2]
                3 -> p3 = ptrs[3]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
            1 -> p1 = ptr
            2 -> p2 = ptr
            3 -> p3 = ptr
        }
    }
}

data class AddressHeapObject5(override val info: ObjectInfo,
                              override val bornAt: Short,
                              override var lastMovedAt: Short = -1,
                              override var tag: Long = UNSET_FORWARDING_ADDR,
                              var p0: Long = TraceSlaveParser.NULL_PTR,
                              var p1: Long = TraceSlaveParser.NULL_PTR,
                              var p2: Long = TraceSlaveParser.NULL_PTR,
                              var p3: Long = TraceSlaveParser.NULL_PTR,
                              var p4: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 5

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        1 -> p1
        2 -> p2
        3 -> p3
        4 -> p4
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
                1 -> p1 = ptrs[1]
                2 -> p2 = ptrs[2]
                3 -> p3 = ptrs[3]
                4 -> p4 = ptrs[4]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
            1 -> p1 = ptr
            2 -> p2 = ptr
            3 -> p3 = ptr
            4 -> p4 = ptr
        }
    }
}

data class AddressHeapObject6(override val info: ObjectInfo,
                              override val bornAt: Short,
                              override var lastMovedAt: Short = -1,
                              override var tag: Long = UNSET_FORWARDING_ADDR,
                              var p0: Long = TraceSlaveParser.NULL_PTR,
                              var p1: Long = TraceSlaveParser.NULL_PTR,
                              var p2: Long = TraceSlaveParser.NULL_PTR,
                              var p3: Long = TraceSlaveParser.NULL_PTR,
                              var p4: Long = TraceSlaveParser.NULL_PTR,
                              var p5: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 6

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        1 -> p1
        2 -> p2
        3 -> p3
        4 -> p4
        5 -> p5
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
                1 -> p1 = ptrs[1]
                2 -> p2 = ptrs[2]
                3 -> p3 = ptrs[3]
                4 -> p4 = ptrs[4]
                5 -> p5 = ptrs[5]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
            1 -> p1 = ptr
            2 -> p2 = ptr
            3 -> p3 = ptr
            4 -> p4 = ptr
            5 -> p5 = ptr
        }
    }
}

data class AddressHeapObject7(override val info: ObjectInfo,
                              override val bornAt: Short,
                              override var lastMovedAt: Short = -1,
                              override var tag: Long = UNSET_FORWARDING_ADDR,
                              var p0: Long = TraceSlaveParser.NULL_PTR,
                              var p1: Long = TraceSlaveParser.NULL_PTR,
                              var p2: Long = TraceSlaveParser.NULL_PTR,
                              var p3: Long = TraceSlaveParser.NULL_PTR,
                              var p4: Long = TraceSlaveParser.NULL_PTR,
                              var p5: Long = TraceSlaveParser.NULL_PTR,
                              var p6: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 7

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        1 -> p1
        2 -> p2
        3 -> p3
        4 -> p4
        5 -> p5
        6 -> p6
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
                1 -> p1 = ptrs[1]
                2 -> p2 = ptrs[2]
                3 -> p3 = ptrs[3]
                4 -> p4 = ptrs[4]
                5 -> p5 = ptrs[5]
                6 -> p6 = ptrs[6]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
            1 -> p1 = ptr
            2 -> p2 = ptr
            3 -> p3 = ptr
            4 -> p4 = ptr
            5 -> p5 = ptr
            6 -> p6 = ptr
        }
    }
}

data class AddressHeapObject8(override val info: ObjectInfo,
                              override val bornAt: Short,
                              override var lastMovedAt: Short = -1,
                              override var tag: Long = UNSET_FORWARDING_ADDR,
                              var p0: Long = TraceSlaveParser.NULL_PTR,
                              var p1: Long = TraceSlaveParser.NULL_PTR,
                              var p2: Long = TraceSlaveParser.NULL_PTR,
                              var p3: Long = TraceSlaveParser.NULL_PTR,
                              var p4: Long = TraceSlaveParser.NULL_PTR,
                              var p5: Long = TraceSlaveParser.NULL_PTR,
                              var p6: Long = TraceSlaveParser.NULL_PTR,
                              var p7: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 8

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        1 -> p1
        2 -> p2
        3 -> p3
        4 -> p4
        5 -> p5
        6 -> p6
        7 -> p7
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
                1 -> p1 = ptrs[1]
                2 -> p2 = ptrs[2]
                3 -> p3 = ptrs[3]
                4 -> p4 = ptrs[4]
                5 -> p5 = ptrs[5]
                6 -> p6 = ptrs[6]
                7 -> p7 = ptrs[7]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
            1 -> p1 = ptr
            2 -> p2 = ptr
            3 -> p3 = ptr
            4 -> p4 = ptr
            5 -> p5 = ptr
            6 -> p6 = ptr
            7 -> p7 = ptr
        }
    }
}

data class AddressHeapObject9(override val info: ObjectInfo,
                              override val bornAt: Short,
                              override var lastMovedAt: Short = -1,
                              override var tag: Long = UNSET_FORWARDING_ADDR,
                              var p0: Long = TraceSlaveParser.NULL_PTR,
                              var p1: Long = TraceSlaveParser.NULL_PTR,
                              var p2: Long = TraceSlaveParser.NULL_PTR,
                              var p3: Long = TraceSlaveParser.NULL_PTR,
                              var p4: Long = TraceSlaveParser.NULL_PTR,
                              var p5: Long = TraceSlaveParser.NULL_PTR,
                              var p6: Long = TraceSlaveParser.NULL_PTR,
                              var p7: Long = TraceSlaveParser.NULL_PTR,
                              var p8: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 9

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        1 -> p1
        2 -> p2
        3 -> p3
        4 -> p4
        5 -> p5
        6 -> p6
        7 -> p7
        8 -> p8
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
                1 -> p1 = ptrs[1]
                2 -> p2 = ptrs[2]
                3 -> p3 = ptrs[3]
                4 -> p4 = ptrs[4]
                5 -> p5 = ptrs[5]
                6 -> p6 = ptrs[6]
                7 -> p7 = ptrs[7]
                8 -> p8 = ptrs[8]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
            1 -> p1 = ptr
            2 -> p2 = ptr
            3 -> p3 = ptr
            4 -> p4 = ptr
            5 -> p5 = ptr
            6 -> p6 = ptr
            7 -> p7 = ptr
            8 -> p8 = ptr
        }
    }
}

data class AddressHeapObject10(override val info: ObjectInfo,
                               override val bornAt: Short,
                               override var lastMovedAt: Short = -1,
                               override var tag: Long = UNSET_FORWARDING_ADDR,
                               var p0: Long = TraceSlaveParser.NULL_PTR,
                               var p1: Long = TraceSlaveParser.NULL_PTR,
                               var p2: Long = TraceSlaveParser.NULL_PTR,
                               var p3: Long = TraceSlaveParser.NULL_PTR,
                               var p4: Long = TraceSlaveParser.NULL_PTR,
                               var p5: Long = TraceSlaveParser.NULL_PTR,
                               var p6: Long = TraceSlaveParser.NULL_PTR,
                               var p7: Long = TraceSlaveParser.NULL_PTR,
                               var p8: Long = TraceSlaveParser.NULL_PTR,
                               var p9: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 10

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        1 -> p1
        2 -> p2
        3 -> p3
        4 -> p4
        5 -> p5
        6 -> p6
        7 -> p7
        8 -> p8
        9 -> p9
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
                1 -> p1 = ptrs[1]
                2 -> p2 = ptrs[2]
                3 -> p3 = ptrs[3]
                4 -> p4 = ptrs[4]
                5 -> p5 = ptrs[5]
                6 -> p6 = ptrs[6]
                7 -> p7 = ptrs[7]
                8 -> p8 = ptrs[8]
                9 -> p9 = ptrs[9]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
            1 -> p1 = ptr
            2 -> p2 = ptr
            3 -> p3 = ptr
            4 -> p4 = ptr
            5 -> p5 = ptr
            6 -> p6 = ptr
            7 -> p7 = ptr
            8 -> p8 = ptr
            9 -> p9 = ptr
        }
    }
}

data class AddressHeapObject11(override val info: ObjectInfo,
                               override val bornAt: Short,
                               override var lastMovedAt: Short = -1,
                               override var tag: Long = UNSET_FORWARDING_ADDR,
                               var p0: Long = TraceSlaveParser.NULL_PTR,
                               var p1: Long = TraceSlaveParser.NULL_PTR,
                               var p2: Long = TraceSlaveParser.NULL_PTR,
                               var p3: Long = TraceSlaveParser.NULL_PTR,
                               var p4: Long = TraceSlaveParser.NULL_PTR,
                               var p5: Long = TraceSlaveParser.NULL_PTR,
                               var p6: Long = TraceSlaveParser.NULL_PTR,
                               var p7: Long = TraceSlaveParser.NULL_PTR,
                               var p8: Long = TraceSlaveParser.NULL_PTR,
                               var p9: Long = TraceSlaveParser.NULL_PTR,
                               var p10: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 11

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        1 -> p1
        2 -> p2
        3 -> p3
        4 -> p4
        5 -> p5
        6 -> p6
        7 -> p7
        8 -> p8
        9 -> p9
        10 -> p10
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
                1 -> p1 = ptrs[1]
                2 -> p2 = ptrs[2]
                3 -> p3 = ptrs[3]
                4 -> p4 = ptrs[4]
                5 -> p5 = ptrs[5]
                6 -> p6 = ptrs[6]
                7 -> p7 = ptrs[7]
                8 -> p8 = ptrs[8]
                9 -> p9 = ptrs[9]
                10 -> p10 = ptrs[10]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
            1 -> p1 = ptr
            2 -> p2 = ptr
            3 -> p3 = ptr
            4 -> p4 = ptr
            5 -> p5 = ptr
            6 -> p6 = ptr
            7 -> p7 = ptr
            8 -> p8 = ptr
            9 -> p9 = ptr
            10 -> p10 = ptr
        }
    }
}

data class AddressHeapObject12(override val info: ObjectInfo,
                               override val bornAt: Short,
                               override var lastMovedAt: Short = -1,
                               override var tag: Long = UNSET_FORWARDING_ADDR,
                               var p0: Long = TraceSlaveParser.NULL_PTR,
                               var p1: Long = TraceSlaveParser.NULL_PTR,
                               var p2: Long = TraceSlaveParser.NULL_PTR,
                               var p3: Long = TraceSlaveParser.NULL_PTR,
                               var p4: Long = TraceSlaveParser.NULL_PTR,
                               var p5: Long = TraceSlaveParser.NULL_PTR,
                               var p6: Long = TraceSlaveParser.NULL_PTR,
                               var p7: Long = TraceSlaveParser.NULL_PTR,
                               var p8: Long = TraceSlaveParser.NULL_PTR,
                               var p9: Long = TraceSlaveParser.NULL_PTR,
                               var p10: Long = TraceSlaveParser.NULL_PTR,
                               var p11: Long = TraceSlaveParser.NULL_PTR) : AddressHO {
    override val pointerCount: Int
        get() = 12

    override fun getPointer(index: Int): Long = when (index) {
        0 -> p0
        1 -> p1
        2 -> p2
        3 -> p3
        4 -> p4
        5 -> p5
        6 -> p6
        7 -> p7
        8 -> p8
        9 -> p9
        10 -> p10
        11 -> p11
        else -> throw IndexOutOfBoundsException()
    }

    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        for (i in ptrs.indices) {
            when (i) {
                0 -> p0 = ptrs[0]
                1 -> p1 = ptrs[1]
                2 -> p2 = ptrs[2]
                3 -> p3 = ptrs[3]
                4 -> p4 = ptrs[4]
                5 -> p5 = ptrs[5]
                6 -> p6 = ptrs[6]
                7 -> p7 = ptrs[7]
                8 -> p8 = ptrs[8]
                9 -> p9 = ptrs[9]
                10 -> p10 = ptrs[10]
                11 -> p11 = ptrs[11]
            }
        }
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        when (ptrNr) {
            0 -> p0 = ptr
            1 -> p1 = ptr
            2 -> p2 = ptr
            3 -> p3 = ptr
            4 -> p4 = ptr
            5 -> p5 = ptr
            6 -> p6 = ptr
            7 -> p7 = ptr
            8 -> p8 = ptr
            9 -> p9 = ptr
            10 -> p10 = ptr
            11 -> p11 = ptr
        }
    }
}

data class AddressHeapObject12Plus(override val info: ObjectInfo,
                                   override val bornAt: Short,
                                   override var lastMovedAt: Short = -1,
                                   override var tag: Long = UNSET_FORWARDING_ADDR,
                                   val p: LongArray) : AddressHO {
    override val pointerCount: Int
        get() = p.size

    override fun getPointer(index: Int): Long = if (index < p.size) p[index] else throw IndexOutOfBoundsException()
    override fun fillPointers(ptrs: LongArray) {
        super.fillPointers(ptrs)
        ptrs.copyInto(p, 0, 0, ptrs.size)
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        p[ptrNr] = ptr
    }
}

data class AddressHeapObjectUnknown(override val info: ObjectInfo, override val bornAt: Short, override var lastMovedAt: Short = -1, override var tag: Long = UNSET_FORWARDING_ADDR) : AddressHO {
    var p: LongArray? = null

    override val pointerCount: Int
        get() = p?.size ?: -1

    override fun getPointer(index: Int): Long = p?.get(index) ?: error("Pointer have not been set before, access not possible. $this")
    override fun fillPointers(ptrs: LongArray) {
        p = Arrays.copyOf(ptrs, ptrs.size)
    }

    override fun setPointer(ptrNr: Int, ptr: Long) {
        super.setPointer(ptrNr, ptr)
        p!![ptrNr] = ptr
    }
}

class IndexHeapObject(val address: Long,
                      val index: Int,
                      val info: ObjectInfo) {
    constructor(address: Long, index: Int, addressHO: AddressHO) : this(address, index, addressHO.info) {
        bornAt = addressHO.bornAt
        lastMovedAt = addressHO.lastMovedAt
    }

    var bornAt: Short = -1
    var lastMovedAt: Short = -1

    val type: AllocatedType
        get() = info.type
    val site: AllocationSite
        get() = info.allocationSite
    val eventType: EventType
        get() = info.eventType
    val size: Int
        get() = info.size
    val isArray: Boolean
        get() = info.isArray
    val arrayLength: Int
        get() = info.arrayLength

    // TODO: These properties are temporary and should be removed once all algorithms changed to use IndexHeapObject
    lateinit var pointsToIndices: IntArray
    lateinit var pointedFromIndices: IntArray

    lateinit var pointsTo: Array<IndexHeapObject?>
    lateinit var pointedFrom: Array<IndexHeapObject?>
}