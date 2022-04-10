package at.jku.anttracks.parser.hprof.heapObjects;

import at.jku.anttracks.parser.hprof.datastructures.Value;

public class PrimArray extends HeapObject {
    private Value<?>[] elements;

    public PrimArray(long objId, int stackTraceSerialNum, byte hprofElemType, Value<?>[] elems) {
        super(objId, stackTraceSerialNum, (long)hprofElemType);
        this.elements = elems;
    }

    public Value<?>[] getElements() {
        
        return elements;
    }

}