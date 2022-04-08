package at.jku.anttracks.parser.heapevolution

import at.jku.anttracks.classification.Filter
import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.heap.ObjectVisitor
import at.jku.anttracks.heap.labs.AddressHO
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.heap.space.SpaceInfo
import at.jku.anttracks.heap.statistics.Statistics
import at.jku.anttracks.parser.EventType
import at.jku.anttracks.parser.ParserGCInfo
import java.util.*
import kotlin.system.measureTimeMillis

interface IHeapEvolutionData {
    val startStatistics: Statistics
    val endStatistics: Statistics
    val startTime: Long
        get() = startStatistics.info.time
    val endTime: Long
        get() = endStatistics.info.time
    val startHeap: IndexBasedHeap
        get() = initialIndexBasedHeap
    val endHeap
        get() = currentIndexBasedHeap
    val spanGCs: Int
        get() = endStatistics.info.id - startStatistics.info.id
    val spanMilliseconds: Long
        get() = endStatistics.info.time - startStatistics.info.time
    val absoluteHeapGrowth: Float
        get() = 1.0f * currentIndexBasedHeap.byteCount - initialIndexBasedHeap.byteCount

    val startGCId: Short

    val detailedHeap: DetailedHeap
    val initialIndexBasedHeap: IndexBasedHeap
    val currentIndexBasedHeap: IndexBasedHeap

    val diedStartIndices: BitSet
    val diedAgeCollection: ObjectAgeCollection
    val diedObjectCount: Int
    val diedByteCount: Long

    val tempAgeCollection: ObjectAgeCollection
    val tempObjectCount: Int
        get() = tempAgeCollection.objectCount
    val tempByteCount: Long
        get() = tempAgeCollection.byteCount

    val bornHeapObjectList: List<Pair<AddressHO, Long>>
    val bornHeapObjectMap: IdentityHashMap<AddressHO, StartEndAddress>
    val bornObjectCount: Int
    val bornByteCount: Long

    val bornPerGCInfo: Map<Short, ObjectCountAndBytes>
    val bornEndIndexMap: Map<Int, StartEndAddress>

    val permHeapObjectMap: IdentityHashMap<AddressHO, StartEndAddress>
    val permObjectCount: Int
    val permByteCount: Int
    val permEndIndexMap: Map<Int, StartEndAddress>

    val gcInfos: MutableList<ParserGCInfo>
    val currentTime: Long
    val latestGCStartTime: Long
}

class HeapEvolutionData(override val startStatistics: Statistics, override val endStatistics: Statistics) : IHeapEvolutionData {
    override val startGCId = startStatistics.info.id

    override lateinit var detailedHeap: DetailedHeap
    override lateinit var initialIndexBasedHeap: IndexBasedHeap

    override val diedStartIndices = BitSet()
    override val diedAgeCollection = ObjectAgeCollection()
    override val diedObjectCount: Int
        get() = diedAgeCollection.objectCount
    override val diedByteCount: Long
        get() = diedAgeCollection.byteCount

    override val tempAgeCollection = ObjectAgeCollection()
    override val tempObjectCount: Int
        get() = tempAgeCollection.objectCount
    override val tempByteCount: Long
        get() = tempAgeCollection.byteCount

    // The "Long" in Pair<AdressHO, Long> is the object's current address, which is needed in finalize()
    private var gcInfoOfLatestBorn: ParserGCInfo? = null
    private var latestBorn: MutableList<Pair<AddressHO, Long>> = mutableListOf()
    override val bornHeapObjectList: List<Pair<AddressHO, Long>>
        get() {
            return if (gcInfoOfLatestBorn == gcInfos.last()) {
                latestBorn
            } else {
                val bornsBuildDuration = measureTimeMillis {
                    latestBorn = mutableListOf()

                    detailedHeap.toObjectStream().filter(object : Filter() {
                        override fun classify(): Boolean {
                            return !permHeapObjectMap.containsKey(`object`())
                        }
                    }).forEach(object : ObjectVisitor {
                        override fun visit(address: Long, obj: AddressHO, space: SpaceInfo, rootPtrs: List<RootPtr>?) {
                            latestBorn.add(obj to address)
                        }
                    }, ObjectVisitor.Settings.NO_INFOS)
                }
                gcInfoOfLatestBorn = gcInfos.last()
                println("born build duration $bornsBuildDuration")
                latestBorn
            }
        }
    override var bornHeapObjectMap: IdentityHashMap<AddressHO, StartEndAddress> = IdentityHashMap()
    override val bornObjectCount: Int
        get() = bornHeapObjectList.size
    override val bornByteCount: Long
        get() = bornHeapObjectList.map { it.first.size.toLong() }.sum()

    override val bornPerGCInfo: Map<Short, ObjectCountAndBytes>
        get() {
            val bornPerGCInfoX = mutableMapOf<Short, ObjectCountAndBytes>()
            bornHeapObjectList.forEach { ho ->
                var objectsBytes = bornPerGCInfoX[ho.first.bornAt]
                if (objectsBytes == null) {
                    objectsBytes = ObjectCountAndBytes()
                    bornPerGCInfoX[ho.first.bornAt] = objectsBytes
                }
                objectsBytes.objects++
                objectsBytes.bytes += ho.first.size
            }
            return bornPerGCInfoX
        }
    // Will be initialized in ::finalize
    override lateinit var bornEndIndexMap: Map<Int, StartEndAddress>

    override lateinit var permHeapObjectMap: IdentityHashMap<AddressHO, StartEndAddress>
    override val permObjectCount
        get() = permHeapObjectMap.count()
    override val permByteCount
        get() = permHeapObjectMap.keys.map { ho -> ho.size }.sum()
    override lateinit var permEndIndexMap: Map<Int, StartEndAddress>

    override val gcInfos: MutableList<ParserGCInfo> = mutableListOf()
    override val currentTime: Long
        get() = gcInfos.lastOrNull()?.time ?: -1
    override val latestGCStartTime: Long
        get() = gcInfos.filter { it.eventType == EventType.GC_START }.lastOrNull()?.time ?: -1

    private var gcInfoOfLatestIndexBasedHeap: ParserGCInfo? = null
    private lateinit var latestIndexBasedHeap: IndexBasedHeap
    override val currentIndexBasedHeap: IndexBasedHeap
        get() = if (gcInfos.size == 1) {
            // we only parsed one gc event so far, thus we're at the beginning of the time window
            initialIndexBasedHeap
        } else {
            if (gcInfoOfLatestIndexBasedHeap != gcInfos.last()) {
                latestIndexBasedHeap = detailedHeap.toIndexBasedHeap(null)
                gcInfoOfLatestIndexBasedHeap = gcInfos.last()
            }
            latestIndexBasedHeap
        }

    fun init(detailedHeap: DetailedHeap) {
        this.detailedHeap = detailedHeap
        this.initialIndexBasedHeap = detailedHeap.toIndexBasedHeap()
        this.permHeapObjectMap = IdentityHashMap<AddressHO, StartEndAddress>().also { permHeapObjectMap ->
            detailedHeap.toObjectStream().forEach(object : ObjectVisitor {
                override fun visit(address: Long, obj: AddressHO, space: SpaceInfo, rootPtrs: List<RootPtr>?) {
                    permHeapObjectMap[obj] = StartEndAddress(address, initialIndexBasedHeap.toIndex(address))
                }
            }, ObjectVisitor.Settings.NO_INFOS)
        }
        println("Heap Evolution Data initialized with ${permHeapObjectMap.size} PERM objects")
    }

    fun finalize() {
        if (!::bornEndIndexMap.isInitialized) {
            println("Current index-based heap object count: ${currentIndexBasedHeap.objectCount}")
            println("Detailed heap object count: ${detailedHeap.objectCount}")
            // Finalize born
            bornHeapObjectList.forEach {
                val currentAddress = if (it.first.tag >= 0) it.first.tag else it.second
                val bornStartEndAddress = StartEndAddress(-1, -1, currentAddress, currentIndexBasedHeap.toIndex(currentAddress))

                bornHeapObjectMap[it.first] = bornStartEndAddress
            }
            bornEndIndexMap = bornHeapObjectMap.values.associate { startEndAddress -> startEndAddress.endIndex to startEndAddress }
            println("BornEndIndexMap size: ${bornEndIndexMap.size}")

            // Finalize perm
            detailedHeap.toObjectStream().forEach(object : ObjectVisitor {
                override fun visit(address: Long, obj: AddressHO, space: SpaceInfo, rootPtrs: List<RootPtr>?) {
                    val permObj = permHeapObjectMap[obj]
                    if (permObj != null) {
                        permObj.endAddress = address
                        permObj.endIndex = currentIndexBasedHeap.toIndex(address)
                    }
                }
            }, ObjectVisitor.Settings.NO_INFOS)

            //permHeapObjectMap.filter { (obj, addrInfo) -> addrInfo.endIndex < 0 }.forEach { (obj, addrInfo) ->
            //    println("DEBUG: Bad boi - PERM object was not found in final heap state: obj $obj, addrInfo $addrInfo")
            //}

            permEndIndexMap = permHeapObjectMap.values.associateBy { startEndAddress -> startEndAddress.endIndex }
            println("PermEndIndexMap size: ${permEndIndexMap.size}")

            if (permHeapObjectMap.size + bornHeapObjectMap.size != detailedHeap.objectCount.toInt()) {
                println("DEBUG: Bad boi - PERM and BORN does not sum up to heap size")
            }
        }
    }

    fun isGCStart(time: Long): Boolean = gcInfos.firstOrNull { it.time == time }?.eventType?.equals(EventType.GC_START) ?: false
}

data class ObjectCountAndBytes(var objects: Int = 0, var bytes: Long = 0L)