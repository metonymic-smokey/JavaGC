
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.util.ImagePack;

import java.util.Arrays;

@C(name = "Feature",
        desc = "A classifier that distinguishes objects based on their mapping to features (Features are supported if a feature mapping file is loaded for the current trace.",
        example = "MyFeature #4 & MyFeature #6",
        type = ClassifierType.MANY,
        collection = ClassifierSourceCollection.ALL)
public class FeatureClassifier extends Classifier<String[]> {

    private static final String[] nonFeature = new String[0];

    @Override
    public ImagePack[] loadIcons() {
        return new ImagePack[]{ImageUtil.getResourceImagePack("Feature", "feature.png")};
    }

    @Override
    public String[] classify() {
        if (symbols().featureCache != null) {
            ObjectInfo objInfo = objectInfo();
            return Arrays.stream(symbols().featureCache.match(objInfo)).mapToObj(id -> features().getFeature(id).name).toArray(String[]::new);
        } else {
            return nonFeature;
        }
    }
}
