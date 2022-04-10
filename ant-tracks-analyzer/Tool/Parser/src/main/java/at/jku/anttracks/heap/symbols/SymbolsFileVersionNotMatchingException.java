
package at.jku.anttracks.heap.symbols;

public class SymbolsFileVersionNotMatchingException extends Exception {

    private static final long serialVersionUID = 8385814235842977340L;

    public SymbolsFileVersionNotMatchingException(int actual) {
        super("The symbol file's version does not match the parser's version!\nFile version: " + actual + "\nParser version: " + Symbols.SYMBOLS_VERSION + "\nThis may " +
                      "be most " +
                      "likly due to an outdated symbols file. Try to regenerate the trace with the newest AntTracks " + "VM.");
    }

}
