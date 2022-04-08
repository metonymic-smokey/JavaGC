
package at.jku.anttracks.features

import at.jku.anttracks.heap.objects.ObjectInfo
import at.jku.anttracks.heap.symbols.Symbols

import java.util.concurrent.ConcurrentHashMap

class FeatureMapCache(val symbols: Symbols) {
    private val cache = ConcurrentHashMap<Int, IntArray>()

    fun match(obj: ObjectInfo): IntArray {
        var result: IntArray? = cache[obj.allocationSite.id]
        if (result == null) {
            result = symbols.features.match(symbols.sites.getById(obj.allocationSite.id))
            cache[obj.allocationSite.id] = result!!
        }
        return result
    }

}
