package com.brr.anttracks.cli.main;

import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.trees.ListClassificationTree;
import at.jku.anttracks.gui.classification.classifier.AllocationSiteClassifier;
import at.jku.anttracks.gui.classification.classifier.CallSitesClassifier;
import at.jku.anttracks.gui.classification.classifier.TypeClassifier;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.utils.Consts;
import at.jku.anttracks.heap.*;
import at.jku.anttracks.heap.io.MetaDataWriterConfig;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.heap.space.SpaceMode;
import at.jku.anttracks.heap.space.SpaceType;
import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.heap.symbols.AllocationSite;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.*;
import at.jku.anttracks.parser.classdefinitions.ClassDefinitionsFile;
import at.jku.anttracks.parser.heap.HeapTraceParser;
import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.parser.symbols.SymbolsFile;
import at.jku.anttracks.parser.symbols.SymbolsParser;
import at.jku.anttracks.util.TraceException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.LogManager;

// Provided by Markus Weninger, SSW, JKU, Austria
public class JsonExportMain {

    private static AppInfo appInfo;
    private static Symbols symbols;
    private static HeapTraceParser parser;

    private static List<GarbageCollectionCause> gcCauses = new ArrayList<>();
    private static List<GarbageCollectionType> gcTypes = new ArrayList<>();
    private static List<Long> gcStarts = new ArrayList<>();
    private static List<Long> gcEnds = new ArrayList<>();

    public static void main(String[] args) {
        // Turn off other logging (e.g., AdditionalPrintingEventHandler)
        LogManager.getLogManager().reset();

        int metaDumpCount = MetaDataWriterConfig.DEFAULT_DUMP_COUNT;
        boolean metaMajorGCsOnly = MetaDataWriterConfig.DEFAULT_AT_MAJOR_GCS_ONLY;
        boolean check = false;


        if (args.length == 0) {
            System.err.println("No trace file path given!");
            return;
        }

        File traceFile = new File(args[0]);
        try {
            // AppInfo represents information about the currently analyzed application
            // This would not be ultimately necessary but make working with classification a bit easier
            appInfo = new AppInfo(traceFile.getAbsolutePath().toString(), traceFile, null);
            // The MetaDataPath is where statistics, etc. are going to be stored while the trace is parsed
            appInfo.setMetaDataPath((BaseFile.isPlainFile(SymbolsFile.SYMBOL_FILE_ID, appInfo.getSymbolsFile()) ? appInfo.getSymbolsFile().getParent() : appInfo.getSymbolsFile()).toString() + File.separator + Consts.ANT_META_DIRECTORY);

            // Before parsing the trace, first parse the symbols file
            // This file contains information about types, allocation sites, etc.
            symbols = new SymbolsParser(SymbolsFile.findSymbolsFileToTrace(traceFile),
                    ClassDefinitionsFile.findClassDefinitionsFileToTrace(traceFile),
                    null,
                    Symbols.CALLCONTEXT_NONE).parse();
            appInfo.setSymbols(symbols);

            // Then, create a trace parser that uses this symbols information
            parser = new HeapTraceParser(appInfo.getSymbols());
            // Once the whole trace has been parsed, if meta-data has been written, we can read statistics information.
            // Some analyses then be performed based on the statistics stored in appInfo.getStatistics()
            // appInfo.getStatistics().addAll(Statistics.Companion.readStatisticsFromMetadata(appInfo.getMetaDataPath(), appInfo.getSymbols()));

            // TODO: Modify the method customHeapListener if you want to perform tasks at the start and at the end of GCs (see the TODOs in this method)
            parser.addHeapListener(customHeapListener());
            // TODO Modify the method customEventHandler if you want to inspect each event read from the trace file.
            // For example, this could be used to count the number of allocation events, move events, etc.
            parser.addEventHandler(JsonExportMain::customEventHandler);
            DetailedHeap detailedHeap = parser.parse();
            // Once the whole trace has been parsed, if meta-data has been written, we can read statistics information.
            // Some analyses then be performed based on the statistics stored in appInfo.getStatistics()
            // appInfo.getStatistics().addAll(Statistics.Companion.readStatisticsFromMetadata(appInfo.getMetaDataPath(), appInfo.getSymbols()));
        } catch (Throwable e) {
            e.printStackTrace(System.err);
        }
    }

    // TODO Modify this method if you want to perform tasks at the beginning or the end of GCs
    // Otherwise, just ignore
    public static HeapListener customHeapListener() {
        return new HeapListener() {

            @Override
            public void close(@NotNull Object sender, @NotNull ParsingInfo parsingInfo) {
                // This method is called when the parser finished
                DetailedHeap heap = (DetailedHeap) sender;
                // TODO: Optionally add own code that should be executed at the very end
                System.out.println("FINISHED");
                System.out.println(gcCauses);
                System.out.println(gcTypes);
                System.out.println(gcStarts);
                System.out.println(gcEnds);
            }

            @Override
            public void phaseChanging(@NotNull Object sender, @NotNull ParserGCInfo from, @NotNull ParserGCInfo to, boolean failed, long position, @NotNull ParsingInfo parsingInfo, boolean inParserTimeWindow) {
                // This method is called before the parser changes from mutator phase (i.e., running application) to GC phase or from GC phase to mutator phase
                // Use this method to perform steps at the start of a GC
                DetailedHeap heap = (DetailedHeap) sender;
                if (to.getEventType() == EventType.GC_START) {
                    // Switching into GC phase
                    // TODO: Do something before the GC phase starts
                    // If you want to inspect the heap, we suggest to use IndexBasedHeap idxHeap = heap.toIndexBasedHeap(false, null) for faster object access
                    System.out.printf("GC %d starts (mutator phase was running from %,dms to %,dms)%n", to.getId(), from.getTime(), to.getTime());
                    gcCauses.add(to.getCause());
                    gcTypes.add(to.getType());
                    gcStarts.add(to.getTime());
                    groupAndWrite(heap, to.getTime());
                }
            }

            @Override
            public void phaseChanged(@NotNull Object sender, @NotNull ParserGCInfo from, @NotNull ParserGCInfo to, boolean failed, long position, @NotNull ParsingInfo parsingInfo, boolean inParserTimeWindow) {
                // This method is called after the parser changes from mutator phase (i.e., running application) to GC phase or from GC phase to mutator phase
                // Use this method to perform steps at the end of a GC
                DetailedHeap heap = (DetailedHeap) sender;
                if (to.getEventType() == EventType.GC_END) {
                    // Switching into mutator phase
                    // TODO: Do something after the GC phase is over
                    // If you want to inspect the heap, we suggest to use IndexBasedHeap idxHeap = heap.toIndexBasedHeap(false, null) for faster object access
                    System.out.printf("GC %d ends (GC phase was running from %,dms to %,dms)%n", to.getId(), from.getTime(), to.getTime());
                    gcEnds.add(to.getTime());
                    groupAndWrite(heap, to.getTime());
                }
            }
        };
    }

    private static void groupAndWrite(DetailedHeap heap, long time) {
        // An IndexBasedHeap allows us to access heap objects via an index
        // This makes access times faster and helps us to faster build classification trees
        IndexBasedHeap idxHeap = heap.toIndexBasedHeap(false, null);
        // Filters could be used to exclude certain objects from the classification tree
        Filter[] filters = new Filter[0];
        TypeClassifier classifier1 = new TypeClassifier();
        AllocationSiteClassifier classifier2 = new AllocationSiteClassifier();
        CallSitesClassifier classifier3 = new CallSitesClassifier();
        // classifier.setup() calls are not needed, groupListParallel takes care of that :)
        // Just combine all classifiers to a ClassifierChain
        ClassifierChain classifiers = new ClassifierChain(classifier1, classifier2, classifier3);
        // Finally, group the objects in the heap into a tree
        ListClassificationTree tree = idxHeap.groupListParallel(filters, classifiers, true, true, null, null);
        // Init some statistic fields in the tree
        // If you want the have info about the deep size (reachabiltiy) and retained size (ownership), set the first two boolean flags to true
        tree.init(idxHeap, false, false, false, false);
        if (!Files.exists(Paths.get("./outputs"))) {
            try {
                Files.createDirectory(Paths.get("./outputs"));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        tree.getRoot().exportAsJSON(idxHeap,
                classifiers,
                time,
                new File("outputs/heapstate_withClassifierIdPerNode_" + time + ".json"),
                new File("outputs/heapstate_withClassifierNamePerNode_" + time + ".json"));
    }

    // TODO Modify this method if you want to inspect the events read from the trace file (e.g., counting ObjAlloc events)
    // Otherwise, just ignore
    public static TraceParsingEventHandler customEventHandler(DetailedHeap workspace, ParsingInfo parsingInfo) {
        return new TraceParsingEventHandler() {
            @Override
            public void doKeepAlive(@NotNull EventType eventType, long addr, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCTag(@NotNull String tagText, @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @Override
            public void doPtrEvent(@NotNull EventType eventType, long fromAddr, long toAddr, @NotNull long[] ptrs, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public long getMoveTarget(long fromAddr, long toAddr, @NotNull SpaceType toSpaceType, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return 0;
            }

            @Override
            public void doParsePlabAlloc(int size, long addr, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseTlabAlloc(int size, long addr, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @NotNull
            @Override
            public ObjectInfo doParseObjAllocSlow(@NotNull EventType eventType, @NotNull AllocationSite allocationSite, long addr, boolean isArray, int arrayLength, int size, boolean mayBeFiller, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return null;
            }

            @NotNull
            @Override
            public ObjectInfo doParseObjAllocSlowCiIr_Deviant(@NotNull EventType eventType, @NotNull AllocatedType allocatedType, @NotNull AllocationSite allocationSite, long addr, boolean isArray, int arrayLength, int realAllocatedTypeId, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return null;
            }

            @NotNull
            @Override
            public ObjectInfo doParseObjAllocNormalIr(@NotNull EventType eventType, int allocationSiteId, @NotNull AllocationSite allocationSite, long addr, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return null;
            }

            @NotNull
            @Override
            public ObjectInfo doParseObjAllocNormalCi(@NotNull EventType eventType, @NotNull AllocationSite allocationSite, @NotNull AllocatedType allocatedType, long addr, boolean isArray, int arrayLength, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return null;
            }

            @Override
            public long doParseObjAllocFastIr(@NotNull EventType eventType, @NotNull AllocationSite allocationSite, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return 0;
            }

            @Override
            public long doParseObjAllocFastC2DeviantType(@NotNull EventType eventType, int header, int allocationSiteId, @NotNull AllocatedType allocatedType, boolean isArray, int arrayLength, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return 0;
            }

            @Override
            public long doParseObjAllocFastCi(@NotNull EventType eventType, @NotNull AllocationSite allocationSite, @NotNull AllocatedType allocatedType, boolean isArray, int arrayLength, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return 0;
            }

            @Override
            public void doParseSyncObj(@NotNull EventType eventType, int allocationSiteId, @NotNull AllocatedType allocatedType, long fromAddr, long toAddr, int length, int size, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseThreadDeath(long id, @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @Override
            public void doParseThreadAlive(int header, long id, @NotNull String name, @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @NotNull
            @Override
            public List<ObjectInfo> doParseGCMoveRegion(@NotNull EventType eventType, long fromAddr, long toAddr, int numOfObjects, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return null;
            }

            @Override
            public void doParseGCDebugRootPtr(long ptr, @NotNull String vmCall, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCOtherRootPtr(long ptr, @NotNull RootPtr.RootType rootType, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCJNIGlobalRootPtr(long ptr, boolean weak, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCCodeBlobRootPtr(long ptr, int classId, int methodId, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCVMInternalThreadDataRootPtr(long ptr, long threadId, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCLocalVariableRootPtr(long ptr, long threadId, int classId, int methodId, int slot, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCJNILocalRootPtr(long ptr, long threadId, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCStaticFieldRootPtr(long ptr, int classId, int offset, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCClassRootPtr(long ptr, int classId, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCClassLoaderRootPtr(long ptr, @NotNull String loaderName, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public long doGCMove(@NotNull EventType eventType, long fromAddr, long toAddr, @Nullable SpaceType toSpaceType, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return 0;
            }

            @Override
            public void doParseGCContinue(int id, long address, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCInterrupt(int id, long address, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCEnd(@NotNull ParserGCInfo gcInfo, long start, long end, boolean failed, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCStart(@NotNull ParserGCInfo gcInfo, long start, long end, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCFailed(int index, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCInfo(int index, int gcId, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseSpaceCreate(int index, long startAddr, long size, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseSpaceAlloc(int index, @NotNull SpaceMode spaceMode, @NotNull SpaceType spaceType, @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @Override
            public void doParseSpaceRelease(int index, @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @Override
            public void doParseSpaceDestroy(int firstIndex, long nRegions, @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @Override
            public void doParseSpaceRedefine(int index, long addr, long size, @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @Override
            public void doCleanUp(@NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @NotNull
            @Override
            public ParsingInfo getParsingInfo() {
                return null;
            }
        };
    }
}
