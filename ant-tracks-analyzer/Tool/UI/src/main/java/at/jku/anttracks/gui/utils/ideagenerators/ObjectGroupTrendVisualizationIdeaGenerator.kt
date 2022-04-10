package at.jku.anttracks.gui.utils.ideagenerators

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.nodes.GroupingNode
import at.jku.anttracks.gui.chart.base.AntSeries
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.DataStructureDevelopmentTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.HeapEvolutionVisualizationTimeSeriesTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.component.chartpane.HeapEvolutionVisualizationTimeSeriesChartContainer
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.component.table.HeapEvolutionVisualizationTimeSeriesGrowthTable
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.HeapGraphVisualizationTab
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes.PATH_TO_MOST_INTERESTING_ROOTS_TYPED
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.toShortMemoryUsageString

object ObjectGroupTrendVisualizationIdeaGenerator {

    @JvmStatic
    fun analyze(heapEvolutionVisualizationTimeSeriesTab: HeapEvolutionVisualizationTimeSeriesTab, classifiers: ClassifierChain, filters: List<Filter>) {
        if (classifiers.list.take(3) == listOf(heapEvolutionVisualizationTimeSeriesTab.heapEvolutionVisualizationInfo.availableClassifier["Type"],
                                               heapEvolutionVisualizationTimeSeriesTab.heapEvolutionVisualizationInfo.availableClassifier["Allocation Site"],
                                               heapEvolutionVisualizationTimeSeriesTab.heapEvolutionVisualizationInfo.availableClassifier["Call Sites"]) &&
                filters.contains(heapEvolutionVisualizationTimeSeriesTab.heapEvolutionVisualizationInfo.availableFilter["Only domain objects"])) {
            val typeChartContainer = heapEvolutionVisualizationTimeSeriesTab.chartContainerBox.children.first() as HeapEvolutionVisualizationTimeSeriesChartContainer
            val heapGrowth = typeChartContainer.diagramInfo.heapEvolutionVisualizationInfo.absoluteHeapGrowth
            val growthTable = typeChartContainer.chart.table

            val sortedGroupings: List<MutableMap.MutableEntry<Long, GroupingNode>> = heapEvolutionVisualizationTimeSeriesTab.heapEvolutionVisualizationInfo.groupings.entries.sortedBy { it.key }

            val firstTreeSize = sortedGroupings.first().value.getByteCount(heapEvolutionVisualizationTimeSeriesTab.heapEvolutionVisualizationInfo.heapEvolutionData.startHeap)
            val lastTreeSize = sortedGroupings.last().value.getByteCount(heapEvolutionVisualizationTimeSeriesTab.heapEvolutionVisualizationInfo.heapEvolutionData.endHeap)

            val treeGrowth = lastTreeSize - firstTreeSize

            val typeWithStrongestAbsoluteGrowth = typeChartContainer.chart.dataset!!.antSeries.maxBy { it.absoluteGrowth }

            typeWithStrongestAbsoluteGrowth?.takeIf { it.absoluteGrowth > 0 }?.also { topTypeSeries ->
                val description = Description("Over this timeframe, the memory occupied by domain objects of type ")
                        .c(topTypeSeries.key)
                        .a(" increased by ")
                        .e(toShortMemoryUsageString(topTypeSeries.absoluteGrowth.toLong()))
                        .a(". This corresponds to over ")
                        .e("${(topTypeSeries.absoluteGrowth * 100.0 / treeGrowth).toInt()}%")
                        .a(" of the total domain object growth and could be the manifestation of a memory leak.\n")

                        .a("You might want to see ")
                        .e("where these object have been allocated")
                        .a(". To do so select the respective series in the chart or use the action below!\n")

                        .a("You can also inspect the ")
                        .e(" heap object graph")
                        .a(" at the end of the time window to find out")
                        .e(" which objects keep the accumulating objects alive")
                        .a(".")

                val ideaActions = mutableListOf("Show alloc sites" does { select(typeChartContainer, topTypeSeries, growthTable) },
                                                "Inspect heap object graph" does { openGraphVisTab(heapEvolutionVisualizationTimeSeriesTab, topTypeSeries.key) })
                val dataStructureDevelopmentTab = heapEvolutionVisualizationTimeSeriesTab.parentTab!!.childTabs.find { it is DataStructureDevelopmentTab }
                if (dataStructureDevelopmentTab != null) {
                    ideaActions.add("Show data structure growths" does { ClientInfo.mainFrame.selectTab(dataStructureDevelopmentTab) })
                    description
                            .linebreak()
                            .appendDefault("Also, don't forget that you performed a ")
                            .appendEmphasized("data structure growth analysis ")
                            .appendDefault("on this time window - maybe there the cause of memory leak will be easy to spot.")
                }

                heapEvolutionVisualizationTimeSeriesTab.ideas.add(Idea("Growth trend!",
                                                                       description,
                                                                       ideaActions,
                                                                       listOf(typeChartContainer.chart at Idea.BulbPosition.TOP_RIGHT),
                                                                       heapEvolutionVisualizationTimeSeriesTab,
                                                                       Idea.HoverAction({ select(typeChartContainer, topTypeSeries, growthTable) },
                                                                                        { unselect(typeChartContainer, topTypeSeries, growthTable) })))
            }
        }
    }

    private fun openGraphVisTab(parentTab: HeapEvolutionVisualizationTimeSeriesTab, type: String) {
        val heap = parentTab.heapEvolutionVisualizationInfo.endHeap
        val objects = heap.stream().filter { heap.getType(it).simpleName == type }.toArray()
        val tab = HeapGraphVisualizationTab()
        tab.init(parentTab.appInfo, heap, objects, PATH_TO_MOST_INTERESTING_ROOTS_TYPED)
        ClientInfo.mainFrame.addAndSelectTab(parentTab, tab)
    }

    fun select(typeChartContainer: HeapEvolutionVisualizationTimeSeriesChartContainer,
               topTypeSeries: AntSeries,
               growthTable: HeapEvolutionVisualizationTimeSeriesGrowthTable) {
        if (typeChartContainer.chart.selectedKeyProperty.get() != topTypeSeries.key) {
            growthTable.selectionModel.select(growthTable.items.find { it.labelProperty.get() == topTypeSeries.key })
            typeChartContainer.chart.notifyKeySelectedPrimary(topTypeSeries.key)
        }
    }

    fun unselect(typeChartContainer: HeapEvolutionVisualizationTimeSeriesChartContainer,
                 topTypeSeries: AntSeries,
                 growthTable: HeapEvolutionVisualizationTimeSeriesGrowthTable) {
        if (typeChartContainer.chart.selectedKeyProperty.get() == topTypeSeries.key) {
            growthTable.selectionModel.clearSelection()
            // notifying a second time deselects the key
            typeChartContainer.chart.notifyKeySelectedPrimary(topTypeSeries.key)
        }
    }
}