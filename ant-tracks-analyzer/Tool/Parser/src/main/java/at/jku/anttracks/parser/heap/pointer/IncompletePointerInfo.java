package at.jku.anttracks.parser.heap.pointer;

public class IncompletePointerInfo {

    public final long fromAddr;
    public final long toAddr;
    public final long[] ptrs;
    public int top;

    // TODO: Include HO directly here
    public IncompletePointerInfo(long fromAddr, long toAddr, long[] ptrs, int top) {
        this.fromAddr = fromAddr;
        this.toAddr = toAddr;
        this.ptrs = ptrs;
        this.top = top;
    }

    synchronized public void addPointers(long[] multiThreadedArrivedPtrs) {
        System.arraycopy(multiThreadedArrivedPtrs, 0, ptrs, top, multiThreadedArrivedPtrs.length);
        top += multiThreadedArrivedPtrs.length;
    }

    public boolean isComplete() {
        return ptrs.length == top;
    }
}