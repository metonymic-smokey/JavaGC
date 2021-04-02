
package at.jku.anttracks.gui.frame.main.tab.timelapse.components

import at.jku.anttracks.classification.*
import at.jku.anttracks.gui.classification.component.configurationpane.ConfigurationPane
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane.ClassificationSelectionListener
import at.jku.anttracks.gui.frame.main.tab.timelapse.TimelapseTab
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.IAvailableClassifierInfo
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.fxml.FXML
import javafx.scene.layout.HBox

/**
 * @author Christina Rammerstorfer & Markus Weninger
 */
class TimelapseConfigurationPane : ConfigurationPane() {

    @FXML
    private lateinit var operationPane: HBox

    private var tab: TimelapseTab? = null

    init {
        FXMLUtil.load(this, TimelapseConfigurationPane::class.java)
    }

    fun init(tab: TimelapseTab, appInfo: AppInfo, availableClassifierInfo: IAvailableClassifierInfo) {
        super.init(appInfo, availableClassifierInfo, Mode.SWITCHABLE, false)
        this.tab = tab

        switchMode()
    }

    fun apply() {
        switchMode()
        tab!!.statisticsInfo.selectedClassifiers = ClassifierChain(classifierSelectionPane.selected)
        tab!!.statisticsInfo.selectedFilters = filterSelectionPane.selected
        tab!!.startNewWorker()
    }

    fun cancel() {
        switchMode()
        classifierSelectionPane.resetSelected(tab!!.statisticsInfo.selectedClassifiers.list)
        filterSelectionPane.resetSelected(tab!!.statisticsInfo.selectedFilters)
    }

    override fun switchMode() {
        super.switchMode()
        operationPane.isVisible = isConfigureMode
        operationPane.isManaged = isConfigureMode
    }

    override fun createClassifierListener(): ClassificationSelectionListener<Classifier<*>, ClassifierFactory> {
        return ClassificationSelectionListener.NO_OP_CLASSIFIER_LISTENER
    }

    override fun createFilterListener(): ClassificationSelectionListener<Filter, FilterFactory> {
        return ClassificationSelectionListener.NOOP_FILTER_LISTENER
    }
}
