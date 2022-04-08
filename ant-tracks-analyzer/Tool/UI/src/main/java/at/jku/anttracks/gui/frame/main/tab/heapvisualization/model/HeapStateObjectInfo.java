
/**
 *
 */
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.heap.symbols.AllocationSite;

/**
 * @author Christina Rammerstorfer
 */
public class HeapStateObjectInfo {
    public final long address;
    public final Object classifications;
    public final AllocationSite allocationSite;
    public final AllocatedType type;
    public final int size;

    public HeapStateObjectInfo(long address, Object classifications, AllocationSite allocationSite, AllocatedType type, int size) {
        super();
        this.address = address;
        this.classifications = classifications;
        this.allocationSite = allocationSite;
        this.type = type;
        this.size = size;
    }

    @Override
    public int hashCode() {
        int result = 1;
        int c = (int) (address ^ (address >>> 32));
        result = 37 * result + c;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof HeapStateObjectInfo) {
            HeapStateObjectInfo other = (HeapStateObjectInfo) obj;
            return address == other.address;
        }
        return false;
    }

}
