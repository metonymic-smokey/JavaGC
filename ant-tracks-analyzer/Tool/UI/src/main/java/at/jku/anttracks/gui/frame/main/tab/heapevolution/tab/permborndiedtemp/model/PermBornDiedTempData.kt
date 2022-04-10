
package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model

import at.jku.anttracks.gui.model.Size

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.math.BigDecimal

class PermBornDiedTempData {

    var id: String
    var before: Size
    var perm: Size
    var born: Size
    var died: Size
    var temp: Size
    var after: Size
    var time: Long = 0

    enum class PermBornDiedTempDataInfo {
        BeforeObjects,
        PermObjects,
        BornObjects,
        DiedObjects,
        TempObjects,
        AfterObjects,
        BeforeBytes,
        PermBytes,
        BornBytes,
        DiedBytes,
        TempBytes,
        AfterBytes,
        PERM,
        DIED,
        BORN,
        TEMP
    }

    constructor(time: Long) : this("", time)

    constructor(id: String, time: Long) : this(id, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, time)

    constructor(id: String,
                perm: Size,
                born: Size,
                died: Size,
                temp: Size,
                time: Long) : this(id,
                                   perm.objects,
                                   born.objects,
                                   died.objects,
                                   temp.objects,
                                   perm.bytes,
                                   born.bytes,
                                   died.bytes,
                                   temp.bytes,
                                   time)

    constructor(id: String,
                permObjects: Double,
                bornObjects: Double,
                diedObjects: Double,
                tempObjects: Double,
                permBytes: Double,
                bornBytes: Double,
                diedBytes: Double,
                tempBytes: Double,
                time: Long) {
        this.id = id
        before = Size(permObjects + diedObjects, permBytes + diedBytes)
        perm = Size(permObjects, permBytes)
        died = Size(diedObjects, diedBytes)
        born = Size(bornObjects, bornBytes)
        temp = Size(tempObjects, tempBytes)
        after = Size(permObjects + bornObjects, permBytes + bornBytes)
        this.time = time
    }

    fun clone(): PermBornDiedTempData {
        return PermBornDiedTempData(id,
                                    perm.objects,
                                    born.objects,
                                    died.objects,
                                    temp.objects,
                                    perm.bytes,
                                    born.bytes,
                                    died.bytes,
                                    temp.bytes,
                                    time)
    }

    fun add(info: PermBornDiedTempDataInfo, n: Long) {
        when (info) {
            PermBornDiedTempDataInfo.BeforeObjects -> throw IllegalArgumentException("BeforeObjects should not be set, is implicitly increased with Perm and Died")
            PermBornDiedTempDataInfo.PermObjects -> {
                before.objects += n.toDouble()
                after.objects += n.toDouble()
                perm.objects += n.toDouble()
            }
            PermBornDiedTempDataInfo.BornObjects -> {
                after.objects += n.toDouble()
                born.objects += n.toDouble()
            }
            PermBornDiedTempDataInfo.DiedObjects -> {
                before.objects += n.toDouble()
                died.objects += n.toDouble()
            }
            PermBornDiedTempDataInfo.TempObjects -> temp.objects += n.toDouble()
            PermBornDiedTempDataInfo.AfterObjects -> throw IllegalArgumentException("AfterObjects should not be set, is implicitly increased with Perm and Born")
            PermBornDiedTempDataInfo.BeforeBytes -> throw IllegalArgumentException("BeforeBytes should not be set, is implicitly increased with Perm and Died")
            PermBornDiedTempDataInfo.PermBytes -> {
                before.bytes += n.toDouble()
                after.bytes += n.toDouble()
                perm.bytes += n.toDouble()
            }
            PermBornDiedTempDataInfo.BornBytes -> {
                after.bytes += n.toDouble()
                born.bytes += n.toDouble()
            }
            PermBornDiedTempDataInfo.DiedBytes -> {
                before.bytes += n.toDouble()
                died.bytes += n.toDouble()
            }
            PermBornDiedTempDataInfo.TempBytes -> temp.bytes += n.toDouble()
            PermBornDiedTempDataInfo.AfterBytes -> throw IllegalArgumentException("AfterBytes should not be set, is implicitly increased with Perm and Born")
            else -> throw IllegalArgumentException("The given parameter is not valid in this context")
        }
    }

    fun add(info: PermBornDiedTempDataInfo, n: Size) {
        when (info) {
            PermBornDiedTempDataInfo.BeforeObjects -> throw IllegalArgumentException("BeforeObjects should not be set, is implicitly increased with Perm and Died")
            PermBornDiedTempDataInfo.PermObjects -> {
                before.objects += n.objects
                after.objects += n.objects
                perm.objects += n.objects
            }
            PermBornDiedTempDataInfo.BornObjects -> {
                after.objects += n.objects
                born.objects += n.objects
            }
            PermBornDiedTempDataInfo.DiedObjects -> {
                before.objects += n.objects
                died.objects += n.objects
            }
            PermBornDiedTempDataInfo.TempObjects -> temp.objects += n.objects
            PermBornDiedTempDataInfo.AfterObjects -> throw IllegalArgumentException("AfterObjects should not be set, is implicitly increased with Perm and Born")
            PermBornDiedTempDataInfo.BeforeBytes -> throw IllegalArgumentException("BeforeBytes should not be set, is implicitly increased with Perm and Died")
            PermBornDiedTempDataInfo.PermBytes -> {
                before.bytes += n.bytes
                after.bytes += n.bytes
                perm.bytes += n.bytes
            }
            PermBornDiedTempDataInfo.BornBytes -> {
                after.bytes += n.bytes
                born.bytes += n.bytes
            }
            PermBornDiedTempDataInfo.DiedBytes -> {
                before.bytes += n.bytes
                died.bytes += n.bytes
            }
            PermBornDiedTempDataInfo.TempBytes -> temp.bytes += n.bytes
            PermBornDiedTempDataInfo.AfterBytes -> throw IllegalArgumentException("AfterBytes should not be set, is implicitly increased with Perm and Born")
            PermBornDiedTempDataInfo.PERM -> {
                before.objects += n.objects
                after.objects += n.objects
                perm.objects += n.objects
                before.bytes += n.bytes
                after.bytes += n.bytes
                perm.bytes += n.bytes
            }
            PermBornDiedTempDataInfo.BORN -> {
                after.objects += n.objects
                born.objects += n.objects
                after.bytes += n.bytes
                born.bytes += n.bytes
            }
            PermBornDiedTempDataInfo.DIED -> {
                before.objects += n.objects
                died.objects += n.objects
                before.bytes += n.bytes
                died.bytes += n.bytes
            }
            PermBornDiedTempDataInfo.TEMP -> {
                temp.objects += n.objects
                temp.bytes += n.bytes
            }
        }
    }

    fun getPercentage(info: PermBornDiedTempDataInfo, total: Long): Double? {
        var reference = 0.0
        when (info) {
            PermBornDiedTempDataInfo.BeforeObjects -> reference = before.objects
            PermBornDiedTempDataInfo.PermObjects -> reference = perm.objects
            PermBornDiedTempDataInfo.BornObjects -> reference = born.objects
            PermBornDiedTempDataInfo.DiedObjects -> reference = died.objects
            PermBornDiedTempDataInfo.TempObjects -> reference = temp.objects
            PermBornDiedTempDataInfo.AfterObjects -> reference = after.objects
            PermBornDiedTempDataInfo.BeforeBytes -> reference = before.bytes
            PermBornDiedTempDataInfo.PermBytes -> reference = perm.bytes
            PermBornDiedTempDataInfo.BornBytes -> reference = born.bytes
            PermBornDiedTempDataInfo.DiedBytes -> reference = died.bytes
            PermBornDiedTempDataInfo.TempBytes -> reference = temp.bytes
            PermBornDiedTempDataInfo.AfterBytes -> reference = after.bytes
            else -> throw IllegalArgumentException("The given parameter is not valid in this context")
        }

        return BigDecimal(reference / total * 100).setScale(1, BigDecimal.ROUND_HALF_UP).toDouble()
    }

    override fun toString(): String {
        return id
    }

    fun add(toAdd: PermBornDiedTempData) {
        add(PermBornDiedTempDataInfo.PERM, toAdd.perm)
        add(PermBornDiedTempDataInfo.BORN, toAdd.born)
        add(PermBornDiedTempDataInfo.DIED, toAdd.died)
        add(PermBornDiedTempDataInfo.TEMP, toAdd.temp)
    }

    override fun hashCode(): Int {
        val prime = 31
        var result = 1
        result = prime * result + id.hashCode()
        return result
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) {
            return true
        }
        if (other == null) {
            return false
        }
        if (javaClass != other.javaClass) {
            return false
        }
        val otherPermBornDiedTempData = other as PermBornDiedTempData?
        if (id != otherPermBornDiedTempData!!.id) {
            return false
        }
        return true
    }

    fun writeStatToMetaData(dos: DataOutputStream) {
        try {
            dos.writeUTF(id)
            dos.writeDouble(perm.objects)
            dos.writeDouble(perm.bytes)

            dos.writeDouble(born.objects)
            dos.writeDouble(born.bytes)

            dos.writeDouble(died.objects)
            dos.writeDouble(died.bytes)

            dos.writeDouble(temp.objects)
            dos.writeDouble(temp.bytes)
        } catch (e: IOException) {
            e.printStackTrace()
        }

    }

    companion object {

        fun loadFromMetaData(dis: DataInputStream, time: Long): PermBornDiedTempData? {
            try {
                return PermBornDiedTempData(dis.readUTF(),
                                            Size(dis.readDouble(), dis.readDouble()),
                                            Size(dis.readDouble(), dis.readDouble()),
                                            Size(dis.readDouble(), dis.readDouble()),
                                            Size(dis.readDouble(), dis.readDouble()),
                                            time)
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }

        }
    }
}
