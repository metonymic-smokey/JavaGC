
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.util.ImagePack;

@C(name = "Allocating Subsystem",
        desc = "This classifier distinguishes objects based on the system which allocated the object. This may be \"VM\", \"Interpreter\", \"C1 Compiler\" or \"C2 " + "Compiler\"",
        example = "C2 compiler",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.ALL)
public class AllocatingSubsystemClassifier extends Classifier<String> {
    public static final int VM_ID = 0;
    public static final String VM = "VM-Internal Code";
    public static final int IR_ID = 1;
    public static final String IR = "Interpreted Code";
    public static final int C1_ID = 2;
    public static final String C1 = "C1-compiled Code";
    public static final int C2_ID = 3;
    public static final String C2 = "C2-compiled Code";

    @Override
    public String classify() {
        switch (eventType()) {
            case OBJ_ALLOC_FAST_C1:
            case OBJ_ALLOC_FAST_C1_DEVIANT_TYPE:
            case OBJ_ALLOC_NORMAL_C1:
            case OBJ_ALLOC_SLOW_C1:
            case OBJ_ALLOC_SLOW_C1_DEVIANT_TYPE:
                return C1;
            case OBJ_ALLOC_FAST_C2:
            case OBJ_ALLOC_FAST_C2_DEVIANT_TYPE:
            case OBJ_ALLOC_NORMAL_C2:
            case OBJ_ALLOC_SLOW_C2:
            case OBJ_ALLOC_SLOW_C2_DEVIANT_TYPE:
                return C2;
            case OBJ_ALLOC_FAST_IR:
            case OBJ_ALLOC_NORMAL_IR:
            case OBJ_ALLOC_SLOW_IR:
            case OBJ_ALLOC_SLOW_IR_DEVIANT_TYPE:
                return IR;
            case OBJ_ALLOC_SLOW:
                return VM;
            default:
                return "";
        }
    }

    @Override
    public ImagePack[] loadIcons() {
        return new ImagePack[]{ImageUtil.getResourceImagePack("Allocating subsystem", "subsystem.png")};
    }
}
