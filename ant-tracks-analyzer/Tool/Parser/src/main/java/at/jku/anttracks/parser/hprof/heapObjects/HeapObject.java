package at.jku.anttracks.parser.hprof.heapObjects;

public abstract class HeapObject implements Comparable<HeapObject> {
    private long objId;
    private int stackTraceSerialNum;
    private long classObjId;

    public HeapObject(long objId, int stackTraceSerialNum, long classObjId) {
        super();
        this.objId = objId;
        this.stackTraceSerialNum = stackTraceSerialNum;
        this.classObjId = classObjId;
    }

    public long getObjId() {
        return objId;
    }

    public int getStackTraceSerialNum() {
        return stackTraceSerialNum;
    }

    public long getClassObjId() {
        return classObjId;
    }

    @Override
    public int compareTo(HeapObject o) {
        return (int) (objId - o.objId);
    }

}
