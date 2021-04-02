package at.jku.anttracks.parser.hprof.heapObjects;

public class ObjectArray extends HeapObject {
    private long[] arrayPointers; // Only set for reference arrays, otherwise null

    public ObjectArray(long objId, int stackTraceSerialNum, long elemClassObjId, long[] elems) {
        super(objId, stackTraceSerialNum, elemClassObjId);
        this.arrayPointers = elems;
    }

    public long[] getArrayPointers() {
        return arrayPointers;
    }

}