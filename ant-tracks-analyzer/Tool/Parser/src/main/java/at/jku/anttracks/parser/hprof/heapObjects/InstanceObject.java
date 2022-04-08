package at.jku.anttracks.parser.hprof.heapObjects;

import at.jku.anttracks.parser.hprof.datastructures.Value;

public class InstanceObject extends HeapObject{
	private Value<?>[] objectFields;

	public InstanceObject(long objId, int stackTraceSerialNum, long classObjId, Value<?>[] instanceFieldValues) {
		super(objId, stackTraceSerialNum, classObjId);
		this.objectFields = instanceFieldValues;
	}

	public Value<?>[] getObjectFields() {
		return objectFields;
	}
}
