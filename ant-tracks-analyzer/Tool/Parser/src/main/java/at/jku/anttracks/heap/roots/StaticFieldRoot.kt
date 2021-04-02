package at.jku.anttracks.heap.roots

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler
import java.nio.ByteBuffer
import java.util.*

class StaticFieldRoot(addr: Long, val classId: Int, val offset: Int) : RootPtr(addr) {

    private var klass: AllocatedType? = null
    private var type: AllocatedType? = null
    private var name: String? = null

    public fun clazz(): String {
        return if (klass != null) klass!!.getExternalName(true, false) else "'unknown class'"
    }

    public fun fieldName(): String {
        return if (name != null) name!! else "'unknown name'"
    }

    public fun fieldTypeWithPackage(): String {
        return if (type != null) type!!.getExternalName(false, false) else "???"
    }

    public fun fieldTypeWithoutPackage(): String {
        return if (type != null) type!!.getExternalName(false, false) else "???"
    }

    override fun toString(): String {
        val b = StringBuilder("\n")
        b.append("- ").append(if (klass != null) klass!!.internalName else "???").append(" (").append(classId).append(")\n")
        b.append("-- ").append(if (type != null) type!!.internalName else "???").append(" ").append(if (name != null) name else "???").append(" (").append(offset).append(")\n")

        return b.toString()
    }

    override fun toShortString(): String {
        return """Static field in ${clazz()} by field ${fieldName()}"""
    }

    override fun toGraphString(): String {
        return "Static field:\n" +
                "Field ${fieldName()}\n" +
                "in class ${clazz()}"
    }

    override fun getMetadata(): ByteArray {
        return ByteBuffer.allocate(1 + java.lang.Long.BYTES + 2 * Integer.BYTES).put(RootType.STATIC_FIELD_ROOT.byteVal).putLong(addr).putInt(classId).putInt(offset).array()
    }

    override fun getRootType(): RootPtr.RootType {
        return RootType.STATIC_FIELD_ROOT
    }

    override fun resolve(hprof: HprofToFastHeapHandler) {
        klass = hprof.objectInfos[addr.toInt()].type
    }

    @Throws(Exception::class)
    override fun resolve(heap: DetailedHeap) {
        klass = heap.symbols.types.getById(classId)
        if (klass != null) {
            val field = Arrays.stream<AllocatedType.FieldInfo>(klass!!.fieldInfos).filter { f -> f.offset == this.offset }.findFirst().orElse(null)
            if (field != null) {
                type = heap.symbols.types.getById(field.getTypeId())
                name = field.name
                resolved = true
                return
            }
        }

        //throw new Exception("Could not resolve static root: " + toString());
    }

    override fun toClassificationString(includePackages: Boolean): Array<String> {
        return arrayOf("Referenced by static field(s)",
                       "In class: " + clazz(),
                       "Field: ${fieldName()} of type ${if (includePackages) fieldTypeWithPackage() else fieldTypeWithoutPackage()}")
    }

    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }

        if (!StaticFieldRoot::class.java.isAssignableFrom(other.javaClass)) {
            return false
        }

        val otherStatic = other as StaticFieldRoot

        return this.addr == otherStatic.addr && this.classId == otherStatic.classId && this.offset == otherStatic.offset
    }

    override fun isInternal(): Boolean {
        if (!resolved) {
            throw IllegalStateException("Must resolve root ptr before calling isInternal()!")
        }

        if (klass == null) {
            throw IllegalStateException("Root ptr has unknown class!")
        }

        val klassName = klass!!.getExternalName(false, false)
        return klassName.contains("java.lang.ClassLoader") || klassName.contains("sun.launcher.LauncherHelper") || klassName.contains("sun.misc.Launcher") || klass!!
                .isSubtypeOf(
                        "java.lang.ClassLoader")
    }
}
