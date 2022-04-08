package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierException;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.util.TraceException;

@C(name = "Collection Health",
        desc = "Based on 'The causes of bloat, the limits of health' by Mitchell and Sevitsky. Classifies each object based on its role in larger collections or data structures",
        example = "Either 'contained', 'head', 'array', or 'entry'",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.FASTHEAP)
public class CollectionHealthClassifier extends Classifier<String> {

    @Override
    public String classify() throws TraceException, ClassifierException {
        AllocatedType type = type();
        if (shouldClassifyAsArray(isArray(), type)) {
            return "array";
        }

        int[] pointsTo = pointsToIndices();
        if (pointsTo != null && pointsTo.length != 0) {
            if (shouldClassifyAsEntry(pointsTo, type)) {
                return "entry";
            }

            if (shouldClassifyAsHead(pointsTo)) {
                return "head";
            }

        }
        return "contained";
    }

    private boolean shouldClassifyAsHead(int[] ptrs) throws TraceException {
        for (int i = 0; i < ptrs.length; i++) {
            int ptr = ptrs[i];
            if (ptr >= 0) {

                int[] ptrPtrs = fastHeap().getToPointers(ptr);
                boolean isArray = fastHeap().isArray(ptr);
                AllocatedType type = fastHeap().getType(ptr);

                if (shouldClassifyAsArray(isArray, type) || isPrimitiveTypeArray(isArray, type) || shouldClassifyAsEntry(ptrPtrs, type)) {
                    return true;
                }
            }
        }
        return false;
    }

    // If any of the referenced objects is of the same type as the given one, the object is considered as "entry"
    private boolean shouldClassifyAsEntry(int[] pointers, AllocatedType type) throws TraceException {
        for (int i = 0; i < pointers.length; i++) {
            int ptrIndex = pointers[i];
            if (ptrIndex >= 0) {
                if (fastHeap().getType(ptrIndex).equals(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean shouldClassifyAsArray(boolean isArray, AllocatedType type) {
        return isReferenceTypeArray(isArray, type);
    }

    private boolean isReferenceTypeArray(boolean isArray, AllocatedType type) {
        // Arrays with name length == 2 are primitive type arrays, e.g., [L
        return isArray && type.internalName.length() != 2;
    }

    private boolean isPrimitiveTypeArray(boolean isArray, AllocatedType type) {
        // Arrays with name length == 2 are primitive type arrays, e.g., [L
        return isArray && type.internalName.length() == 2;
    }
}