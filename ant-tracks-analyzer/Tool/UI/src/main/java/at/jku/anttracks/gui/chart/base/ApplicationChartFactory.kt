
package at.jku.anttracks.gui.chart.base

import at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane.MemoryApplicationStackedAreaChart
import at.jku.anttracks.gui.chart.jfreechart.xy.line.gc.GCJFreeChartPane
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.alivedead.AliveDeadStackedAreaJFreeChartPane
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.allocatingsubsystem.AllocatingSubsystemStackedAreaJFreeChartPane
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.cpuutilization.GCActivityStackedAreaJFreeChartPane
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.feature.FeatureStackedAreaJFreeChartPane
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.memory.MemoryStackedAreaJFreeChartPane
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.objectkind.ObjectKindStackedAreaJFreeChartPane
import at.jku.anttracks.gui.model.AppInfo

object ApplicationChartFactory {

    enum class ApplicationChartType {
        OBJECTS,
        BYTES,
        GC_TYPES,
        GC_UTILIZATIOM,
        ALIVE_DEAD_BYTES,
        ALIVE_DEAD_OBJECTS,
        ALIVE_DEAD_RELATIVE,
        FEATURE_OBJECTS,
        FEATURE_BYTES,
        ALLOCATING_SUBSYSTEM,
        OBJECT_KINDS
    }

    enum class MemoryConsumptionUnit(val label: String, val unit: String) {
        BYTES("Memory", "# Bytes"),
        OBJECTS("Objects", "# Objects")
    }

    enum class FeatureMemoryConsumptionUnit(val label: String, val unit: String) {
        BYTES("Memory", "# Bytes"),
        OBJECTS("Objects", "# Objects")
    }

    enum class AliveDeadMemoryConsumptionUnit(val unit: String) {
        BYTES("# Bytes"),
        OBJECTS("# Objects"),
        RELATIVE("% Objects")
    }

    private fun createBytesChart(chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
//        val chart = MemoryStackedAreaJFreeChartPane()
        val chart = MemoryApplicationStackedAreaChart()
        chart.init(chartSynchronizer, MemoryConsumptionUnit.BYTES)
        return chart
    }

    private fun createObjectsChart(chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
        val chart = MemoryStackedAreaJFreeChartPane()
        chart.init(chartSynchronizer, MemoryConsumptionUnit.OBJECTS)
        return chart
    }

    private fun createGCTypesChart(chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
        val chart = GCJFreeChartPane()
        chart.init(chartSynchronizer)
        return chart
    }

    private fun createGCUtilizationChart(chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
        val chart = GCActivityStackedAreaJFreeChartPane()
        chart.init(chartSynchronizer, GCActivityStackedAreaJFreeChartPane.Mode.GC)
        return chart
    }

    private fun createAliveDeadBytesChart(chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
        val chartPanel = AliveDeadStackedAreaJFreeChartPane()
        chartPanel.init(chartSynchronizer, AliveDeadMemoryConsumptionUnit.BYTES)
        return chartPanel
    }

    private fun createAliveDeadObjectsChart(chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
        val chartPanel = AliveDeadStackedAreaJFreeChartPane()
        chartPanel.init(chartSynchronizer, AliveDeadMemoryConsumptionUnit.OBJECTS)
        return chartPanel
    }

    private fun createAliveDeadRelativeChart(chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
        val chartPanel = AliveDeadStackedAreaJFreeChartPane()
        chartPanel.init(chartSynchronizer, AliveDeadMemoryConsumptionUnit.RELATIVE)
        return chartPanel
    }

    private fun createFeatureObjectsChart(chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
        val chartPanel = FeatureStackedAreaJFreeChartPane()
        chartPanel.init(chartSynchronizer, FeatureMemoryConsumptionUnit.OBJECTS)
        return chartPanel
    }

    private fun createFeatureBytesChart(chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
        val chartPanel = FeatureStackedAreaJFreeChartPane()
        chartPanel.init(chartSynchronizer, FeatureMemoryConsumptionUnit.BYTES)
        return chartPanel
    }

    private fun createAllocatingSubsystemChart(chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
        val allocatingSubsystemApplicationChart = AllocatingSubsystemStackedAreaJFreeChartPane()
        allocatingSubsystemApplicationChart.init(chartSynchronizer)
        return allocatingSubsystemApplicationChart
    }

    private fun createObjectKindsChart(chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
        val objectKindsApplicationChart = ObjectKindStackedAreaJFreeChartPane()
        objectKindsApplicationChart.init(chartSynchronizer)
        return objectKindsApplicationChart
    }

    @JvmStatic
    fun createChart(type: ApplicationChartType, chartSynchronizer: ChartSynchronizer): at.jku.anttracks.gui.chart.base.AntChartPane<*, AppInfo> {
        return when (type) {
            ApplicationChartFactory.ApplicationChartType.OBJECTS -> createObjectsChart(chartSynchronizer)
            ApplicationChartFactory.ApplicationChartType.BYTES -> createBytesChart(chartSynchronizer)
            ApplicationChartFactory.ApplicationChartType.GC_TYPES -> createGCTypesChart(chartSynchronizer)
            ApplicationChartFactory.ApplicationChartType.GC_UTILIZATIOM -> createGCUtilizationChart(chartSynchronizer)
            ApplicationChartFactory.ApplicationChartType.ALIVE_DEAD_BYTES -> createAliveDeadBytesChart(chartSynchronizer)
            ApplicationChartFactory.ApplicationChartType.ALIVE_DEAD_OBJECTS -> createAliveDeadObjectsChart(chartSynchronizer)
            ApplicationChartFactory.ApplicationChartType.ALIVE_DEAD_RELATIVE -> createAliveDeadRelativeChart(chartSynchronizer)
            ApplicationChartFactory.ApplicationChartType.FEATURE_OBJECTS -> createFeatureObjectsChart(chartSynchronizer)
            ApplicationChartFactory.ApplicationChartType.FEATURE_BYTES -> createFeatureBytesChart(chartSynchronizer)
            ApplicationChartFactory.ApplicationChartType.ALLOCATING_SUBSYSTEM -> createAllocatingSubsystemChart(chartSynchronizer)
            ApplicationChartFactory.ApplicationChartType.OBJECT_KINDS -> createObjectKindsChart(chartSynchronizer)
        }
    }
}
