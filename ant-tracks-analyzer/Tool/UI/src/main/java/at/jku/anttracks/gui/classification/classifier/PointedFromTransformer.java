
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Transformer;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.classification.nodes.FastHeapGroupingNode;
import at.jku.anttracks.classification.nodes.GroupingNode;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.objects.ObjectInfoCache;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.util.ImagePack;

import java.util.function.Supplier;

@C(name = "Pointed From",
        desc = "This classifier is used to classify the objects that point to a given object.",
        example = "(using \"Pointed from Type\") Pointed From -> HashMap#Node",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.FASTHEAP)
public class PointedFromTransformer extends Transformer {

    @ClassifierProperty(overviewLevel = 10)
    protected ClassifierChain classifiers = new ClassifierChain(new TypeClassifier());

    public ClassifierChain getClassifiers() {
        return classifiers;
    }

    public void setClassifiers(ClassifierChain classifiers) {
        this.classifiers = classifiers;
        if (classifiers != null && classifiers.length() > 0) {
            classifiers.getList().forEach(classifier -> classifier.setup(() -> symbols(), () -> fastHeap()));
        }
    }

    ObjectInfoCache objInfoCache = new ObjectInfoCache();

    @Override
    public void setup(Supplier<Symbols> symbolsSupplier, Supplier<IndexBasedHeap> fastHeapSupplier) {
        super.setup(symbolsSupplier, fastHeapSupplier);
        if (classifiers != null && classifiers.length() > 0) {
            classifiers.getList().forEach(classifier -> classifier.setup(symbolsSupplier, fastHeapSupplier));
        }
    }

    @Override
    public GroupingNode classify(GroupingNode g) throws Exception {
        int[] pointedFrom = pointedFromIndices();
        if (pointedFrom != null && pointedFrom.length > 0) {
            for (int i = 0; i < pointedFrom.length; i++) {
                ((FastHeapGroupingNode) g).classify(fastHeap(), pointedFrom[i], classifiers);
            }
        }
        return g;
    }

    @Override
    public String title() {
        return Classifier.CLASSIFICATION_MODE == ClassificationMode.LIST ? "Pointed From (# Objects)" : "Pointers From (# Pointers)";
    }

    public ImagePack[] loadIcons() {
        return new ImagePack[]{ImageUtil.getResourceImagePack("Pointed from", "pointed_from.png")};
    }
}
