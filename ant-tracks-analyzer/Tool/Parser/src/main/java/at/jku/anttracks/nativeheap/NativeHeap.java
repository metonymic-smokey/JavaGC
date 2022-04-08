package at.jku.anttracks.nativeheap;

import at.jku.anttracks.heap.symbols.Symbols;

public class NativeHeap {
    private final Symbols symbols;

    public static NativeHeap constructHeap(Symbols symbols) {
        NativeHeap heap = new NativeHeap(symbols);
        return heap;
    }

    public NativeHeap(Symbols symbols) {
        this.symbols = symbols;
    }

    public void complete() {
    }

    public void removeAllListeners() {
    }
}
