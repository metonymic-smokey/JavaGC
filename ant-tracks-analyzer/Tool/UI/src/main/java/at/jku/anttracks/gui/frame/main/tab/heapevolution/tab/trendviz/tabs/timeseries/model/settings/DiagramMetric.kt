package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings

import at.jku.anttracks.classification.nodes.GroupingNode

enum class DiagramMetric(val label: String, val producer: (GroupingNode) -> Double) {
    BYTES("BYTES", { it.getByteCount(null).toDouble() }),
    OBJECTS("OBJECTS", { it.objectCount.toDouble() }),
    RETAINED_SIZE("RETAINED SIZE", { it.retainedSizeProperty().get().toDouble() }),
    TRANSITIVE_CLOSURE_SIZE("TRANS. CLOSURE SIZE", { it.transitiveClosureSizeProperty().get().toDouble() });

    override fun toString(): String {
        return label
    }
}