
package at.jku.anttracks.gui.model;

import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.trees.ClassificationTree;

import java.util.List;

public class HeapVisualizationStatisticsInfo extends SelectedClassifierInfo {
    DetailedHeapInfo detailsInfo;
    private ClassificationTree grouping;

    public HeapVisualizationStatisticsInfo(DetailedHeapInfo detailsInfo,
                                           ClassifierChain selectedClassifier,
                                           List<Filter> selectedFilters) {
        super(selectedClassifier, selectedFilters);
        this.detailsInfo = detailsInfo;
    }

    public DetailedHeapInfo getDetailsInfo() {
        return detailsInfo;
    }

    public void setGrouping(ClassificationTree grouping) {
        this.grouping = grouping;
    }

    public ClassificationTree getGrouping() {
        return grouping;
    }
}
