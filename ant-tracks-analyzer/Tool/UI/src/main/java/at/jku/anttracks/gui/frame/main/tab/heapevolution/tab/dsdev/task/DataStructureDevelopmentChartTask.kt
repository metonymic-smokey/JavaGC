package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.task

import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.DataStructureDevelopmentInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.setting.GrowthType
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.tab.chart.DataStructureDevelopmentChartTab
import at.jku.anttracks.gui.utils.AntTask
import javafx.collections.FXCollections
import javafx.collections.ObservableList
import javafx.scene.chart.XYChart
import java.util.concurrent.ExecutionException

class DataStructureDevelopmentChartTask(private val info: DataStructureDevelopmentInfo,
                                        private val tab: DataStructureDevelopmentChartTab) : AntTask<ObservableList<XYChart.Series<String, Number>>>() {

    companion object {
        const val N_BARS = 10
        val addressRegex: Regex = Regex("\\d{1,3}([,.]\\d{3})*")
    }

    @Throws(Exception::class)
    override fun backgroundWork(): ObservableList<XYChart.Series<String, Number>> {
        updateTitle("Initializing data structure growth chart")
        // find the 10 data structures with the strongest growth and put them into the chart
        updateMessage("Finding data structures with strongest growth...")

        // sort growths
        val sortedGrowths = info.dataStructures.sortedBy {
            when (info.chartGrowthTypeSorting) {
                GrowthType.TRANSITIVE -> -it.sizeInfo!!.transitiveHeapGrowthPortion
                GrowthType.RETAINED -> -it.sizeInfo!!.retainedHeapGrowthPortion
                GrowthType.DATA_STRUCTURE -> -it.sizeInfo!!.dataStructureHeapGrowthPortion
                GrowthType.DEEP_DATA_STRUCTURE -> -it.sizeInfo!!.deepDataStructureHeapGrowthPortion
            }
        }

        // put them into series
        val transitiveClosureGrowthSeries = XYChart.Series<String, Number>()
        val retainedClosureGrowthSeries = XYChart.Series<String, Number>()
        val dataStructureClosureGrowthSeries = XYChart.Series<String, Number>()
        val deepDataStructureClosureGrowthSeries = XYChart.Series<String, Number>()
        transitiveClosureGrowthSeries.name = GrowthType.TRANSITIVE.text
        retainedClosureGrowthSeries.name = GrowthType.RETAINED.text
        dataStructureClosureGrowthSeries.name = GrowthType.DATA_STRUCTURE.text
        deepDataStructureClosureGrowthSeries.name = GrowthType.DEEP_DATA_STRUCTURE.text

        sortedGrowths.stream()
                .limit(N_BARS.toLong())
                .forEach { ds ->
                    transitiveClosureGrowthSeries.data.add(
                            XYChart.Data(ds.label(info.heapEvolutionInfo.endHeap), ds.sizeInfo!!.transitiveHeapGrowthPortion))
                    retainedClosureGrowthSeries.data.add(
                            XYChart.Data(ds.label(info.heapEvolutionInfo.endHeap), ds.sizeInfo!!.retainedHeapGrowthPortion))
                    dataStructureClosureGrowthSeries.data.add(
                            XYChart.Data(ds.label(info.heapEvolutionInfo.endHeap), ds.sizeInfo!!.dataStructureHeapGrowthPortion))
                    deepDataStructureClosureGrowthSeries.data.add(
                            XYChart.Data(ds.label(info.heapEvolutionInfo.endHeap), ds.sizeInfo!!.deepDataStructureHeapGrowthPortion))
                }

        return FXCollections.observableArrayList<XYChart.Series<String, Number>>().apply {
            if (info.seriesDisplayedInChart[GrowthType.TRANSITIVE]!!) add(transitiveClosureGrowthSeries)
            if (info.seriesDisplayedInChart[GrowthType.RETAINED]!!) add(retainedClosureGrowthSeries)
            if (info.seriesDisplayedInChart[GrowthType.DATA_STRUCTURE]!!) add(dataStructureClosureGrowthSeries)
            if (info.seriesDisplayedInChart[GrowthType.DEEP_DATA_STRUCTURE]!!) add(deepDataStructureClosureGrowthSeries)
        }
    }

    override fun finished() {
        try {
            val series = get()
            tab.setChartData(series)
        } catch (e: InterruptedException) {
            e.printStackTrace()
        } catch (e: ExecutionException) {
            e.printStackTrace()
        }
    }
}
