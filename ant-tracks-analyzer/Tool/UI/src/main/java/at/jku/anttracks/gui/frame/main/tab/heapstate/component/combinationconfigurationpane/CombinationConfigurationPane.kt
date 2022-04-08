
package at.jku.anttracks.gui.frame.main.tab.heapstate.component.combinationconfigurationpane

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierFactory
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.FilterFactory
import at.jku.anttracks.gui.classification.component.configurationpane.ConfigurationPane
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane.ClassificationSelectionListener
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.IAvailableClassifierInfo
import at.jku.anttracks.gui.utils.FXMLUtil

class CombinationConfigurationPane : ConfigurationPane() {
    init {
        FXMLUtil.load(this, CombinationConfigurationPane::class.java)
    }

    fun init(appInfo: AppInfo,
             availableClassifierInfo: IAvailableClassifierInfo) {
        super.init(appInfo, availableClassifierInfo, Mode.ANALYSIS, false)
        classifierSelectionPane.selectedLabelVisible = false;
        filterSelectionPane.selectedLabelVisible = false;
    }

    override fun createClassifierListener(): ClassificationSelectionListener<Classifier<*>, ClassifierFactory> {
        return ClassificationSelectionListener.NO_OP_CLASSIFIER_LISTENER
    }

    override fun createFilterListener(): ClassificationSelectionListener<Filter, FilterFactory> {
        return ClassificationSelectionListener.NOOP_FILTER_LISTENER
    }
}
