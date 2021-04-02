
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.util.ImagePack;

@C(name = "Array Length",
        desc = "This classifier distinguishes arrays based on their length. Non-array objects return -1 as length.",
        example = "14",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.ALL)
public class ArrayLengthClassifier extends Classifier<Integer> {

    @Override
    public Integer classify() {
        return arrayLength();
    }

    @Override
    public ImagePack[] loadIcons() {
        return new ImagePack[]{ImageUtil.getResourceImagePack("Array length", "arraylength.png")};
    }
}
