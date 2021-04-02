
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.heap.datastructures.dsl.DSLDataStructure;
import at.jku.anttracks.util.ImagePack;

@C(name = "Role in data structure",
        desc = "This classifier distinguishes objects based what role they take in one of the recognized data structures",
        example = "'Backbone' or 'Not part of any data structure'",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.FASTHEAP)
public class RoleInDataStructureClassifier extends Classifier<String> {

    @ClassifierProperty(overviewLevel = 10)
    private boolean includeIndirectDataStructures = true;

    public boolean getIncludeIndirectDataStructures() {
        return includeIndirectDataStructures;
    }

    public void setIncludeIndirectDataStructures(boolean includeIndirectDataStructures) {
        this.includeIndirectDataStructures = includeIndirectDataStructures;
    }

    @Override
    public ImagePack[] loadIcons() {
        return new ImagePack[]{
                //                ImageUtil.getResourceImagePack("Data Structure Membership", "ds_mem.png")
        };
    }

    @Override
    public String classify() {
        if (DSLDataStructure.isDataStructureHead(index(), fastHeap())) {
            return "Head";
        } else if (fastHeap().getDataStructures(index(), includeIndirectDataStructures, false) != null) {
            return "Backbone or data";
        } else {
            return "Not part of any data structure";
        }
    }
}
