package at.jku.anttracks.parser

import at.jku.anttracks.heap.GarbageCollectionCause
import at.jku.anttracks.heap.GarbageCollectionType

data class ParserGCInfo(val eventType: EventType,
                        val type: GarbageCollectionType,
                        val cause: GarbageCollectionCause,
                        val id: Short,
                        val time: Long,
                        val concurrent: Boolean = false)