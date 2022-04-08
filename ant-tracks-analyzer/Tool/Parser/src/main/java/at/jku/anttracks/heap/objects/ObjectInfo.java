
package at.jku.anttracks.heap.objects;

import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.heap.symbols.AllocationSite;
import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.util.Consts;

// Special thing: id, size, isMirror, isArray are excluded from hash and equals
public class ObjectInfo {
    public static final String MIRROR_CLASS = "Ljava/lang/Class;";

    private static ObjectInfo nullInfo = new ObjectInfo();

    public static ObjectInfo NULL() {
        return nullInfo;
    }

    public int id = -1;

    public String thread;
    public EventType eventType;
    public AllocationSite allocationSite;
    public AllocatedType type;
    public int size;

    public boolean isMirror = false;

    public boolean isArray = false;
    public int arrayLength = -1;

    public ObjectInfo() {}

    public static ObjectInfo newInstanceInfo(String thread,
                                             EventType eventType,
                                             AllocationSite allocationSite,
                                             AllocatedType type,
                                             int heapWordSize) {
        ObjectInfo objectInfo = new ObjectInfo();
        objectInfo.thread = thread;
        objectInfo.eventType = eventType;
        objectInfo.allocationSite = allocationSite;
        objectInfo.type = type;
        objectInfo.size = padObjectSize(type.size, heapWordSize);
        assert objectInfo.size > 0 : "Object size must be positive";

        return objectInfo;
    }

    public static ObjectInfo newMirrorInfo(String thread,
                                           EventType eventType,
                                           AllocationSite allocationSite,
                                           AllocatedType type,
                                           int size,
                                           int heapWordSize) {
        ObjectInfo objectInfo = new ObjectInfo();
        objectInfo.thread = thread;
        objectInfo.eventType = eventType;
        objectInfo.allocationSite = allocationSite;
        objectInfo.type = type;
        // Mirrors provide size
        objectInfo.size = padObjectSize(size, heapWordSize);
        assert objectInfo.size > 0 : "Object size must be positive";

        objectInfo.isMirror = true;

        return objectInfo;
    }

    public static ObjectInfo newArrayInfo(String thread,
                                          EventType eventType,
                                          AllocationSite allocationSite,
                                          AllocatedType type,
                                          int arrayLength,
                                          int heapWordSize) {
        ObjectInfo objectInfo = new ObjectInfo();
        objectInfo.thread = thread;
        objectInfo.eventType = eventType;
        objectInfo.allocationSite = allocationSite;
        objectInfo.type = type;
        // Arrays provide their length
        assert arrayLength >= 0 : "Array length must be at least 0";
        if (thread.equals("")) {
            // HPROF
            int headerSize = 8 + heapWordSize + 4;
            int elemSize;
            switch (type.internalName) {
                case "[B":
                    elemSize = 1;
                    break;
                case "[C":
                    elemSize = 2;
                    break;
                case "[D":
                    elemSize = 8;
                    break;
                case "[F":
                    elemSize = 4;
                    break;
                case "[I":
                    elemSize = 4;
                    break;
                case "[J":
                    elemSize = 8;
                    break;
                case "[S":
                    elemSize = 2;
                    break;
                case "[Z":
                    elemSize = 1;
                    break;
                default:
                    elemSize = heapWordSize;
                    break;
            }
            int arraySize = arrayLength * elemSize;
            objectInfo.size = padObjectSize(headerSize + arraySize, heapWordSize);
        } else {
            // AntTracks trace
            objectInfo.size = padObjectSize(getArraySize(type, arrayLength), heapWordSize);
        }
        assert objectInfo.size > 0 : "Object size must be positive";

        objectInfo.isArray = true;
        objectInfo.arrayLength = arrayLength;

        return objectInfo;
    }

    private static int padObjectSize(int unpadded, int heapWordSize) {
        int padding = unpadded % heapWordSize;
        int padded = unpadded + (padding == 0 ? 0 : heapWordSize - padding);
        return padded;
    }

    private static int getArraySize(AllocatedType type, int length) {
        int sizeInfo = type.size;
        int headerSize = sizeInfo >>> 8;
        int elemSize = sizeInfo & Consts.HEX_ELEM_SIZE;
        int size = headerSize + elemSize * length;
        return size;
    }

    public boolean isSmallArray() {
        return arrayLength >= 0 && arrayLength < Consts.ARRAY_SIZE_MAX_SMALL;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }
        ObjectInfo that = (ObjectInfo) o;
        return arrayLength == that.arrayLength &&
                thread.equals(that.thread) &&
                eventType == that.eventType &&
                allocationSite.getId() == that.allocationSite.getId() &&
                type.id == that.type.id;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 31 * hash + thread.hashCode();
        hash = 31 * hash + eventType.hashCode();
        hash = 31 * hash + type.id;
        hash = 31 * hash + allocationSite.getId();
        hash = 31 * hash + arrayLength;
        return hash;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ObjectInfo[");
        sb.append("id: ");
        sb.append(id);
        sb.append(", thread: ");
        sb.append(thread);
        sb.append(", event: ");
        sb.append(eventType);
        sb.append(", site: ");
        sb.append(allocationSite);
        sb.append(", type: ");
        sb.append(type);
        sb.append(", size: ");
        sb.append(size);
        sb.append(", isMirror: ");
        sb.append(isMirror);
        sb.append(", isArray: ");
        sb.append(isArray);
        sb.append(", arrLen: ");
        sb.append(arrayLength);
        sb.append("]");
        return sb.toString();
    }
}