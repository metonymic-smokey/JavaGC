package at.jku.anttracks.parser.printing;

import at.jku.anttracks.heap.io.HeapIndexReader;
import at.jku.anttracks.heap.io.HeapPosition;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.ParsingInfo;
import at.jku.anttracks.parser.TraceParser;
import at.jku.anttracks.parser.TraceParsingEventHandler;
import at.jku.anttracks.parser.TraceScannerFactory;
import at.jku.anttracks.util.TraceException;

import java.io.FileNotFoundException;
import java.io.IOException;

public class PrintingTraceParser extends TraceParser<Void> {
    public PrintingTraceParser(Symbols symbols) throws Exception {
        super(symbols);
    }

    @Override
    protected void doWorkspaceCompletion(Void workspace) throws TraceException {

    }

    @Override
    protected void doRemoveListenersOnCompletion(Void workspace) {

    }

    @Override
    protected Void generateWorkspaceFromMetaData(HeapIndexReader heapIndexReader, HeapPosition heapPosition, ParsingInfo parsingInfo) throws FileNotFoundException, IOException {
        return null;
    }

    @Override
    protected Void generatePlainWorkspace(TraceScannerFactory factory, ParsingInfo parsingInfo) throws IOException {
        return null;
    }

    @Override
    protected void doParseCleanupAfterSuccessfulParse(Void workspace) throws TraceException {

    }

    @Override
    protected TraceParsingEventHandler createMainEventHandler(ParsingInfo parsingInfo) {
        return new AdditionalPrintingEventHandler(parsingInfo);
    }
}
