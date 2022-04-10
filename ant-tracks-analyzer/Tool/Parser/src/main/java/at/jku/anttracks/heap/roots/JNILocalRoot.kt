package at.jku.anttracks.heap.roots

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.parser.heap.ThreadInfo
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler

import java.nio.ByteBuffer

class JNILocalRoot(addr: Long, val threadId: Long) : RootPtr(addr) {
    private var thread: ThreadInfo? = null

    override fun toString(): String {
        return "${javaClass.simpleName}\n" +
                "- ${thread!!.threadName ?: "'unknown thread'"} (id $threadId)"
    }

    override fun toShortString(): String {
        return javaClass.simpleName + " in " + if (thread != null) thread!!.threadName else "unknown thread"
    }

    override fun toGraphString(): String {
        return "${javaClass.simpleName}\nin ${thread?.threadName ?: "unknown thread"}"
    }

    override fun getMetadata(): ByteArray {
        return ByteBuffer.allocate(1 + 2 * java.lang.Long.BYTES).put(RootType.JNI_LOCAL_ROOT.byteVal).putLong(addr).putLong(threadId).array()
    }

    override fun getRootType(): RootPtr.RootType {
        return RootType.JNI_LOCAL_ROOT
    }

    override fun resolve(hprof: HprofToFastHeapHandler) {
        thread = hprof.threadInfos.find { ti -> ti.threadId == threadId }
        resolved = true
    }

    @Throws(Exception::class)
    override fun resolve(heap: DetailedHeap) {
        thread = heap.threadsById[threadId]
        if (thread == null) {
            //            throw new Exception("Could not resolve JNI local root: " + toString());
        } else {
            resolved = true
        }
    }

    override fun toClassificationString(includePackages: Boolean): Array<String> {
        return arrayOf("Referenced by JNI local(s)", "In thread: " + if (thread != null) thread!!.threadName else "???")
    }

    override fun equals(obj: Any?): Boolean {
        if (obj == null) {
            return false
        }

        if (!JNILocalRoot::class.java.isAssignableFrom(obj.javaClass)) {
            return false
        }

        val other = obj as JNILocalRoot?

        return this.addr == other!!.addr && this.threadId == other.threadId
    }
}
