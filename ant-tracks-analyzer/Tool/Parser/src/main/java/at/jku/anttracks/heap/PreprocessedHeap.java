package at.jku.anttracks.heap;

import at.jku.anttracks.heap.symbols.Symbols;

import java.io.File;

public class PreprocessedHeap {
    public final File inputPath;
    public final File symbolsFile;
    public final File classDefinitionFile;
    public final File metaDir;
    public final Symbols symbols;

    public PreprocessedHeap(File inputPath, File symbolsFile, File classDefinitionFile, File metaDir, Symbols symbols) {
        this.inputPath = inputPath;
        this.symbolsFile = symbolsFile;
        this.classDefinitionFile = classDefinitionFile;
        this.metaDir = metaDir;
        this.symbols = symbols;
    }
}
