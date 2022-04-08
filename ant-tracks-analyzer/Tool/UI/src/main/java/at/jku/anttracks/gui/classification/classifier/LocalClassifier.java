package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;

@C(name = "Local classification",
        desc = "Classify specific nodes in a tree",
        example = "-",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.FASTHEAP)
public class LocalClassifier extends Classifier<String> {

    @ClassifierProperty(overviewLevel = 10)
    protected ClassifierChain classifiers = new ClassifierChain();

    public ClassifierChain getClassifiers() {
        return classifiers;
    }

    public void setClassifiers(ClassifierChain classifiers) {
        this.classifiers = classifiers;
    }

    @Override
    protected String classify() throws Exception {
        // this classifier is only used for easy classifier chain building (through classifier property dialog)
        return "you shouldn't see this - something went wrong - report to the master (eg)";
    }
}
