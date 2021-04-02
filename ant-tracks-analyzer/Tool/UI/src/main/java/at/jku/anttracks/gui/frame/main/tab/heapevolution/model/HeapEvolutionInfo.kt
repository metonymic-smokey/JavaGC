package at.jku.anttracks.gui.frame.main.tab.heapevolution.model

import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.AvailableFastHeapClassifierInfo
import at.jku.anttracks.gui.model.IAppInfo
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.statistics.Statistics
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.parser.heapevolution.HeapEvolutionData
import at.jku.anttracks.parser.heapevolution.IHeapEvolutionData
import java.util.function.Supplier

interface IHeapEvolutionInfo {
    val appInfo: AppInfo
    val heapEvolutionData: HeapEvolutionData
}

class HeapEvolutionInfo(override val appInfo: AppInfo,
                        override val startStatistics: Statistics,
                        override val endStatistics: Statistics,
                        override val heapEvolutionData: HeapEvolutionData = HeapEvolutionData(startStatistics, endStatistics)) :
        AvailableFastHeapClassifierInfo(),
        IHeapEvolutionInfo,
        IHeapEvolutionData by heapEvolutionData,
        IAppInfo by appInfo {
    fun reset() {
        HeapEvolutionData(startStatistics, endStatistics)
    }

    override val fastHeapSupplier: Supplier<IndexBasedHeap?>
        get() = Supplier { heapEvolutionData.currentIndexBasedHeap }
    override val symbolsSupplier: Supplier<Symbols?>
        get() = Supplier { heapEvolutionData.currentIndexBasedHeap.symbols }
}
