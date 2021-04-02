package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.component.configurationpane

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierFactory
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.FilterFactory
import at.jku.anttracks.gui.classification.component.configurationpane.ConfigurationPane
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.model.ShortLivedObjectsInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.tab.classification.ShortLivedObjectsClassificationTab
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.fxml.FXML
import javafx.scene.control.Button

class ShortLivedObjectsClassificationConfigurationPane : ConfigurationPane() {

    @FXML
    private lateinit var applyButton: Button
    @FXML
    private lateinit var cancelButton: Button

    private lateinit var tab: ShortLivedObjectsClassificationTab
    private lateinit var info: ShortLivedObjectsInfo

    init {
        FXMLUtil.load(this, ShortLivedObjectsClassificationConfigurationPane::class.java)
    }

    fun init(info: ShortLivedObjectsInfo, tab: ShortLivedObjectsClassificationTab) {
        super.init(info.heapEvolutionInfo.appInfo, info.heapEvolutionInfo, Mode.SWITCHABLE, false)

        this.tab = tab
        this.info = info

        // disable data structure switch, we dont have data structure information anyway...
        dataStructureSwitch.isDisable = true

        // set default classifier chain
        selectTypeClassifierChain()

        // button behaviour
        cancelButton.setOnAction {
            classifierSelectionPane.resetSelected(info.selectedClassifiers.list)
            filterSelectionPane.resetSelected(info.selectedFilters)
            switchMode()
        }
        applyButton.setOnAction {
            tab.updateClassification()
            switchMode()
        }

        // start in minimized mode
        switchMode()
    }

    override fun createClassifierListener(): ClassificationSelectionPane.ClassificationSelectionListener<Classifier<*>, ClassifierFactory> {
        return object : ClassificationSelectionPane.ClassificationSelectionListener<Classifier<*>, ClassifierFactory> {
            override fun selected(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>,
                                  x: Classifier<*>) {
                applyButton.isDisable = false
            }

            override fun deselected(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>,
                                    x: Classifier<*>) {
                applyButton.isDisable = sender.selected.isEmpty()
            }

            override fun propertiesChanged(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>,
                                           x: Classifier<*>) {
                tab.updateClassification()
            }
        }
    }

    override fun createFilterListener(): ClassificationSelectionPane.ClassificationSelectionListener<Filter, FilterFactory> {
        return object : ClassificationSelectionPane.ClassificationSelectionListener<Filter, FilterFactory> {
            override fun selected(sender: ClassificationSelectionPane<Filter, FilterFactory>?, x: Filter?) {}

            override fun deselected(sender: ClassificationSelectionPane<Filter, FilterFactory>?, x: Filter?) {}

            override fun propertiesChanged(sender: ClassificationSelectionPane<Filter, FilterFactory>?, x: Filter?) {
                tab.updateClassification()
            }

        }
    }

    override fun switchMode() {
        super.switchMode()
        applyButton.isVisible = isConfigureMode
        applyButton.isManaged = isConfigureMode
        cancelButton.isVisible = isConfigureMode
        cancelButton.isManaged = isConfigureMode
    }

    fun selectTypeClassifierChain() {
        classifierSelectionPane.resetSelected(info.typeClassifierChain.list)
        info.selectedClassifiers = info.typeClassifierChain
    }

    fun selectAllocSiteClassifierChain() {
        classifierSelectionPane.resetSelected(info.allocSiteClassifierChain.list)
        info.selectedClassifiers = info.allocSiteClassifierChain
    }

}