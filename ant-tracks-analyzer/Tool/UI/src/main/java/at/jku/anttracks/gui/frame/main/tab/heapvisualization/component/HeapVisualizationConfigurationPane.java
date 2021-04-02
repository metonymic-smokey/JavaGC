package at.jku.anttracks.gui.frame.main.tab.heapvisualization.component;

import at.jku.anttracks.classification.*;
import at.jku.anttracks.gui.classification.component.configurationpane.ConfigurationPane;
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane;
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane.ClassificationSelectionListener;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.HeapVisualizationTab;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.IAvailableClassifierInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import javafx.fxml.FXML;

public class HeapVisualizationConfigurationPane extends ConfigurationPane {

    private HeapVisualizationTab heapVisualizationTab;

    @FXML
    private HeapVisualizationOperationPane actionPanel;

    public HeapVisualizationConfigurationPane() {
        FXMLUtil.load(this, HeapVisualizationConfigurationPane.class);
    }

    public void init(AppInfo appInfo,
                     IAvailableClassifierInfo availableClassifierInfo,
                     HeapVisualizationTab heapVisualizationTab) {
        super.init(appInfo, availableClassifierInfo, Mode.SWITCHABLE, true);
        this.heapVisualizationTab = heapVisualizationTab;
        actionPanel.init(heapVisualizationTab);
    }

    @Override
    protected ClassificationSelectionListener<Classifier<?>, ClassifierFactory> createClassifierListener() {
        return new ClassificationSelectionListener<Classifier<?>, ClassifierFactory>() {
            @Override
            public void selected(ClassificationSelectionPane<Classifier<?>, ClassifierFactory> sender, Classifier<?> classifier) {
                heapVisualizationTab.classifiersChanged(new ClassifierChain(sender.getSelected()));
            }

            @Override
            public void deselected(ClassificationSelectionPane<Classifier<?>, ClassifierFactory> sender, Classifier<?> classifier) {
                heapVisualizationTab.classifiersChanged(new ClassifierChain(sender.getSelected()));
            }

            @Override
            public void propertiesChanged(ClassificationSelectionPane<Classifier<?>, ClassifierFactory> sender, Classifier<?> x) {
                // Also restart visualization if properties changed
                heapVisualizationTab.classifiersChanged(new ClassifierChain(sender.getSelected()));
            }
        };
    }

    @Override
    protected ClassificationSelectionListener<Filter, FilterFactory> createFilterListener() {
        return new ClassificationSelectionListener<Filter, FilterFactory>() {
            @Override
            public void selected(ClassificationSelectionPane<Filter, FilterFactory> sender, Filter x) {
                heapVisualizationTab.filtersChanged(sender.getSelected());
            }

            @Override
            public void deselected(ClassificationSelectionPane<Filter, FilterFactory> sender, Filter x) {
                heapVisualizationTab.filtersChanged(sender.getSelected());
            }

            @Override
            public void propertiesChanged(ClassificationSelectionPane<Filter, FilterFactory> sender, Filter x) {
                // Also restart visualization if properties changed
                heapVisualizationTab.filtersChanged(sender.getSelected());
            }
        };
    }

    @Override
    public void switchMode() {
        super.switchMode();
        if (actionPanel != null) {
            actionPanel.setVisible(isConfigureMode());
            actionPanel.setManaged(isConfigureMode());
        }
        // repaint();
        // revalidate();
    }
}
