package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.component.configurationpane

import at.jku.anttracks.classification.*
import at.jku.anttracks.gui.classification.classifier.AllocationSiteClassifier
import at.jku.anttracks.gui.classification.component.configurationpane.ConfigurationPane
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane
import at.jku.anttracks.gui.classification.filter.OnlyDataStructureHeadsFilter
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.tab.classification.PermBornDiedTempClassificationTab
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.fxml.FXML
import javafx.scene.control.Button

class PermBornDiedTempClassificationConfigurationPane : ConfigurationPane() {

    @FXML
    private lateinit var applyButton: Button
    @FXML
    private lateinit var cancelButton: Button

    private lateinit var permBornDiedTempClassificationTab: PermBornDiedTempClassificationTab
    private lateinit var info: PermBornDiedTempInfo

    init {
        FXMLUtil.load(this, PermBornDiedTempClassificationConfigurationPane::class.java)
    }

    fun init(permBornDiedTempClassificationTab: PermBornDiedTempClassificationTab, info: PermBornDiedTempInfo) {
        super.init(info.heapEvolutionInfo.appInfo, info.heapEvolutionInfo, Mode.SWITCHABLE, false)
        this.permBornDiedTempClassificationTab = permBornDiedTempClassificationTab
        this.info = info

        // default classifier is type and allocation site
        classifierSelectionPane.resetSelected(listOf(info.heapEvolutionInfo.availableClassifier["Type"],
                                                     info.heapEvolutionInfo.availableClassifier["Allocation Site"].also { (it as AllocationSiteClassifier).style = AllocationSiteClassifier.AllocationSiteStyle.Shortest },
                                                     info.heapEvolutionInfo.availableClassifier["Call Sites"]))
        info.selectedClassifiers = ClassifierChain(info.heapEvolutionInfo.availableClassifier["Type"],
                                                   info.heapEvolutionInfo.availableClassifier["Allocation Site"],
                                                   info.heapEvolutionInfo.availableClassifier["Call Sites"])

        // button behaviour
        cancelButton.setOnAction {
            classifierSelectionPane.resetSelected(info.selectedClassifiers.list)
            filterSelectionPane.resetSelected(info.selectedFilters)
            switchMode()
        }
        applyButton.setOnAction {
            classify()
            switchMode()
        }

        dataStructureSwitch.selectedProperty().addListener { obs, wasSelected, isSelected ->
            if (isSelected) {
                filterSelectionPane.selected.add(info.heapEvolutionInfo.availableFilter[OnlyDataStructureHeadsFilter.NAME])
            } else {
                filterSelectionPane.selected.removeIf { it is OnlyDataStructureHeadsFilter }
            }
            classify()
        }

        // start in minimized mode
        switchMode()
    }

    fun classify() {
        // while diffing is still running it suffices to adjust the active classifiers, on the next diffing update the classification will be adjusted accordingly
        info.selectedClassifiers = ClassifierChain(classifierSelectionPane.selected)
        info.selectedFilters = filterSelectionPane.selected

        if (info.heapEvolutionAnalysisCompleted) {
            // perform classification
            permBornDiedTempClassificationTab.classify()
        }
    }

    override fun createClassifierListener(): ClassificationSelectionPane.ClassificationSelectionListener<Classifier<*>, ClassifierFactory> {
        return object : ClassificationSelectionPane.ClassificationSelectionListener<Classifier<*>, ClassifierFactory> {
            override fun selected(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>, x: Classifier<*>) {
                applyButton.isDisable = false
            }

            override fun deselected(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>, x: Classifier<*>) {
                applyButton.isDisable = sender.selected.isEmpty()
            }

            override fun propertiesChanged(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>, x: Classifier<*>) {
                // Nothing to do if property changed
            }
        }
    }

    override fun createFilterListener(): ClassificationSelectionPane.ClassificationSelectionListener<Filter, FilterFactory> {
        return ClassificationSelectionPane.ClassificationSelectionListener.NOOP_FILTER_LISTENER
    }

    override fun switchMode() {
        super.switchMode()
        applyButton.isVisible = isConfigureMode
        applyButton.isManaged = isConfigureMode
        cancelButton.isVisible = isConfigureMode
        cancelButton.isManaged = isConfigureMode
    }
}
