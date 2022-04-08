package at.jku.anttracks.gui.chart.extjfx.chartpanes.evolution

import at.jku.anttracks.gui.chart.extjfx.chartpanes.application.SimplifiedReducedMemoryChartPane
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.utils.toNiceNumberString
import at.jku.anttracks.parser.EventType
import cern.extjfx.chart.plugins.XRangeIndicator
import cern.extjfx.chart.plugins.XValueIndicator

class SimplifiedReducedMemoryChartPaneWithFixedSelection : SimplifiedReducedMemoryChartPane() {
    override val xSelector = null

    fun init(initialYUnit: Companion.Unit, appInfo: AppInfo, fromTime: Double, toTime: Double) {
        super.init(initialYUnit)

        val gcLabelGenerator: (Double) -> String = { time ->
            val gcInfo = appInfo.getStatistics(time.toLong())!!.first().info
            "GC #${gcInfo.id} ${if (gcInfo.meta == EventType.GC_START) "START" else "END"} @ ${toNiceNumberString(gcInfo.time)}ms"
        }

        val rangeLabelGenerator: (Double, Double) -> String = { fromX, toX ->
            "From ${gcLabelGenerator.invoke(fromX)}\nTo ${gcLabelGenerator.invoke(toX)}"
        }

        if (fromTime == toTime) {
            extjfxChartPane.plugins.add(XValueIndicator(fromTime, gcLabelGenerator(fromTime)))
        } else {
            extjfxChartPane.plugins.add(XRangeIndicator(fromTime, toTime, rangeLabelGenerator(fromTime, toTime)))
        }
    }
}