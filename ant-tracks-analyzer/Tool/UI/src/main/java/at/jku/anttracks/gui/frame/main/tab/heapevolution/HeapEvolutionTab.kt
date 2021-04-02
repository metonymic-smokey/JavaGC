package at.jku.anttracks.gui.frame.main.tab.heapevolution

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.chart.extjfx.chartpanes.evolution.SimplifiedReducedMemoryChartPaneWithFixedSelection
import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.analysismethod.HeapEvolutionAnalysisMethod
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.HeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.model.IHeapEvolutionInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.analysismethod.DataStructureDevelopmentHeapEvolutionAnalysisMethod
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.analysismethod.PermBornDiedTempHeapEvolutionAnalysisMethod
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.analysismethod.ShortLivedObjectsHeapEvolutionAnalysisMethod
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.trendviz.analysismethod.HeapEvolutionVisualizationAnalysisMethod
import at.jku.anttracks.gui.frame.main.tab.heapevolution.task.HeapEvolutionParserTask
import at.jku.anttracks.gui.model.*
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.heap.statistics.Statistics
import at.jku.anttracks.util.ThreadUtil
import at.jku.anttracks.util.toString
import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.scene.control.CheckBox
import javafx.scene.control.Label
import javafx.scene.control.TitledPane
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import org.controlsfx.control.Notifications

class HeapEvolutionTab : ApplicationBaseTab() {

    @FXML
    lateinit var heapEvolutionAnalysisMethodsConfigurationSections: VBox
    @FXML
    lateinit var chart: SimplifiedReducedMemoryChartPaneWithFixedSelection

    lateinit var heapEvolutionAnalysisMethods: MutableMap<HeapEvolutionAnalysisMethod, BooleanProperty>

    private val atLeastOneHeapEvolutionAnalysisMethodSelected: BooleanProperty = SimpleBooleanProperty(false)

    lateinit var info: HeapEvolutionInfo

    private val heapEvolutionAnalysisRunning: BooleanProperty = SimpleBooleanProperty(false)

    override val initialTabIdeas by lazy {
        listOf(Idea("Heap Evolution",
                    Description()
                            a "Use this tab to analyze the application's behavior over time. \n"
                            a "Select analysis methods and then hit \"Start heap evolution analysis\".",
                    listOf("Start heap evolution analysis" performs {
                        if (atLeastOneHeapEvolutionAnalysisMethodSelected.and(heapEvolutionAnalysisRunning.not()).get()) {
                            runHeapEvolutionAnalysis()
                        }
                    }),
                    listOf(heapEvolutionAnalysisMethodsConfigurationSections at Idea.BulbPosition.TOP_RIGHT),
                    this,
                    null,
                    null)
        )
    }

    init {
        FXMLUtil.load(this, HeapEvolutionTab::class.java)
    }

    fun init(appInfo: AppInfo,
             startStatistics: Statistics,
             endStatistics: Statistics,
             permBornDiedTempSelected: Boolean = false,
             objectGroupTrendSelected: Boolean = false,
             dataStructureDevelopmentSelected: Boolean = false,
             shortLivedObjectsSelected: Boolean = false) {
        super.init(appInfo,
                   SimpleStringProperty("Heap evolution"),
                   SimpleStringProperty("In the time window from ${startStatistics.info.time.toString("%,d")}ms to ${endStatistics.info.time.toString("%,d")}ms"),
                   SimpleStringProperty("Select and configure the heap evolution analysis methods that you want to apply within the selected time window."),
                   Consts.HEAP_EVOLUTION_ICON,
                   listOf(ActionTabAction("Start heap evolution analysis",
                                          "Apply the selected methods",
                                          null,
                                          atLeastOneHeapEvolutionAnalysisMethodSelected.and(heapEvolutionAnalysisRunning.not())) { runHeapEvolutionAnalysis() }),
                   true)

        info = HeapEvolutionInfo(appInfo, startStatistics, endStatistics)

        heapEvolutionAnalysisMethods = mutableMapOf(
                PermBornDiedTempHeapEvolutionAnalysisMethod(info) to SimpleBooleanProperty(permBornDiedTempSelected),
                HeapEvolutionVisualizationAnalysisMethod(info) to SimpleBooleanProperty(objectGroupTrendSelected),
                ShortLivedObjectsHeapEvolutionAnalysisMethod(info) to SimpleBooleanProperty(shortLivedObjectsSelected))
        heapEvolutionAnalysisMethods[DataStructureDevelopmentHeapEvolutionAnalysisMethod(info)] = SimpleBooleanProperty(dataStructureDevelopmentSelected)

        // generate configuration section for each diffing method
        heapEvolutionAnalysisMethods.forEach { entry ->
            // checkbox to select a diffing method
            val checkBox = CheckBox(entry.key.name +
                                            if (entry.key.description.length > 0) {
                                                ":"
                                            } else {
                                                ""
                                            })
            checkBox.isWrapText = true

            // keep checkboxes and map consistent
            checkBox.selectedProperty().bindBidirectional(heapEvolutionAnalysisMethods[entry.key])

            // clicking the checkbox enables or disables the settings
            entry.key.settingsPane?.disableProperty()?.bind(checkBox.selectedProperty().not())

            // init checkbox to current map value
            checkBox.isSelected = entry.value.get()

            // enable or disable settings depending on map value
//            entry.key.settingsPane?.isDisable = !entry.value.get()

            // wrap all into VBox
            val heapEvolutionAnalysisMethodConfigurationSection = VBox(20.0,
                                                                       HBox(10.0,
                                                                            checkBox,
                                                                            Label(entry.key.description).apply {
                                                                                isWrapText = true
                                                                                onMouseClicked = EventHandler { checkBox.isSelected = !checkBox.isSelected }
                                                                            }
                                                                       )
            )
            // add a settings pane if present
            entry.key.settingsPane?.also {
                val settingsWrapper = TitledPane("Settings", it)
                settingsWrapper.isExpanded = false
                // show hide settings when diffing method is selected/unselected
                checkBox.selectedProperty().addListener { obs, wasSelected, isSelected ->
                    settingsWrapper.isExpanded = isSelected
                }
                heapEvolutionAnalysisMethodConfigurationSection.children.add(settingsWrapper)
            }

            // add border to vbox
            heapEvolutionAnalysisMethodConfigurationSection.padding = Insets(15.0, 15.0, 15.0, 15.0)
            heapEvolutionAnalysisMethodConfigurationSection.style = "-fx-border-color: black"

            // add to tab
            heapEvolutionAnalysisMethodsConfigurationSections.children.add(heapEvolutionAnalysisMethodConfigurationSection)
        }

        // update the boolean property that enabled 'start diffing' action
        atLeastOneHeapEvolutionAnalysisMethodSelected.bind(Bindings.createBooleanBinding({ heapEvolutionAnalysisMethods.values.any { it.get() } }, heapEvolutionAnalysisMethods.values.toTypedArray()))

        // init chart
        chart.init(ReducedXYChartPane.Companion.Unit.BYTES, appInfo, startStatistics.info.time.toDouble(), endStatistics.info.time.toDouble())
        chart.plot(appInfo)
    }

    fun selectHeapEvolutionAnalysisMethods(permBornDiedTempSelected: Boolean,
                                           objectGroupTrendSelected: Boolean,
                                           dataStructureDevelopmentSelected: Boolean,
                                           shortLivedObjectsSelected: Boolean) {
        heapEvolutionAnalysisMethods.entries.forEach {
            when (it.key) {
                is PermBornDiedTempHeapEvolutionAnalysisMethod -> it.value.set(permBornDiedTempSelected)
                is HeapEvolutionVisualizationAnalysisMethod -> it.value.set(objectGroupTrendSelected)
                is DataStructureDevelopmentHeapEvolutionAnalysisMethod -> it.value.set(dataStructureDevelopmentSelected)
                is ShortLivedObjectsHeapEvolutionAnalysisMethod -> it.value.set(shortLivedObjectsSelected)
            }
        }
    }

    fun runHeapEvolutionAnalysis() {
        if (!atLeastOneHeapEvolutionAnalysisMethodSelected.get()) {
            Notifications.create()
                    .title("Can't run heap evolution analysis!")
                    .text("You have to select at least one heap evolution analysis method to apply!")
                    .showError()

        } else if (heapEvolutionAnalysisRunning.get()) {
            Notifications.create()
                    .title("Can't run heap evolution analysis!")
                    .text("A heap evolution analysis is still running! Please cancel it or wait until it completes before starting a new one.")
                    .showError()

        } else if (heapEvolutionAnalysisMethods.filter { it.value.get() }.all { it.key.parseAndCheckSettings() }) {
            // parsed the setting of all selected diffing methods (can't update after parsing started) and made sure that they are valid
            // clear previous tabs
            this.childTabs.clear()

            info.reset()
            heapEvolutionAnalysisMethods.keys.forEach { method -> method.initInfo() }

            // add the tabs displaying the analysis results
            val tabsToAdd = heapEvolutionAnalysisMethods.filter { it.value.get() }.flatMap { it.key.createTabs() }
            childTabs.addAll(tabsToAdd)
            ClientInfo.mainFrame.selectTab(tabsToAdd.first())

            // run the parser task
            val parserTask = HeapEvolutionParserTask(info, this)
            heapEvolutionAnalysisRunning.bind(parserTask.runningProperty())
            tasks.add(parserTask)
            ThreadUtil.startTask(parserTask)
        }
    }

    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {

    }

    override fun cleanupOnClose() {

    }
}