package at.jku.anttracks.heap;

import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.util.TraceException;

public interface FrontBackObjectAccess extends ObjectAccess {
    AddressHO getObjectInFront(long objAddr) throws TraceException;
    AddressHO getObjectInBack(long objAddr) throws TraceException;

    default AddressHO getObject(long objAddr, boolean inFront) throws TraceException {
        return inFront ? getObjectInFront(objAddr) : getObjectInBack(objAddr);
    }
}
