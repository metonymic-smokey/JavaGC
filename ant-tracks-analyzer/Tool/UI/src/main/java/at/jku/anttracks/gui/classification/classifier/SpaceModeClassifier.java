
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.heap.space.SpaceMode;
import at.jku.anttracks.util.ImagePack;

@C(name = "Space Mode",
        desc = "This classifier distinguishes objects based on their containing space mode.",
        example = "HUMONGOUS_STARTING",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.ALL)
public class SpaceModeClassifier extends Classifier<SpaceMode> {

    @Override
    public SpaceMode classify() {
        return space().getMode();
    }

    @Override
    public ImagePack[] loadIcons() {
        return new ImagePack[]{ImageUtil.getResourceImagePack("Space mode", "space_mode.png")};
    }

}
