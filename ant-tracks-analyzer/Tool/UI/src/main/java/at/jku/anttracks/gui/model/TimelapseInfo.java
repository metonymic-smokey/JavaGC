
package at.jku.anttracks.gui.model;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.symbols.Symbols;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

/**
 * @author Christina Rammerstorfer
 */
public class TimelapseInfo extends AvailableDetailedHeapClassifierInfo {

    private final AppInfo appInfo;

    public TimelapseInfo(AppInfo appInfo) {
        this.appInfo = appInfo;
    }

    public AppInfo getAppInfo() {
        return appInfo;
    }

    @Override
    public Supplier<Symbols> getSymbolsSupplier() {
        return () -> null;
    }

    @Nullable
    @Override
    public Supplier<DetailedHeap> getDetailedHeapSupplier() {
        return () -> null;
    }
}
