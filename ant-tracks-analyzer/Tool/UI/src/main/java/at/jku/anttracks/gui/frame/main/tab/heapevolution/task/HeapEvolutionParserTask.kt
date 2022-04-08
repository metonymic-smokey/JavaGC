package at.jku.anttracks.gui.frame.main.tab.heapevolution.task

import at.jku.anttracks.gui.frame.main.tab.heapevolution.HeapEvolutionTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.utils.AntTask
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.heap.HeapAdapter
import at.jku.anttracks.heap.io.MetaDataReaderConfig
import at.jku.anttracks.parser.ParserGCInfo
import at.jku.anttracks.parser.ParsingInfo
import at.jku.anttracks.parser.heapevolution.HeapEvolutionTraceParser
import at.jku.anttracks.util.toString
import java.io.File

class HeapEvolutionParserTask(val heapEvolutionInfo: HeapEvolutionInfo,
                              val configurationTab: HeapEvolutionTab) : AntTask<HeapEvolutionTraceParser>() {
    override fun backgroundWork(): HeapEvolutionTraceParser {
        updateTitle("Parse trace file to calculate heap evolution info")

        val selectedHeapEvolutionAnalysisMethods = configurationTab.heapEvolutionAnalysisMethods.filter { it.value.get() }.map { it.key }.toList()
        val heapEvolutionUpdateListeners = selectedHeapEvolutionAnalysisMethods.flatMap { method -> method.createHeapEvolutionAnalysisUpdateListeners() }
        val parser = HeapEvolutionTraceParser(heapEvolutionInfo.appInfo.symbols,
                                              MetaDataReaderConfig(heapEvolutionInfo.appInfo.symbols.root + File.separator + Consts.ANT_META_DIRECTORY),
                                              heapEvolutionInfo.heapEvolutionData,
                                              selectedHeapEvolutionAnalysisMethods.map { it.parserActions }.reduce { set1, set2 -> set1.union(set2) },
                                              heapEvolutionUpdateListeners)

        // progress indication
        parser.addHeapListener(object : HeapAdapter() {
            override fun phaseChanging(sender: Any,
                                       from: ParserGCInfo,
                                       to: ParserGCInfo,
                                       failed: Boolean,
                                       position: Long,
                                       parsingInfo: ParsingInfo,
                                       inParserTimeWindow: Boolean) {
                updateMessage("@ ${from.time.toString("%,d")} ms (Window ${parsingInfo.fromTime.toString("%,d")} ms - ${parsingInfo.toTime.toString("%,d")} ms)")
                updateProgress(position - parsingInfo.fromByte, parsingInfo.toByte - parsingInfo.fromByte)
            }
        })

        parser.parse()
        return parser
    }

    override fun finished() {

    }
}