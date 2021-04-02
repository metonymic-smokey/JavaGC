
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.heap.space.SpaceType;
import at.jku.anttracks.util.ImagePack;

@C(name = "Space Type",
        desc = "This classifier distinguishes objects based on their containing space type.",
        example = "EDEN",
        type = ClassifierType.ONE, collection = ClassifierSourceCollection.ALL)
public class SpaceTypeClassifier extends Classifier<SpaceType> {

    @Override
    public SpaceType classify() {
        return space().getType();
    }

    @Override
    public ImagePack[] loadIcons() {
        return new ImagePack[]{ImageUtil.getResourceImagePack("Space type", "space_type.png")};
    }

}
