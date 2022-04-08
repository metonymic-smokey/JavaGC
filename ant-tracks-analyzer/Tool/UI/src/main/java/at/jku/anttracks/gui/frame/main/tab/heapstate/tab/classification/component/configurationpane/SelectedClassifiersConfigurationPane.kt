
package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.configurationpane

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierFactory
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.FilterFactory
import at.jku.anttracks.gui.classification.component.configurationpane.ConfigurationPane
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane.ClassificationSelectionListener
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.IAvailableClassifierInfo
import at.jku.anttracks.gui.utils.FXMLUtil

class SelectedClassifiersConfigurationPane : ConfigurationPane() {
    init {
        FXMLUtil.load(this, SelectedClassifiersConfigurationPane::class.java)
    }

    // Is used to forward PropertyChanged events to allow reclassification of tree
    private lateinit var classifierListener: ClassificationSelectionPane.ClassificationSelectionListener<Classifier<*>, ClassifierFactory>
    private lateinit var filterListener: ClassificationSelectionListener<Filter, FilterFactory>

    fun init(appInfo: AppInfo,
             availableClassifierInfo: IAvailableClassifierInfo,
             classifierListener: ClassificationSelectionListener<Classifier<*>, ClassifierFactory>,
             filterListener: ClassificationSelectionListener<Filter, FilterFactory>) {
        this.classifierListener = classifierListener
        this.filterListener = filterListener
        super.init(appInfo, availableClassifierInfo, Mode.ANALYSIS, false)
        classifierSelectionPane.selectedLabelVisible = false;
        filterSelectionPane.selectedLabelVisible = false;
    }

    override fun createClassifierListener(): ClassificationSelectionListener<Classifier<*>, ClassifierFactory> {
        return classifierListener
    }

    override fun createFilterListener(): ClassificationSelectionListener<Filter, FilterFactory> {
        return filterListener
    }
}
