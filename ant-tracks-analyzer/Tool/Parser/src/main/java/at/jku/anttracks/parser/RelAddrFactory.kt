
package at.jku.anttracks.parser

import at.jku.anttracks.heap.space.SpaceType

class RelAddrFactory(private val alignment: Long) {
    private var base: Long = 0

    init {
        setBase(0)
    }

    fun setBase(base: Long) {
        this.base = base
    }

    fun create(rel_addr: Int): Long {
        val offset = rel_addr.toLong()
        val maskedOffset = offset and ADDR_SIGN_MASK // necessary because of sign extension
        if (maskedOffset != offset) {
            println("Offsets did not match after masking: $offset vs $maskedOffset")
        }

        val addrOffset = maskedOffset * alignment
        return base + addrOffset
    }

    companion object {
        // stuff carried over from discarded RelAddr class
        private val spaces: Array<at.jku.anttracks.heap.space.SpaceType> = at.jku.anttracks.heap.space.SpaceType.values()
        private const val SPACE_MASK: Int = 0x3
        private const val ADDR_MASK: Int = 0xFFFFFFFC.toInt()
        private const val ADDR_SIGN_MASK: Long = (1L shl 32) - 1

        /*
     * public static long getRelAddr(Heap heap, long absAddr) throws TraceException { return absAddr - getSpaceFromAbsolute(heap,
     * absAddr).getAddress(); }
     */

        fun getDefinedSpaceOnly(spaceId: Int): at.jku.anttracks.heap.space.SpaceType {
            val maskedSpaceId = spaceId and SPACE_MASK
            if (spaceId != maskedSpaceId) {
                println("SpaceId did not match after masking: $spaceId vs $maskedSpaceId")
            }
            return spaces[spaceId]
        }

        fun getDefinedAddrOnly(addr: Int): Long {
            val maskedAddr = (addr and ADDR_MASK).toLong()
            if (addr.toLong() != maskedAddr) {
                println("Addresses did not match after masking: $addr vs $maskedAddr")
            }
            return maskedAddr
        }
    }
}
