package at.jku.anttracks.heap.roots

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler

import java.nio.ByteBuffer

class OtherRoot(addr: Long, private val rootType: RootPtr.RootType) : RootPtr(addr) {
    override fun toString(): String {
        return rootType.stringRep
    }

    override fun toShortString(): String {
        return rootType.stringRep
    }

    override fun toGraphString(): String {
        return rootType.stringRep
    }

    override fun getMetadata(): ByteArray {
        return ByteBuffer.allocate(1 + java.lang.Long.BYTES).put(rootType.byteVal).putLong(addr).array()
    }

    override fun getRootType(): RootPtr.RootType {
        return rootType
    }

    override fun resolve(hprof: HprofToFastHeapHandler?) {
        resolved = true
    }

    @Throws(Exception::class)
    override fun resolve(heap: DetailedHeap) {
        resolved = true
    }

    override fun toClassificationString(includePackages: Boolean): Array<String> {
        return arrayOf("Root: " + toString())
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }

        if (!OtherRoot::class.java.isAssignableFrom(obj.javaClass)) {
            return false
        }

        val other = obj as OtherRoot?

        return this.addr == other!!.addr
    }

    override fun getTypeString(): String {
        return rootType.stringRep
    }

}
