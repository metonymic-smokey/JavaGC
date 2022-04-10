
package at.jku.anttracks.heap.io

import at.jku.anttracks.heap.statistics.*
import at.jku.anttracks.parser.io.BaseFile
import at.jku.anttracks.util.Consts.HEAP_FILES_MAGIC_PREFIX
import at.jku.anttracks.util.Consts.STATISTICS_META_FILE
import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException

class StatisticsWriter @Throws(IOException::class)
constructor(path: String) : AutoCloseable {

    private val out: DataOutputStream

    init {
        val statisticsFilePath = path + File.separator + STATISTICS_META_FILE
        out = DataOutputStream(BufferedOutputStream(BaseFile.openW(statisticsFilePath)))
        out.writeInt(HEAP_FILES_MAGIC_PREFIX)
    }

    @Throws(IOException::class)
    fun flush() {
        out.flush()
    }

    @Throws(IOException::class)
    override fun close() {
        out.close()
    }

    fun write(statistics: Collection<Statistics>) = statistics.forEach { write(it) }

    @Throws(IOException::class)
    fun write(heapStatistics: Statistics) {
        out.writeShort(heapStatistics.info.id.toInt())
        out.writeLong(heapStatistics.info.time)
        out.writeInt(heapStatistics.info.meta.id)
        out.writeInt(heapStatistics.info.type.id)
        out.writeInt(heapStatistics.info.cause.id)
        out.writeBoolean(heapStatistics.info.concurrent)
        out.writeBoolean(heapStatistics.info.failed)
        out.writeLong(heapStatistics.info.reachableBytes ?: -1)

        writeSpaceStatistics(heapStatistics.eden)
        writeSpaceStatistics(heapStatistics.survivor)
        writeSpaceStatistics(heapStatistics.old)
    }

    @Throws(IOException::class)
    private fun writeSpaceStatistics(space: SpaceStatistics) {
        writeAllocators(space.allocators)
        writeMemoryConsumption(space.memoryConsumption)
        writeObjectTypes(space.objectTypes)
        writeFeatureAllocations(space.featureConsumptions)
    }

    @Throws(IOException::class)
    private fun writeAllocators(allocator: Allocators) {
        out.writeLong(allocator.vm)
        out.writeLong(allocator.ir)
        out.writeLong(allocator.c1)
        out.writeLong(allocator.c2)
    }

    @Throws(IOException::class)
    private fun writeMemoryConsumption(memoryConsumption: MemoryConsumption) {
        out.writeLong(memoryConsumption.objects)
        out.writeLong(memoryConsumption.bytes)
    }

    @Throws(IOException::class)
    private fun writeObjectTypes(objectTypes: ObjectTypes) {
        out.writeLong(objectTypes.instances)
        out.writeLong(objectTypes.smallArrays)
        out.writeLong(objectTypes.bigArrays)
    }

    @Throws(IOException::class)
    private fun writeFeatureAllocations(featureAllocation: Array<MemoryConsumption>?) {
        if (featureAllocation == null) {
            out.writeBoolean(false)
        } else {
            out.writeBoolean(true)
            out.writeInt(featureAllocation.size)
            for (memoryConsumption in featureAllocation) {
                out.writeLong(memoryConsumption.objects)
                out.writeLong(memoryConsumption.bytes)
            }
        }
    }

}
