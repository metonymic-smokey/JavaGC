
package at.jku.anttracks.heap

import at.jku.anttracks.parser.ParserGCInfo
import at.jku.anttracks.parser.ParsingInfo

interface HeapListener {
    fun phaseChanging(sender: Any,
                      from: ParserGCInfo,
                      to: ParserGCInfo,
                      failed: Boolean,
                      position: Long,
                      parsingInfo: ParsingInfo,
                      inParserTimeWindow: Boolean)

    fun phaseChanged(sender: Any,
                     from: ParserGCInfo,
                     to: ParserGCInfo,
                     failed: Boolean,
                     position: Long,
                     parsingInfo: ParsingInfo,
                     inParserTimeWindow: Boolean)

    fun close(sender: Any, parsingInfo: ParsingInfo)

    companion object {

        fun firePhaseChanging(listeners: List<HeapListener>,
                              sender: Any,
                              from: ParserGCInfo,
                              to: ParserGCInfo,
                              failed: Boolean,
                              position: Long,
                              parsingInfo: ParsingInfo,
                              inParserTimeWindow: Boolean) {
            for (i in listeners.indices) {
                listeners[i].phaseChanging(sender, from, to, failed, position, parsingInfo, inParserTimeWindow)
            }
        }

        fun firePhaseChanged(listeners: List<HeapListener>,
                             sender: Any,
                             from: ParserGCInfo,
                             to: ParserGCInfo,
                             failed: Boolean,
                             position: Long,
                             parsingInfo: ParsingInfo,
                             inParserTimeWindow: Boolean) {
            for (i in listeners.indices) {
                listeners[i].phaseChanged(sender, from, to, failed, position, parsingInfo, inParserTimeWindow)
            }
        }

        fun fireClose(listener: HeapListener, sender: Any, parsingInfo: ParsingInfo) = fireClose(listOf(listener), sender, parsingInfo)

        fun fireClose(listeners: List<HeapListener>, sender: Any, parsingInfo: ParsingInfo) {
            for (i in listeners.indices) {
                listeners[i].close(sender, parsingInfo)
            }
        }
    }
}

open class HeapAdapter : HeapListener {
    override fun phaseChanging(sender: Any, from: ParserGCInfo, to: ParserGCInfo, failed: Boolean, position: Long, parsingInfo: ParsingInfo, inParserTimeWindow: Boolean) {

    }

    override fun phaseChanged(sender: Any, from: ParserGCInfo, to: ParserGCInfo, failed: Boolean, position: Long, parsingInfo: ParsingInfo, inParserTimeWindow: Boolean) {

    }

    override fun close(sender: Any, parsingInfo: ParsingInfo) {

    }
}