
package at.jku.anttracks.gui.frame.main.tab.heapstate.component.expertconfigurationpane

import at.jku.anttracks.classification.Classifier
import at.jku.anttracks.classification.ClassifierFactory
import at.jku.anttracks.classification.Filter
import at.jku.anttracks.classification.FilterFactory
import at.jku.anttracks.gui.classification.component.configurationpane.ConfigurationPane
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane.ClassificationSelectionListener
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.model.IAvailableClassifierInfo
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import org.controlsfx.control.PopOver

class ExpertConfigurationPane : ConfigurationPane() {
    val validClassifierSelection: BooleanProperty = SimpleBooleanProperty(false)

    override val componentDescriptions by lazy {
        super.componentDescriptions + listOf(Triple(filterSelectionPane.available.children[3],
                                                    Description("Predefined available filters")
                                                            .linebreak()
                                                            .appendDefault("Hover them for a description"),
                                                    PopOver.ArrowLocation.BOTTOM_LEFT),
                                             Triple(filterSelectionPane.availableAddButton,
                                                    Description("Define your own filters"),
                                                    PopOver.ArrowLocation.BOTTOM_LEFT),
                                             Triple(classifierSelectionPane.availableSingleCardinalitySubPane.children[4],
                                                    Description("Predefined available classifiers")
                                                            .linebreak()
                                                            .appendDefault("Hover them for a description"),
                                                    PopOver.ArrowLocation.BOTTOM_LEFT),
                                             Triple(classifierSelectionPane.availableAddButton,
                                                    Description("Define your own classifiers"),
                                                    PopOver.ArrowLocation.BOTTOM_LEFT))
    }

    init {
        FXMLUtil.load(this, ExpertConfigurationPane::class.java)
    }

    fun init(appInfo: AppInfo, availableClassifierInfo: IAvailableClassifierInfo) {
        super.init(appInfo, availableClassifierInfo, Mode.CONFIGURE, false)
    }

    override fun createClassifierListener(): ClassificationSelectionListener<Classifier<*>, ClassifierFactory> {
        return object : ClassificationSelectionListener<Classifier<*>, ClassifierFactory> {
            override fun selected(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>, x: Classifier<*>) {
                validClassifierSelection.set(true)
            }

            override fun deselected(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>, x: Classifier<*>) {
                validClassifierSelection.set(sender.selected.isNotEmpty())
            }

            override fun propertiesChanged(sender: ClassificationSelectionPane<Classifier<*>, ClassifierFactory>, x: Classifier<*>) {
                // Nothing to do if property changed
            }
        }
    }

    override fun createFilterListener(): ClassificationSelectionListener<Filter, FilterFactory> {
        return ClassificationSelectionListener.NOOP_FILTER_LISTENER
    }
}
