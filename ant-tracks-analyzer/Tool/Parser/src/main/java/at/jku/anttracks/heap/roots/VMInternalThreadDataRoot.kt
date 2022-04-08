package at.jku.anttracks.heap.roots

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.parser.heap.ThreadInfo
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler

import java.nio.ByteBuffer

class VMInternalThreadDataRoot(addr: Long, val threadId: Long) : RootPtr(addr) {
    private var thread: ThreadInfo? = null

    override fun toString(): String {
        val b = StringBuilder(javaClass.simpleName).append("\n")
        b.append(" - ").append(if (thread != null) thread!!.threadName else "'unknown thread'")

        return b.toString()
    }

    override fun toShortString(): String {
        return javaClass.simpleName + " in " + if (thread != null) thread!!.threadName else "'unknown thread'"
    }

    override fun toGraphString(): String {
        return "${javaClass.simpleName} in ${thread?.threadName ?: "'unknown thread'"}"
    }

    override fun getMetadata(): ByteArray {
        return ByteBuffer.allocate(1 + java.lang.Long.BYTES + java.lang.Long.BYTES).put(RootPtr.RootType.VM_INTERNAL_THREAD_DATA_ROOT.byteVal).putLong(addr).putLong(threadId).array()
    }

    override fun getRootType(): RootPtr.RootType {
        return RootPtr.RootType.VM_INTERNAL_THREAD_DATA_ROOT
    }

    override fun resolve(hprof: HprofToFastHeapHandler) {
        thread = hprof.threadInfos.find { ti -> ti.threadId == threadId }
        resolved = true
    }

    @Throws(Exception::class)
    override fun resolve(heap: DetailedHeap) {
        thread = heap.threadsById[threadId]
        if (thread == null) {
            //            throw new Exception("Could not resolve Thread root: " + toString());
        } else {
            resolved = true
        }
    }

    override fun toClassificationString(includePackages: Boolean): Array<String> {
        return arrayOf("Root: VM internal thread data", "Thread: " + if (thread != null) thread!!.threadName else "???")
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }

        if (!VMInternalThreadDataRoot::class.java.isAssignableFrom(obj.javaClass)) {
            return false
        }

        val other = obj as VMInternalThreadDataRoot?

        return this.addr == other!!.addr
    }

}
