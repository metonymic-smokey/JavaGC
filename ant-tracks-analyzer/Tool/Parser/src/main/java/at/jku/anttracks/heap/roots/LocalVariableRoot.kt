package at.jku.anttracks.heap.roots

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.parser.heap.ThreadInfo
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler
import java.nio.ByteBuffer
import java.util.*

class LocalVariableRoot(addr: Long, val threadId: Long, val classId: Int, val methodId: Int, val slot: Int) : RootPtr(addr) {
    private var thread: ThreadInfo? = null
    private var klass: AllocatedType? = null
    private var method: AllocatedType.MethodInfo? = null
    private var localType: AllocatedType? = null
    private var localName: String? = null

    private var callStackIndex: Int = 0

    constructor(addr: Long, threadId: Long, classId: Int, methodId: Int, slot: Int, callStackIndex: Int) : this(addr, threadId, classId, methodId, slot) {

        this.callStackIndex = callStackIndex
    }

    override fun toString(): String {
        val b = StringBuilder("Local variable:\n")
        b.append("- ").append(if (thread != null) thread!!.threadName else "'unknown thread'").append(" (").append(threadId).append(")\n")
        b.append("-- ").append(if (klass != null) klass!!.internalName else "'unknown class'").append(" (").append(classId).append(")\n")
        b.append("--- ").append(if (method != null) method!!.name else "'unknown method'").append(" (").append(methodId).append(")\n")
        b.append("---- ")
                .append(if (localType != null) localType!!.getExternalName(true, false) else "'unknown type'")
                .append(" ")
                .append(if (localName != null) localName else "'unknown variable name'")
                .append(" (#")
                .append(slot)
                .append(")")

        return b.toString()
    }

    override fun toShortString(): String {
        return "Local variable " +
                (if (localName != null) localName else "??? (#$slot)") +
                " in " + (if (klass != null) klass!!.getExternalName(true, false) else "???") +
                "::" + (if (method != null) method!!.name else "???") +
                " running in " + if (thread != null) thread!!.threadName else "???"
    }

    override fun toGraphString(): String {
        return "Local Variable:\n" +
                "In class ${klass?.getExternalName(true, false) ?: "'unknown class'"}\n" +
                "in method ${method?.name ?: "'unknown method'"}\n" +
                "in variable ${localName ?: "'unknown variable name (slot #$slot)'"}" +
                "(by thread ${thread?.threadName ?: "'unknown thread'"})\n"

    }

    override fun getMetadata(): ByteArray {
        return ByteBuffer.allocate(1 + 2 * java.lang.Long.BYTES + 3 * Integer.BYTES + 1 * Integer.BYTES)
                .put(RootType.LOCAL_VARIABLE_ROOT.byteVal)
                .putLong(addr)
                .putLong(threadId)
                .putInt(classId)
                .putInt(methodId)
                .putInt(slot)

                .putInt(callStackIndex)
                .array()
    }

    override fun getRootType(): RootPtr.RootType {
        return RootType.LOCAL_VARIABLE_ROOT
    }

    fun setCallStackIndex(callStackIndex: Int) {
        this.callStackIndex = callStackIndex
    }

    override fun resolve(hprof: HprofToFastHeapHandler) {
        localType = hprof.objectInfos[addr.toInt()].type
        thread = hprof.threadInfos.find { it.threadId == threadId }
        // TODO: Use StackTrace and StackFrame to reconstruct thread-local variables
        method = null
        localName = null
        resolved = true
    }

    @Throws(Exception::class)
    override fun resolve(heap: DetailedHeap) {
        if (addr != -1L) {
            val obj = heap.getObject(addr)
            if (obj != null) {
                localType = obj.type
            }
        }
        thread = heap.threadsById[threadId]
        if (thread != null) {
            klass = heap.symbols.types.getById(classId)
            if (klass != null && klass!!.methodInfos != null) {
                method = Arrays.stream<AllocatedType.MethodInfo>(klass!!.methodInfos).filter { mi -> mi.idnum == this.methodId }.findFirst().orElse(null)
                if (method != null) {
                    localName = method!!.locals[slot]
                    // it's fine if slot can't be resolved (data only available for user defined methods)
                    resolved = true
                    return
                }
            }
        }

        //throw new Exception("Could not resolve method local root: " + toString());
    }

    override fun toClassificationString(includePackages: Boolean): Array<String?> {
        return toClassificationString(includePackages, false)
    }

    fun toClassificationString(includePackages: Boolean, includeCallStack: Boolean): Array<String?> {
        val ret: Array<String?>
        val callStack: Array<String>

        if (includeCallStack) {
            callStack = callStack(includePackages)

            ret = arrayOfNulls(3 + callStack.size)

            for (i in callStack.indices) {
                ret[i + 2] = callStack[i]
            }
        } else {
            ret = arrayOfNulls(4)
            ret[2] = "In method: " + (if (klass != null) klass!!.getExternalName(!includePackages, false) else "???") + " :: " + if (method != null) method!!.name else "???"
        }

        ret[0] = "Referenced by local variable(s)"
        ret[1] = "In thread: " + if (thread != null) thread!!.threadName else "???"
        ret[ret.size - 1] = "Local variable: " + (if (localType != null) localType!!.getExternalName(!includePackages, false) else "???") + " " + (if (localName != null)
            localName
        else
            "???") + " (Slot #" + slot + ")"

        return ret
    }

    private fun callStack(includePackages: Boolean): Array<String> {
        // returns an array of method names representing the stack frame starting at the given root
        val frames = ArrayList<String>()
        var i = callStackIndex
        while (i >= 0 && i < thread!!.callstack.size) {
            frames.add(0, "In method: " + thread!!.callstack[i].type.getExternalName(!includePackages, false) + " :: " + thread!!.callstack[i].name)
            i = i + 1
        }
        return frames.toTypedArray()
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }

        if (!LocalVariableRoot::class.java.isAssignableFrom(obj.javaClass)) {
            return false
        }

        val other = obj as LocalVariableRoot?

        return this.addr == other!!.addr && this.threadId == other.threadId && this.classId == other.classId && this.methodId == other.methodId && this.slot == other.slot
    }

    override fun isInternal(): Boolean {
        if (!resolved) {
            throw IllegalStateException("Must resolve root ptr before calling isInternal()!")
        }

        if (thread == null) {
            throw IllegalStateException("Root ptr has unknown thread!")
        }

        return thread!!.threadName == "Reference Handler" || thread!!.threadName == "Finalizer"
    }
}
