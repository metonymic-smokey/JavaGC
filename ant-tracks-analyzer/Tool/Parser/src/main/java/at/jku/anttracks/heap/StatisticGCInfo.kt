
package at.jku.anttracks.heap

import at.jku.anttracks.parser.EventType

class StatisticGCInfo(val type: GarbageCollectionType,
                      val cause: GarbageCollectionCause,
                      val concurrent: Boolean,
                      val failed: Boolean,
                      val meta: EventType,
                      val id: Short,
                      val time: Long,
                      var reachableBytes: Long? = null) {

    init {
        assert(meta == EventType.GC_START || meta == EventType.GC_END) { "Statistics can only be written at GC_START, GC_END" }
    }

    fun matches(gcLookup: GarbageCollectionLookup): Boolean {
        return (gcLookup.cause == null || gcLookup.cause == cause) &&
                (gcLookup.event == null || gcLookup.event == meta) &&
                (gcLookup.type == null || gcLookup.type == type)
    }
}
