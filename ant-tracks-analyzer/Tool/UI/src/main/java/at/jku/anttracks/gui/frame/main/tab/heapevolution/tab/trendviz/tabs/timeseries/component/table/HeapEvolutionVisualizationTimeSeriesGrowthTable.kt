

package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.component.table

import at.jku.anttracks.gui.chart.jfreechart.xy.mixed.objectgrouptrend.ObjectGroupTrendChartDataSet
import at.jku.anttracks.gui.component.tableview.AntTableView
import at.jku.anttracks.gui.component.tableview.cell.ColorTableCell
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.HeapEvolutionVisualizationTimeSeriesDiagramInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.DiagramMetric
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.settings.SeriesSort
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.toShortMemoryUsageString
import at.jku.anttracks.util.safe
import at.jku.anttracks.util.toString
import javafx.scene.control.TableCell
import javafx.scene.control.TableColumn
import javafx.util.Callback
import java.awt.Color

class HeapEvolutionVisualizationTimeSeriesGrowthTable : AntTableView<HeapEvolutionVisualizationTimeSeriesGrowthTableRow>() {
    private lateinit var diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo
    private lateinit var colorColumn: TableColumn<HeapEvolutionVisualizationTimeSeriesGrowthTableRow, Color>
    private lateinit var labelColumn: TableColumn<HeapEvolutionVisualizationTimeSeriesGrowthTableRow, String>
    private lateinit var valueColumn: TableColumn<HeapEvolutionVisualizationTimeSeriesGrowthTableRow, Number>

    init {
        FXMLUtil.load(this, HeapEvolutionVisualizationTimeSeriesGrowthTable::class.java)
    }

    fun init(diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo) {
        this.diagramInfo = diagramInfo
        // Define columns
        colorColumn = TableColumn("")
        colorColumn.setCellValueFactory { param -> param.value.colorProperty }
        colorColumn.prefWidth = 32.0
        colorColumn.cellFactory = Callback {
            object : ColorTableCell<HeapEvolutionVisualizationTimeSeriesGrowthTableRow>() {
                override fun updateItem(color: Color?, empty: Boolean) {
                    super.updateItem(color, empty)
                    // Change color of whole row
                    if (color == ObjectGroupTrendChartDataSet.HIGHLIGHT_COLOR) {
                        tableRow.style = "-fx-background-color: rgb(${color.red},${color.green},${color.blue},0.35);"
                    } else {
                        tableRow.style = ""
                    }
                }
            }
        }

        labelColumn = TableColumn("Name")
        labelColumn.setCellValueFactory { param -> param.value.labelProperty }
        labelColumn.cellFactory = Callback {
            object : TableCell<HeapEvolutionVisualizationTimeSeriesGrowthTableRow, String>() {
                override fun updateItem(item: String?, empty: Boolean) {
                    super.updateItem(item, empty)

                    if (item == null || empty) {
                        text = null
                        style = ""
                    } else {
                        text = item
                        style = if ((tableRow.item as? HeapEvolutionVisualizationTimeSeriesGrowthTableRow)?.colorProperty?.value == ObjectGroupTrendChartDataSet.HIGHLIGHT_COLOR) {
                            "-fx-font-weight: bold"
                        } else {
                            ""
                        }
                    }
                }
            }
        }
        labelColumn.prefWidth = 200.0

        valueColumn = TableColumn(diagramInfo.seriesSort.toString())
        valueColumn.setCellValueFactory { param ->
            when (diagramInfo.seriesSort) {
                SeriesSort.START -> param.value.start
                SeriesSort.AVG -> param.value.average
                SeriesSort.END -> param.value.end
                SeriesSort.ABS_GROWTH -> param.value.absoluteGrowth
                SeriesSort.REL_GROWTH -> param.value.relativeGrowth
            }.safe
        }
        valueColumn.cellFactory = Callback { column ->
            object : TableCell<HeapEvolutionVisualizationTimeSeriesGrowthTableRow, Number>() {
                override fun updateItem(item: Number?, empty: Boolean) {
                    super.updateItem(item, empty)

                    if (item == null || empty) {
                        text = null;
                        style = "";
                    } else {
                        // Format bytes as KB, MB, GB, and format objects in full length
                        if (diagramInfo.diagramMetric == DiagramMetric.BYTES || diagramInfo.diagramMetric == DiagramMetric.RETAINED_SIZE || diagramInfo.diagramMetric == DiagramMetric.TRANSITIVE_CLOSURE_SIZE) {
                            text = toShortMemoryUsageString(item.toLong())
                        } else {
                            text = item.toLong().toString("%,d")
                            //setTextFill(Color.CHOCOLATE);
                            //setStyle("-fx-background-color: yellow");
                        }
                        style = if ((tableRow.item as? HeapEvolutionVisualizationTimeSeriesGrowthTableRow)?.colorProperty?.value == ObjectGroupTrendChartDataSet.HIGHLIGHT_COLOR) {
                            "-fx-font-weight: bold"
                        } else {
                            ""
                        }
                    }
                }
            }
        }
        valueColumn.prefWidth = 100.0

        columns.apply {
            add(colorColumn)
            add(labelColumn)
            add(valueColumn)
        }

        refreshSorting()
    }

    fun update(dataSet: ObjectGroupTrendChartDataSet) {
        this.labelColumn.text = dataSet.getClassifiersOfSeries().joinToString(" / ")
        this.valueColumn.text = diagramInfo.seriesSort.toString()
        this.items.clear()
        this.items.addAll(dataSet.antSeries.filter { it.key != ObjectGroupTrendChartDataSet.OTHER }.map { HeapEvolutionVisualizationTimeSeriesGrowthTableRow(it) })
        refreshSorting()
    }

    private fun refreshSorting() {
        valueColumn.sortType = TableColumn.SortType.DESCENDING
        sortOrder.clear()
        sortOrder.add(valueColumn)
    }
}
