package at.jku.anttracks.parser.heapevolution

import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.heap.IndexBasedHeap

data class StartEndAddress(val startAddress: Long = -1, val startIndex: Int = -1, var endAddress: Long = -1, var endIndex: Int = -1) {
    var sizeInfo: SizeInfo? = null

    fun label(endHeap: IndexBasedHeap): String {
        return String.format("%s\n@ %,d", endHeap.getType(endIndex).getSimpleName(true), endHeap.getAddress(endIndex))
    }

    fun calculateSizeInfo(startNode: GroupingNode?, endNode: GroupingNode?, absoluteHeapGrowth: Float) {
        val startTransitiveSize = startNode?.transitiveClosureSizeProperty()?.longValue() ?: 0
        val endTransitiveSize = endNode?.transitiveClosureSizeProperty()?.longValue() ?: 0
        val absoluteTransitiveGrowthRate = endTransitiveSize - startTransitiveSize
        val relativeTransitiveGrowthRate = 100.0f * absoluteTransitiveGrowthRate / startTransitiveSize
        val transitiveHeapGrowthPortion = absoluteTransitiveGrowthRate * 100.0f / absoluteHeapGrowth

        val startRetainedSize = startNode?.retainedSizeProperty()?.longValue() ?: 0
        val endRetainedSize = endNode?.retainedSizeProperty()?.longValue() ?: 0
        val absoluteRetainedGrowthRate = endRetainedSize - startRetainedSize
        val relativeRetainedGrowthRate = 100.0f * absoluteRetainedGrowthRate / startRetainedSize
        val retainedHeapGrowthPortion = absoluteRetainedGrowthRate * 100.0f / absoluteHeapGrowth

        val startDataStructureSize = startNode?.dataStructureSizeProperty()?.longValue() ?: 0
        val endDataStructureSize = endNode?.dataStructureSizeProperty()?.longValue() ?: 0
        val absoluteDataStructureGrowthRate = endDataStructureSize - startDataStructureSize
        val relativeDataStructureGrowthRate = 100.0f * absoluteDataStructureGrowthRate / startDataStructureSize
        val dataStructureHeapGrowthPortion = absoluteDataStructureGrowthRate * 100.0f / absoluteHeapGrowth

        val startDeepDataStructureSize = startNode?.deepDataStructureSizeProperty()?.longValue() ?: 0
        val endDeepDataStructureSize = endNode?.deepDataStructureSizeProperty()?.longValue() ?: 0
        val absoluteDeepDataStructureGrowthRate = endDeepDataStructureSize - startDeepDataStructureSize
        val relativeDeepDataStructureGrowthRate = 100.0f * absoluteDeepDataStructureGrowthRate / startDeepDataStructureSize
        val deepDataStructureHeapGrowthPortion = absoluteDeepDataStructureGrowthRate * 100.0f / absoluteHeapGrowth

        sizeInfo = SizeInfo(startTransitiveSize,
                            endTransitiveSize,
                            absoluteTransitiveGrowthRate,
                            relativeTransitiveGrowthRate,
                            transitiveHeapGrowthPortion,
                            startRetainedSize,
                            endRetainedSize,
                            absoluteRetainedGrowthRate,
                            relativeRetainedGrowthRate,
                            retainedHeapGrowthPortion,
                            startDataStructureSize,
                            endDataStructureSize,
                            absoluteDataStructureGrowthRate,
                            relativeDataStructureGrowthRate,
                            dataStructureHeapGrowthPortion,
                            startDeepDataStructureSize,
                            endDeepDataStructureSize,
                            absoluteDeepDataStructureGrowthRate,
                            relativeDeepDataStructureGrowthRate,
                            deepDataStructureHeapGrowthPortion)

    }
}

data class SizeInfo(
        val startTransitiveSize: Long = Long.MIN_VALUE,
        val endTransitiveSize: Long = Long.MIN_VALUE,
        val absoluteTransitiveGrowthRate: Long = Long.MIN_VALUE,
        val relativeTransitiveGrowthRate: Float = Float.MIN_VALUE,
        val transitiveHeapGrowthPortion: Float = Float.MIN_VALUE,
        val startRetainedSize: Long = Long.MIN_VALUE,
        val endRetainedSize: Long = Long.MIN_VALUE,
        val absoluteRetainedGrowthRate: Long = Long.MIN_VALUE,
        val relativeRetainedGrowthRate: Float = Float.MIN_VALUE,
        val retainedHeapGrowthPortion: Float = Float.MIN_VALUE,
        val startDataStructureSize: Long = Long.MIN_VALUE,
        val endDataStructureSize: Long = Long.MIN_VALUE,
        val absoluteDataStructureGrowthRate: Long = Long.MIN_VALUE,
        val relativeDataStructureGrowthRate: Float = Float.MIN_VALUE,
        val dataStructureHeapGrowthPortion: Float = Float.MIN_VALUE,
        val startDeepDataStructureSize: Long = Long.MIN_VALUE,
        val endDeepDataStructureSize: Long = Long.MIN_VALUE,
        val absoluteDeepDataStructureGrowthRate: Long = Long.MIN_VALUE,
        val relativeDeepDataStructureGrowthRate: Float = Float.MIN_VALUE,
        val deepDataStructureHeapGrowthPortion: Float = Float.MIN_VALUE
)