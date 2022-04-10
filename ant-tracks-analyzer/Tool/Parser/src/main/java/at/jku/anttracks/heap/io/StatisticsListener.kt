package at.jku.anttracks.heap.io

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.HeapAdapter
import at.jku.anttracks.heap.statistics.Statistics
import at.jku.anttracks.parser.EventType
import at.jku.anttracks.parser.ParserGCInfo
import at.jku.anttracks.parser.ParsingInfo

class StatisticsListener(val statisticsList: MutableList<Statistics>) : HeapAdapter() {

    override fun phaseChanging(
            sender: Any,
            from: ParserGCInfo,
            to: ParserGCInfo,
            failed: Boolean,
            position: Long,
            parsingInfo: ParsingInfo,
            inParserTimeWindow: Boolean) {
        if (to.eventType == EventType.GC_START) {
            statisticsList.add(statisticsList.size, Statistics.collect(sender as DetailedHeap, to, failed, parsingInfo))
        }
    }

    override fun phaseChanged(
            sender: Any,
            from: ParserGCInfo,
            to: ParserGCInfo,
            failed: Boolean,
            position: Long,
            parsingInfo: ParsingInfo,
            inParserTimeWindow: Boolean) {
        if (to.eventType == EventType.GC_END) {
            statisticsList.add(statisticsList.size, Statistics.collect(sender as DetailedHeap, to, failed, parsingInfo))
        }
    }

}