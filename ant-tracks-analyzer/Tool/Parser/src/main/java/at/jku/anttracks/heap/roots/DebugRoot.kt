package at.jku.anttracks.heap.roots

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler

import java.nio.ByteBuffer

class DebugRoot(addr: Long, val vmCall: String?) : RootPtr(addr) {
    override fun toString(): String {
        return "${javaClass.simpleName}\n" +
                "- ${vmCall ?: "'unknown cause'"}"
    }

    override fun toShortString(): String {
        return javaClass.simpleName + " due to " + (vmCall ?: "'unknown cause'")
    }

    override fun toGraphString(): String {
        return "${javaClass.simpleName}\ndue to ${vmCall ?: "'unknown cause'"}"
    }

    override fun getMetadata(): ByteArray {
        return ByteBuffer.allocate(1 + 1 * java.lang.Long.BYTES + 1 * Integer.BYTES + vmCall!!.toByteArray().size * java.lang.Byte.BYTES)
                .put(RootType.DEBUG_ROOT.byteVal)
                .putLong(addr)
                .putInt(vmCall.length)
                .put(vmCall.toByteArray())
                .array()
    }

    override fun getRootType(): RootPtr.RootType {
        return RootType.DEBUG_ROOT
    }

    override fun getTypeString(): String? {
        return vmCall
    }

    override fun resolve(hprof: HprofToFastHeapHandler) {
        resolved = true
    }

    @Throws(Exception::class)
    override fun resolve(heap: DetailedHeap) {
        resolved = true
    }

    override fun toClassificationString(includePackages: Boolean): Array<String> {
        return arrayOf("Referenced by debug root(s)", "With VM call: " + (vmCall ?: "null"))
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }

        if (!DebugRoot::class.java.isAssignableFrom(obj.javaClass)) {
            return false
        }

        val other = obj as DebugRoot?

        return this.addr == other!!.addr && this.vmCall == other.vmCall
    }

}
