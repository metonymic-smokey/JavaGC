
package at.jku.anttracks.heap.labs;

import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectAccess;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.heap.space.SpaceInfo;
import at.jku.anttracks.parser.heap.pointer.PtrUpdateVisitor;
import at.jku.anttracks.util.TraceException;

import java.util.List;

public abstract class Lab implements Comparable<Lab>, ObjectAccess {

    public enum Kind {
        TLAB(0),
        PLAB(1),
        VIRTUAL(2),
        REGION_VIRTUAL(3);

        public final int id;

        Kind(int id) {
            this.id = id;
        }

        public static Kind byId(int id) {
            return values()[id];
        }
    }

    public static final int UNKNOWN_CAPACITY = -1;
    public static final int OBJECT_NOT_ASSIGNED = -1;
    public static final int OBJECT_NOT_FOUND = -1;
    public static final int OBJECT_PTR_DELETED = -2;
    public static final long UNSET_FORWARDING_ADDR = -2;
    public final String thread;
    public final Kind kind;
    public final long addr;

    protected Lab(String thread, Kind kind, long addr) {
        this.thread = thread;
        this.kind = kind;
        this.addr = addr;
    }

    @Override
    public abstract Lab clone();

    public long bottom() {
        return addr;
    }

    public long top() {
        return bottom() + position();
    }

    public long end() {
        return bottom() + capacity();
    }

    public abstract int capacity();

    public abstract int position();

    public boolean contains(long searchAddr) {
        return searchAddr >= addr && searchAddr < end();
    }

    public abstract int getObjectCount();

    public abstract void iterate(DetailedHeap heap,
                                 SpaceInfo space,
                                 List<Filter> filter,
                                 ObjectVisitor visitor,
                                 ObjectVisitor.Settings visitorSettings);

    public abstract long tryAllocate(long objAddr, AddressHO obj) throws TraceException;

    public abstract void resetForwardingAddresses();

    public abstract void reduceSize();

    // Object access
    public abstract AddressHO getObjectAtIndex(int index) throws TraceException;

    public abstract int getAddressIndex(long addr) throws TraceException;

    public void resetCapacity() {}

    public boolean isFull() {
        return position() == capacity();
    }

    public boolean isExtendable() {
        return false;
    }

    public Lab sublab(long bottom, long end) throws TraceException {
        if (bottom == end) {
            return null;
        }
        if (bottom() == bottom && end() == end) {
            return this;
        }
        if (!(bottom() <= bottom && bottom < end())) {
            throw new IllegalArgumentException(String.format("%,d e [%,d, %,d[", bottom, bottom(), end()));
        }
        if (!(bottom() <= end && end <= end())) {
            throw new IllegalArgumentException(String.format("%,d e [%,d, %,d]", end, bottom(), end()));
        }
        if (!(bottom < end)) {
            throw new IllegalArgumentException(String.format("%,d < %,d", bottom, end));
        }
        end = Math.min(end, top()); // necessary for non-full labs
        Lab sublab = new MultiObjectLab(thread, kind, bottom, (int) (end - bottom));
        long top = bottom;
        while (top < end) {
            AddressHO obj = getObject(top);
            long assigned = sublab.tryAllocate(top, obj);
            assert assigned != Lab.OBJECT_NOT_ASSIGNED;
            top += obj.getSize();
        }
        assert sublab.isFull();
        assert top == end;
        return sublab;
    }

    @Override
    public int compareTo(Lab lab) {
        long result = (addr - lab.addr);
        if (result < 0) {
            return -1;
        } else if (result > 0) {
            return 1;
        } else {
            return 0;
        }
    }

    @Override
    public String toString() {
        final StringBuilder string = new StringBuilder();
        string.append(String.format("LAB (kind %s) @ %,d - %,d (%,d / %,d)\n", kind, addr, addr + capacity(), position(), capacity()));

        /*
        iterate(null,
                null,
                null,
                (address, object, space, rootPtrs) -> string.append(String.format(
                        " OBJ @ %,d - %,d (%,d)\n",
                        address,
                        address + object.getSize(),
                        object.getSize())), ObjectVisitor.Settings.Companion.getNO_INFOS());*/
        return string.toString();
    }

    public void variableCapacity() {}

    public abstract void iterateUpdatePointer(PtrUpdateVisitor iterator, Space space);
}