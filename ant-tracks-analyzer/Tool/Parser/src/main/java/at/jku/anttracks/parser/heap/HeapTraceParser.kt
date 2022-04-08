
package at.jku.anttracks.parser.heap

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.GarbageCollectionCause
import at.jku.anttracks.heap.GarbageCollectionLookup
import at.jku.anttracks.heap.GarbageCollectionType
import at.jku.anttracks.heap.io.HeapIndexReader
import at.jku.anttracks.heap.io.HeapPosition
import at.jku.anttracks.heap.io.MetaDataReaderConfig
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.parser.*
import at.jku.anttracks.parser.printing.AdditionalPrintingEventHandler
import at.jku.anttracks.util.Counter
import at.jku.anttracks.util.TraceException
import java.io.FileNotFoundException
import java.io.IOException
import java.util.logging.Level

open class HeapTraceParser : TraceParser<DetailedHeap> {
    constructor(symbols: Symbols) : super(symbols)
    constructor(symbols: Symbols, readerConfig: MetaDataReaderConfig, toTime: Long) : super(symbols, readerConfig, toTime, toTime)
    constructor(symbols: Symbols, readerConfig: MetaDataReaderConfig, fromTime: Long, toTime: Long) : super(symbols, readerConfig, fromTime, toTime)
    constructor(symbols: Symbols, readerConfig: MetaDataReaderConfig, toGc: GarbageCollectionLookup) : super(symbols, readerConfig, toGc, toGc)

    val allocTime = Counter()
    val moveTime = Counter()
    val gcStartCompleteProcessingTime = Counter()
    val gcEndCompleteProcessingTime = Counter()

    var latestGCStartTime: Long = -1
    var latestGCEndTime: Long = -1

    init {
        addEventHandler { heapInfo, parsingInfo -> AdditionalPrintingEventHandler(parsingInfo) }
    }

    @Throws(IOException::class)
    override fun generatePlainWorkspace(factory: TraceScannerFactory, parsingInfo: ParsingInfo): DetailedHeap {
        val heap = HeapBuilder.constructHeap(symbols, parsingInfo)
        for (hl in heapListeners) {
            heap.addListener(hl)
        }
        return heap
    }

    @Throws(FileNotFoundException::class, IOException::class)
    override fun generateWorkspaceFromMetaData(heapIndexReader: HeapIndexReader,
                                               heapPosition: HeapPosition,
                                               parsingInfo: ParsingInfo): DetailedHeap {
        val heap: DetailedHeap
        if (heapPosition.fileName != -1) {
            heap = heapIndexReader.getHeap(heapPosition.fileName, symbols, parsingInfo)
        } else {
            heap = HeapBuilder.constructHeap(symbols, parsingInfo)
        }
        for (hl in heapListeners) {
            heap.addListener(hl)
        }

        // This is the case if the first selected GC point has exaclty been reconstructed from a heap dump file.
        // The GC-end event at which the heap dump file got created will not be read from the trace anymore, therefore
        // we have to start the diffing here manually.
        if (heapPosition.fromPosition == heapPosition.toPosition) {
            // We only dump heaps on GC-end, so this cannot be a GC start
            // Notify all listeners that we are at a GC end
            // TODO: Currently we say that the last GC was MINOR, but it could also have been a MAJOR
            // TODO: Also length is not correct but just set to 1
            heapListeners.stream()
                    .forEach { l ->
                        l.phaseChanging(heap,
                                        ParserGCInfo(
                                                EventType.GC_START,
                                                GarbageCollectionType.MINOR,
                                                GarbageCollectionCause(-1, "Artificial AntTracks Analyzer Event", false),
                                                (heapPosition.fileName - 1).toShort(),
                                                parsingInfo.fromTime, false),
                                        ParserGCInfo(
                                                EventType.GC_END,
                                                GarbageCollectionType.MINOR,
                                                GarbageCollectionCause(-1, "Artificial AntTracks Analyzer Event", false),
                                                heapPosition.fileName.toShort(),
                                                parsingInfo.fromTime, false),
                                        false,
                                        heapPosition.fromPosition,
                                        parsingInfo,
                                        true)
                    }
        }

        return heap
    }

    override fun doWorkspaceCompletion(workspace: DetailedHeap) {
        logger.log(Level.INFO, "bringing heap into consistent state")
        try {
            workspace.complete()
        } catch (e: TraceException) {
            e.printStackTrace()
        }
    }

    override fun doRemoveListenersOnCompletion(workspace: DetailedHeap) {
        workspace.removeAllListeners()
    }

    @Throws(TraceException::class)
    override fun doParseCleanupAfterSuccessfulParse(workspace: DetailedHeap) {
        workspace.assignLabsIncorrectlyAssumedToBeFillers(false)
    }

    override fun createNewThreadLocalHeap(thread: String): ThreadLocalHeap {
        return ThreadLocalHeap(thread, ThreadLocalHeap.STATE_IN_QUEUE, workspace.latestGCId(), workspace.gc.eventType)
    }

    override fun createMainEventHandler(parsingInfo: ParsingInfo): TraceParsingEventHandler {
        return HeapEventHandler(workspace, symbols, parsingInfo, this)
    }
}
