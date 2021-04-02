
package at.jku.anttracks.classification;

import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.util.CollectionsUtil;
import at.jku.anttracks.util.ImagePack;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.util.CollectionsUtil;
import at.jku.anttracks.util.ImagePack;

import java.util.function.Supplier;

public class ClassifierFactory implements CollectionsUtil.Factory<Classifier<?>> {
    private final Supplier<Classifier<?>> supplier;

    private final String name;
    private final String desc;
    private final ClassifierType type;
    private final String example;
    private final ClassifierSourceCollection sourceCollection;

    protected ImagePack icon;

    private final String sourceCode;
    private final boolean isOnTheFlyCompilable;
    private final boolean isCustom;

    public ClassifierFactory(Supplier<Symbols> symbolsSupplier, Supplier<IndexBasedHeap> fastHeapSupplier, Classifier<?> oc) {
        this.name = oc.getName();
        this.desc = oc.getDesc();
        this.type = oc.getType();
        this.sourceCollection = oc.getSourceCollection();
        this.example = oc.getExample();
        this.icon = oc.getIcon(null);
        this.isOnTheFlyCompilable = oc.isOnTheFlyCompilable();
        this.sourceCode = oc.getSourceCode();
        this.isCustom = oc.isCustom();

        this.supplier = () -> {
            Classifier<?> classifier = null;
            try {
                classifier = oc.getClass().newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                // TODO: Error handling
                e.printStackTrace();
                return null;
            }
            classifier.setup(symbolsSupplier, fastHeapSupplier);
            classifier.setIsCustom(oc.isCustom());
            classifier.setSourceCode(oc.getSourceCode());
            return classifier;
        };
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public ClassifierType getType() {
        return type;
    }

    public String getExample() {
        return example;
    }

    public ImagePack getIcon() {
        return icon;
    }

    public boolean isCustom() {
        return isCustom;
    }

    public boolean isOnTheFlyCompilable() {
        return isOnTheFlyCompilable;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public ClassifierSourceCollection getSourceCollection() {
        return sourceCollection;
    }

    public String toString() {
        return name;
    }

    @Override
    public Classifier<?> create() {
        return supplier.get();
    }
}
