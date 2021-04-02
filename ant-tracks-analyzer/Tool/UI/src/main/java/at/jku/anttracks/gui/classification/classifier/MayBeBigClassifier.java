package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.util.ImagePack;

import java.util.Arrays;

@C(name = "May Be Big",
        desc = "This classifier distinguishes objects based on how many other objects the reach",
        example = "\"May be big\", \"Small\", or \"Pointerless\"",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.ALL)
public class MayBeBigClassifier extends Classifier<String> {

    @ClassifierProperty(overviewLevel = 10)
    protected int threshold = 1000;

    public int getThreshold() {
        return threshold;
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
    }

    @Override
    protected String classify() throws Exception {
        int size = 0;
        int[] directPoiters = pointsToIndices();

        if (directPoiters != null && directPoiters.length > 0) {
            size = directPoiters.length;
            if (size >= threshold) {
                return "May be big";
            }
            directPoiters = Arrays.copyOf(directPoiters, threshold);
            for (int i = 0; i < size; i++) {
                int[] toPtrs = fastHeap().getToPointers(directPoiters[i]);
                if (toPtrs != null && toPtrs.length > 0) {
                    for (int j = 0; j < toPtrs.length; j++) {
                        boolean add = true;
                        for (int k = 0; k < size; k++) {
                            if (directPoiters[k] == toPtrs[j]) {
                                add = false;
                                break;
                            }
                        }
                        if (add) {
                            directPoiters[size] = toPtrs[j];
                            size++;
                            if (size == directPoiters.length) {
                                return "May be big";
                            }
                        }
                    }
                }
            }
        } else {
            return "Pointerless";
        }

        return "Small";
    }

    @Override
    public ImagePack[] loadIcons() {
        // TODO
        return super.loadIcons();
    }
}
