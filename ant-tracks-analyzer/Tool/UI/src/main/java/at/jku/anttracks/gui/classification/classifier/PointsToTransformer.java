
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.ClassifierException;
import at.jku.anttracks.classification.Transformer;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.classification.nodes.FastHeapGroupingNode;
import at.jku.anttracks.classification.nodes.GroupingNode;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.util.ImagePack;

import java.util.function.Supplier;

@C(name = "Points To (Direct)",
        desc = "This transformer is used to classify the objects an object directly points to.",
        example = "(using \"Points to Type\") Points To -> char[] & Integer",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.FASTHEAP)
public class PointsToTransformer extends Transformer {

    @ClassifierProperty(overviewLevel = 10)
    protected ClassifierChain classifiers = new ClassifierChain(new TypeClassifier());

    public ClassifierChain getClassifiers() {
        return classifiers;
    }

    public void setClassifiers(ClassifierChain classifiers) {
        this.classifiers = classifiers;
        if (classifiers != null && classifiers.length() > 0) {
            classifiers.getList().forEach(classifier -> classifier.setup(this::symbols, this::fastHeap));
        }
    }

    private static String NULL_KEY = "(At least one object has a null field)";
    private static final Classifier<String> NULL_CLASSIFIER = new NullClassifier();
    private static final ClassifierChain NULL_CLASSIFIER_CHAIN = new ClassifierChain(NULL_CLASSIFIER);

    @ClassifierProperty(overviewLevel = 10)
    private boolean showNulls = true;

    public boolean getShowNulls() {
        return showNulls;
    }

    public void setShowNulls(boolean showNulls) {
        this.showNulls = showNulls;
    }

    @Override
    public void setup(Supplier<Symbols> symbolsSupplier, Supplier<IndexBasedHeap> fastHeapSupplier) {
        super.setup(symbolsSupplier, fastHeapSupplier);
        if (classifiers != null && classifiers.length() > 0) {
            classifiers.getList().forEach(classifier -> classifier.setup(symbolsSupplier, fastHeapSupplier));
        }
    }

    @Override
    public ImagePack[] loadIcons() {
        return new ImagePack[]{ImageUtil.getResourceImagePack("Points to", "points_to.png")};
    }

    @Override
    public String title() {
        return Classifier.CLASSIFICATION_MODE == ClassificationMode.LIST ? "Points To (# Objects)" : "Pointers To (# Pointers)";
    }

    @Override
    public GroupingNode classify(GroupingNode transformerRoot) throws ClassifierException, Exception {
        int[] pointsTo = pointsToIndices();
        if (pointsTo != null && pointsTo.length > 0) {
            for (int i = 0; i < pointsTo.length; i++) {
                int ptr = pointsTo[i];
                if (ptr < 0) {
                    // Null pointer
                    if (showNulls) {
                        ((FastHeapGroupingNode) transformerRoot).classify(fastHeap(), ptr, NULL_CLASSIFIER_CHAIN);
                        GroupingNode nullNode = transformerRoot.getChild(NULL_KEY);
                        if (nullNode != null) {
                            nullNode.getData().clear();
                        }
                    }
                } else {
                    // Normal pointers
                    ((FastHeapGroupingNode) transformerRoot).classify(fastHeap(), ptr, classifiers);
                }
            }
        }
        return transformerRoot;
    }

    public static class NullClassifier extends Classifier<String> {
        @Override
        protected String classify() throws Exception {
            return NULL_KEY;
        }
    }
}
