package com.brr.anttracks.cli.main;

import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.heap.space.SpaceInfo;

import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.utils.Consts;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.HeapListener;
import at.jku.anttracks.heap.io.MetaDataWriterConfig;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.heap.space.SpaceMode;
import at.jku.anttracks.heap.space.SpaceType;
import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.heap.symbols.AllocationSite;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.parser.*;
import at.jku.anttracks.parser.classdefinitions.ClassDefinitionsFile;
import at.jku.anttracks.parser.heap.HeapTraceParser;
import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.parser.symbols.SymbolsFile;
import at.jku.anttracks.parser.symbols.SymbolsParser;
import at.jku.anttracks.util.TraceException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.brr.anttracks.cli.main.JsonExportMain;

import java.nio.file.Files.*;
import java.lang.Long;
import java.lang.Integer;
import java.lang.Short;
import java.lang.Boolean;
import java.nio.file.StandardOpenOption;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.Files;
import java.io.IOException;
import java.util.List;
import java.util.HashMap;
import java.util.logging.LogManager;
import java.util.concurrent.atomic.AtomicLong;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Properties;

// Provided by Markus Weninger, SSW, JKU, Austria
public class Main {

    private static AppInfo appInfo;
    private static Symbols symbols;
    private static HeapTraceParser parser;

    public static void main(String[] args) {
        // Class.forName("org.postgresql.Driver");
        Properties props = new Properties();
        props.put("jdbc.url", "jdbc:postgresql://localhost:5432/postgres");
        props.put("user", "postgres");
        props.put("password", "password");
        props.put("ssl", "false");

        try {
            Connection c = DriverManager.getConnection(props.getProperty("jdbc.url"), props);
            System.out.println("Success");
            System.out.println(c.getClientInfo());
            String createSensorTableQuery = "CREATE TABLE test_data (id SERIAL PRIMARY KEY, type TEXT NOT NULL, location TEXT NOT NULL)";
            try (Statement stmt = c.createStatement()) {
                stmt.execute(createSensorTableQuery);
            }
            // c.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
            // This would not be ultimately necessary but make working with classification a
            // bit easier
            appInfo = new AppInfo(traceFile.getAbsolutePath(), traceFile, null);
            // The MetaDataPath is where statistics, etc. are going to be stored while the
            // trace is parsed
            appInfo.setMetaDataPath((BaseFile.isPlainFile(SymbolsFile.SYMBOL_FILE_ID, appInfo.getSymbolsFile())
                    ? appInfo.getSymbolsFile().getParent()
                    : appInfo.getSymbolsFile()).toString() + File.separator + Consts.ANT_META_DIRECTORY);

            File symbolsFile = SymbolsFile.findSymbolsFileToTrace(traceFile);
            File classDefsFile = ClassDefinitionsFile.findClassDefinitionsFileToTrace(traceFile);
            System.out.println("Symbols file found   : " + symbolsFile);
            System.out.println("Class defs file found: " + classDefsFile);

            // Before parsing the trace, first parse the symbols file
            // This file contains information about types, allocation sites, etc.
            symbols = new SymbolsParser(symbolsFile, classDefsFile, null, Symbols.CALLCONTEXT_NONE).parse();
            appInfo.setSymbols(symbols);

            // Then, create a trace parser that uses this symbols information
            parser = new HeapTraceParser(appInfo.getSymbols());
            // If we want, we can add a meta data writer to store statistics (e.g., heap
            // size, etc.) to disk
            // This also writes certain heap states to disk to make subsequent analyses of
            // the same trace file faster
            // Since this is not needed, we disable it at the moment
            // parser.addHeapListener(new MetaDataWriterListener(new
            // MetaDataWriterConfig(appInfo.getMetaDataPath()),
            // Statistics.Companion::collect));

            // TODO: Modify the method customHeapListener if you want to perform tasks at
            // the start and at the end of GCs (see the TODOs in this method)

            // uncomment this for JSON export
            // parser.addHeapListener(JsonExportMain.customHeapListener());
            parser.addHeapListener(customHeapListener());
            // TODO Modify the method customEventHandler if you want to inspect each event
            // read from the trace file.
            // For example, this could be used to count the number of allocation events,
            // move events, etc.
            parser.addEventHandler(JsonExportMain::customEventHandler);
            parser.addEventHandler(Main::customEventHandler);
            DetailedHeap detailedHeap = parser.parse();
            // Once the whole trace has been parsed, if meta-data has been written, we can
            // read statistics information.
            // Some analyses then be performed based on the statistics stored in
            // appInfo.getStatistics()
            // appInfo.getStatistics().addAll(Statistics.Companion.readStatisticsFromMetadata(appInfo.getMetaDataPath(),
            // appInfo.getSymbols()));
        } catch (Throwable e) {
            e.printStackTrace(System.err);
        }
    }

    // TODO Modify this method if you want to perform tasks at the beginning or the
    // end of GCs
    // Otherwise, just ignore
    private static HeapListener customHeapListener() {
        return new HeapListener() {

            @Override
            public void close(@NotNull Object sender, @NotNull ParsingInfo parsingInfo) {
                // This method is called when the parser finished
                DetailedHeap heap = (DetailedHeap) sender;
                // TODO: Optionally add own code that should be executed at the very end
                System.out.println("FINISHED");
            }

            @Override
            public void phaseChanging(@NotNull Object sender, @NotNull ParserGCInfo from, @NotNull ParserGCInfo to,
                    boolean failed, long position, @NotNull ParsingInfo parsingInfo, boolean inParserTimeWindow) {
                // This method is called before the parser changes from mutator phase (i.e.,
                // running application) to GC phase or from GC phase to mutator phase
                // Use this method to perform steps at the start of a GC
                DetailedHeap heap = (DetailedHeap) sender;
                if (to.getEventType() == EventType.GC_START) {
                    // Switching into GC phase
                    // If you want to inspect the heap, we suggest to use IndexBasedHeap idxHeap =
                    // heap.toIndexBasedHeap(false, null) for faster object access
                    // IndexBasedHeap idxHeap = heap.toIndexBasedHeap(false, null);

                    // heap.toObjectStream().forEach(new ObjectVisitor() {
                    // @Override public void visit(long address, AddressHO obj, SpaceInfo space,
                    // List<? extends RootPtr> rootPtrs) {
                    // System.out.format("Address: %d, obj info: %s, bornAt: %d, lastMovedAt: %d,
                    // tag: %d %n", address, obj.getInfo(), obj.getBornAt(), obj.getLastMovedAt(),
                    // obj.getTag());
                    // }
                    // }, new ObjectVisitor.Settings(true));

                    System.out.printf("GC %d starts (mutator phase was running from %,dms to %,dms)%n", to.getId(),
                            from.getTime(), to.getTime());
                }
            }

            @Override
            public void phaseChanged(@NotNull Object sender, @NotNull ParserGCInfo from, @NotNull ParserGCInfo to,
                    boolean failed, long position, @NotNull ParsingInfo parsingInfo, boolean inParserTimeWindow) {
                // This method is called after the parser changes from mutator phase (i.e.,
                // running application) to GC phase or from GC phase to mutator phase
                // Use this method to perform steps at the end of a GC
                DetailedHeap heap = (DetailedHeap) sender;
                if (to.getEventType() == EventType.GC_END) {
                    // Switching into mutator phase

                    // If you want to inspect the heap, we suggest to use IndexBasedHeap idxHeap =
                    // heap.toIndexBasedHeap(false, null) for faster object access
                    System.out.printf("GC %d ends (GC phase was running from %,dms to %,dms)%n", to.getId(),
                            from.getTime(), to.getTime());
                }
            }
        };
    }

    // TODO Modify this method if you want to inspect the events read from the trace
    // file (e.g., counting ObjAlloc events)
    // Otherwise, just ignore
    private static TraceParsingEventHandler customEventHandler(DetailedHeap heap, ParsingInfo parsingInfo) {
        return new TraceParsingEventHandler() {
            @Override
            public void doKeepAlive(@NotNull EventType eventType, long addr, @NotNull ThreadLocalHeap threadLocalHeap)
                    throws TraceException {

            }

            @Override
            public void doParseGCTag(@NotNull String tagText, @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @Override
            public void doPtrEvent(@NotNull EventType eventType, long fromAddr, long toAddr, @NotNull long[] ptrs,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public long getMoveTarget(long fromAddr, long toAddr, @NotNull SpaceType toSpaceType,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return 0;
            }

            @Override
            public void doParsePlabAlloc(int size, long addr, @NotNull ThreadLocalHeap threadLocalHeap)
                    throws TraceException {

            }

            @Override
            public void doParseTlabAlloc(int size, long addr, @NotNull ThreadLocalHeap threadLocalHeap)
                    throws TraceException {

            }

            @NotNull
            @Override
            public ObjectInfo doParseObjAllocSlow(@NotNull EventType eventType, @NotNull AllocationSite allocationSite,
                    long addr, boolean isArray, int arrayLength, int size, boolean mayBeFiller,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                // System.out.println(eventType + "\t" + allocationSite + "\t" + addr + "\t" +
                // isArray + "\t" + arrayLength + "\t" + size);
                return null;
            }

            @NotNull
            @Override
            public ObjectInfo doParseObjAllocSlowCiIr_Deviant(@NotNull EventType eventType,
                    @NotNull AllocatedType allocatedType, @NotNull AllocationSite allocationSite, long addr,
                    boolean isArray, int arrayLength, int realAllocatedTypeId, @NotNull ThreadLocalHeap threadLocalHeap)
                    throws TraceException {
                return null;
            }

            @NotNull
            @Override
            public ObjectInfo doParseObjAllocNormalIr(@NotNull EventType eventType, int allocationSiteId,
                    @NotNull AllocationSite allocationSite, long addr, @NotNull ThreadLocalHeap threadLocalHeap)
                    throws TraceException {
                return null;
            }

            @NotNull
            @Override
            public ObjectInfo doParseObjAllocNormalCi(@NotNull EventType eventType,
                    @NotNull AllocationSite allocationSite, @NotNull AllocatedType allocatedType, long addr,
                    boolean isArray, int arrayLength, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return null;
            }

            @Override
            public long doParseObjAllocFastIr(@NotNull EventType eventType, @NotNull AllocationSite allocationSite,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return 0;
            }

            @Override
            public long doParseObjAllocFastC2DeviantType(@NotNull EventType eventType, int header, int allocationSiteId,
                    @NotNull AllocatedType allocatedType, boolean isArray, int arrayLength,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return 0;
            }

            @Override
            public long doParseObjAllocFastCi(@NotNull EventType eventType, @NotNull AllocationSite allocationSite,
                    @NotNull AllocatedType allocatedType, boolean isArray, int arrayLength,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return 0;
            }

            @Override
            public void doParseSyncObj(@NotNull EventType eventType, int allocationSiteId,
                    @NotNull AllocatedType allocatedType, long fromAddr, long toAddr, int length, int size,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseThreadDeath(long id, @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @Override
            public void doParseThreadAlive(int header, long id, @NotNull String name,
                    @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @NotNull
            @Override
            public List<ObjectInfo> doParseGCMoveRegion(@NotNull EventType eventType, long fromAddr, long toAddr,
                    int numOfObjects, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return null;
            }

            @Override
            public void doParseGCDebugRootPtr(long ptr, @NotNull String vmCall,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCOtherRootPtr(long ptr, @NotNull RootPtr.RootType rootType,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCJNIGlobalRootPtr(long ptr, boolean weak, @NotNull ThreadLocalHeap threadLocalHeap)
                    throws TraceException {

            }

            @Override
            public void doParseGCCodeBlobRootPtr(long ptr, int classId, int methodId,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCVMInternalThreadDataRootPtr(long ptr, long threadId,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCLocalVariableRootPtr(long ptr, long threadId, int classId, int methodId, int slot,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCJNILocalRootPtr(long ptr, long threadId, @NotNull ThreadLocalHeap threadLocalHeap)
                    throws TraceException {

            }

            @Override
            public void doParseGCStaticFieldRootPtr(long ptr, int classId, int offset,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCClassRootPtr(long ptr, int classId, @NotNull ThreadLocalHeap threadLocalHeap)
                    throws TraceException {

            }

            @Override
            public void doParseGCClassLoaderRootPtr(long ptr, @NotNull String loaderName,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public long doGCMove(@NotNull EventType eventType, long fromAddr, long toAddr,
                    @Nullable SpaceType toSpaceType, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                return 0;
            }

            @Override
            public void doParseGCContinue(int id, long address, @NotNull ThreadLocalHeap threadLocalHeap)
                    throws TraceException {

            }

            @Override
            public void doParseGCInterrupt(int id, long address, @NotNull ThreadLocalHeap threadLocalHeap)
                    throws TraceException {

            }

            final AtomicLong lastTag = new AtomicLong(1);
            HashMap<Long, Long> tagBornMap = new HashMap<Long, Long>();

            private void logObject(String event, long address, AddressHO obj, @NotNull ParserGCInfo gcInfo) {
                // TODO: show all call sites instead of getCallSites()[0]
                System.out.println("OBJECT " + event + ": " + obj + " at: " + gcInfo.getTime() + " address: " + address
                        + " gcId: " + gcInfo.getId() + " allocationSites: " + obj.getSite().getCallSites()[0]);
            }

            private void objectBorn(long address, AddressHO obj, @NotNull ParserGCInfo gcInfo) {
                // increment and set tag
                long tag = lastTag.getAndIncrement();
                obj.setTag(tag);

                // add tag -> time to map
                Long tagL = new Long(tag);
                Long timeL = new Long(gcInfo.getTime());
                tagBornMap.put(tagL, timeL);

                // logObject("BORN", address, obj, gcInfo);
            }

            private void objectMoved(long address, AddressHO obj, @NotNull ParserGCInfo gcInfo) {
                // logObject("MOVED", address, obj, gcInfo);
            }

            Path outputPath = Paths.get("outputs/lifetimes.csv");

            private void objectYeeted(long address, AddressHO obj, @NotNull ParserGCInfo gcInfo) {
                // logObject("DELETED", address, obj, gcInfo);

                Long tag = new Long(obj.getTag());
                if (tagBornMap.containsKey(tag)) {
                    Long bornTime = tagBornMap.remove(tag);
                    Long curTime = gcInfo.getTime();
                    Long lifetime = curTime - bornTime;
                    String str = Short.toString(obj.getBornAt()) + ","
                        + obj.getLastMovedAt() + "," + obj.getTag() + ","
                        + obj.getSize() + "," + obj.getArrayLength() + ","
                        + address + "," + gcInfo.getTime() + ","
                        + gcInfo.getId() + ","
                        + obj.getSite().getCallSites()[0] + ","
                        + lifetime + "\n";

                    try {
                        Files.write(outputPath, str.getBytes(), StandardOpenOption.APPEND);
                    } catch (IOException x) {
                        System.err.println(x);
                    }
                }
            }

            @Override
            public void doParseGCEnd(@NotNull ParserGCInfo gcInfo, long start, long end, boolean failed,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                heap.toObjectStream(false).forEach(new ObjectVisitor() {
                    @Override
                    public void visit(long address, AddressHO obj, SpaceInfo space, List<? extends RootPtr> rootPtrs) {
                        if (gcInfo.getId() == obj.getLastMovedAt()) {
                            objectMoved(address, obj, gcInfo);
                        }
                        if (space.isBeingCollected() && obj.getLastMovedAt() != gcInfo.getId()) {
                            objectYeeted(address, obj, gcInfo);
                        }
                    }
                }, new ObjectVisitor.Settings(true));
            }

            @Override
            public void doParseGCStart(@NotNull ParserGCInfo gcInfo, long start, long end,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {
                // System.out.println("doParseGCStart");
                heap.toObjectStream(true).forEach(new ObjectVisitor() {
                    @Override
                    public void visit(long address, AddressHO obj, SpaceInfo space, List<? extends RootPtr> rootPtrs) {
                        // System.out.format("Address: %d, obj info: %s, bornAt: %d, lastMovedAt: %d,
                        // tag: %d %n", address, obj.getInfo(), obj.getBornAt(), obj.getLastMovedAt(),
                        // obj.getTag());
                        if (heap.latestGCId() == obj.getBornAt()) {
                            objectBorn(address, obj, gcInfo);
                        }
                        if (heap.latestGCId() == obj.getLastMovedAt()) {
                            objectMoved(address, obj, gcInfo);
                        }
                        // if (space.isBeingCollected() && obj.getLastMovedAt() != heap.latestGCId()) {
                        // System.out.println("[START] OBJECT YEETED: " + obj + " at: " +
                        // gcInfo.getTime() + " address: " + address);
                        // }
                    }
                }, new ObjectVisitor.Settings(true));
            }

            @Override
            public void doParseGCFailed(int index, @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseGCInfo(int index, int gcId, @NotNull ThreadLocalHeap threadLocalHeap)
                    throws TraceException {

            }

            @Override
            public void doParseSpaceCreate(int index, long startAddr, long size,
                    @NotNull ThreadLocalHeap threadLocalHeap) throws TraceException {

            }

            @Override
            public void doParseSpaceAlloc(int index, @NotNull SpaceMode spaceMode, @NotNull SpaceType spaceType,
                    @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @Override
            public void doParseSpaceRelease(int index, @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @Override
            public void doParseSpaceDestroy(int firstIndex, long nRegions, @NotNull ThreadLocalHeap threadLocalHeap) {

            }

            @Override
            public void doParseSpaceRedefine(int index, long addr, long size,
                    @NotNull ThreadLocalHeap threadLocalHeap) {

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
