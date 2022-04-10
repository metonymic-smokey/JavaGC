package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.IHeapEvolutionInfo
import at.jku.anttracks.gui.model.SelectedClassifierInfo
import at.jku.anttracks.parser.heapevolution.IHeapEvolutionData

class PermBornDiedTempInfo(val heapEvolutionInfo: HeapEvolutionInfo) :
        SelectedClassifierInfo(ClassifierChain(), listOf<Filter>()),
        IHeapEvolutionInfo by heapEvolutionInfo,
        IHeapEvolutionData by heapEvolutionInfo {

    val heapEvolutionAnalysisCompleted
        get() = currentTime == endTime

    var minorGCDuration: Long = 0
    var majorGCDuration: Long = 0
    val minorGCOverhead: Double
        get() = minorGCDuration.toDouble() / (currentTime - startTime)
    val majorGCOverhead: Double
        get() = majorGCDuration.toDouble() / (currentTime - startTime)
    val totalGCOverhead: Double
        get() = minorGCOverhead + majorGCOverhead
}