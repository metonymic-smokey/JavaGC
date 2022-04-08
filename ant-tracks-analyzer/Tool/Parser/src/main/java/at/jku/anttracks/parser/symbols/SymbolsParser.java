
package at.jku.anttracks.parser.symbols;

import at.jku.anttracks.features.FeatureMap;
import at.jku.anttracks.features.io.FeaturesParser;
import at.jku.anttracks.heap.datastructures.dsl.DSLDSPartDesc;
import at.jku.anttracks.heap.symbols.*;
import at.jku.anttracks.parser.Scanner;
import at.jku.anttracks.parser.classdefinitions.ClassDefinitionsParser;
import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.parser.io.FileInfo;
import at.jku.anttracks.parser.io.InputStreamScanner;
import at.jku.anttracks.heap.datastructures.dsl.DataStructureUtil;
import at.jku.anttracks.util.FileUtil;
import at.jku.anttracks.util.TraceException;
import at.jku.anttracks.util.ZipFileUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static at.jku.anttracks.heap.symbols.Symbols.CALLCONTEXT_NONE;
import static at.jku.anttracks.util.Consts.HEX_BYTE;
import static at.jku.anttracks.util.Consts.HEX_SHORT;

public class SymbolsParser {

    private static boolean VERBOSE = false;

    private static final Logger LOGGER = Logger.getLogger(SymbolsParser.class.getSimpleName());

    private final File symbolsFile;
    private final File classDefinitionsFile;
    private final File featuresFile;
    private final int useCallContext;

    private final double IO_TO_CLASSDEF_RATIO = 0.25;

    private Scanner scanner;
    public static boolean parsePtrs = true;

    public SymbolsParser(File symbolsFile, File classDefinitionsFile, File features, int useCallContext) {
        this.symbolsFile = symbolsFile;
        this.classDefinitionsFile = (useCallContext != CALLCONTEXT_NONE ? classDefinitionsFile : null);
        featuresFile = features;
        this.useCallContext = useCallContext;
    }

    public Symbols parse() throws IOException, TraceException, SymbolsFileVersionNotMatchingException, SymbolsFileException {
        LOGGER.log(Level.INFO, "preparing to parse symbols in {0}", symbolsFile);

        InputStream in = SymbolsFile.open(symbolsFile);
        // Reads magig byte, header & filetype
        FileInfo info = SymbolsFile.readSymbolsFileInfo(in);

        String root = (BaseFile.isPlainFile(SymbolsFile.SYMBOL_FILE_ID, symbolsFile) ? symbolsFile.getParent() : symbolsFile).toString();

        long time = System.currentTimeMillis();
        LOGGER.log(Level.INFO, "parsing symbols of {0}", root);
        scanner = new InputStreamScanner(in);
        in = null;

        // This parser supports both a dedicated class definitions file, as well as class definitions in the symbols file
        ClassDefinitions classDefinitions = new ClassDefinitions();
        Exception classDefinitionsException = null;

        Symbols symbols;
        try {
            int version = scanner.getWord();
            if (version != Symbols.SYMBOLS_VERSION) {
                throw new SymbolsFileVersionNotMatchingException(version);
            }
            int flags = scanner.getWord();
            boolean anchors = ((flags & 0b1) != 0);
            boolean pointers = ((flags & 0b10) != 0);
            boolean fragmented_heap = ((flags & 0b100) != 0);
            pointers = pointers && parsePtrs;

            int heapWordSize = scanner.getWord();
            String trace = scanner.getStringZeroTerminated();

            symbols = new Symbols(root,
                                  info.getHeader(),
                                  anchors,
                                  heapWordSize,
                                  pointers,
                                  trace,
                                  featuresFile != null ? featuresFile.getAbsolutePath() : null,
                                  featuresFile != null ? new FeatureMap() : null,
                                  fragmented_heap);

            parse(symbols, classDefinitions);
        } finally {
            scanner.close();
        }

        scanner = null;
        time = System.currentTimeMillis() - time;
        LOGGER.log(Level.INFO, "parsed symbols of {0} in {1}s", new Object[]{root, 1e-3 * time});

        if (VERBOSE) {
            reportEmptyAllocationSites(symbols);
        }

        try {
            if (classDefinitionsFile != null) {
                if (!classDefinitions.isEmpty()) {
                    throw new TraceException("Symbols file contained class definitions, but a class definitions file was also specified.");
                }
                if (useCallContext != CALLCONTEXT_NONE) {
                    final ClassDefinitionsParser parser = new ClassDefinitionsParser(classDefinitionsFile);
                    classDefinitions = parser.parse();
                }
            }

            if (useCallContext != CALLCONTEXT_NONE) {
                if (!classDefinitions.isEmpty()) {
                    time = System.currentTimeMillis();
                    symbols.initCallContext(classDefinitions, useCallContext);
                    time = System.currentTimeMillis() - time;
                    LOGGER.info(String.format("Call context analysis took %,dms", time));
                    if (symbols.useStaticCallContext()) {
                        LOGGER.info("Starting static stack trace extension...");
                        symbols.amendStackTracesStatic();
                    }
                } else {
                    LOGGER.info("Trace didn't contain any class definitions, call context analysis will be unavailable.");
                }
            }

            if (!symbols.useDynamicCallContext()) {
                symbols.freeCallContextMemory();
            }
        } catch (Exception ex) {
            // Something bad happend during class file loading
            LOGGER.warning("Processing class definitions file failed!\n" + ex.getMessage() + "\n" + Arrays.stream(ex.getStackTrace())
                                                                                                          .map(x -> x.toString())
                                                                                                          .reduce((a, b) -> a + "\n" + b));
            // TODO mw: Check how much we gain by using call context info
            symbols.initCallContext(null, CALLCONTEXT_NONE);
        }

        if (featuresFile != null) {
            LOGGER.log(Level.INFO, "parsing features in {0}", featuresFile);
            try (FeaturesParser parser = new FeaturesParser(ZipFileUtil.isZipFilePath(featuresFile.getAbsolutePath()) ?
                                                            ZipFileUtil.openR(featuresFile) :
                                                            FileUtil.openR(featuresFile))) {
                parser.parse(symbols.features);
            } catch (Exception e) {
                throw new IOException(e);
            }
            LOGGER.log(Level.INFO,
                       "parsed {0} features ({1} packages mapped, {2} types mapped, {3} methods mapped, {4} instructions mapped)",
                       new Object[]{symbols.features.getFeatureCount(),
                                    symbols.features.getPackageMappingCount(),
                                    symbols.features.getTypeMappingCount(),
                                    symbols.features.getMethodeMappingCount(),
                                    symbols.features.getInstructionMappingCount()});
        }

        return symbols;
    }

    private static void reportEmptyAllocationSites(Symbols symbols) {
        int possibleEmpty = 0;

        // LOGGER.info(String.format("Allocation Sites: %d", symbols.sites.getLength()));
        for (AllocationSite allocationSite : symbols.sites) {
            if (allocationSite != null) {
                if (allocationSite.getId() != possibleEmpty) {
                    LOGGER.info(String.format("Not assigned: %d-%d", possibleEmpty, allocationSite.getId() - 1));
                }
                possibleEmpty = allocationSite.getId() + 1;
            }
        }
    }

    private void parse(Symbols symbols, ClassDefinitions classDefinitions) throws IOException, TraceException, SymbolsFileException {
        int magicByte = scanner.getMagicByte();
        AllocatedType lastAllocatedType = null;

        Map<String, String> stringCache = new HashMap<>();

        while (magicByte != 0) {
            //progressUpdater.accept(scanner.getGlobalPosition(), symbolsFile.length());
            if (magicByte == AllocationSite.MAGIC_BYTE) {
                // LOGGER.info("Magic-Byte: AllocationSite - Normal");
                parseAllocationSite(symbols, stringCache);
            } else if (magicByte == AllocatedType.MAGIC_BYTE) {
                // LOGGER.info("Magic-Byte: AllocatedType");
                lastAllocatedType = parseAllocatedType(symbols);
            } else if (magicByte == ClassDefinition.MAGIC_BYTE) {
                // LOGGER.info("Magic-Byte: ClassDefinition");
                parseClassDefinition(classDefinitions);
            } else if (magicByte == AllocationSite.MAGIC_BYTE_SIMPLE) {
                // LOGGER.info("Magic-Byte: AllocationSite - Simple");
                parseAllocationSiteSimple(symbols);
            } else if (magicByte == AllocatedType.MAGIC_BYTE_TYPE_FIELD_INFO) {
                // LOGGER.info("Magic-Byte: AllocatedType - Type Info");
                parseAllocatedTypeFieldInfo(false, symbols, lastAllocatedType);
            } else if (magicByte == AllocatedType.MAGIC_BYTE_TYPE_SUPER_FIELD_INFO) {
                // LOGGER.info("Magic-Byte: AllocatedType - Type-Super-Info");
                parseAllocatedTypeFieldInfo(true, symbols, lastAllocatedType);
            } else if (magicByte == AllocatedType.MAGIC_BYTE_TYPE_METHOD_INFO) {
                // LOGGER.info("Magic-Byte: AllocatedType - Type-Method-Info");
                parseAllocatedTypeMethodInfo(true, symbols, lastAllocatedType);
            } else if (magicByte == 17) {
                // LOGGER.info("Magic-Byte: 17 - GC cause");
                parseGarbageCollectionCause(symbols);
            } else {
                LOGGER.warning("Magic-Byte: UNKNOWN! (" + magicByte + ")");
                throw new TraceException("PARSE ERROR. Invalid symbols file. Expected known MAGIC_BYTE");
            }
            magicByte = scanner.getMagicByte();
        }

        symbols.types.complete();
        symbols.sites.complete();

        List<DSLDSPartDesc> descriptions = DataStructureUtil.INSTANCE.parseDataStructureDefinitionFiles(symbols.getDataStructureDefinitionFiles());
        DataStructureUtil.INSTANCE.resolveDescriptionsAndStoreDefinitionsInTypes(symbols.types, descriptions);
    }

    private void parseGarbageCollectionCause(Symbols symbols) throws IOException {
        int id = scanner.getInt();
        String name = scanner.getStringZeroTerminated();
        boolean ok = scanner.getMagicByte() != 0;
        symbols.causes.add(id, name, ok);
    }

    private void parseAllocationSiteSimple(Symbols symbols) throws IOException {
        // mask last two bytes because if first bit is set (<=> short is
        // negative) it will be sign extended and the int will also be negative
        int id = (scanner.getShort()) & HEX_SHORT;
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (((id >> 15) & 1) != 0) {
            id = id << 8 | ((scanner.getByte()) & HEX_BYTE);
        }

        String signature = scanner.getStringZeroTerminated();
        int allocatedTypeId = scanner.getWord();

        AllocationSite allocationSite = new AllocationSite(id,
                                                           new AllocationSite.Location[]{new AllocationSite.Location(signature, AllocationSite.UNKNOWN_BCI)},
                                                           allocatedTypeId);
        symbols.sites.add(allocationSite);
    }

    private void parseAllocationSite(Symbols symbols, Map<String, String> stringCache) throws IOException, TraceException {
        // mask last two bytes because if first bit is set (<=> short is
        // negative) it will be sign extended and the int will also be negative
        int id = (scanner.getShort()) & HEX_SHORT;
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (((id >> 15) & 1) != 0) {
            id = id << 8 | ((scanner.getByte()) & HEX_BYTE);
        }

        AllocationSite.Location[] callSites = new AllocationSite.Location[scanner.getWord()];
        for (int i = 0; i < callSites.length; i++) {
            String signature = scanner.getString(')');
            int bci = scanner.getWord();
            String cachedString = stringCache.get(signature);
            if (cachedString == null) {
                stringCache.put(signature, signature);
                cachedString = signature;
            }

            callSites[i] = new AllocationSite.Location(cachedString, bci);
        }
        int allocatedType = scanner.getWord();

        AllocationSite allocationSite = new AllocationSite(id, callSites, allocatedType);

        symbols.sites.add(allocationSite);
    }

    private void parseAllocatedTypeFieldInfo(boolean supr, Symbols symbols, AllocatedType lastAllocatedType) throws IOException, TraceException {

        int count = scanner.getWord();
        lastAllocatedType.createOrExpandFieldInfos(count);

        for (int i = 0; i < count; i++) {
            int offset = scanner.getInt();
            String signature = scanner.getNullTerminatedString(true);
            String name = scanner.getNullTerminatedString(true);
            int flags = scanner.getInt();
            lastAllocatedType.addFieldInfo(supr, offset, signature, name, flags);
        }
    }

    private void parseAllocatedTypeMethodInfo(boolean supr, Symbols symbols, AllocatedType lastAllocatedType) throws IOException, TraceException {

        int count = scanner.getWord();
        lastAllocatedType.createOrExandMethodInfos(count);

        for (int i = 0; i < count; i++) {
            int idnum = scanner.getInt();
            String name = scanner.getNullTerminatedString(true);

            int localsCount = scanner.getInt();
            HashMap<Integer, String> methodLocals = new HashMap<>();
            for (int j = 0; j < localsCount; j++) {
                methodLocals.put(scanner.getInt(), scanner.getNullTerminatedString(true));
            }

            lastAllocatedType.addMethodInfo(idnum, name, methodLocals);
        }
    }

    private AllocatedType parseAllocatedType(Symbols symbols) throws IOException, TraceException, SymbolsFileException {
        int id = scanner.getWord();
        assert id >= 0 : "Invalid type id";
        int superId = scanner.getWord();
        String name = scanner.getString(' ');
        // Some lambdas end with <something>/<number>;
        // for example: Example$$Lambda$1/1072591677
        // (see https://stackoverflow.com/questions/21858482/what-is-a-java-8-lambda-expression-compiled-to)
        // Since we cannot distinguish between such a slash and a slash that has to be translated to a dot ('.') when converting a Java internal name to its external name, we
        // replace the /<number>; by _lambda;
        if (name.endsWith("/0;")) {
            name = name.replaceAll("/\\d+;", "_lambda;");
        }
        int size = scanner.getWord();

        if (!name.startsWith("[") && !name.equals("Ljava/lang/Object;")) {
            // Everything except arrays and java.lang.Object must have a super type
            assert superId > 0 : "Invalid super type id";
        }

        AllocatedType allocatedType = new AllocatedType(id, superId, name, size);
        allocatedType = symbols.types.add(id, allocatedType);

        return allocatedType;
    }

    private void parseClassDefinition(ClassDefinitions classDefinitions) throws IOException {
        if (classDefinitions != null) {
            if (useCallContext == CALLCONTEXT_NONE) {
                ClassDefinitionsParser.skipClassDefinition(scanner);
            } else {
                ClassDefinitionsParser.parseClassDefinition(scanner, classDefinitions);
            }
        }
    }
}
