
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.util.ImagePack;

@C(name = "Space",
        desc = "This classifier distinguishes objects based on their containing space. This space is depending on the used garbage collector and the age of the object.",
        example = "Space #0 @ 0xC0000000 - 0xCA700000 (Length: 175112192) (Mode: NORMAL, Type: OLD)",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.ALL)
public class SpaceClassifier extends Classifier<String> {

    @Override
    public String classify() {
        return space().toString();
    }

    @Override
    public ImagePack[] loadIcons() {
        return new ImagePack[]{ImageUtil.getResourceImagePack("Space", "space.png")};
    }
}
