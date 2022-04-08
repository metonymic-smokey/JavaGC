
package at.jku.anttracks.gui.frame.main.tab.heapstate.component.combinationselectionlistview

import at.jku.anttracks.classification.ClassifierChain
import at.jku.anttracks.gui.classification.Combination
import at.jku.anttracks.gui.classification.CombinationType
import at.jku.anttracks.gui.classification.DefaultCombinations
import at.jku.anttracks.gui.classification.component.configurationpane.ConfigurationPane
import at.jku.anttracks.gui.frame.main.tab.heapstate.component.combinationconfigurationpane.CombinationConfigurationPane
import at.jku.anttracks.gui.frame.main.tab.heapstate.component.expertconfigurationpane.ExpertConfigurationPane
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.IAvailableClassifierInfo
import at.jku.anttracks.gui.model.SelectedClassifierInfo
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.SimpleBooleanProperty
import javafx.event.EventHandler
import javafx.geometry.HPos
import javafx.geometry.Insets
import javafx.scene.control.Label
import javafx.scene.control.RadioButton
import javafx.scene.control.Separator
import javafx.scene.control.ToggleGroup
import javafx.scene.layout.ColumnConstraints
import javafx.scene.layout.GridPane
import javafx.scene.layout.Priority

class CombinationSelectionPane : GridPane() {
    // initialized in init()
    lateinit var expertConfigurationPane: ExpertConfigurationPane
    private lateinit var appInfo: AppInfo
    private lateinit var availableClassifierInfo: IAvailableClassifierInfo
    private lateinit var lastAcceptedClassifierInfo: SelectedClassifierInfo
    val selectedClassifierInfo
        get() = SelectedClassifierInfo(
                ClassifierChain(selectedConfigurationPane?.classifierSelectionPane?.selected),
                selectedConfigurationPane?.filterSelectionPane?.selected!!)

    val validClassifierSelection = SimpleBooleanProperty(true)

    private val radioButtonToggleGroup = ToggleGroup()
    private var selectedRadioButton: RadioButton? = null
    private var selectedConfigurationPane: ConfigurationPane? = null

    init {
        FXMLUtil.load(this, CombinationSelectionPane::class.java)

        this.columnConstraints.clear()
        this.columnConstraints.addAll(
                ColumnConstraints(180.0, 180.0, 220.0, Priority.ALWAYS, HPos.LEFT, true),
                ColumnConstraints(200.0, 200.0, Double.MAX_VALUE, Priority.ALWAYS, HPos.LEFT, true),
                ColumnConstraints(300.0, 300.0, 800.0, Priority.ALWAYS, HPos.LEFT, true)
        )
    }

    fun init(appInfo: AppInfo,
             availableClassifierInfo: IAvailableClassifierInfo,
             vararg combinationTypes: CombinationType) {
        this.appInfo = appInfo
        this.availableClassifierInfo = availableClassifierInfo

        addHeader()

        val defaultCombinations = DefaultCombinations(availableClassifierInfo)
        val selectedCombinations = combinationTypes.flatMap { type -> defaultCombinations[type] }
        selectedCombinations.forEachIndexed { index, combination ->
            addCombination(index + 1, combination)
        }
        addExpert(selectedCombinations.size + 1)

        resetExpertUI()
    }

    private fun addHeader() {
        addRow(0,
               Label("Combination Name").apply { style = "-fx-font-weight: bold;" },
               Label("Description").apply { style = "-fx-font-weight: bold;" },
               Label("Classifiers + Filters").apply { style = "-fx-font-weight: bold;" })
        add(Separator().apply {
            padding = Insets(15.0, 0.0, 15.0, 0.0)
        }, 0, 1, 3, 1)
    }

    private fun addCombination(index: Int, combination: Combination) {
        val configPane = CombinationConfigurationPane().apply {
            init(this@CombinationSelectionPane.appInfo, this@CombinationSelectionPane.availableClassifierInfo)
            classifierSelectionPane.resetSelected(combination.classifiers.list)
            filterSelectionPane.resetSelected(combination.filters)
            if (combination.dataStructureAnalysis) {
                dataStructureSwitch.fire()
            }
            dataStructureSwitch.isDisable = true
            switchToAnalysisMode()
        }
        val radioButton = RadioButton(combination.name).apply {
            toggleGroup = radioButtonToggleGroup
            isWrapText = true
            selectedProperty().addListener { _, _, newValue ->
                if (newValue) {
                    if (::expertConfigurationPane.isInitialized) {
                        expertConfigurationPane.classifierSelectionPane.isExpanded = false
                        expertConfigurationPane.filterSelectionPane.isExpanded = false
                    }
                    selectedRadioButton = this
                    selectedConfigurationPane = configPane
                    validClassifierSelection.unbind()
                    validClassifierSelection.set(true)
                }
            }
            // Select the very first combination (index 0 are the headings)
            if (index == 1) {
                isSelected = true
                selectedRadioButton = this
                selectedConfigurationPane = configPane
                lastAcceptedClassifierInfo = selectedClassifierInfo
            }
        }

        val descLabel = Label(combination.description).apply {
            isWrapText = true
            onMouseClicked = EventHandler { _ -> radioButton.selectedProperty().set(true) }
        }

        addRow(index * 2, radioButton, descLabel, configPane)
        add(Separator().apply {
            padding = Insets(15.0, 0.0, 15.0, 0.0)
        }, 0, index * 2 + 1, 3, 1)
    }

    private fun addExpert(index: Int) {
        expertConfigurationPane = ExpertConfigurationPane().apply {
            init(this@CombinationSelectionPane.appInfo, this@CombinationSelectionPane.availableClassifierInfo)
            classifierSelectionPane.resetSelected(listOf())
            filterSelectionPane.resetSelected(listOf())
            switchToConfigureMode()
            classifierSelectionPane.isExpanded = false
            filterSelectionPane.isExpanded = false
        }

        val radioButton = RadioButton("Expert").apply {
            toggleGroup = radioButtonToggleGroup
            isWrapText = true
            selectedProperty().addListener { _, _, newValue ->
                if (newValue) {
                    expertConfigurationPane.classifierSelectionPane.isExpanded = true
                    expertConfigurationPane.filterSelectionPane.isExpanded = true
                }
                selectedRadioButton = this
                selectedConfigurationPane = expertConfigurationPane
                validClassifierSelection.bind(expertConfigurationPane.validClassifierSelection)
            }
        }

        add(radioButton, 0, index * 2)
        add(expertConfigurationPane, 1, index * 2, 2, 1)
        add(Separator().apply {
            padding = Insets(15.0, 0.0, 15.0, 0.0)
        }, 0, index * 2 + 1, 3, 1)
    }

    fun resetExpertUI() {
        expertConfigurationPane.filterSelectionPane.resetSelected(lastAcceptedClassifierInfo.selectedFilters)
        expertConfigurationPane.classifierSelectionPane.resetSelected(lastAcceptedClassifierInfo.selectedClassifiers.list)
    }

    fun acceptEdit() {
        lastAcceptedClassifierInfo = selectedClassifierInfo
    }
}