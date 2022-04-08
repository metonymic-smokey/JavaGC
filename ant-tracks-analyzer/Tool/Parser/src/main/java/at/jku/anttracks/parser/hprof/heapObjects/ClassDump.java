package at.jku.anttracks.parser.hprof.heapObjects;

import at.jku.anttracks.parser.hprof.datastructures.Static;
import at.jku.anttracks.parser.hprof.datastructures.Constant;
import at.jku.anttracks.parser.hprof.datastructures.InstanceField;

public class ClassDump extends HeapObject {

    private final Static[] statics;
    private final Constant[] constants;
    private final InstanceField[] instanceFields;

    public ClassDump(long classObjId, int stackTraceSerialNum, long superClassObjId, Constant[] constants, Static[] statics,
                     InstanceField[] instanceFields) {
        super(classObjId, stackTraceSerialNum, superClassObjId);
        this.constants = constants;
        this.statics = statics;
        this.instanceFields = instanceFields;
    }

    public Static[] getStatics() {
        return statics;
    }
}
