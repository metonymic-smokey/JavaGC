
package at.jku.anttracks.classification;

import at.jku.anttracks.classification.nodes.GroupingNode;
import at.jku.anttracks.classification.nodes.ListGroupingNode;
import at.jku.anttracks.classification.nodes.MapGroupingNode;
import at.jku.anttracks.classification.nodes.GroupingNode;
import at.jku.anttracks.classification.nodes.ListGroupingNode;
import at.jku.anttracks.classification.nodes.MapGroupingNode;

public abstract class Transformer extends Classifier<GroupingNode> {
    protected GroupingNode classify() throws Exception {
        return classify(baseGrouping());
    }

    protected abstract GroupingNode classify(GroupingNode base) throws Exception;

    public abstract String title();

    public GroupingNode baseGrouping() {
        GroupingNode base = null;

        try {
            switch (Classifier.CLASSIFICATION_MODE) {
                case MAP:
                    base = new MapGroupingNode(null, 0, 0, null, title());
                    break;

                case LIST:
                    base = new ListGroupingNode(null, 0, 0, null, title());
                    break;

                /*
                case FILE:
                    base = new FileGroupingNode(null, 0, 0, null, title());
                    break;
                    */
            }
        } catch (Exception e) {
            assert false : "Base grouping may not throw exception";
        }
        base.setClassifier(this.getClass());
        return base;
    }
}
