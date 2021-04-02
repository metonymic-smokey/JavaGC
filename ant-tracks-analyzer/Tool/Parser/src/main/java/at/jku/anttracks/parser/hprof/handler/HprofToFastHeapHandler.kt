/*
 * Copyright 2014 Edward Aftandilian. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package at.jku.anttracks.parser.hprof.handler

import at.jku.anttracks.heap.objects.ObjectInfo
import at.jku.anttracks.heap.objects.ObjectInfoCache
import at.jku.anttracks.heap.roots.*
import at.jku.anttracks.heap.roots.RootPtr
import at.jku.anttracks.heap.symbols.AllocatedType
import at.jku.anttracks.heap.symbols.AllocationSite
import at.jku.anttracks.heap.symbols.Symbols
import at.jku.anttracks.heap.symbols.SymbolsFileException
import at.jku.anttracks.parser.EventType
import at.jku.anttracks.parser.heap.ThreadInfo
import at.jku.anttracks.parser.hprof.datastructures.*
import at.jku.anttracks.parser.hprof.heapObjects.*
import at.jku.anttracks.util.SignatureConverter
import java.util.*
import kotlin.system.measureTimeMillis

/**
 * Prints details for each record encountered.
 */
class HprofToFastHeapHandler : CountingRecordHandler() {

    var time: Long = 0
        private set
    lateinit var addr: LongArray
        private set
    lateinit var toPtrs: Array<IntArray?>
        private set
    lateinit var frmPtrs: Array<IntArray?>
        private set
    lateinit var classKeys: LongArray
        private set
    private lateinit var arrayLengths: IntArray
        private set
    val objectInfoCache = ObjectInfoCache()
    lateinit var objectInfos: Array<ObjectInfo>
        private set
    val threads = mutableListOf<Thread>()
    lateinit var threadInfos: Array<ThreadInfo>
    // TODO: Check heapWordSize if it is 4 or 8 depending on compressedOops
    val symbols = Symbols("fake", IntArray(0), false, 4, true, "fake", null, null, false)

    private val stackTraces = mutableMapOf<Int, StackTrace>()
    private val stackFrames = mutableMapOf<Long, StackFrame>()

    private val objects = ArrayList<HeapObject>()
    val gcRoots = HashMap<Long, MutableList<RootPtr>>()
    private val primitives = LongArray(12)

    private val stringMap = HashMap<Long, String>()
    val classMap = HashMap<Long, TypeInfo>()

    private var postProcessingTime = 0L

    /* handler for file header */

    override fun header(format: String, idSize: Int, time: Long) {
        super.header(format, idSize, time)
        this.time = time
    }

    /* Handlers for top-level records */

    override fun stringInUTF8(id: Long, data: String) {
        super.stringInUTF8(id, data)
        // store string for later lookup
        stringMap[id] = data
    }

    override fun loadClass(classSerialNum: Int, classObjId: Long, stackTraceSerialNum: Int, classNameStringId: Long) {
        super.loadClass(classSerialNum, classObjId, stackTraceSerialNum, classNameStringId)
        val name = stringMap[classNameStringId] ?: error("string not found in stringmap")
        val typeInfo = TypeInfo(name)
        classMap[classObjId] = typeInfo
        savePrimitives(name, classObjId)
    }

    /* Handlers for root pointers */

    override fun rootUnknown(objId: Long) {
        super.rootUnknown(objId)
        val r = OtherRoot(objId, RootPtr.RootType.DEBUG_ROOT)
        if (gcRoots[objId] == null) {
            gcRoots[objId] = mutableListOf()
        }
        gcRoots[objId]!!.add(r)
    }

    override fun rootJNIGlobal(objId: Long, JNIGlobalRefId: Long) {
        super.rootJNIGlobal(objId, JNIGlobalRefId)
        val r = JNIGlobalRoot(objId, false)
        if (gcRoots[objId] == null) {
            gcRoots[objId] = mutableListOf()
        }
        gcRoots[objId]!!.add(r)
    }

    override fun rootJNILocal(objId: Long, threadSerialNum: Int, frameNum: Int) {
        super.rootJNILocal(objId, threadSerialNum, frameNum)
        val r = JNILocalRoot(objId, threadSerialNum.toLong())
        if (gcRoots[objId] == null) {
            gcRoots[objId] = mutableListOf()
        }
        gcRoots[objId]!!.add(r)
    }

    override fun rootJavaFrame(objId: Long, threadSerialNum: Int, frameNum: Int) {
        super.rootJavaFrame(objId, threadSerialNum, frameNum)
        val r = LocalVariableRoot(objId, threadSerialNum.toLong(), -1, -1, -1)
        if (gcRoots[objId] == null) {
            gcRoots[objId] = mutableListOf()
        }
        gcRoots[objId]!!.add(r)
    }

    override fun rootNativeStack(objId: Long, threadSerialNum: Int) {
        super.rootNativeStack(objId, threadSerialNum)
        val r = VMInternalThreadDataRoot(objId, threadSerialNum.toLong())
        if (gcRoots[objId] == null) {
            gcRoots[objId] = mutableListOf()
        }
        gcRoots[objId]!!.add(r)
    }

    override fun rootStickyClass(objId: Long) {
        super.rootStickyClass(objId)
        val r = ClassRoot(objId, -1)
        if (gcRoots[objId] == null) {
            gcRoots[objId] = mutableListOf()
        }
        gcRoots[objId]!!.add(r)
    }

    override fun rootThreadBlock(objId: Long, threadSerialNum: Int) {
        super.rootThreadBlock(objId, threadSerialNum)
        val r = VMInternalThreadDataRoot(objId, threadSerialNum.toLong())
        if (gcRoots[objId] == null) {
            gcRoots[objId] = mutableListOf()
        }
        gcRoots[objId]!!.add(r)
    }

    override fun rootMonitorUsed(objId: Long) {
        super.rootMonitorUsed(objId)
        val r = OtherRoot(objId, RootPtr.RootType.BUSY_MONITOR_ROOT)
        if (gcRoots[objId] == null) {
            gcRoots[objId] = mutableListOf()
        }
        gcRoots[objId]!!.add(r)
    }

    override fun rootThreadObj(objId: Long, threadSerialNum: Int, stackTraceSerialNum: Int) {
        super.rootThreadObj(objId, threadSerialNum, stackTraceSerialNum)
        val r = VMInternalThreadDataRoot(objId, threadSerialNum.toLong())
        if (gcRoots[objId] == null) {
            gcRoots[objId] = mutableListOf()
        }
        gcRoots[objId]!!.add(r)
    }

    /* Handlers for heap dump records */

    override fun classDump(classObjId: Long, stackTraceSerialNum: Int, superClassObjId: Long,
                           classLoaderObjId: Long, signersObjId: Long, protectionDomainObjId: Long, reserved1: Long,
                           reserved2: Long, instanceSize: Int, constants: Array<Constant>, statics: Array<Static>,
                           instanceFields: Array<InstanceField>) {
        super.classDump(classObjId,
                        stackTraceSerialNum,
                        superClassObjId,
                        classLoaderObjId,
                        signersObjId,
                        protectionDomainObjId,
                        reserved1,
                        reserved2,
                        instanceSize,
                        constants,
                        statics,
                        instanceFields)
        val typeInfo = classMap[classObjId] ?: error("class not found in classmap")
        typeInfo.size = instanceSize + 16
        objects.add(ClassDump(classObjId, stackTraceSerialNum, superClassObjId, constants, statics, instanceFields))
    }

    /**
     * @param objId               id of that instance
     * @param stackTraceSerialNum serialNum of that stack trace
     * @param classObjId          objId of the class of the instance
     * @param instanceFieldValues the values of the fields, if type is obj the value is a pointer
     */
    override fun instanceDump(objId: Long, stackTraceSerialNum: Int, classObjId: Long, instanceFieldValues: Array<Value<*>>) {
        super.instanceDump(objId, stackTraceSerialNum, classObjId, instanceFieldValues)
        objects.add(InstanceObject(objId, stackTraceSerialNum, classObjId, instanceFieldValues))
    }

    /**
     * @param objId               id of that array
     * @param stackTraceSerialNum serialNum of that stack trace
     * @param elemClassObjId      objId of the element class
     * @param elems               pointer to all the elements in the array
     */
    override fun objArrayDump(objId: Long, stackTraceSerialNum: Int, elemClassObjId: Long, elems: LongArray) {
        super.objArrayDump(objId, stackTraceSerialNum, elemClassObjId, elems)
        objects.add(ObjectArray(objId, stackTraceSerialNum, elemClassObjId, elems))
    }

    /**
     * @param objId               id of that array
     * @param stackTraceSerialNum serialNum of that stack trace
     * @param elemType       code of the primitive classKeys
     * @param elems               array of the elements in that primitive classKeys
     */
    override fun primArrayDump(objId: Long, stackTraceSerialNum: Int, elemType: Byte, elems: Array<Value<*>>) {
        super.primArrayDump(objId, stackTraceSerialNum, elemType, elems)
        objects.add(PrimArray(objId, stackTraceSerialNum, elemType, elems))
    }

    override fun stackTrace(stackTraceSerialNum: Int, threadSerialNum: Int, numFrames: Int, stackFrameIds: LongArray) {
        super.stackTrace(stackTraceSerialNum, threadSerialNum, numFrames, stackFrameIds)
        stackTraces[stackTraceSerialNum] = StackTrace(stackTraceSerialNum, threadSerialNum, numFrames, stackFrameIds)
    }

    override fun stackFrame(stackFrameId: Long, methodNameStringId: Long, methodSigStringId: Long, sourceFileNameStringId: Long, classSerialNum: Int, location: Int) {
        super.stackFrame(stackFrameId, methodNameStringId, methodSigStringId, sourceFileNameStringId, classSerialNum, location)
        stackFrames[stackFrameId] = StackFrame(stackFrameId, methodNameStringId, methodSigStringId, sourceFileNameStringId, classSerialNum, location)
    }

    override fun startThread(threadSerialNum: Int, threadObjectId: Long, stackTraceSerialNum: Int, threadNameStringId: Long, threadGroupNameId: Long, threadParentGroupNameId: Long) {
        super.startThread(threadSerialNum, threadObjectId, stackTraceSerialNum, threadNameStringId, threadGroupNameId, threadParentGroupNameId)
        threads += Thread(threadSerialNum, threadObjectId, stackTraceSerialNum, threadNameStringId, threadGroupNameId, threadParentGroupNameId)
    }

    override fun finished() {
        super.finished()
        postProcessingTime = measureTimeMillis {
            objects.sort()
            val size = objects.size
            addr = LongArray(size)
            toPtrs = arrayOfNulls(size)
            frmPtrs = arrayOfNulls(size)
            val fromPointers = arrayOfNulls<Pointer>(size)
            classKeys = LongArray(size)
            arrayLengths = IntArray(size)

            for (i in 0 until size) {
                val obj = objects[i]
                addr[i] = i.toLong()
                arrayLengths[i] = -1

                val rootPointerList = gcRoots[obj.objId]
                if (rootPointerList != null) {
                    gcRoots.remove(obj.objId)
                    gcRoots[i.toLong()] = rootPointerList
                    rootPointerList.forEach { rp -> rp.addr = i.toLong() }
                }

                when (obj) {
                    is PrimArray -> {
                        classKeys[i] = primitives[obj.getClassObjId().toInt()]
                        toPtrs[i] = IntArray(0)
                        arrayLengths[i] = obj.elements.size
                    }
                    is ClassDump -> {
                        if (primitives[0] == 0L) {
                            println(classMap[obj.getObjId()]!!.externalClassName)
                        } //error case
                        classKeys[i] = primitives[0]
                        val pointers = ArrayList<Int>()

                        val fields = obj.statics
                        for (field in fields) {
                            if (field.value.type == Type.OBJ) {
                                val index = binarySearch(0, size - 1, field.value.value as Long)
                                if (index >= 0) {
                                    pointers.add(index)
                                    val next = fromPointers[index]
                                    fromPointers[index] = Pointer(i, next)
                                } else if (field.value.value == 0) {
                                    pointers.add(index)
                                }
                            }
                        }
                        toPtrs[i] = IntArray(pointers.size)
                        for (k in pointers.indices) {
                            toPtrs[i]!![k] = pointers[k]
                        }
                    }
                    is ObjectArray -> {
                        classKeys[i] = obj.getClassObjId()

                        val pointer = obj.arrayPointers
                        arrayLengths[i] = pointer.size
                        val newPointer = IntArray(pointer.size)
                        for (k in pointer.indices) {
                            val index = binarySearch(0, size - 1, pointer[k])
                            if (index >= 0) {
                                newPointer[k] = index
                                val next = fromPointers[index]
                                fromPointers[index] = Pointer(i, next)
                            } else if (pointer[k] == 0L) {
                                newPointer[k] = index
                            }
                        }
                        toPtrs[i] = newPointer
                    }
                    is InstanceObject -> {
                        classKeys[i] = obj.getClassObjId()

                        val pointers = ArrayList<Int>()
                        val fields = obj.objectFields
                        for (field in fields) {
                            if (field.type == Type.OBJ) {
                                val index = binarySearch(0, size - 1, field.value as Long)
                                if (index >= 0) {
                                    pointers.add(index)
                                    val next = fromPointers[index]
                                    fromPointers[index] = Pointer(i, next)
                                } else if (field.value == 0) {
                                    pointers.add(index)
                                }
                            }
                        }
                        toPtrs[i] = IntArray(pointers.size)
                        for (k in pointers.indices) {
                            toPtrs[i]!![k] = pointers[k]
                        }
                    }
                }
            }
            for (i in 0 until size) {
                if (fromPointers[i] == null) {
                    frmPtrs[i] = IntArray(0)
                } else {
                    val pointers = fromPointers[i]!!.toArrayList()
                    frmPtrs[i] = IntArray(pointers.size)
                    for (k in pointers.indices) {
                        frmPtrs[i]!![k] = pointers[k]
                    }
                }
            }
        }

        var typeid = 1
        for (ti in classMap.values) {
            ti.internalTypeName = SignatureConverter.convertExternalNameToInternal(ti.externalClassName)
            ti.id = typeid
            val type = AllocatedType(typeid, 0, ti.internalTypeName, ti.size)
            try {
                symbols.types.add(typeid, type)
            } catch (e: SymbolsFileException) {
                e.printStackTrace()
            }

            typeid++
        }

        val prototype = ObjectInfo()
        objectInfos = classKeys
                .mapIndexed { i, classKey ->
                    objectInfoCache["unknown thread",
                            symbols.sites.getById(AllocationSite.ALLOCATION_SITE_IDENTIFIER_UNKNOWN),
                            symbols.types.getById(classMap[classKey]!!.id),
                            EventType.NOP,
                            -1,
                            arrayLengths[i],
                            prototype,
                            symbols]
                }
                .toTypedArray()

        threadInfos = threads.map { t -> ThreadInfo(t.threadSerialNum.toLong(), stringMap[t.threadNameStringId], stringMap[t.threadNameStringId], true) }.toTypedArray()

        gcRoots.values.forEach { rpList -> rpList.forEach { rp -> rp.resolve(this) } }

        symbols.types.complete()
        symbols.sites.complete()

        print()
    }

    override fun print() {
        super.print()
        println("### postProcessingTime $postProcessingTime")
        println("### objects ${addr.size}")
        println("### pointers ${toPtrs.map { it?.size ?: 0 }.sum()}")
        println("### rootpointedobjects ${gcRoots.size}")
    }

    //saves the classObjId for each primitive type (including java.lang.Class) so it can be found faster
    // works with external and internal names
    private fun savePrimitives(name: String, classObjId: Long) {
        when (name) {
            "java/lang/Class", "java.lang.Class" -> primitives[0] = classObjId
            "[Z", "boolean[]" -> primitives[4] = classObjId
            "[C", "char[]" -> primitives[5] = classObjId
            "[F", "float[]" -> primitives[6] = classObjId
            "[D", "double[]" -> primitives[7] = classObjId
            "[B", "byte[]" -> primitives[8] = classObjId
            "[S", "short[]" -> primitives[9] = classObjId
            "[I", "int[]" -> primitives[10] = classObjId
            "[J", "long[]" -> primitives[11] = classObjId
            else -> {
            }
        }
    }

    /**
     * Searches through the objects to find on with a certain id
     *
     * @param l      left end
     * @param r      right end
     * @param search object to search
     * @return the index of the object, or -1 if not found
     */
    private fun binarySearch(l: Int, r: Int, search: Long): Int {
        if (r >= l) {
            val mid = l + (r - l) / 2
            val temp = objects[mid].objId
            if (temp == search) {
                return mid
            }
            return if (temp > search) {
                binarySearch(l, mid - 1, search)
            } else {
                binarySearch(mid + 1, r, search)
            }
        }
        return -1
    }

    private class Pointer(val value: Int, val next: Pointer?) {
        var arrayList: ArrayList<Int>? = null

        fun toArrayList(): ArrayList<Int> {
            if (arrayList == null) {
                arrayList = ArrayList()
            }
            arrayList!!.add(value)
            var temp: Pointer? = next
            while (temp != null) {
                arrayList!!.add(temp.value)
                temp = temp.next
            }
            arrayList!!.sort()
            return arrayList!!
        }
    }
}