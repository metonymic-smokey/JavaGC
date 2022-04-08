package at.jku.anttracks.parser.threadseparation

import at.jku.anttracks.heap.io.HeapIndexReader
import at.jku.anttracks.heap.io.HeapPosition
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.parser.*
import at.jku.anttracks.util.Consts
import at.jku.anttracks.util.MutableLong
import java.util.*
import java.util.concurrent.BlockingQueue

open class ThreadMemorySeparatingTraceParser(symbols: Symbols) : at.jku.anttracks.parser.TraceParser<Void?>(symbols) {
    override fun doRemoveListenersOnCompletion(workspace: Void?) {
        //
    }

    override fun generateWorkspaceFromMetaData(heapIndexReader: HeapIndexReader,
                                               heapPosition: HeapPosition,
                                               parsingInfo: ParsingInfo): Void? {
        return null
    }

    override fun generatePlainWorkspace(factory: TraceScannerFactory?, parsingInfo: ParsingInfo?): Void? {
        return null
    }

    override fun doParseCleanupAfterSuccessfulParse(workspace: Void?) {
        //
    }

    override fun createMainEventHandler(parsingInfo: ParsingInfo?): TraceParsingEventHandler? {
        return null
    }

    override fun doWorkspaceCompletion(workspace: Void?) {
        //
    }

    override fun startSlaveThreads(queueSize: MutableLong?,
                                   masterQueue: BlockingQueue<ThreadLocalHeap>?,
                                   workspace: Void?,
                                   handler: ErrorHandler?,
                                   check: Boolean,
                                   parsingInfo: ParsingInfo?): List<TraceSlaveParser<Void?>> {
        val relAddrFactory = RelAddrFactory(symbols.heapWordSize.toLong())

        var slaves = 1
        if (MULTITHREADING) {
            slaves = Consts.AVAILABLE_PROCESSORS
        }

        val result = ArrayList<TraceSlaveParser<Void?>>()
        for (i in 0 until slaves) {
            val parserThread =
                    result.add(ThreadMemorySeparatingTraceSlaveParser(i,
                                                                      queueSize!!,
                                                                      masterQueue!!,
                                                                      relAddrFactory,
                                                                      symbols!!,
                                                                      check,
                                                                      handler!!))
        }
        return result
    }
}