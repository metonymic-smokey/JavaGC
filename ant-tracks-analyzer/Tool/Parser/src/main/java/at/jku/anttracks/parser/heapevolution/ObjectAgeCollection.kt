package at.jku.anttracks.parser.heapevolution

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.nodes.MapGroupingNode
import at.jku.anttracks.classification.trees.MapClassificationTree
import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.ObjectStream
import at.jku.anttracks.heap.objects.ObjectInfo
import at.jku.anttracks.heap.space.SpaceInfo
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap

class ObjectAgeCollection {
    private val map: HashMap<ObjectInfo, Int2LongOpenHashMap> = hashMapOf()
    var objectCount: Int = 0
    var byteCount: Long = 0

    fun put(objectInfo: ObjectInfo, age: Int) {
        map.computeIfAbsent(objectInfo) { objInfo ->
            Int2LongOpenHashMap().also { it.defaultReturnValue(0) }
        }.also {
            it.addTo(age, 1)
        }

        objectCount++
        byteCount += objectInfo.size
    }

    fun putAll(other: ObjectAgeCollection) {
        other.map.forEach { otherEntry ->
            this.map.merge(otherEntry.key, otherEntry.value.clone()) { thisInnerMap, otherClonedInnerMap ->
                otherClonedInnerMap.forEach {
                    thisInnerMap.merge(it.key, it.value, Long::plus)
                }
                thisInnerMap
            }
        }

        objectCount += other.objectCount
        byteCount += other.byteCount
    }

    fun get(objectInfo: ObjectInfo) =
            map.get(objectInfo)?.values?.sum()

    fun get(objectInfo: ObjectInfo, age: Int) = map.get(objectInfo)?.get(age)

    fun omitAge() = map.mapValues { it.value.values.sum() }

    fun clear() {
        map.clear()
    }

    fun classify(heap: DetailedHeap, classifiers: ClassifierChain, filters: Array<Filter>, listener: ObjectStream.IterationListener? = null, addFilterNodeInTree: Boolean): MapClassificationTree {
        var counter = 0L
        val grouping = MapGroupingNode()

        map.forEach { outerEntry ->
            outerEntry.value.forEach { innerEntry ->
                grouping.classify(null,
                                  -1,
                                  outerEntry.key,
                                  SpaceInfo("Unknown space"),
                                  outerEntry.key.type,
                                  outerEntry.key.size,
                                  outerEntry.key.isArray,
                                  outerEntry.key.arrayLength,
                                  outerEntry.key.allocationSite,
                                  null,
                                  null,
                                  outerEntry.key.eventType,
                                  null,
                                  innerEntry.key,
                                  outerEntry.key.thread,
                                  heap.threadsByInternalName[outerEntry.key.thread]?.threadName ?: "unknown thread name",
                                  innerEntry.value,
                                  classifiers,
                                  filters,
                                  addFilterNodeInTree)

                counter += innerEntry.value
                // if (counter > 10_000) {
                //    listener?.objectsIterated(counter)
                //    counter = 0
                // }
            }
        }

        println(counter)
        println(grouping.objectCount)

        return MapClassificationTree(grouping, filters, classifiers)
    }

    companion object {
        fun merge(first: ObjectAgeCollection, second: ObjectAgeCollection) =
                ObjectAgeCollection().also {
                    it.putAll(first)
                    it.putAll(second)
                }
    }

}