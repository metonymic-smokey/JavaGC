package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model

import at.jku.anttracks.gui.chart.base.ApplicationChartFactory
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.component.table.PermBornDiedTempTreeTableView

class PlotablePermBornDiedTempData(data: PermBornDiedTempData,
                                   info: PermBornDiedTempTreeTableView.PlotInfo,
                                   memoryType: ApplicationChartFactory.MemoryConsumptionUnit,
                                   val plotStyle: PlotStyle) {

    var before: Double = 0.toDouble()
    var perm: Double = 0.toDouble()
    var died: Double = 0.toDouble()
    var born: Double = 0.toDouble()
    var temp: Double = 0.toDouble()
    var after: Double = 0.toDouble()
    var maxDied: Double = 0.toDouble()
    var maxAfter: Double = 0.toDouble()
    var maxTemp: Double = 0.toDouble()

    enum class PlotStyle {
        PermDiedBorn,
        Temp
    }

    init {
        when (memoryType) {
            ApplicationChartFactory.MemoryConsumptionUnit.OBJECTS -> {
                before = data.before.objects
                perm = data.perm.objects
                died = data.died.objects
                born = data.born.objects
                temp = data.temp.objects
                after = data.after.objects
                maxDied = info.maxDiedObjects
                maxAfter = info.maxAfterObjects
                maxTemp = info.maxTempObjects
            }
            ApplicationChartFactory.MemoryConsumptionUnit.BYTES -> {
                before = data.before.bytes
                perm = data.perm.bytes
                died = data.died.bytes
                born = data.born.bytes
                temp = data.temp.bytes
                after = data.after.bytes
                maxDied = info.maxDiedBytes
                maxAfter = info.maxAfterBytes
                maxTemp = info.maxTempBytes
            }
        }
    }
}
