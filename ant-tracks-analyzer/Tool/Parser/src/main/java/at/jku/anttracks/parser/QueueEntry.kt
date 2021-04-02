
package at.jku.anttracks.parser

import java.nio.ByteBuffer

data class QueueEntry(val thread: String, val buffer: ByteBuffer, val position: Long, val isCompressed: Boolean, val sync: SyncLevel) {
    companion object {
        val NULL = QueueEntry("DUMMY", ByteBuffer.allocate(0), 0, false, SyncLevel.NONE)
    }
}
