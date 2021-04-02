
package at.jku.anttracks.heap.io

import at.jku.anttracks.heap.*
import at.jku.anttracks.heap.statistics.Statistics
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.parser.EventType
import at.jku.anttracks.parser.ParserGCInfo
import at.jku.anttracks.parser.ParsingInfo
import at.jku.anttracks.parser.io.BaseFile
import at.jku.anttracks.util.Consts
import at.jku.anttracks.util.FileUtil

import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.logging.Level
import java.util.logging.Logger

open class MetaDataWriterListener(private val config: MetaDataWriterConfig, private val statisticsObtainFunction: ((DetailedHeap, ParserGCInfo, Boolean, ParsingInfo) -> Statistics)?) : HeapAdapter() {

    private val LOGGER = Logger.getLogger(MetaDataWriterListener::class.java.name)

    private val indexWriter: HeapIndexWriter
    private val statisticsWriter: StatisticsWriter
    private val executors: ExecutorService = Executors.newFixedThreadPool(Consts.AVAILABLE_PROCESSORS)

    private var dumps = 1

    init {
        val file = File(config.path)
        if (file.exists()) {

            FileUtil.deleteRecursively(file)
        }
        file.mkdir()

        indexWriter = HeapIndexWriter(config.path)
        statisticsWriter = StatisticsWriter(config.path)

        // TODO copy feature file to meta dir
        /*
        if (heap.getSymbols().featureFile != null) {
            try (InputStream in = FileUtil.openR(new File(heap.getSymbols().featureFile))) {
                String path = config.path + File.separator + FEATURES_META_FILE;
                try (OutputStream out = ZipFileUtil.isZipFilePath(path) ? ZipFileUtil.openW(new File(path)) : FileUtil.openW(new File(path))) {
                    StreamUtil.copy(in, out);
                }
            }
        }
        */
    }

    /**
     * Event handling before the GC starts.
     */
    override fun phaseChanging(
            sender: Any,
            from: ParserGCInfo,
            to: ParserGCInfo,
            failed: Boolean,
            position: Long,
            parsingInfo: ParsingInfo,
            withinSelectedTimeWindow: Boolean) {
        if (from.eventType === EventType.GC_END) {
            // Switching away from mutator = Switching into GC phase
            val heap = sender as DetailedHeap
            handleHeapEvent(position, to, failed, heap, false, parsingInfo)
        }
    }

    /**
     * Event handling after the GC ended.
     */
    override fun phaseChanged(
            sender: Any,
            from: ParserGCInfo,
            to: ParserGCInfo,
            failed: Boolean,
            position: Long,
            parsingInfo: ParsingInfo,
            withinSelectedTimeWindow: Boolean) {
        if (to.eventType === EventType.GC_END) {
            // Switching into mutator phase
            val heap = sender as DetailedHeap
            val progress = 1.0 * position / parsingInfo.traceLength
            val writeHeap = config.dumps > 0 && dumps < config.dumps * progress && (!config.atMajorGCsOnly || from.type.isFull)
            handleHeapEvent(position, to, failed, heap, writeHeap, parsingInfo)
        }
    }

    private fun handleHeapEvent(position: Long,
                                gcInfo: ParserGCInfo,
                                failed: Boolean,
                                heap: DetailedHeap,
                                writeHeap: Boolean,
                                parsingInfo: ParsingInfo) {
        try {
            writeIndex(position, gcInfo, writeHeap)
            if (statisticsObtainFunction != null) {
                writeStatistics(statisticsObtainFunction.invoke(heap, gcInfo, failed, parsingInfo))
            }
            if (writeHeap) {
                HeapWriter(config.path, gcInfo.id.toInt()).use { heapWriter -> heapWriter.write(heap, executors) }
                dumps++
            }
        } catch (e: IOException) {
            LOGGER.log(Level.SEVERE, "Unexpected error @ $position", e)
        }

    }

    fun writeIndex(position: Long, gcInfo: StatisticGCInfo, writeHeap: Boolean) {
        writeIndex(position, gcInfo.type, gcInfo.meta, gcInfo.cause, gcInfo.time, gcInfo.id.toInt(), writeHeap)
    }

    fun writeIndex(position: Long, gcInfo: ParserGCInfo, writeHeap: Boolean) {
        writeIndex(position, gcInfo.type, gcInfo.eventType, gcInfo.cause, gcInfo.time, gcInfo.id.toInt(), writeHeap)
    }

    fun writeIndex(position: Long, type: GarbageCollectionType, eventType: EventType, cause: GarbageCollectionCause, time: Long, id: Int, writeHeap: Boolean) {
        indexWriter.write(position, type, eventType, cause, time, id, writeHeap)
        indexWriter.flush()
    }

    @Throws(IOException::class)
    fun writeStatistics(stat: Statistics) {
        statisticsWriter.write(stat)
        statisticsWriter.flush()
    }

    fun writeHeadersFile(symbols: Symbols) {
        DataOutputStream(BaseFile.openW(config.path + File.separator + Consts.HEADERS_META_FILE)).use { out ->
            out.writeInt(symbols.header.size)
            for (i in symbols.header.indices) {
                out.writeInt(symbols.header[i])
            }
        }
    }

    override fun close(
            sender: Any,
            parsingInfo: ParsingInfo) {
        val heap = sender as DetailedHeap

        executors.shutdown()

        try {
            statisticsWriter.close()
        } catch (e: IOException) {
            LOGGER.log(Level.SEVERE, "Unexpected error", e)
        }

        try {
            indexWriter.close()
        } catch (e: IOException) {
            LOGGER.log(Level.SEVERE, "Unexpected error", e)
        }

        try {
            writeHeadersFile(heap.symbols)
        } catch (e: IOException) {
            LOGGER.log(Level.SEVERE, "Unexpected error", e)
        }
    }
}