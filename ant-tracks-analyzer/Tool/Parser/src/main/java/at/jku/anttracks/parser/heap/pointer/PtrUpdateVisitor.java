
package at.jku.anttracks.parser.heap.pointer;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.heap.labs.Lab;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.util.TraceException;

public class PtrUpdateVisitor {
    public final boolean front;
    public final DetailedHeap heap;
    private final boolean gcFailed;

    public PtrUpdateVisitor(boolean front, DetailedHeap heap, boolean failed) {
        this.front = front;
        this.heap = heap;
        this.gcFailed = failed;
    }

    public void visit(Space space, Lab lab, long addr, AddressHO object) {
        try {
            if (gcFailed) {
                PointerHandling.updateRefsFailedGC(heap, space, lab, addr, object);
            } else {
                PointerHandling.updateRefsNew(heap, space, lab, addr, object);
            }
        } catch (TraceException e) {
            e.printStackTrace();
        }
    }
}
