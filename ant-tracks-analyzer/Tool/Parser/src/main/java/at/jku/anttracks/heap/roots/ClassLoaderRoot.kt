package at.jku.anttracks.heap.roots

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler

import java.nio.ByteBuffer

class ClassLoaderRoot(addr: Long, val loaderName: String?) : RootPtr(addr) {
    override fun toString(): String {
        return "${javaClass.simpleName}\n" +
                "- ${loaderName ?: "'unknown loader name'"}"
    }

    override fun toShortString(): String {
        return javaClass.simpleName + " by " + (loaderName ?: "'unknown loader name'")
    }

    override fun toGraphString(): String {
        return "${javaClass.simpleName}\nby (${loaderName ?: "'unknown loader name'"})"
    }

    override fun getMetadata(): ByteArray {
        return ByteBuffer.allocate(1 + 1 * java.lang.Long.BYTES + 1 * Integer.BYTES + loaderName!!.toByteArray().size * java.lang.Byte.BYTES)
                .put(RootType.CLASS_LOADER_ROOT.byteVal)
                .putLong(addr)
                .putInt(loaderName.length)
                .put(loaderName.toByteArray())
                .array()
    }

    override fun getRootType(): RootPtr.RootType {
        return RootType.CLASS_LOADER_ROOT
    }

    override fun getTypeString(): String {
        return "Class loader"
    }

    override fun resolve(hprof: HprofToFastHeapHandler?) {
        resolved = true
    }

    @Throws(Exception::class)
    override fun resolve(heap: DetailedHeap) {
        resolved = true
    }

    override fun toClassificationString(includePackages: Boolean): Array<String> {
        return arrayOf("Referenced by class loader(s)", "With name: " + (loaderName ?: "???"))
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }

        if (!ClassLoaderRoot::class.java.isAssignableFrom(obj.javaClass)) {
            return false
        }

        val other = obj as ClassLoaderRoot?

        return this.addr == other!!.addr && this.loaderName == other.loaderName
    }

}
