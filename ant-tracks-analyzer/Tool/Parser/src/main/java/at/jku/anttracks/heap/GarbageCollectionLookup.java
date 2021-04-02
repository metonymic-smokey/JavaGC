package at.jku.anttracks.heap;

import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.parser.EventType;

public class GarbageCollectionLookup {
    public final EventType event;
    public final int nth;
    public final GarbageCollectionCause cause;
    public final GarbageCollectionType type;

    public GarbageCollectionLookup(EventType event, GarbageCollectionType type, GarbageCollectionCause cause, int id) {
        this.event = event;
        this.type = type;
        this.cause = cause;
        this.nth = id;
    }
}