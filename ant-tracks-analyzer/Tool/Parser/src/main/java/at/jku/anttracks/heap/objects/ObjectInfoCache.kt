
package at.jku.anttracks.heap.objects

import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.heap.symbols.AllocationSite
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.parser.EventType
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class ObjectInfoCache(objects: Collection<ObjectInfo>? = null, symbols: Symbols? = null) {
    private val currentId = AtomicInteger()

    // Use very low load factors to ensure fast access on the cost of memory
    private val loadFactor = 0.2f
    private val initSize = (1_000 * loadFactor).toInt()
    private var idCache = ConcurrentHashMap<Int, ObjectInfo>(initSize, loadFactor)
    private var cache = ConcurrentHashMap<ObjectInfo, ObjectInfo>(initSize, loadFactor)
    private var cacheMisses = 0

    val size: Int
        get() = cache.size
    val infos: ArrayList<ObjectInfo>
        get() {
            val result = ArrayList<ObjectInfo>()
            result.addAll(cache.values)
            return result
        }

    init {
        val prototype = ObjectInfo()

        if (objects != null && symbols != null) {
            objects.forEach {
                get(it, prototype, symbols)
            }
        }
    }

    operator fun get(thread: String,
                     allocationSite: AllocationSite,
                     type: AllocatedType,
                     eventType: EventType,
                     classSize: Int,
                     arrayLength: Int,
                     prototype: ObjectInfo,
                     symbols: Symbols): ObjectInfo {
        prototype.thread = thread
        prototype.allocationSite = allocationSite
        prototype.type = type
        prototype.eventType = eventType
        prototype.arrayLength = arrayLength

        val cached = cache.get(prototype)
        if (cached != null) return cached

        synchronized(idCache) {
            val c = cache.get(prototype)
            if (c != null) return c

            cacheMisses++
            // println("Cache miss: $cacheMisses | ${cache.size}")

            val newlyCached: ObjectInfo
            if (arrayLength >= 0) {
                newlyCached = ObjectInfo.newArrayInfo(thread, eventType, allocationSite, type, arrayLength, symbols.heapWordSize)
            } else {
                if (classSize > 0) {
                    newlyCached = ObjectInfo.newMirrorInfo(thread, eventType, allocationSite, type, classSize, symbols.heapWordSize)
                } else {
                    newlyCached = ObjectInfo.newInstanceInfo(thread, eventType, allocationSite, type, symbols.heapWordSize)
                }
            }
            newlyCached.id = currentId.getAndIncrement()

            cache.putIfAbsent(newlyCached, newlyCached)
            idCache.put(newlyCached.id, newlyCached)
            return newlyCached
        }
    }

    operator fun get(info: ObjectInfo,
                     prototype: ObjectInfo,
                     symbols: Symbols): ObjectInfo {
        return get(info.thread,
                   info.allocationSite,
                   info.type,
                   info.eventType,
                   if (info.isMirror) info.size else -1,
                   info.arrayLength,
                   prototype,
                   symbols)
    }

    operator fun get(id: Int): ObjectInfo {
        return idCache[id]!!
    }

    fun clear() {
        idCache.clear()
        cache.clear()
    }

    fun clone(): ObjectInfoCache {
        val dolly = ObjectInfoCache()
        dolly.currentId.set(this.currentId.get())
        dolly.idCache.putAll(this.idCache)
        dolly.cache.putAll(this.cache)

        return dolly
    }
}
