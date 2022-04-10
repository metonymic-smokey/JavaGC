
package at.jku.anttracks.parser.heap;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.GarbageCollectionLookup;
import at.jku.anttracks.heap.HeapListener;
import at.jku.anttracks.heap.io.HeapIndexReader;
import at.jku.anttracks.heap.io.HeapPosition;
import at.jku.anttracks.heap.io.MetaDataReaderConfig;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.ParsingInfo;
import at.jku.anttracks.parser.TraceScannerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.function.Function;

/**
 * @author Christina Rammerstorfer
 */
public class ListenerHeapTraceParser extends HeapTraceParser {

    private final Function<DetailedHeap, HeapListener> listener;

    public ListenerHeapTraceParser(Symbols symbols, Function<DetailedHeap, HeapListener> listener) throws Exception {
        super(symbols);
        this.listener = listener;
    }

    public ListenerHeapTraceParser(Symbols symbols, MetaDataReaderConfig readerConfig, GarbageCollectionLookup to, Function<DetailedHeap, HeapListener> listener)
            throws Exception {
        super(symbols, readerConfig, to);
        this.listener = listener;
    }

    public ListenerHeapTraceParser(Symbols symbols, MetaDataReaderConfig readerConfig, long toTime, Function<DetailedHeap, HeapListener> listener) throws Exception {
        super(symbols, readerConfig, toTime);
        this.listener = listener;
    }

    @Override
    protected DetailedHeap generatePlainWorkspace(TraceScannerFactory factory, ParsingInfo parsingInfo) throws IOException {

        DetailedHeap heap = HeapBuilder.constructHeap(symbols, parsingInfo);
        listener.apply(heap);
        return heap;
    }

    @Override
    protected DetailedHeap generateWorkspaceFromMetaData(HeapIndexReader heapIndexReader,
                                                         HeapPosition heapPosition,
                                                         ParsingInfo parsingInfo) throws FileNotFoundException, IOException {
        DetailedHeap heap;
        if (heapPosition.fileName != -1) {
            heap = heapIndexReader.getHeap(heapPosition.fileName, symbols, parsingInfo);
        } else {
            heap = HeapBuilder.constructHeap(symbols, parsingInfo);
        }
        listener.apply(heap);
        return heap;
    }

}
