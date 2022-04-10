package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.ClassifierException;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;

import java.util.BitSet;

@C(name = "Points To (Closure)",
        desc = "This transformer is used to classify all objects that are directly or indirectly reachable by a given object.",
        example = "(using \"Type\") Points To (Closure) -> char[] & Integer",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.FASTHEAP)
public class PointsToClosureTransformer extends AbstractPointsToClosureTransformer {

    @Override
    protected BitSet closure() throws ClassifierException {
        return closures().getTransitiveClosure();
    }
}
