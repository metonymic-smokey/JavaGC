
package at.jku.anttracks.heap.statistics

import at.jku.anttracks.heap.*
import at.jku.anttracks.heap.io.StatisticsReader
import at.jku.anttracks.heap.labs.AddressHO
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.heap.space.SpaceInfo
import at.jku.anttracks.heap.space.SpaceType
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.parser.EventType
import at.jku.anttracks.parser.ParserGCInfo
import at.jku.anttracks.parser.ParsingInfo
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.math.roundToInt
import kotlin.system.measureTimeMillis

class Statistics(val info: StatisticGCInfo, val eden: SpaceStatistics, val survivor: SpaceStatistics, val old: SpaceStatistics) {
    companion object {
        private val LOGGER = Logger.getLogger(Statistics::class.java.simpleName)
        const val REACHABLE_MEMORY_CALCULATION_OVERHEAD_THRESHOLD = 2.0

        fun collect(heap: DetailedHeap,
                    parserGCInfo: ParserGCInfo,
                    failed: Boolean,
                    parsingInfo: ParsingInfo) = collect(heap,
                                                        StatisticGCInfo(parserGCInfo.type,
                                                                        parserGCInfo.cause,
                                                                        parserGCInfo.concurrent,
                                                                        failed,
                                                                        parserGCInfo.eventType,
                                                                        parserGCInfo.id,
                                                                        parserGCInfo.time),
                                                        failed,
                                                        parsingInfo)

        fun collect(heap: DetailedHeap,
                    statisticsGCInfo: StatisticGCInfo,
                    failed: Boolean,
                    parsingInfo: ParsingInfo): Statistics {
            class ThreadLocalStatisticsVisitor : ObjectVisitor {
                var eden = SpaceStatistics(heap.symbols)
                var survivor = SpaceStatistics(heap.symbols)
                var old = SpaceStatistics(heap.symbols)

                override fun visit(address: Long, obj: AddressHO, space: SpaceInfo, rootPtrs: List<RootPtr>?) {
                    when (space.type) {
                        SpaceType.EDEN -> eden
                        SpaceType.SURVIVOR -> survivor
                        SpaceType.OLD -> old
                        SpaceType.UNDEFINED -> null
                        null -> null
                    }?.add(obj.info)
                }
            }

            //val m = ApplicationStatistics.getInstance().createMeasurement("Collect statistics")

            // we calculate the amount of reachable memory as often as possible while never letting the calculation exceed 10% of the total parse time
            var reachableBytes: Long? = null
            LOGGER.log(Level.INFO, "Reachable memory calculation runtime overhead: ${(parsingInfo.reachableMemoryCalculationOverhead * 100).roundToInt()}%")
            if (statisticsGCInfo.meta == EventType.GC_END && parsingInfo.reachableMemoryCalculationOverhead <= REACHABLE_MEMORY_CALCULATION_OVERHEAD_THRESHOLD) {
                parsingInfo.reachableMemoryCalculationDuration += measureTimeMillis { reachableBytes = heap.toIndexBasedHeap(false, null).reachableFromRootByteCount }
            }
            statisticsGCInfo.reachableBytes = reachableBytes

            val eden = SpaceStatistics(heap.symbols)
            val survivor = SpaceStatistics(heap.symbols)
            val old = SpaceStatistics(heap.symbols)
            heap.toObjectStream().forEachParallel(object : ObjectStream.ThreadVisitorGenerator<ThreadLocalStatisticsVisitor> {
                override fun generate(): ThreadLocalStatisticsVisitor {
                    return ThreadLocalStatisticsVisitor()
                }

            }, ObjectVisitor.Settings.NO_INFOS).forEach { tlsv ->
                eden.merge(tlsv.eden)
                survivor.merge(tlsv.survivor)
                old.merge(tlsv.old)
            }

            //m.end()
            return Statistics(statisticsGCInfo, eden, survivor, old)
        }

        fun readStatisticsFromMetadata(preprocessedHeap: PreprocessedHeap): List<Statistics>? {
            return readStatisticsFromMetadata(preprocessedHeap.metaDir.absolutePath, preprocessedHeap.symbols)
        }

        fun readStatisticsFromMetadata(fullMetaDataPath: String, symbols: Symbols): List<Statistics>? {
            LOGGER.log(Level.INFO, "reading statistics from meta data")
            var statistics: List<Statistics>? = null
            try {
                val reader = StatisticsReader(fullMetaDataPath)
                statistics = reader.read(symbols)
            } catch (t: Throwable) {
                LOGGER.log(Level.WARNING, "reading statistics from meta data failed (this may be due to a change in the meta data format) -> reparse")
                statistics = null
            }

            return statistics
        }

        fun findStatistics(statistics: List<Statistics>, gcLookup: GarbageCollectionLookup): Statistics? {
            var id = 0
            for (stat in statistics) {
                if (stat.info.matches(gcLookup)) {
                    if (id == gcLookup.nth) {
                        return stat
                    } else {
                        id++
                    }
                }
            }
            return null
        }
    }

    val totalBytes
        get() = eden.memoryConsumption.bytes + survivor.memoryConsumption.bytes + old.memoryConsumption.bytes

    val totalObjects
        get() = eden.memoryConsumption.objects + survivor.memoryConsumption.objects + old.memoryConsumption.objects

    val totalInstances
        get() = eden.objectTypes.instances + survivor.objectTypes.instances + old.objectTypes.instances

    val totalSmallArrays
        get() = eden.objectTypes.smallArrays + survivor.objectTypes.smallArrays + old.objectTypes.smallArrays

    val totalBigArrays
        get() = eden.objectTypes.bigArrays + survivor.objectTypes.bigArrays + old.objectTypes.bigArrays

    val totalInstancesBytes
        get() = eden.objectTypes.instancesBytes + survivor.objectTypes.instancesBytes + old.objectTypes.instancesBytes

    val totalSmallArraysBytes
        get() = eden.objectTypes.smallArraysBytes + survivor.objectTypes.smallArraysBytes + old.objectTypes.smallArraysBytes

    val totalBigArraysBytes
        get() = eden.objectTypes.bigArraysBytes + survivor.objectTypes.bigArraysBytes + old.objectTypes.bigArraysBytes

}
