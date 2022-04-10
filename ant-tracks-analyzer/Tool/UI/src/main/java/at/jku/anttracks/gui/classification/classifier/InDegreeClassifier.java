
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierException;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.heap.roots.RootPtr;

import java.util.List;

@C(name = "In Degree",
        desc = "This classifier distinguishes objects based on the number of pointer that point to them.",
        example = "4",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.FASTHEAP)
public class InDegreeClassifier extends Classifier<String> {

    @ClassifierProperty(overviewLevel = 10)
    private boolean checkForRootPointer = true;

    public boolean getCheckForRootPointer() {
        return checkForRootPointer;
    }

    public void setCheckForRootPointer(boolean showNulls) {
        this.checkForRootPointer = showNulls;
    }

    @Override
    public String classify() throws ClassifierException {
        boolean rootPointed = false;
        if (checkForRootPointer) {
            List<? extends RootPtr> roots = rootPointers();
            rootPointed = roots != null && !roots.isEmpty();
        }
        int[] fromPointerIndices = pointedFromIndices();
        return (fromPointerIndices == null ? "Unknown" : String.valueOf(fromPointerIndices.length)) + (checkForRootPointer ?
                                                                                                       (rootPointed ? " (root pointed)" : " (not root pointed)") :
                                                                                                       "");
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
