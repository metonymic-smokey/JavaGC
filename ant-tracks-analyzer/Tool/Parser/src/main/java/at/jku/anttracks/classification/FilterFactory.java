
package at.jku.anttracks.classification;

import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.util.CollectionsUtil;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.util.CollectionsUtil;

import java.util.function.Supplier;

public class FilterFactory implements CollectionsUtil.Factory<Filter> {
    private final String name;
    private final String desc;
    private final ClassifierSourceCollection sourceCollection;

    private final Supplier<Filter> supplier;

    public FilterFactory(Supplier<Symbols> symbolsSupplier, Supplier<IndexBasedHeap> fastHeapSupplier, Filter filter) {
        this.name = filter.getName();
        this.desc = filter.getDesc();
        this.sourceCollection = filter.getSourceCollection();
        supplier = () -> {
            Filter newFilter = null;
            try {
                newFilter = filter.getClass().newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                // TODO: Error handling
                e.printStackTrace();
                return null;
            }
            newFilter.setup(symbolsSupplier, fastHeapSupplier);
            newFilter.setIsCustom(true);
            newFilter.setSourceCode(filter.getSourceCode());
            return newFilter;
        };
    }

    public String getName() {
        return name;
    }

    public String getDesc() {
        return desc;
    }

    public ClassifierSourceCollection getSourceCollection() {
        return sourceCollection;
    }

    public boolean isOnTheFlyCompilable() {
        return create().isOnTheFlyCompilable();
    }

    public String getSourceCode() {
        return create().getSourceCode();
    }

    @Override
    public Filter create() {
        return supplier.get();
    }

    @Override
    public String toString() {
        return name;
    }
}
