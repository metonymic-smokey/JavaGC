package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.tab.chart

import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.DataStructureDevelopmentTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.DataStructureDevelopmentInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.setting.GrowthType
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.setting.findByText
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.task.DataStructureDevelopmentChartTask
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.SimpleStringProperty
import javafx.collections.ObservableList
import javafx.fxml.FXML
import javafx.scene.chart.BarChart
import javafx.scene.chart.NumberAxis
import javafx.scene.chart.XYChart
import javafx.scene.control.*
import javafx.scene.layout.VBox
import javafx.util.StringConverter
import org.controlsfx.control.PopOver

class DataStructureDevelopmentChartTab : ApplicationBaseTab() {

    @FXML
    private lateinit var growthBarChart: BarChart<String, Number>

    @FXML
    private lateinit var yAxis: NumberAxis

    @FXML
    private lateinit var configureButton: Button

    override val componentDescriptions by lazy {
        listOf(Triple(growthBarChart,
                      Description("The chart shows you the strongest growing data structures according to the selected metric.")
                              .linebreak()
                              .appendDefault("The y-values are 'heap growth portions', that is, the absolute growth of a data structure in relation to the overall heap growth.")
                              .linebreak()
                              .appendDefault("You can click one of the bars to jump to the respective table entry in the classification tab!"),
                      PopOver.ArrowLocation.BOTTOM_LEFT),
               Triple(growthBarChart.lookup(".chart-legend-item")?.parent ?: growthBarChart,
                      Description("You can change the sorting of the data structures by clicking the respective legend items!"),
                      PopOver.ArrowLocation.TOP_CENTER),
               Triple(configureButton,
                      Description("Configure the displayed series"),
                      PopOver.ArrowLocation.BOTTOM_RIGHT))
    }

    override val initialTabIdeas by lazy {
        listOf(Idea("Data structure development chart tab",
                    Description("This tab gives you an overview of the data structures that grew the most over the selected timeframe.")
                            .linebreak()
                            .appendDefault("For these 'top' data structures, the ")
                            .appendEmphasized("heap growth portion ")
                            .appendDefault("is calculated, that is, the growth of a data structure in relation to the total heap growth. ")
                            .appendDefault("This metric comes in four flavours: ")
                            .linebreak()
                            .appendEmphasized("Transitive HPG")
                            .appendDefault(": growth of total memory reachable from a data structure.")
                            .linebreak()
                            .appendEmphasized("Retained HPG")
                            .appendDefault(": growth of total memory owned (i.e. kept alive) by a data structure.")
                            .linebreak()
                            .appendEmphasized("Data structure HPG")
                            .appendDefault(": growth of memory occupied by data objects in a data structure.")
                            .linebreak()
                            .appendEmphasized("Deep data structure HPG")
                            .appendDefault(": growth of memory occupied by data objects in a data structure, including nested data structures."),
                    listOf("Show classification tab" does { ClientInfo.mainFrame.selectTab(dsDevelopmentTab.classificationTab) },
                           "I don't get this chart!" does { showComponentDescriptions() }),
                    null,
                    this))
    }

    init {
        FXMLUtil.load(this, DataStructureDevelopmentChartTab::class.java)
    }

    lateinit var info: DataStructureDevelopmentInfo
    private lateinit var dsDevelopmentTab: DataStructureDevelopmentTab

    fun init(info: DataStructureDevelopmentInfo, dsDevelopmentTab: DataStructureDevelopmentTab) {
        super.init(info.heapEvolutionInfo.appInfo,
                   SimpleStringProperty("Strongest growing data structures"),
                   SimpleStringProperty("Overview of the data structures that grew the most"),
                   SimpleStringProperty("The chart shows the data structures that grew the most over the selected timeframe according to one of the four available metrics."),
                   Consts.CHART_ICON,
                   listOf(),
                   true)

        this.info = info
        this.dsDevelopmentTab = dsDevelopmentTab

        yAxis.tickLabelFormatter = object : StringConverter<Number>() {
            override fun fromString(p0: String?): Number = p0?.toDouble() ?: 0.0

            override fun toString(p0: Number?): String = "%.1f".format(p0)
        }

        configureButton.setOnAction { _ ->
            val configDialog = Dialog<ButtonType>()
            configDialog.title = "Select the series you want to display"
            val vbox = VBox(10.0,
                            CheckBox("Transitive size").apply { isSelected = info.seriesDisplayedInChart[GrowthType.TRANSITIVE]!! },
                            CheckBox("Retained size").apply { isSelected = info.seriesDisplayedInChart[GrowthType.RETAINED]!! },
                            CheckBox("Data structure size").apply { isSelected = info.seriesDisplayedInChart[GrowthType.DATA_STRUCTURE]!! },
                            CheckBox("Deep data structure size").apply { isSelected = info.seriesDisplayedInChart[GrowthType.DEEP_DATA_STRUCTURE]!! })
            configDialog.dialogPane.content = vbox
            configDialog.dialogPane.buttonTypes.add(ButtonType.APPLY)
            configDialog.showAndWait().filter { it == ButtonType.APPLY }.ifPresent { _ ->
                val selection = vbox.children.map { (it as CheckBox).isSelected }
                val previousSelection = info.seriesDisplayedInChart.toMap()
                info.seriesDisplayedInChart[GrowthType.TRANSITIVE] = selection[0]
                info.seriesDisplayedInChart[GrowthType.RETAINED] = selection[1]
                info.seriesDisplayedInChart[GrowthType.DATA_STRUCTURE] = selection[2]
                info.seriesDisplayedInChart[GrowthType.DEEP_DATA_STRUCTURE] = selection[3]
                if (info.seriesDisplayedInChart != previousSelection) {
                    updateChart()
                }
            }
        }
    }

    fun updateChart() {
        if (info.dataStructures.isEmpty()) {
            return
        }

        val barChartUpdateTask = DataStructureDevelopmentChartTask(info, this)
        tasks.add(barChartUpdateTask)
        at.jku.anttracks.util.ThreadUtil.startTask<ObservableList<XYChart.Series<String, Number>>>(barChartUpdateTask)
    }

    fun setChartData(series: ObservableList<XYChart.Series<String, Number>>) {
        fun initLegend() {
            growthBarChart.lookupAll(".chart-legend-item").forEach { legendItem ->
                val legendItemLabel = (legendItem as Label).text
                Tooltip.install(legendItem, Tooltip("Click to sort by $legendItemLabel"))
                legendItem.setOnMouseEntered { legendItem.setStyle("-fx-underline: true") }
                legendItem.setOnMouseExited { legendItem.setStyle("-fx-underline: false") }
                legendItem.setOnMouseClicked {
                    info.chartGrowthTypeSorting = findByText(legendItemLabel)!!

                    val barChartInitTask = DataStructureDevelopmentChartTask(info, this)
                    tasks.add(barChartInitTask)
                    at.jku.anttracks.util.ThreadUtil.startTask<ObservableList<XYChart.Series<String, Number>>>(barChartInitTask)
                }
            }
        }

        fun initSeriesNodes() {
            // clicking a bar in the chart highlights the corresponding entry in the TreeTableView
            growthBarChart.data.forEach { s ->
                s.data.forEach { data ->
                    Tooltip.install(data.node, Tooltip("Click to highlight object in tree table"))
                    data.node.style = "-fx-opacity: 0.6; -fx-cursor: hand"
                    data.node.setOnMouseEntered { data.node.style = "-fx-opacity: 1" }
                    data.node.setOnMouseExited { data.node.style = "-fx-opacity: 0.6" }
                    data.node.setOnMouseClicked { _ ->
                        // the address as used in the address classifier
                        ClientInfo.mainFrame.selectTab(dsDevelopmentTab.classificationTab)
                        dsDevelopmentTab.classificationTab.treeTableView.showAndSelect(data.xValue.split("@".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1].replace(" ",
                                                                                                                                                                                 ""))
                    }
                }
            }
        }

        growthBarChart.data = series
        initLegend()
        initSeriesNodes()
    }

    override fun cleanupOnClose() {
    }

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {
    }

}
