
package at.jku.anttracks.gui.model;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.symbols.Symbols;
import org.jetbrains.annotations.NotNull;

import java.util.function.Supplier;

public class DetailedHeapInfo extends AvailableDetailedHeapClassifierInfo {

    private final long time;
    private DetailedHeap heap;
    private final AppInfo appInfo;

    public DetailedHeapInfo(AppInfo appInfo, long time) {
        this.appInfo = appInfo;
        this.time = time;
    }

    public long getTime() {
        return time;
    }

    public void setHeap(DetailedHeap heap) {
        this.heap = heap;
    }

    @Override
    public Supplier<DetailedHeap> getDetailedHeapSupplier() {
        return () -> heap;
    }

    @NotNull
    @Override
    public Supplier<Symbols> getSymbolsSupplier() {
        return () -> heap.getSymbols();
    }

    public AppInfo getAppInfo() {
        return appInfo;
    }
}
