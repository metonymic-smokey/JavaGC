
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierException;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;

import java.util.Arrays;

import static at.jku.anttracks.heap.FastHeap.NULL_INDEX;

@C(name = "Out Degree",
        desc = "This classifier distinguishes objects based on the number of their pointers.",
        example = "4",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.FASTHEAP)
public class OutDegreeClassifier extends Classifier<String> {

    @ClassifierProperty(overviewLevel = 10)
    private boolean alsoCountNullFields = false;

    public boolean getAlsoCountNullFields() {
        return alsoCountNullFields;
    }

    public void setAlsoCountNullFields(boolean showNulls) {
        this.alsoCountNullFields = showNulls;
    }

    @Override
    public String classify() throws ClassifierException {
        int[] toPointerIndices = pointsToIndices();
        return toPointerIndices == null ?
               "No pointer array available" :
               String.valueOf(alsoCountNullFields ? toPointerIndices.length : Arrays.stream(toPointerIndices).filter(x -> x != NULL_INDEX).count());
    }

    /*
    @Override
    public ImagePack[] loadIcons() {
        return new ImagePack[]{
                ImageUtil.getResourceImagePack("Address", "address.png")
        };
    }
    */
}
