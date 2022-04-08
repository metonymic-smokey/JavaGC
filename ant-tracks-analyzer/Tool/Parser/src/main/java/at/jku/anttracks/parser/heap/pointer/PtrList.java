package at.jku.anttracks.parser.heap.pointer;

import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.parser.TraceParser;

import java.security.InvalidParameterException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * This class represent a set of pointer events that belong to the same object.
 * The concatenation of the events' pointer arrays make up the objects pointers.
 */
public class PtrList {
    public final long targetAddress;
    public final List<PtrEvent> events;

    public PtrList(long targetAddress) {
        this.targetAddress = targetAddress;
        this.events = new ArrayList<>();
    }

    public PtrList(PtrEvent initialEvent) {
        this(initialEvent.toAddr);
        this.events.add(initialEvent);
    }

    public PtrList(List<PtrEvent> events) {
        if (events == null && events.size() == 0) {
            throw new InvalidParameterException("The passed events list may not be null or empty");
        }
        this.targetAddress = events.get(0).toAddr;
        assert events.stream().allMatch(x -> x.toAddr == targetAddress) : "All events must handle the same object";
        this.events = events;
    }

    public void addEvent(PtrEvent event) {
        assert targetAddress == event.toAddr : "All events must handle the same object";
        this.events.add(event);
    }

    public long[] getFinalPointerArray() {
        if (TraceParser.CONSISTENCY_CHECK) {
            if (events.stream().filter(x -> x.ptrs == null).count() > 0) {
                System.out.println("bad list, somewhere are null pointers");
                for (PtrEvent event : events) {
                    if (event == null) {
                        System.out.println("Null event!");
                    } else {
                        System.out.println(event);
                    }
                }
            }

            if (events.stream().filter(x -> x.ptrs == null).count() > 0) {
                assert getMovePointerEvent().isPresent() : "NULL pointer may only occur for event lists that contain a move";
                assert events.stream()
                             .filter(x -> x.ptrs == null && x.isDedicatedPtrEvent())
                             .count() == 0 : "If the list contains NULL pointers, they may only exist " + "in the move event and not in the dedicated pointer events";
            }
        }

        int ptrCount = events.stream().filter(x -> x.ptrs != null).mapToInt(x -> x.ptrs.length).sum();
        long[] ptrs = new long[ptrCount];
        int dest = 0;
        for (int i = 0; i < events.size(); i++) {
            PtrEvent e = events.get(i);
            if (e.ptrs != null) {
                System.arraycopy(e.ptrs, 0, ptrs, dest, e.ptrs.length);
                dest += e.ptrs.length;
            }
        }
        assert dest == ptrCount : "Did not combine the whole array";
        return ptrs;
    }

    public Stream<PtrEvent> stream() {
        return events.stream();
    }

    public int size() {
        return events.size();
    }

    public PtrEvent get(int i) {
        return events.get(i);
    }

    public Optional<PtrEvent> getMovePointerEvent() {
        return stream().filter(x -> !x.isDedicatedPtrEvent()).findFirst();
    }

    public EventType getPtrUpdateEventType() {
        if (stream().allMatch(x -> x.eventType == EventType.GC_PTR_UPDATE_PREMOVE)) {
            return EventType.GC_PTR_UPDATE_PREMOVE;
        } else if (stream().allMatch(x -> x.eventType == EventType.GC_PTR_UPDATE_POSTMOVE)) {
            return EventType.GC_PTR_UPDATE_POSTMOVE;
        } else {
            throw new IllegalStateException("Ptr list is in an illegal state");
        }
    }
}
