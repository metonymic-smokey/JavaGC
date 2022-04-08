package at.jku.anttracks.heap.roots

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler

import java.nio.ByteBuffer

class JNIGlobalRoot(addr: Long, val weak: Boolean) : RootPtr(addr) {
    override fun toString(): String {
        return javaClass.simpleName
    }

    override fun toShortString(): String {
        return javaClass.simpleName
    }

    override fun toGraphString(): String {
        return javaClass.simpleName
    }

    override fun getMetadata(): ByteArray {
        return ByteBuffer.allocate(1 + java.lang.Long.BYTES + 1).put(RootType.JNI_GLOBAL_ROOT.byteVal).putLong(addr).put(if (weak) 1.toByte() else 0.toByte()).array()
    }

    override fun getRootType(): RootPtr.RootType {
        return RootType.JNI_GLOBAL_ROOT
    }

    override fun resolve(hprof: HprofToFastHeapHandler?) {
        resolved = true
    }

    @Throws(Exception::class)
    override fun resolve(heap: DetailedHeap) {
        resolved = true
    }

    override fun toClassificationString(includePackages: Boolean): Array<String> {
        return arrayOf("Referenced by JNI global(s)")
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }

        if (!JNIGlobalRoot::class.java.isAssignableFrom(obj.javaClass)) {
            return false
        }

        val other = obj as JNIGlobalRoot?

        return this.addr == other!!.addr
    }
}
