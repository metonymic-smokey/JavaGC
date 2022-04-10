
package at.jku.anttracks.parser.classdefinitions;

import at.jku.anttracks.heap.symbols.ClassDefinition;
import at.jku.anttracks.heap.symbols.ClassDefinitions;
import at.jku.anttracks.parser.Scanner;
import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.parser.io.InputStreamScanner;
import at.jku.anttracks.util.TraceException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Parser for a class definitions file.
 *
 * @author Peter Feichtinger
 */
public class ClassDefinitionsParser {

    private static final Logger LOGGER = Logger.getLogger(ClassDefinitionsParser.class.getSimpleName());

    private final File mFile;
    private Scanner mScanner;

    /**
     * Create a new {@link ClassDefinitionsParser} for the specified file.
     *
     * @param file The file to parse.
     */
    public ClassDefinitionsParser(File file) {
        mFile = Objects.requireNonNull(file);
    }

    /**
     * Parse class definitions from the file.
     *
     * @return A {@link ClassDefinitions} object filled with all class definitions from the file.
     * @throws IOException    If there is a IO error or the file ends abruptly.
     * @throws TraceException If an unknown magic byte is encountered.
     */
    public ClassDefinitions parse() throws IOException, TraceException {
        LOGGER.log(Level.INFO, "Preparing to parse class definitions in {0}", mFile);

        InputStream in = ClassDefinitionsFile.open(mFile);
        ClassDefinitionsFile.readClassDefinitionsFileInfo(in); // unused

        final String root = (BaseFile.isPlainFile(ClassDefinitionsFile.FILE_ID, mFile) ? mFile.getParent() : mFile).toString();

        long time = System.currentTimeMillis();
        LOGGER.log(Level.INFO, "Parsing class definitions of {0}", root);
        mScanner = new InputStreamScanner(in);
        in = null;

        final ClassDefinitions result = new ClassDefinitions();
        try {
            @SuppressWarnings("unused")
            final int flags = mScanner.getWord();
            @SuppressWarnings("unused")
            final String trace = mScanner.getStringZeroTerminated();

            for (int magicByte; (magicByte = mScanner.getMagicByte()) != 0; ) {
                if (magicByte == ClassDefinition.MAGIC_BYTE) {
                    // LOGGER.info("Magic-Byte: ClassDefinition");
                    parseClassDefinition(mScanner, result);
                } else {
                    LOGGER.severe("Magic-Byte: UNKNOWN! (" + magicByte + ")");
                    throw new TraceException("PARSE ERROR. Invalid class definitions file. Expected known MAGIC_BYTE");
                }
            } // while(magicByte != 0)
        } finally {
            mScanner.close();
        }

        time = System.currentTimeMillis() - time;
        LOGGER.log(Level.INFO, "Parsed class definitions of {0} in {1}s", new Object[]{root, 1e-3 * time});
        return result;
    }

    /**
     * Parse a class definition from the specified scanner and add it to the specified class definitions.
     *
     * @param scanner The {@link Scanner} to read from.
     * @param defs    The {@link ClassDefinitions} to add the parsed definition to.
     * @throws IOException If there is an IO error.
     */
    public static void parseClassDefinition(Scanner scanner, ClassDefinitions defs) throws IOException {
        final int id = scanner.getWord();
        final int size = scanner.getWord();
        final byte[] data = scanner.get(size);

        final ClassDefinition def = new ClassDefinition(id, data);
        defs.add(def);
    }

    /**
     * Skip a class definition from the specified scanner.
     *
     * @param scanner The {@link Scanner} to read from.
     * @throws IOException If there is an IO error.
     */
    public static void skipClassDefinition(Scanner scanner) throws IOException {
        scanner.getWord(); // unused id
        final int size = scanner.getWord();
        scanner.skip(size);
    }
}
