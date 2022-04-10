
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.util.ImagePack;

import static at.jku.anttracks.util.Consts.ARRAY_SIZE_MAX_SMALL;

@C(name = "Object Kind",
        desc = "This classifier distinguishes objects based on their object kind. " +
                "This may either be \"Mirror\", i.e., Class<> objects, \"Instance\" for object instances, " +
                "\"Small Array\" for arrays with less than 255 elements, or \"Big Array\" for arrays with at least 255 elements",
        example = "Small Array",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.ALL)
public class ObjectKindsClassifier extends Classifier<String> {

    public static final String MIRROR = "Mirror";
    public static final String INSTANCES = "Instance";
    public static final String SMALL_ARRAY = "Small Array";
    public static final String BIG_ARRAY = "Big Array";

    @Override
    public String classify() {
        String id;

        if (isArray()) {
            if (arrayLength() < ARRAY_SIZE_MAX_SMALL) {
                id = SMALL_ARRAY;
            } else {
                id = BIG_ARRAY;
            }
        } else {
            if (type() instanceof AllocatedType.MirrorAllocatedType) {
                id = MIRROR;
            } else {
                id = INSTANCES;
            }
        }

        return id;
    }

    public ImagePack[] loadIcons() {
        return new ImagePack[]{ImageUtil.getResourceImagePack("Object kinds", "objectkinds.png")};
    }
}
