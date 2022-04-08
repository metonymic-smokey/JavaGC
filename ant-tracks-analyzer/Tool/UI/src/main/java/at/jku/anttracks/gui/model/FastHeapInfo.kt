
package at.jku.anttracks.gui.model

import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.symbols.Symbols
import java.util.function.Supplier

class FastHeapInfo(val appInfo: AppInfo, val time: Long) : AvailableFastHeapClassifierInfo() {
    private var heap: IndexBasedHeap? = null

    override val fastHeapSupplier: Supplier<IndexBasedHeap?>
        get() = Supplier { heap }

    override val symbolsSupplier: Supplier<Symbols?>
        get() = Supplier { heap?.symbols }

    fun setHeap(heap: IndexBasedHeap) {
        this.heap = heap
    }
}
