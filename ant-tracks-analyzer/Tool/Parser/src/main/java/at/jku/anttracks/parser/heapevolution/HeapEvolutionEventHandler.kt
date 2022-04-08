package at.jku.anttracks.parser.heapevolution

import at.jku.anttracks.heap.ObjectVisitor
import at.jku.anttracks.heap.labs.AddressHO
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.heap.space.SpaceInfo
import at.jku.anttracks.parser.ParserGCInfo
import at.jku.anttracks.parser.ParsingInfo
import at.jku.anttracks.parser.ThreadLocalHeap
import at.jku.anttracks.parser.TraceSlaveParser.mayBeFiller
import at.jku.anttracks.parser.heap.HeapEventHandler

class HeapEvolutionEventHandler(val parser: HeapEvolutionTraceParser,
                                parsingInfo: ParsingInfo,
                                actions: Set<HeapEvolutionAction>) : HeapEventHandler(parser.workspace, parser.symbols, parsingInfo, parser) {

    // boolean flags controlling the performed computations
    private val trackPermObjects: Boolean = actions.contains(HeapEvolutionAction.TRACK_PERM_OBJECTS)
    private val trackBornObjects: Boolean = actions.contains(HeapEvolutionAction.TRACK_BORN_OBJECTS)
    private val trackDiedObjects: Boolean = actions.contains(HeapEvolutionAction.TRACK_DIED_OBJECTS)
    private val trackTempObjects: Boolean = actions.contains(HeapEvolutionAction.TRACK_TEMP_OBJECTS)

// --------------------------------------------------------
// ----------------------- GC -----------------------------
// --------------------------------------------------------

    override fun doParseGCStart(gcInfo: ParserGCInfo, start: Long, end: Long, threadLocalHeap: ThreadLocalHeap) {
        parser.latestTime = gcInfo.time

        if (parser.startOfDiffWindow()) {
            // the initial index based heap needs resolved root pointers for eventual classification
            parser.workspace.resolveRootPtrs()
            parser.heapEvolutionData.init(parser.workspace)
        }

        if (parser.withinDiffWindow()) {
            // update the heap evolution data and...
            parser.heapEvolutionData.gcInfos.add(gcInfo)

            if (parser.startOfDiffWindow()) {
                parser.heapEvolutionUpdateListeners.forEach { it.timeWindowStart(parser.heapEvolutionData) }
            }
            parser.heapEvolutionUpdateListeners.forEach { it.gcStart(parser.heapEvolutionData) }
            if (parser.endOfDiffWindow()) {
                parser.heapEvolutionUpdateListeners.forEach { it.timeWindowEnd(parser.heapEvolutionData) }
            }
        }

        super.doParseGCStart(gcInfo, start, end, threadLocalHeap)
    }

    override fun doParseGCEnd(gcInfo: ParserGCInfo, start: Long, end: Long, failed: Boolean, threadLocalHeap: ThreadLocalHeap) {
        parser.latestTime = gcInfo.time

        if (!parser.startOfDiffWindow() && parser.withinDiffWindow()) {
            heap.toObjectStream(false).forEach(object : ObjectVisitor {
                override fun visit(address: Long, obj: AddressHO, space: SpaceInfo, rootPtrs: List<RootPtr>?) {
                    if (space.isBeingCollected && obj.lastMovedAt != heap.latestGCId()) {
                        val bornBeforeTimeWindowStart = obj.bornAt < parser.heapEvolutionData.startGCId
                        if (bornBeforeTimeWindowStart) {
                            // PERM object in collected region was not moved -> DIED
                            val diedPerm = parser.heapEvolutionData.permHeapObjectMap.remove(obj)
                            if (diedPerm == null) {
                                println("DEBUG: Bad boi ... Perm object died but was not found in address list")
                            }
                            parser.heapEvolutionData.diedStartIndices.set(diedPerm!!.startIndex)
                            val survivedGCs = heap.latestGCId() - obj.bornAt - 1
                            if (survivedGCs < 0) {
                                println("DEBUG: Bad boi ... Age < 0... This could be the case for filler objects")
                            }
                            parser.heapEvolutionData.diedAgeCollection.put(obj.info, survivedGCs)
                        } else if (obj.bornAt == parser.heapEvolutionData.startGCId && parser.heapEvolutionData.permHeapObjectMap.containsKey(obj)) {
                            if (mayBeFiller(heap.symbols, obj.site)) {
                                // Remove filler object
                                parser.heapEvolutionData.permHeapObjectMap.remove(obj)
                            } else {
                                println("DEBUG: Bad boi ... PERM object found that was created during the time window's first GC but that is not a filler.")
                            }
                        } else {
                            // BORN object in collected region was not moved -> TEMP
                            val survivedGCs = heap.latestGCId() - obj.bornAt - 1
                            if (survivedGCs < 0) {
                                println("DEBUG: Bad boi ... Age < 0... This could be the case for filler objects")
                            }
                            parser.heapEvolutionData.tempAgeCollection.put(obj.info, survivedGCs)
                        }
                    }
                }
            }, ObjectVisitor.Settings.NO_INFOS)

            // we delayed updating the heap until now because BACK gets cleared on GC end, and we needed BACK to extract PERM and BORN info
            super.doParseGCEnd(gcInfo, start, end, failed, threadLocalHeap)

            // update the diffing data and...
            parser.heapEvolutionData.gcInfos.add(gcInfo)

            parser.heapEvolutionUpdateListeners.forEach { it.gcEnd(parser.heapEvolutionData) }
            if (parser.endOfDiffWindow()) {
                parser.heapEvolutionData.finalize()
                parser.heapEvolutionUpdateListeners.forEach { it.timeWindowEnd(parser.heapEvolutionData) }
            }
        } else {
            super.doParseGCEnd(gcInfo, start, end, failed, threadLocalHeap)

            if (parser.startOfDiffWindow()) {
                // the initial index based heap needs resolved root pointers for eventual classification
                parser.workspace.resolveRootPtrs()
                parser.heapEvolutionData.init(parser.workspace)
                parser.heapEvolutionData.gcInfos.add(gcInfo)
                parser.heapEvolutionUpdateListeners.forEach { it.timeWindowStart(parser.heapEvolutionData) }
                parser.heapEvolutionUpdateListeners.forEach { it.gcEnd(parser.heapEvolutionData) }
            }
        }
    }
}