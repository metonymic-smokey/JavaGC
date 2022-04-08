package at.jku.anttracks.heap;

import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.util.TraceException;

public interface ObjectAccess {
    static long PTR_ADJUSTMENT_NEEDED_MASK = 0x1L;
    static long PTR_ADJUSTMENT_NEEDED_RESET_MASK = 0xFFFFFFFFFFFFFFFEL;

    // Object access
    AddressHO getObject(long objAddr) throws TraceException;
}
