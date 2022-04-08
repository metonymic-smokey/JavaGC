
package at.jku.anttracks.heap.io

import at.jku.anttracks.heap.DetailedHeap
import at.jku.anttracks.heap.GarbageCollectionType
import at.jku.anttracks.heap.labs.AddressHO
import at.jku.anttracks.heap.labs.Lab
import at.jku.anttracks.heap.labs.MultiObjectLab
import at.jku.anttracks.heap.labs.SingleObjectLab
import at.jku.anttracks.heap.objects.ObjectInfo
import at.jku.anttracks.heap.objects.ObjectInfoCache
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.heap.space.Space
import at.jku.anttracks.heap.space.SpaceMode
import at.jku.anttracks.heap.space.SpaceType
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.parser.EventType
import at.jku.anttracks.parser.ParserGCInfo
import at.jku.anttracks.parser.ParsingInfo
import at.jku.anttracks.parser.TraceParser
import at.jku.anttracks.parser.heap.ThreadInfo
import at.jku.anttracks.parser.io.BaseFile
import at.jku.anttracks.util.Consts.HEAP_FILES_MAGIC_PREFIX
import at.jku.anttracks.util.Consts.UNDEFINED_ADDR
import at.jku.anttracks.util.TraceException
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class HeapReader(val path: String,
                 val fileName: Int,
                 val symbols: Symbols) : AutoCloseable {

    private val inputStream = DataInputStream(BufferedInputStream(BaseFile.openR(path + File.separator + fileName)))

    @Throws(IOException::class)
    fun read(parsingInfo: ParsingInfo): DetailedHeap {
        if (inputStream.readInt() != HEAP_FILES_MAGIC_PREFIX) {
            throw IllegalArgumentException("Expected magic prefix")
        }
        if (inputStream.readInt() != VERSION) {
            throw IllegalArgumentException("Unsupported version")
        }

        val gcInfo = ParserGCInfo(
                EventType.parse(inputStream.readInt()),
                GarbageCollectionType.parse(inputStream.readInt()),
                symbols.causes.get(inputStream.readInt())!!,
                inputStream.readShort(),
                inputStream.readLong(),
                inputStream.readBoolean())
        // CopyOnWriteArrayList<Long> ageRanges = readAgeRanges();

        val cache = ObjectInfoCache()
        val prototypes = readPrototypes(cache)

        val spaces = arrayOfNulls<Space>(inputStream.readInt())
        for (i in spaces.indices) {
            spaces[i] = readSpace(prototypes)
            spaces[i]?.setId(i.toShort())
        }

        val rootPtrs = Long2ObjectOpenHashMap<MutableList<RootPtr>>()
        val noOfRoots: Int
        if (symbols.expectPointers) {
            noOfRoots = inputStream.readInt()
            for (i in 0 until noOfRoots) {
                var root: RootPtr? = null
                try {
                    root = RootPtr.fromMetadata(inputStream)
                } catch (e: Exception) {
                    e.printStackTrace()
                }

                var existingRoots: MutableList<RootPtr>? = rootPtrs[root!!.addr]
                if (existingRoots != null) {
                    existingRoots.add(root)
                    existingRoots.sortBy { it.rootType }
                } else {
                    existingRoots = ArrayList()
                    existingRoots.add(root)
                    rootPtrs[root.addr] = existingRoots
                }
            }
        }

        val threads = ConcurrentHashMap<Long, ThreadInfo>()
        val noOfThreads = inputStream.readInt()
        for (i in 0 until noOfThreads) {
            val t = ThreadInfo(inputStream.readLong(), inputStream.readUTF(), inputStream.readUTF(), inputStream.readBoolean())
            threads[t.threadId] = t

            // rebuild callstack
            val callStackSize = inputStream.readInt()
            for (j in 0 until callStackSize) {
                t.addStackframe(inputStream.readInt(), inputStream.readInt())
            }
        }

        val readHeap = DetailedHeap(symbols, cache, spaces, gcInfo, rootPtrs, threads, parsingInfo)

        if (TraceParser.CONSISTENCY_CHECK) {
            readHeap.validate(symbols.isHeapFragmented)
        }
// heap.getOld().setAgeRanges(ageRanges);
        return readHeap
    }

    /*
     * private CopyOnWriteArrayList<Long> readAgeRanges() throws IOException { int size = in.readInt(); CopyOnWriteArrayList<Long> ageRanges
     * = new CopyOnWriteArrayList<Long>(); for (int i = 0; i < size; i++) { ageRanges.add(in.readLong()); }
     *
     * return ageRanges; }
     */

    @Throws(IOException::class)
    private fun readPrototypes(cache: ObjectInfoCache): Map<Int, ObjectInfo> {
        val count = inputStream.readInt()
        val prototypes = HashMap<Int, ObjectInfo>()

        val key = ObjectInfo()

        for (i in 0 until count) {
            val id = inputStream.readInt()
            val prototype = readPrototype(cache, key)
            prototypes[id] = prototype
        }
        return prototypes
    }

    @Throws(IOException::class)
    private fun readPrototype(cache: ObjectInfoCache,
                              key: ObjectInfo): ObjectInfo {
        val threadName = inputStream.readUTF()
        val allocatedTypeId = inputStream.readInt()
        val allocationSiteId = inputStream.readInt()

        val eventType = EventType.parse(inputStream.readInt())
        val prototype: ObjectInfo

        var classSize = -1
        var arrayLength = -1
        when (inputStream.readInt()) {
            0 -> classSize = inputStream.readInt()
            1 -> {
            }
            2 -> arrayLength = inputStream.readInt()
            else -> throw IOException("Unknown object prototype")
        }
        prototype = cache.get(threadName, symbols.sites.getById(allocationSiteId), symbols.types.getById(allocatedTypeId), eventType, classSize, arrayLength, key, symbols)
        return prototype
    }

    @Throws(IOException::class)
    private fun readSpace(prototypes: Map<Int, ObjectInfo>): Space? {
        if (inputStream.readByte().toInt() == 0) {
            return null
        }

        val name = inputStream.readUTF()
        val space = Space(name)
        val addr = inputStream.readLong()
        val length = inputStream.readLong()
        space.resetAddressAndLength(addr, length)
        val spaceType = inputStream.readInt()
        if (spaceType >= 0) {
            space.type = SpaceType.values()[spaceType]
        }
        val spaceMode = inputStream.readInt()
        if (spaceMode >= 0) {
            space.mode = SpaceMode.values()[spaceMode]
        }
        val count = inputStream.readInt()
        for (i in 0 until count) {
            val lab = readLab(prototypes)
            try {
                space.assignLab(lab)
            } catch (e: TraceException) {
                throw IOException(e)
            }

        }
        return space
    }

    @Throws(IOException::class)
    private fun readLab(prototypes: Map<Int, ObjectInfo>): Lab {
        val thread = inputStream.readUTF()
        val kind = Lab.Kind.byId(inputStream.readInt());
        val addr = inputStream.readLong()
        val capacity = inputStream.readInt()
        val objectCount = inputStream.readInt()
        if (objectCount == 1) {
            val id = inputStream.readInt()
            val info: ObjectInfo = prototypes[id]!!
            val obj = AddressHO.createObject(info, (-1).toShort(), symbols, false) // TODO Born
            val lab = SingleObjectLab(thread, kind, addr, obj) // TODO: Born
            val pointerCount = inputStream.readInt()
            val ptrs = LongArray(pointerCount)
            for (ptrIdx in 0 until pointerCount) {
                ptrs[ptrIdx] = inputStream.readLong()
            }
            try {
                obj.fillPointers(ptrs)
            } catch (e: TraceException) {
                throw IOException(e)
            }
            return lab
        } else {
            val lab = MultiObjectLab(thread, kind, addr, capacity)
            for (i in 0 until objectCount) {
                val id = inputStream.readInt()
                val info = prototypes[id]!!
                val obj = AddressHO.createObject(info, (-1).toShort(), symbols, false) // TODO Born
                // add feature to obj?
                try {
                    val assignedAddr = lab.tryAllocate(UNDEFINED_ADDR, obj)
                    assert(assignedAddr != Lab.OBJECT_NOT_ASSIGNED.toLong())
                    val pointerCount = inputStream.readInt()
                    val ptrs = LongArray(pointerCount)
                    for (ptrIdx in 0 until pointerCount) {
                        ptrs[ptrIdx] = inputStream.readLong()
                    }
                    obj.fillPointers(ptrs)
                } catch (e: TraceException) {
                    throw IOException(e)
                }

            }
            return lab
        }
    }

    @Throws(IOException::class)
    override fun close() {
        inputStream.close()
    }

    companion object {
        const val VERSION = 1
    }
}
