
package at.jku.anttracks.gui.model;

import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;

import java.util.List;

/**
 * @author Christina Rammerstorfer
 */
public class TimelapseStatisticsInfo extends SelectedClassifierInfo {

    public final TimelapseInfo timelapseInfo;

    public TimelapseStatisticsInfo(TimelapseInfo info) {
        super(null, null);
        this.timelapseInfo = info;
    }

    public TimelapseStatisticsInfo(TimelapseInfo info,
                                   ClassifierChain selectedClassifier,
                                   List<Filter> selectedFilters) {
        super(selectedClassifier, selectedFilters);
        this.timelapseInfo = info;
    }

}
