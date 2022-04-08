package at.jku.anttracks.heap.roots

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler

import java.nio.ByteBuffer

class ClassRoot(addr: Long, val classId: Int) : RootPtr(addr) {
    private var klass: AllocatedType? = null

    override fun toString(): String {
        return "${javaClass.simpleName}\n" +
                "- ${klass?.getExternalName(true, false) ?: "'unknown class'"} (id $classId)"
    }

    override fun toShortString(): String {
        return javaClass.simpleName + " by " + if (klass != null) klass!!.getExternalName(true, false) else "'unknown class'"
    }

    override fun toGraphString(): String {
        return "${javaClass.simpleName}\nby (${klass?.getExternalName(true, false) ?: "'unknown class'"})"
    }

    override fun getMetadata(): ByteArray {
        return ByteBuffer.allocate(1 + java.lang.Long.BYTES + Integer.BYTES).put(RootType.CLASS_ROOT.byteVal).putLong(addr).putInt(classId).array()
    }

    override fun getRootType(): RootPtr.RootType {
        return RootType.CLASS_ROOT
    }

    override fun resolve(hprof: HprofToFastHeapHandler) {
        klass = hprof.objectInfos[addr.toInt()].type
        if (klass != null) {
            resolved = true
        }
    }

    @Throws(Exception::class)
    override fun resolve(heap: DetailedHeap) {
        klass = heap.symbols.types.getById(classId)
        if (klass == null) {
            //            throw new Exception("Could not resolve Class root: " + toString());
        } else {
            resolved = true
        }
    }

    override fun toClassificationString(includePackages: Boolean): Array<String> {
        return arrayOf("Root: Class", "Class: " + if (klass != null) klass!!.getExternalName(true, false) else "???")
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }

        if (!ClassRoot::class.java.isAssignableFrom(obj.javaClass)) {
            return false
        }

        val other = obj as ClassRoot?

        return this.addr == other!!.addr
    }
}
