package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.ClassifierException;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;

import java.util.BitSet;

@C(name = "Points To (GC Closure)",
        desc = "This transformer is used to classify all objects that are directly and indirectly reachable by a given object, and only by the given object.",
        example = "(using \"Type\") Points To (Closure) -> char[] & Integer",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.FASTHEAP)
public class PointsToGCClosureTransformer extends AbstractPointsToClosureTransformer {

    @Override
    protected BitSet closure() throws ClassifierException {
        return closures().getGCClosure();
    }
}
