
package at.jku.anttracks.gui.classification.classifier;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.annotations.C;
import at.jku.anttracks.classification.annotations.ClassifierProperty;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.util.ImagePack;

import java.util.function.Supplier;

@C(name = "Address",
        desc = "This classifier distinguishes objects based on their address.",
        example = "[30,450,670]",
        type = ClassifierType.ONE,
        collection = ClassifierSourceCollection.ALL)
public class AddressClassifier extends Classifier<String> {

    @ClassifierProperty(overviewLevel = 10)
    public Classifier<?> additionalClassifier = null;

    public Classifier<?> getAdditionalClassifier() {
        return additionalClassifier;
    }

    public void setAdditionalClassifier(Classifier<?> additionalClassifier) {
        this.additionalClassifier = additionalClassifier;
        if (additionalClassifier != null) {
            additionalClassifier.setup(() -> symbols(), () -> fastHeap());
        }
    }

    @ClassifierProperty(overviewLevel = 10)
    public boolean prepend = true;

    public boolean getPrepend() {
        return prepend;
    }

    public void setPrepend(boolean prepend) {
        this.prepend = prepend;
    }

    @Override
    public void setup(Supplier<Symbols> symbolsSupplier, Supplier<IndexBasedHeap> fastHeapSupplier) {
        super.setup(symbolsSupplier, fastHeapSupplier);
        if (additionalClassifier != null) {
            additionalClassifier.setup(symbolsSupplier, fastHeapSupplier);
        }
    }

    @Override
    public String classify() {
        try {
            return String.format("%s [%,d] %s",
                                 prepend && additionalClassifier != null ? additionalClassifier.classify(index()) : "",
                                 address(),
                                 !prepend && additionalClassifier != null ? additionalClassifier.classify(index()) : "");
        } catch (Exception ex) {
            ex.printStackTrace();
            return "classification error";
        }

    }

    @Override
    public ImagePack[] loadIcons() {
        return new ImagePack[]{ImageUtil.getResourceImagePack("Address", "address.png")};
    }

}
