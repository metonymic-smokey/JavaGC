
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;

@C(name = "Heap Statistics",
        desc = "This classifier does no classifiaction at all and is only used to obtain heap statistics (# of object & # of bytes in heap)",
        example = "All objects",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.ALL)
public class OverallClassifier extends Classifier<String> {

    @Override
    public String classify() {
        // Classifiy every object with the same identifier
        return "All objects";
    }
}
