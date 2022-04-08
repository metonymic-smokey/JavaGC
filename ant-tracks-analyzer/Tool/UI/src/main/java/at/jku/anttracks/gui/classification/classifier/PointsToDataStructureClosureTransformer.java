package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.ClassifierException;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;

import java.util.BitSet;

@C(name = "Points To (DS Closure)",
        desc = "This transformer is used to classify all objects that are part of the data structure of the given head object.",
        example = "(using \"Type\") Points To (DS Closure) -> Object[] & String",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.FASTHEAP)
public class PointsToDataStructureClosureTransformer extends AbstractPointsToClosureTransformer {

    @ClassifierProperty(overviewLevel = 10)
    protected boolean deepDataStructureClosure = false;

    public boolean getDeepDataStructureClosure() {
        return deepDataStructureClosure;
    }

    public void setDeepDataStructureClosure(boolean deepDataStructureClosure) {
        this.deepDataStructureClosure = deepDataStructureClosure;
    }

    @Override
    protected BitSet closure() throws ClassifierException {
        return deepDataStructureClosure ?
               closures().getDeepDataStructureClosure() :
               closures().getDataStructureClosure();
    }
}
