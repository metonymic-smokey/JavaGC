
package at.jku.anttracks.heap.io;

import at.jku.anttracks.heap.GarbageCollectionCause;
import at.jku.anttracks.heap.GarbageCollectionLookup;
import at.jku.anttracks.heap.GarbageCollectionType;
import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.heap.GarbageCollectionCause;
import at.jku.anttracks.heap.GarbageCollectionLookup;
import at.jku.anttracks.heap.GarbageCollectionType;
import at.jku.anttracks.parser.EventType;

public class HeapIndexEntry {
    public final long position;
    public final GarbageCollectionType gcType;
    public final EventType gcMeta;
    public final long time;
    public final int fileName;
    public final boolean heapDumpFileExists;
    public final GarbageCollectionCause gcCause;

    public HeapIndexEntry(long position, GarbageCollectionType gcType, EventType gcMeta, GarbageCollectionCause gcCause, long time, int gcCount, boolean fileExists) {
        super();
        this.position = position;
        this.gcType = gcType;
        this.gcMeta = gcMeta;
        this.gcCause = gcCause;
        this.time = time;
        fileName = gcCount;
        heapDumpFileExists = fileExists;
    }

    public boolean matches(GarbageCollectionLookup gcLookup) {
        if (gcLookup.event != null && !gcMeta.equals(gcLookup.event)) {
            return false;
        }
        if (gcLookup.cause != null && !gcCause.equals(gcLookup.cause)) {
            return false;
        }
        if (gcLookup.type != null && !gcType.equals(gcLookup.type)) {
            return false;
        }
        return true;
    }
}
