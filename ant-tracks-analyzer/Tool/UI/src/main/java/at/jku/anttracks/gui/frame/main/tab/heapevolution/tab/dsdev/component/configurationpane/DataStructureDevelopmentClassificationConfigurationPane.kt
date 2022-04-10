package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.component.configurationpane

import at.jku.anttracks.classification.*
import at.jku.anttracks.gui.classification.classifier.AddressClassifier
import at.jku.anttracks.gui.classification.classifier.TypeClassifier
import at.jku.anttracks.gui.classification.component.configurationpane.ConfigurationPane
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.DataStructureDevelopmentInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.tab.classification.DataStructureDevelopmentClassificationTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.task.DataStructureDevelopmentClassificationTask
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.util.ThreadUtil
import javafx.fxml.FXML
import javafx.scene.control.Button

class DataStructureDevelopmentClassificationConfigurationPane : ConfigurationPane() {

    @FXML
    private lateinit var applyButton: Button
    @FXML
    private lateinit var cancelButton: Button

    private lateinit var dataStructureDevelopmentOverviewClassificationTab: DataStructureDevelopmentClassificationTab
    private lateinit var info: DataStructureDevelopmentInfo

    init {
        FXMLUtil.load(this, DataStructureDevelopmentClassificationConfigurationPane::class.java)
    }

    fun init(dataStructureDevelopmentOverviewClassificationTab: DataStructureDevelopmentClassificationTab, info: DataStructureDevelopmentInfo) {
        super.init(info.heapEvolutionInfo.appInfo, info.heapEvolutionInfo, Mode.SWITCHABLE, false)
        this.dataStructureDevelopmentOverviewClassificationTab = dataStructureDevelopmentOverviewClassificationTab
        this.info = info

        // default classifier is address
        val addressClassifier = info.heapEvolutionInfo.availableClassifier[AddressClassifier::class] as AddressClassifier
        addressClassifier.additionalClassifier = info.heapEvolutionInfo.availableClassifier[TypeClassifier::class]
        classifierSelectionPane.addSelectedButNotShown(addressClassifier)

        dataStructureSwitch.isSelected = true
        dataStructureSwitch.isDisable = true

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

        // start in minimized mode
        switchMode()
    }

    fun classify() {
        info.selectedClassifiers = ClassifierChain(classifierSelectionPane.selected)
        info.selectedFilters = filterSelectionPane.selected

        // perform classification
        val groupingTask = DataStructureDevelopmentClassificationTask(info, dataStructureDevelopmentOverviewClassificationTab)
        dataStructureDevelopmentOverviewClassificationTab.tasks.add(groupingTask)
        ThreadUtil.startTask(groupingTask)
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
