package at.jku.anttracks.heap.roots

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler
import java.nio.ByteBuffer
import java.util.*

class CodeBlobRoot(addr: Long, val classId: Int, val methodId: Int) : RootPtr(addr) {
    private var klass: AllocatedType? = null
    private var method: AllocatedType.MethodInfo? = null

    override fun toString(): String {
        return "${javaClass.simpleName}\n" +
                "- ${klass!!.getExternalName(true, false) ?: "'unknown class'"} (id $classId)\n" +
                "-- ${method!!.name ?: "'unknown method'"} (id $methodId)"
    }

    override fun toShortString(): String {
        return javaClass.simpleName + " by " + (if (method != null) method!!.name else "'unknown method'") + " in " + if (klass != null) klass!!.getExternalName(true, false) else "'unknown class'"
    }

    override fun toGraphString(): String {
        return "${javaClass.simpleName}\nby ${method?.name ?: "'unknown method'"}\nin ${klass?.getExternalName(true, false) ?: "'unknown class'"})"
    }

    override fun getMetadata(): ByteArray {
        return ByteBuffer.allocate(1 + java.lang.Long.BYTES + 2 * Integer.BYTES).put(RootType.CODE_BLOB_ROOT.byteVal).putLong(addr).putInt(classId).putInt(methodId).array()
    }

    override fun getRootType(): RootPtr.RootType {
        return RootType.CODE_BLOB_ROOT
    }

    override fun resolve(hprof: HprofToFastHeapHandler) {
        klass = hprof.objectInfos[addr.toInt()].type
        if(klass != null) {
            // We currently do not create CodeBlob roots in HPROF parser
            // TODO: If there should be a reason to create CodeBlob roots in HPROF parser, implement method resolving
            resolved = true
        }
    }

    @Throws(Exception::class)
    override fun resolve(heap: DetailedHeap) {
        klass = heap.symbols.types.getById(classId)
        if (klass != null) {
            method = Arrays.stream<AllocatedType.MethodInfo>(klass!!.methodInfos).filter { methodInfo -> methodInfo.idnum == methodId }.findFirst().orElse(null)
            resolved = true
        }

        //throw new Exception("Could not resolve code blob root: " + toString());
    }

    override fun toClassificationString(includePackages: Boolean): Array<String> {
        return arrayOf("Root: Code blob", "Class: " + if (klass != null) klass!!.getExternalName(false, false) else "???", "Method: " + if (method != null) method!!.name else "???")
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }

        if (!CodeBlobRoot::class.java.isAssignableFrom(obj.javaClass)) {
            return false
        }

        val other = obj as CodeBlobRoot?

        return this.addr == other!!.addr && this.classId == other.classId && this.methodId == other.methodId
    }
}
