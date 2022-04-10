
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.ClassifierException;
import at.jku.anttracks.classification.Transformer;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.nodes.GroupingNode;
import at.jku.anttracks.classification.nodes.ListGroupingNode;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.util.ImagePack;

import java.util.BitSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public abstract class AbstractPointsToClosureTransformer extends Transformer {

    @ClassifierProperty(overviewLevel = 10)
    protected ClassifierChain classifiers = new ClassifierChain(new TypeClassifier());

    public ClassifierChain getClassifiers() {
        return classifiers;
    }

    public void setClassifiers(ClassifierChain classifiers) {
        this.classifiers = classifiers;
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
    public void setup(Supplier<Symbols> symbolsSupplier, Supplier<IndexBasedHeap> fastHeapSupplier) {
        super.setup(symbolsSupplier, fastHeapSupplier);
        if (classifiers != null && classifiers.length() > 0) {
            classifiers.getList().forEach(classifier -> classifier.setup(symbolsSupplier, fastHeapSupplier));
        }
    }

    @Override
    public GroupingNode classify(GroupingNode transformerRoot) throws ClassifierException {
        class ThreadLocalListGroupingNodes {
            Map<Thread, ListGroupingNode> nodes = new ConcurrentHashMap<>();

            public ListGroupingNode get() {
                Thread t = Thread.currentThread();
                if (!nodes.containsKey(t)) {
                    nodes.put(t, new ListGroupingNode(null, 0, 0, null, transformerRoot.getKey()));
                }
                return nodes.get(t);
            }

            public ListGroupingNode combine(ListGroupingNode into) {
                nodes.values().stream().forEach(into::merge);
                return into;
            }
        }

        //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("fast heap: points to (closure)");
        BitSet closure = closure();
        ThreadLocalListGroupingNodes tl = new ThreadLocalListGroupingNodes();
        if (closure != null && closure.cardinality() > 0) {
            // TODO filters
            closure.stream().parallel().forEach(idx -> {
                try {
                    tl.get().classify(fastHeap(), idx, classifiers);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
        }
        tl.combine((ListGroupingNode) transformerRoot);
        //m.end();
        return transformerRoot;
    }

    protected abstract BitSet closure() throws ClassifierException;
}
