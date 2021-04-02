
package at.jku.anttracks.heap;

import at.jku.anttracks.heap.objects.ArrayInfo;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.space.SpaceInfo;
import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.heap.symbols.AllocationSite;
import at.jku.anttracks.parser.EventType;

public class HeapObject {
    public final long address;
    public final SpaceInfo space;
    public final AllocatedType type;
    public final int size;
    public final boolean isArray;
    public final int arrayLength;
    public final AllocationSite allocationSite;
    public final long[] pointedFrom;
    public final long[] pointsTo;
    public final EventType eventType;

    // TODO remove redundant info
    public final ObjectInfo objectInfo;

    public HeapObject(long address,
                      SpaceInfo space,
                      AllocatedType type,
                      int size,
                      boolean isArray,
                      int arrayLength,
                      AllocationSite allocationSite,
                      long[] pointedFrom,
                      long[] pointsTo,
                      EventType eventType,
                      ObjectInfo objectInfo) {
        this.address = address;
        this.space = space;
        this.type = type;
        this.size = size;
        this.isArray = isArray;
        this.arrayLength = arrayLength;
        this.allocationSite = allocationSite;
        this.pointedFrom = pointedFrom;
        this.pointsTo = pointsTo;
        this.eventType = eventType;

        this.objectInfo = objectInfo;
    }

    public HeapObject(long addr, SpaceInfo space, long[] pointedFrom, long[] pointsTo, ObjectInfo eObj) {
        this(addr,
             space,
             eObj.type,
             eObj.size,
             eObj instanceof ArrayInfo,
             (eObj instanceof ArrayInfo) ? ((ArrayInfo) eObj).getLength() : 0,
             eObj.allocationSite,
             pointedFrom,
             pointsTo,
             eObj.eventType,
             eObj);
    }
}
