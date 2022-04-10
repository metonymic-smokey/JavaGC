package at.jku.anttracks.gui.chart.jfreechart.xy.mixed.objectgrouptrend

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.model.HeapEvolutionVisualizationInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.component.chartpane.HeapEvolutionVisualizationTimeSeriesChartContainer.Companion.getClassificationText
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.component.table.HeapEvolutionVisualizationTimeSeriesGrowthTable
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.tabs.timeseries.model.HeapEvolutionVisualizationTimeSeriesDiagramInfo
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import cern.extjfx.chart.NumericAxis
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.scene.chart.StackedAreaChart
import javafx.scene.chart.XYChart
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.input.MouseEvent
import javafx.scene.text.Font

class ReducedObjectGroupTrendChartPane : ReducedXYChartPane<HeapEvolutionVisualizationInfo>() {
    @FXML
    private lateinit var table: HeapEvolutionVisualizationTimeSeriesGrowthTable

    @FXML
    private lateinit var arrow: ImageView

    @FXML
    private lateinit var title: Label

    init {
        FXMLUtil.load(this, ReducedObjectGroupTrendChartPane::class.java)
    }

    override val chart = StackedAreaChart<Number, Number>(NumericAxis(), NumericAxis())
    override val xUnit = Companion.Unit.TIME
    override val yUnits = listOf(Companion.Unit.OBJECTS, Companion.Unit.BYTES)

    private lateinit var diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo

    fun init(initialYUnit: Companion.Unit, diagramInfo: HeapEvolutionVisualizationTimeSeriesDiagramInfo) {
        super.init(initialYUnit)
        chart.createSymbols = false
        this.diagramInfo = diagramInfo

        arrow.image = SwingFXUtils.toFXImage(Consts.ARROW_IMAGE, null)
        arrow.rotate = 90.0
        arrow.isVisible = diagramInfo.selectedKeys.isNotEmpty()
        arrow.isManaged = arrow.isVisible
        title.text = getClassificationText(diagramInfo, null) // TODO use dataset
        title.padding = Insets(20.0, 0.0, 5.0, 0.0)
        title.font = Font(title.font.name, 20.0)

        table.init(diagramInfo)

        chart.data.addListener(ListChangeListener<XYChart.Series<Number, Number>> { change ->
            change.addedSubList.forEach { series ->
                series.node?.opacity = 0.75
                series.node?.addEventHandler(MouseEvent.MOUSE_ENTERED) {
                    series.node?.opacity = 1.0
                }
                series.node?.addEventHandler(MouseEvent.MOUSE_EXITED) {
                    if (series.name != table.selectionModel.selectedItem.labelProperty.get()) {
                        series.node?.opacity = 0.75
                    }
                }
                series.node?.addEventHandler(MouseEvent.MOUSE_CLICKED) { _ ->
                    table.selectionModel.clearAndSelect(table.items.indexOfFirst { it.labelProperty.get() == series.name })
                }
            }
        })
        table.selectionModel.selectedItemProperty().addListener { _, _, newlySelectedItem ->
            chart.data.find { it.name == newlySelectedItem.labelProperty.get() }?.node?.opacity = 1.0
        }
    }

    override fun createDataset(info: HeapEvolutionVisualizationInfo): List<XYChart.Series<Number, Number>> {
        val dataset = ObjectGroupTrendChartDataSet(info, table.selectionModel.selectedItem?.labelProperty?.get(), diagramInfo)
        return dataset.antSeries.map { antSeries ->
            XYChart.Series<Number, Number>(antSeries.key,
                                           FXCollections.observableArrayList(antSeries.data.map { XYChart.Data<Number, Number>(it.xValue, it.yValue) }))
        }
    }
}