
package at.jku.anttracks.parser.heap.pointer;

import at.jku.anttracks.parser.EventType;

import java.util.Arrays;

public class PtrEvent {
    public final EventType eventType;
    public final long toAddr;
    public final long fromAddr;
    public long[] ptrs;
    public static final int MAX_PTRS_PER_EVENT = 12;
    public static final int ENCODING_END = 0;
    public static final int ENCODING_RELATIVE_PTR = 1;
    public static final int ENCODING_ABSOLUTE_PTR = 2;
    public static final int ENCODING_NULL_PTR = 3;

    public PtrEvent(EventType eventType, long fromAddr, long toAddr, long[] ptrs) {
        this.eventType = eventType;
        this.fromAddr = fromAddr;
        this.toAddr = toAddr;
        this.ptrs = ptrs;
    }

    public boolean isDedicatedPtrEvent() {
        return EventType.Companion.isDedicatedPtrEvent(eventType);
    }

    @Override
    public String toString() {
        return String.format("PtrEvent (%s) from %,d to %,d, containing following pointers: %s",
                             eventType.toString(),
                             fromAddr,
                             toAddr,
                             ptrs == null ? "NULL" : Arrays.toString(ptrs));
    }
}
