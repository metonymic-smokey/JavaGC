package at.jku.anttracks.parser.heapevolution

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.GarbageCollectionCause
import at.jku.anttracks.heap.GarbageCollectionType
import at.jku.anttracks.heap.io.MetaDataReaderConfig
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.parser.*
import at.jku.anttracks.parser.heap.HeapTraceParser
import at.jku.anttracks.util.MutableLong
import java.util.concurrent.BlockingQueue

enum class HeapEvolutionAction {
    TRACK_PERM_OBJECTS,
    TRACK_BORN_OBJECTS,
    TRACK_DIED_OBJECTS,
    TRACK_TEMP_OBJECTS
}

interface HeapEvolutionUpdateListener {
    fun timeWindowStart(heapEvolutionData: HeapEvolutionData) {}
    fun gcStart(heapEvolutionData: HeapEvolutionData) {}
    fun gcEnd(heapEvolutionData: HeapEvolutionData) {}
    fun timeWindowEnd(heapEvolutionData: HeapEvolutionData) {}
}

class HeapEvolutionTraceParser(symbols: Symbols,
                               readerConfig: MetaDataReaderConfig,
                               val heapEvolutionData: HeapEvolutionData,
                               private val parserHeapEvolutionActions: Set<HeapEvolutionAction>,
                               val heapEvolutionUpdateListeners: List<HeapEvolutionUpdateListener>) : HeapTraceParser(symbols, readerConfig, heapEvolutionData.startTime, heapEvolutionData.endTime) {

    // boolean flags controlling the performed computations
    private val trackPermObjects: Boolean = parserHeapEvolutionActions.contains(HeapEvolutionAction.TRACK_PERM_OBJECTS)
    private val trackBornObjects: Boolean = parserHeapEvolutionActions.contains(HeapEvolutionAction.TRACK_BORN_OBJECTS)
    private val trackDiedObjects: Boolean = parserHeapEvolutionActions.contains(HeapEvolutionAction.TRACK_DIED_OBJECTS)
    private val trackTempObjects: Boolean = parserHeapEvolutionActions.contains(HeapEvolutionAction.TRACK_TEMP_OBJECTS)

    // this thread safe list is used by all event handlers to gather the spaces that are to be collected during a minor collection
    /// private val collectedSpaces = CopyOnWriteArrayList<Space>()

    // stores the time of the last gc point; used to detect when a final gc start is omitted by the parser (see doParseCleanupAfterSuccessfulParse)
    var latestTime: Long = -1

    override fun startSlaveThreads(queueSize: MutableLong?,
                                   masterQueue: BlockingQueue<ThreadLocalHeap>?,
                                   workspace: DetailedHeap?,
                                   handler: ErrorHandler?,
                                   check: Boolean,
                                   parsingInfo: ParsingInfo?): MutableList<TraceSlaveParser<DetailedHeap>> {
        val slaves = super.startSlaveThreads(queueSize, masterQueue, workspace, handler, check, parsingInfo)

        // metadata contains only heaps at gc end
        // however the gc end event will not be parsed...we still want to init collections and heap at that point though...
        // must only initialize once all slave threads (eventhandlers) have been created
        latestTime = workspace!!.gc.time

        if (withinDiffWindow()) {
            workspace.resolveRootPtrs()
            heapEvolutionData.init(workspace)

            // the metadata gc was the start of the heap evolution analysis window!
            // fake HeapEvolutionEventHandler GC_END event and trigger listeners
            heapEvolutionData.gcInfos.add(ParserGCInfo(EventType.GC_END,
                                                       GarbageCollectionType.MAJOR,
                                                       GarbageCollectionCause(-1, "metadata", false),
                                                       workspace.latestGCId(),
                                                       workspace.gc.time))
            heapEvolutionUpdateListeners.forEach { it.timeWindowStart(heapEvolutionData) }
            heapEvolutionUpdateListeners.forEach { it.gcEnd(heapEvolutionData) }
        }

        return slaves
    }

    override fun createMainEventHandler(parsingInfo: ParsingInfo): TraceParsingEventHandler {
        return HeapEvolutionEventHandler(this, parsingInfo, parserHeapEvolutionActions)
    }

    fun startOfDiffWindow(): Boolean = latestTime == fromTime

    fun withinDiffWindow(): Boolean = latestTime in fromTime..toTime

    fun endOfDiffWindow(): Boolean = latestTime == toTime

    override fun doParseCleanupAfterSuccessfulParse(workspace: DetailedHeap) {
        super.doParseCleanupAfterSuccessfulParse(workspace)

        heapEvolutionData.finalize()

        if (latestTime != toTime) {
            // the event at toTime is a GC START, this event however has not been parsed (final GC STARTS are not parsed by convention)
            // but we still want to perform heap evolution tasks for this event and call listeners!
            // partly copied from gcstart method in event handler:

            // TODO check if workspace.latestGCId() is correct here (larger then the one of the latest gcInfo entry)
            heapEvolutionData.gcInfos.add(ParserGCInfo(EventType.GC_START,
                                                       GarbageCollectionType.MINOR,
                                                       GarbageCollectionCause(-1, "last GC start of time window", true),
                                                       workspace.latestGCId(),
                                                       toTime))
            heapEvolutionUpdateListeners.forEach { it.gcStart(heapEvolutionData) }
            heapEvolutionUpdateListeners.forEach { it.timeWindowEnd(heapEvolutionData) }
        }
    }
}