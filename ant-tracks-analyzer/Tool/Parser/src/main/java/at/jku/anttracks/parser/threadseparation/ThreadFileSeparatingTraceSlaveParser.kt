
package at.jku.anttracks.parser.threadseparation

import at.jku.anttracks.heap.symbols.AllocatedTypes.MIRROR_CLASS_NAME
import at.jku.anttracks.parser.*
import at.jku.anttracks.util.Consts
import java.util.concurrent.BlockingQueue

class ThreadFileSeparatingTraceSlaveParser
(id: Int,
 size: at.jku.anttracks.util.MutableLong,
 masterQueue: BlockingQueue<ThreadLocalHeap>,
 relAddrFactory: RelAddrFactory,
 symbols: at.jku.anttracks.heap.symbols.Symbols,
 test: Boolean,
 error: at.jku.anttracks.parser.ErrorHandler) : at.jku.anttracks.parser.TraceSlaveParser<Void?>(id, size, masterQueue, null, relAddrFactory, symbols, test, error, null, null) {

    // --------------------------------------------------------
    // ------------ Methods ------------------------------------
    // --------------------------------------------------------

    private infix fun ThreadLocalHeap.write(data: at.jku.anttracks.parser.EventType): ThreadLocalHeap {
        write(data.id)
        return this
    }

    private infix fun ThreadLocalHeap.write(data: Int): ThreadLocalHeap {
        fileDataOutputStream.writeInt(data)
        return this
    }

    private infix fun ThreadLocalHeap.write(data: Long): ThreadLocalHeap {
        fileDataOutputStream.writeLong(data)
        return this
    }

    private infix fun ThreadLocalHeap.write(data: String): ThreadLocalHeap {
        fileDataOutputStream.write(data.toByteArray())
        return this
    }

    private fun ThreadLocalHeap.flush() = fileDataOutputStream.flush()

    override fun cleanUp(threadLocalHeap: ThreadLocalHeap) {
        threadLocalHeap write at.jku.anttracks.parser.EventType.CLEANUP
        //threadLocalHeap.flush();
    }

    private fun parseGCTag(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val tagTextLength = nextWord
        val tagText = getString(tagTextLength)

        threadLocalHeap write at.jku.anttracks.parser.EventType.GC_TAG write tagTextLength write tagText
    }

    override fun parseSpaceRedefine(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val index = nextWord
        val startAddr = nextDoubleWord
        val size = nextDoubleWord

        threadLocalHeap write at.jku.anttracks.parser.EventType.SPACE_REDEFINE write index write startAddr write size
    }

    override fun parseSpaceDestroy(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val firstIndex = nextWord
        val nRegions = nextDoubleWord

        threadLocalHeap write at.jku.anttracks.parser.EventType.SPACE_DESTROY write firstIndex write nRegions
    }

    override fun parseSpaceRelease(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val index = nextWord

        threadLocalHeap write at.jku.anttracks.parser.EventType.SPACE_RELEASE write index
    }

    override fun parseSpaceAlloc(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val spaceTypeId = word shr 18 and 0xFF
        val spaceModeId = word shr 10 and 0xFF
        val index = nextWord

        threadLocalHeap write at.jku.anttracks.parser.EventType.SPACE_ALLOC write index write spaceModeId write spaceTypeId
    }

    override fun parseSpaceCreate(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val index = nextWord
        val startAddr = nextDoubleWord
        val size = nextDoubleWord

        threadLocalHeap write at.jku.anttracks.parser.EventType.SPACE_CREATE write index write startAddr write size
    }

    override fun parseGCInfo(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val index = word and 0x00FFFFFF
        val id = nextWord

        threadLocalHeap write at.jku.anttracks.parser.EventType.GC_INFO write index write id
    }

    override fun parseGCFailed(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val index = word and 0x00FFFFFF

        threadLocalHeap write at.jku.anttracks.parser.EventType.GC_FAILED write index
    }

    override fun parseGCStart(word: Int, start: Long, end: Long, threadLocalHeap: ThreadLocalHeap) {
        threadLocalHeap write at.jku.anttracks.parser.EventType.GC_START write word
        writeGCMeta(threadLocalHeap)
        threadLocalHeap write start write end
    }

    override fun parseGCEnd(word: Int, start: Long, end: Long, threadLocalHeap: ThreadLocalHeap) {
        threadLocalHeap write at.jku.anttracks.parser.EventType.GC_END write word
        writeGCMeta(threadLocalHeap)
        threadLocalHeap write start write end
    }

    private fun writeGCMeta(threadLocalHeap: ThreadLocalHeap) {
        val id = nextWord
        val time = nextDoubleWord
        val base = nextDoubleWord
        relAddrFactory.setBase(base)

        threadLocalHeap write at.jku.anttracks.parser.EventType.GC_START write id write time write base
    }

    override fun parseGCInterrupt(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val id = nextWord
        val address = nextDoubleWord

        threadLocalHeap write at.jku.anttracks.parser.EventType.GC_INTERRUPT write id write address
    }

    override fun parseGCContinue(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val id = nextWord
        val address = nextDoubleWord

        threadLocalHeap write at.jku.anttracks.parser.EventType.GC_CONTINUE write id write address
    }

    override fun parseGCMoveFast_WideOrNarrow_Ptr(type: at.jku.anttracks.parser.EventType, word: Int, toSpace: Int, threadLocalHeap: ThreadLocalHeap) {
        var fromAddr: Long = -1

        if (type == at.jku.anttracks.parser.EventType.GC_MOVE_FAST_NARROW) {
            val from = (RelAddrFactory.getDefinedAddrOnly(recoverValue(word.toLong(), 1, 3)) shr 2).toInt()
            fromAddr = relAddrFactory.create(from)
        } else if (type == at.jku.anttracks.parser.EventType.GC_MOVE_FAST_WIDE || type == at.jku.anttracks.parser.EventType.GC_MOVE_FAST_WIDE_PTR) {
            fromAddr = nextDoubleWord
        } else if (type == at.jku.anttracks.parser.EventType.GC_MOVE_FAST || type == at.jku.anttracks.parser.EventType.GC_MOVE_FAST_PTR) {
            fromAddr = relAddrFactory.create(nextWord)
        } else {
            assert(false)
            throw at.jku.anttracks.util.TraceException("internal error")
        }

        threadLocalHeap write type write fromAddr write toSpace
        if (type == at.jku.anttracks.parser.EventType.GC_MOVE_FAST_PTR || type == at.jku.anttracks.parser.EventType.GC_MOVE_FAST_WIDE_PTR) {
            writePointerWords(word, threadLocalHeap)
            threadLocalHeap write word
        }
    }

    override fun parseGCMoveSlow(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {
        val fromAddr = nextDoubleWord
        val toAddr = nextDoubleWord

        threadLocalHeap write type write fromAddr write toAddr

        if (type == at.jku.anttracks.parser.EventType.GC_MOVE_SLOW_PTR) {
            writePointerWords(word, threadLocalHeap)
            threadLocalHeap write word
        }
    }

    override fun parseGCKeepAlive(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {
        val addr = nextDoubleWord

        threadLocalHeap write type write addr
        if (type == at.jku.anttracks.parser.EventType.GC_KEEP_ALIVE_PTR) {
            writePointerWords(word, threadLocalHeap)
            threadLocalHeap write word
        }
    }

    override fun parseGCRootPtr(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {
        threadLocalHeap write type
        for (i in 0 until at.jku.anttracks.heap.roots.RootPtr.MAX_ROOTS_PER_EVENT) {
            var ptr = nextDoubleWord
            threadLocalHeap write ptr

            if (ptr == -1L) {    // not a nullptr (those are 0) but the convention for end of root block
                // no more roots in this block
                break
            } else if (ptr == 0L) {
                // NULLPTR
                ptr = at.jku.anttracks.parser.TraceSlaveParser.NULL_PTR.toLong()    // because we use -1 for NULLPTRs
            }

            val rootTypeId = nextWord
            threadLocalHeap write rootTypeId
            val rootType = at.jku.anttracks.heap.roots.RootPtr.RootType.values()[rootTypeId]
            when (rootType) {
                at.jku.anttracks.heap.roots.RootPtr.RootType.CLASS_LOADER_ROOT -> {
                    val loaderName = getString(nextWord)
                    // TODO String length?
                    threadLocalHeap write loaderName
                }

                at.jku.anttracks.heap.roots.RootPtr.RootType.CLASS_ROOT -> {
                    val classId = nextWord
                    threadLocalHeap write classId
                }

                at.jku.anttracks.heap.roots.RootPtr.RootType.STATIC_FIELD_ROOT -> {
                    val classId = nextWord
                    val offset = nextWord
                    threadLocalHeap write classId write offset
                }

                at.jku.anttracks.heap.roots.RootPtr.RootType.LOCAL_VARIABLE_ROOT -> {
                    val threadId = nextDoubleWord
                    val classId = nextWord
                    val methodId = nextWord
                    val slot = nextWord
                    threadLocalHeap write threadId write classId write methodId write slot
                }

                at.jku.anttracks.heap.roots.RootPtr.RootType.VM_INTERNAL_THREAD_DATA_ROOT -> {
                    val threadId = nextDoubleWord
                    threadLocalHeap write threadId
                }

                at.jku.anttracks.heap.roots.RootPtr.RootType.CODE_BLOB_ROOT -> {
                    val classId = nextWord
                    val methodId = nextWord
                    threadLocalHeap write classId write methodId
                }

                at.jku.anttracks.heap.roots.RootPtr.RootType.JNI_LOCAL_ROOT -> {
                    val threadId = nextDoubleWord
                    threadLocalHeap write threadId
                }

                at.jku.anttracks.heap.roots.RootPtr.RootType.JNI_GLOBAL_ROOT -> {
                    val weak = nextWord != 0
                    threadLocalHeap write (if (weak) 1 else 0)
                }

                // other roots
                at.jku.anttracks.heap.roots.RootPtr.RootType.CLASS_LOADER_INTERNAL_ROOT, at.jku.anttracks.heap.roots.RootPtr.RootType.UNIVERSE_ROOT, at.jku.anttracks.heap.roots.RootPtr.RootType.SYSTEM_DICTIONARY_ROOT, at.jku.anttracks.heap.roots.RootPtr.RootType.BUSY_MONITOR_ROOT, at.jku.anttracks.heap.roots.RootPtr.RootType.INTERNED_STRING, at.jku.anttracks.heap.roots.RootPtr.RootType.FLAT_PROFILER_ROOT, at.jku.anttracks.heap.roots.RootPtr.RootType.MANAGEMENT_ROOT, at.jku.anttracks.heap.roots.RootPtr.RootType.JVMTI_ROOT -> {

                }

                at.jku.anttracks.heap.roots.RootPtr.RootType.DEBUG_ROOT -> {
                    val vmCall = getString(nextWord)
                    // TODO String length?
                    threadLocalHeap write vmCall
                }

                else -> throw at.jku.anttracks.util.TraceException(rootType.toString() + " is not a valid root type id!")
            }
        }
    }

    override fun parseGCObjPtr(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {
        val addr = nextDoubleWord

        threadLocalHeap write type write addr write word

        writePointerWords(word, threadLocalHeap)
    }

    @Throws(at.jku.anttracks.util.TraceException::class)
    override fun parseGCMoveRegion(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap): List<at.jku.anttracks.heap.objects.ObjectInfo>? {
        val numOfObjects = recoverValue(word.toLong(), 1, 3)
        val fromAddr = nextDoubleWord
        val toAddr = nextDoubleWord

        threadLocalHeap write type write fromAddr write toAddr write numOfObjects
        return null
    }

    override fun parseThreadAlive(type: at.jku.anttracks.parser.EventType, header: Int, threadLocalHeap: ThreadLocalHeap) {
        val MAX_NAME_LENGTH = 12
        val id = nextDoubleWord
        val name = StringBuilder()
        end@ for (i in 0 until MAX_NAME_LENGTH) {
            val word = nextWord
            for (j in 0..3) {
                val c = (word shr (3 - j) * 8 and 0xFF).toChar()
                if (c == '\u0000') {
                    name.append(c)
                    break@end
                }
                name.append(c)
            }
        }

        val finalName = name.toString()

        // TODO String length?
        threadLocalHeap write at.jku.anttracks.parser.EventType.THREAD_ALIVE write header write id write finalName
    }

    @Throws(at.jku.anttracks.util.TraceException::class)
    override fun parseThreadDeath(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {
        val id = nextDoubleWord

        threadLocalHeap write type write id
    }

    override fun parseSyncObj(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {
        val allocationSiteId = recoverValue(word.toLong(), 1, 3)
        val allocatedTypeId = nextWord
        val allocatedType = symbols.types.getById(allocatedTypeId)

        val fromAddr = nextDoubleWord
        val toAddr = if (type == at.jku.anttracks.parser.EventType.SYNC_OBJ) nextDoubleWord else fromAddr
        val length: Int
        if (allocatedType.internalName.startsWith("[")) {
            length = nextWord
        } else {
            length = Consts.UNDEFINED_LENGTH
        }
        val size: Int
        // TODO: We reworked mirror classes, probably something has to be changed here!
        if (allocatedType.internalName == MIRROR_CLASS_NAME) {
            size = nextWord
        } else {
            size = 0
        }

        threadLocalHeap write type write allocationSiteId write allocatedTypeId write fromAddr write toAddr write length write size
    }

    override fun parseObjAllocFastCi(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {
        var allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_2)
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (allocationSiteId shr 15 and 1 != 0) {
            allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_3)
        }
        val allocationSite = symbols.sites.getById(allocationSiteId)!!
        val allocatedType = symbols.types.getById(allocationSite.allocatedTypeId)

        val isArray = allocatedType.internalName.startsWith("[")
        var arrayLength = Consts.UNDEFINED_LENGTH
        if (isArray) {
            arrayLength = recoverArrayLength(word)
        }

        threadLocalHeap write type write allocationSiteId write arrayLength
    }

    override fun parseObjAllocFastC2DeviantType(type: at.jku.anttracks.parser.EventType, header: Int, threadLocalHeap: ThreadLocalHeap) {
        var allocationSiteId = recoverValue(header.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_2)
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (allocationSiteId shr 15 and 1 != 0) {
            allocationSiteId = recoverValue(header.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_3)
        }
        val allocatedType = symbols.types.getById(nextWord)

        val isArray = allocatedType.internalName.startsWith("[")
        var arrayLength = Consts.UNDEFINED_LENGTH
        if (isArray) {
            arrayLength = recoverArrayLength(header)
        }

        threadLocalHeap write type write allocationSiteId write arrayLength
    }

    override fun parseObjAllocFastIr(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {
        var allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_2)
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (allocationSiteId shr 15 and 1 != 0) {
            allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_3)
        }

        threadLocalHeap write type write allocationSiteId
    }

    @Throws(at.jku.anttracks.util.TraceException::class)
    override fun parseObjAllocNormalCi(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {
        var allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_2)
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (allocationSiteId shr 15 and 1 != 0) {
            allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_3)
        }
        val allocationSite = symbols.sites.getById(allocationSiteId)
        val allocatedType = symbols.types.getById(allocationSite.allocatedTypeId)
        errorOnMirrorClass(allocatedType)

        val isArray = allocatedType.internalName.startsWith("[")
        val addr = nextDoubleWord
        var arrayLength = Consts.UNDEFINED_LENGTH
        if (isArray) {
            arrayLength = recoverArrayLength(word)
        }

        threadLocalHeap write type write allocationSiteId write addr write arrayLength
    }

    override fun parseObjAllocNormalIr(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {

        var allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_2)
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (allocationSiteId shr 15 and 1 != 0) {
            allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_3)
        }
        val allocationSite = symbols.sites.getById(allocationSiteId)
        val addr = nextDoubleWord

        threadLocalHeap write type write allocationSiteId write addr
    }

    @Throws(at.jku.anttracks.util.TraceException::class)
    override fun parseObjAllocSlowCiIr_Deviant(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {
        var allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_2)

        var bigAllocSite = false
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (allocationSiteId shr 15 and 1 != 0) {
            allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_3)
            bigAllocSite = true
        }

        val allocationSite = symbols.sites.getById(allocationSiteId)
        val addr = nextDoubleWord

        var arrayLength = 0
        if (!bigAllocSite) { // all arrays have a small allocSite ID
            arrayLength = recoverArrayLength(word)
        }

        var realAllocatedTypeId = -1
        if (type == at.jku.anttracks.parser.EventType.OBJ_ALLOC_SLOW_C1_DEVIANT_TYPE || type == at.jku.anttracks.parser.EventType.OBJ_ALLOC_SLOW_IR_DEVIANT_TYPE || type == at.jku.anttracks.parser.EventType.OBJ_ALLOC_SLOW_C2_DEVIANT_TYPE) {
            realAllocatedTypeId = nextWord
        }

        threadLocalHeap write type write allocationSiteId write addr write arrayLength write realAllocatedTypeId
    }

    override fun parseObjAllocSlow(type: at.jku.anttracks.parser.EventType, word: Int, threadLocalHeap: ThreadLocalHeap) {
        var allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_2)
        var bigAllocSite = false
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (allocationSiteId shr 15 and 1 != 0) {
            allocationSiteId = recoverValue(word.toLong(), at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_1, at.jku.anttracks.parser.TraceSlaveParser.ALLOCATION_SITE_INDEX_3)
            bigAllocSite = true
        }

        var allocationSite = symbols.sites.getById(allocationSiteId)

        val addr = nextDoubleWord

        var arrayLength = 0
        if (!bigAllocSite) { // all arrays have a small allocSite ID
            arrayLength = recoverArrayLength(word)
        }

        if (allocationSite.allocatedTypeId == at.jku.anttracks.heap.symbols.AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN) {
            val id = nextWord
            allocationSite = allocationSite.copy(id)
        }

        val allocatedType = symbols.types.getById(allocationSite.allocatedTypeId)

        var size = Consts.UNDEFINED_LENGTH

        if (allocatedType.internalName == at.jku.anttracks.heap.symbols.AllocatedTypes.MIRROR_CLASS_NAME) {
            size = nextWord
            allocatedType.size = size
            arrayLength = Consts.UNDEFINED_LENGTH
        }

        threadLocalHeap write type write allocationSiteId write addr write arrayLength write size
    }

    override fun parseTlabAlloc(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val size = nextDoubleWord.toInt()
        val addr = nextDoubleWord

        threadLocalHeap write at.jku.anttracks.parser.EventType.TLAB_ALLOC write addr write size
    }

    @Throws(at.jku.anttracks.util.TraceException::class)
    override fun parsePlabAlloc(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val size = nextDoubleWord.toInt()
        val addr = nextDoubleWord
        threadLocalHeap write at.jku.anttracks.parser.EventType.PLAB_ALLOC write addr write size
    }

    private fun writePointerWords(word: Int, threadLocalHeap: ThreadLocalHeap) {
        val ptrKinds = recoverValue(word.toLong(), 1, 3)
        var lastRef: Long = 0

        // return null if event contains no pointers
        var ptrs: LongArray? = if (ptrKinds == 0) null else LongArray(at.jku.anttracks.parser.heap.pointer.PtrEvent.MAX_PTRS_PER_EVENT)
        if (ptrs != null) {
            var i = 0
            var encodingEnd = false
            while (!encodingEnd && i < at.jku.anttracks.parser.heap.pointer.PtrEvent.MAX_PTRS_PER_EVENT) {
                val kind = ptrKinds shr (at.jku.anttracks.parser.heap.pointer.PtrEvent.MAX_PTRS_PER_EVENT - i - 1) * 2 and 0x3
                if (kind == at.jku.anttracks.parser.heap.pointer.PtrEvent.ENCODING_RELATIVE_PTR) {
                    threadLocalHeap write nextWord
                } else if (kind == at.jku.anttracks.parser.heap.pointer.PtrEvent.ENCODING_ABSOLUTE_PTR) {
                    threadLocalHeap write nextDoubleWord
                } else if (kind == at.jku.anttracks.parser.heap.pointer.PtrEvent.ENCODING_NULL_PTR) {

                } else if (kind == at.jku.anttracks.parser.heap.pointer.PtrEvent.ENCODING_END) {

                } else {
                    assert(false) { "Unknown pointer encoding type" }
                }
                i++
            }
        }
    }
}
