package com.brr.anttracks.cli.main;

import at.jku.anttracks.heap.HeapAdapter;
import at.jku.anttracks.heap.io.MetaDataWriterConfig;
import at.jku.anttracks.heap.io.MetaDataWriterListener;
import at.jku.anttracks.heap.statistics.Statistics;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.heap.MemoryMappedFastHeap;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.nodes.ListGroupingNode;
import at.jku.anttracks.classification.trees.ListClassificationTree;
import at.jku.anttracks.gui.classification.classifier.AllocationSiteClassifier;
import at.jku.anttracks.gui.classification.classifier.TypeClassifier;
import at.jku.anttracks.gui.classification.classifier.CallSitesClassifier;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.HeapStateClassificationInfo;
import at.jku.anttracks.gui.model.FastHeapInfo;
import at.jku.anttracks.gui.model.SelectedClassifierInfo;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.parser.ParserGCInfo;
import at.jku.anttracks.parser.ParsingInfo;
import at.jku.anttracks.parser.TraceParser;
import at.jku.anttracks.parser.classdefinitions.ClassDefinitionsFile;
import at.jku.anttracks.parser.heap.HeapTraceParser;
import at.jku.anttracks.parser.heap.HeapBuilder;
import at.jku.anttracks.parser.symbols.SymbolsFile;
import at.jku.anttracks.parser.symbols.SymbolsParser;
import at.jku.anttracks.util.ApplicationStatistics;
import at.jku.anttracks.util.GCReporter;
import at.jku.anttracks.util.Counter;
import at.jku.anttracks.util.ProgressListener;
import org.jetbrains.annotations.NotNull;
import javafx.beans.property.SimpleBooleanProperty;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Optional;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static at.jku.anttracks.util.Consts.ANT_META_DIRECTORY;



public class Main {

    private static ListClassificationTree groupHeapObjects(HeapStateClassificationInfo statisticsInfo) {
        // updateTitle("Heap State: ");
        // updateMessage(String.format("Classify heap objects for time %,.3fs using classifiers %s",
                                    // statisticsInfo.getHeapStateInfo().getTime() / 1000.0f,
                                    // statisticsInfo.getSelectedClassifierInfo().getSelectedClassifiers()));
        long objectCount = statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get().getObjectCount();

        ListClassificationTree grouping = null;
        // Counter objectsProcessed = new Counter();
        final long t = System.nanoTime();
        // ObjectStream.IterationListener iterationListener = (oc) -> {
            // objectsProcessed.add(oc);
            // updateProgress(objectsProcessed.get(), objectCount);
            // updateClassificationMetrics(objectsProcessed.get(), t);
        // };

        grouping = statisticsInfo.getHeapStateInfo()
                                 .getFastHeapSupplier()
                                 .get()
                                 .groupListParallel(statisticsInfo.getSelectedClassifierInfo().getSelectedFilters().toArray(new Filter[0]),
                                                    statisticsInfo.getSelectedClassifierInfo().getSelectedClassifiers(),
                                                    true,
                                                    true,
                                                    null,
                                                    new SimpleBooleanProperty(false));

        // ClientInfo.meterRegistry.timer("classification." + statisticsInfo.getSelectedClassifierInfo().getSelectedClassifiers().toString() + ".per_object")
                                // .record((long) ((1.0 * System.nanoTime() - t) / grouping.getRoot().getObjectCount() * 1_000_000), TimeUnit.NANOSECONDS);

        //        break;
        //}

        // if (!isCancelled()) {
            // updateClassificationMetrics(objectsProcessed.get(), t);

            // updateMessage(String.format("Calculate object & byte counts and overall closure for time %,.3fs (This may take some seconds, please wait)",
                                        // statisticsInfo.getHeapStateInfo().getTime() / 1000.0f));
            long t2 = System.nanoTime();
            grouping.init(statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get(), true, true, false, false);

            // heapStateClassificationTab.getHeapMetricsTable().groupingInitTime.valueProperty.setValue((System.nanoTime() - t2) / 1_000_000_000.0);

            LOGGER.log(Level.INFO, "Finished classification");
        // }

        if (grouping.getRoot().getObjectCount() != statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get().getObjectCount()) {
            LOGGER.info(String.format("Classification Tree Overall Object Count (%,d) does not match Fast Heap Overall Object Count (%,d)",
                                           grouping.getRoot().getObjectCount(),
                                           statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get().getObjectCount()));
        }

        if (grouping.getRoot().getByteCount(statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get()) != statisticsInfo.getHeapStateInfo()
                                                                                                                            .getFastHeapSupplier()
                                                                                                                            .get()
                                                                                                                            .getByteCount()) {
            LOGGER.info(String.format("Classification Tree Overall Byte Count (%,d) does not match Fast Heap Overall Byte Count (%,d)",
                                           grouping.getRoot().getByteCount(statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get()),
                                           statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get().getByteCount()));
        }

        Optional<Statistics> stat = statisticsInfo.getHeapStateInfo()
                                                  .getAppInfo()
                                                  .getStatistics()
                                                  .stream()
                                                  .filter(statistics -> statistics.getInfo().getTime() == statisticsInfo.getHeapStateInfo().getTime())
                                                  .findFirst();

        // Stat is not present if HPROF file is used
        if (stat.isPresent()) {
            long statObjectCount = stat.get().getEden().memoryConsumption.getObjects() +
                    stat.get().getSurvivor().memoryConsumption.getObjects() +
                    stat.get().getOld().memoryConsumption.getObjects();
            long statMemoryConsumption = stat.get().getEden().memoryConsumption.getBytes() +
                    stat.get().getSurvivor().memoryConsumption.getBytes() +
                    stat.get().getOld().memoryConsumption.getBytes();
            if (grouping.getRoot().getObjectCount() != statObjectCount) {
                LOGGER.info(String.format("Classification Tree Overall Object Count (%,d) does not match Statistics Object Count (%,d)",
                                               grouping.getRoot().getObjectCount(),
                                               statObjectCount));
            }

            if (grouping.getRoot().getByteCount(statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get()) != statMemoryConsumption) {
                LOGGER.info(String.format("Classification Tree Overall Byte Count (%,d) does not match Statistics Byte Count (%,d)",
                                               grouping.getRoot().getByteCount(statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get()),
                                               statMemoryConsumption));
            }
        }

        return grouping;
    }

    private static final Logger LOGGER = Logger.getLogger(Main.class.getSimpleName());

    public static void main(String argv[]) throws IOException {
        List<File> traceFiles = new ArrayList<>();
        String metaPath = null;
        File features = null;
        boolean reportGc = true;
        int useCallContext = Symbols.CALLCONTEXT_NONE;

        int metaDumpCount = MetaDataWriterConfig.DEFAULT_DUMP_COUNT;
        boolean metaMajorGCsOnly = MetaDataWriterConfig.DEFAULT_AT_MAJOR_GCS_ONLY;
        boolean check = false;

        for (int index = 0; index < argv.length; index++) {
            System.out.printf("Parameter %d of %d: %s\n", index + 1, argv.length, argv[index]);
            String arg = argv[index];
            if (arg.contains("=")) {
                int splitterIndex = arg.indexOf("=");
                String key = arg.substring(0, splitterIndex);
                String value = arg.substring(splitterIndex + 1);
                System.out.printf("Key: %s, Value: %s\n", key, value);
                if (key.equals("ConsistencyTest")) {
                    check = Boolean.parseBoolean(value);
                    System.out.printf("ConsistencyTest: %s\n", check);
                } else if (key.equals("MetaDataPath")) {
                    metaPath = value;
                    System.out.printf("Meta path: %s\n", metaPath);
                } else if (key.equals("MetaDataDumpCount")) {
                    metaDumpCount = Integer.parseInt(value);
                    System.out.printf("Meta dump count: %s\n", metaDumpCount);
                } else if (key.equals("MetaDataAtMajorGCsOnly")) {
                    metaMajorGCsOnly = Boolean.parseBoolean(value);
                    System.out.printf("Meta data at MajorGCs only: %s\n", metaMajorGCsOnly);
                } else if (key.equals("FeaturesPath")) {
                    features = new File(value);
                    System.out.printf("Feature Path: %s\n", features);
                } else if (key.equals("ReportGC")) {
                    reportGc = Boolean.parseBoolean(value);
                    System.out.printf("Report GC: %s\n", reportGc);
                } else if (key.equals("UseCallContext")) {
                    switch (value.toLowerCase()) {
                        case "none":
                        case "false":
                            useCallContext = Symbols.CALLCONTEXT_NONE;
                            break;
                        case "static":
                        case "fast":
                            useCallContext = Symbols.CALLCONTEXT_STATIC;
                            break;
                        case "full":
                        case "true":
                            useCallContext = Symbols.CALLCONTEXT_FULL;
                            break;
                        default:
                            System.err.println("Unknown value for UseCallContext ignored: " + value);
                            break;
                    }
                    System.out.printf("Call Context: %s\n", useCallContext);
                } else {
                    System.err.println("Did not understand parameter '" + arg + "'");
                    System.exit(1);
                }
            } else if (arg.equals("GenerateMetaData")) {
                metaPath = ANT_META_DIRECTORY;
            } else {
                traceFiles.add(new File(arg).getAbsoluteFile());
            }
        }

        // benchmark flag to compare parsing vs consuming of ptrs
        String parsePtrs = System.getProperty("ParsePtrs");
        SymbolsParser.parsePtrs = parsePtrs != null ? Boolean.parseBoolean(parsePtrs) : true;

        TraceParser.CONSISTENCY_CHECK = check;

        int errors = 0;
        if (reportGc) {
            GCReporter.getInstance();
        }

        if (traceFiles.size() == 0) {
            System.err.println("No trace file path given!");
        }

        // 1. Logging
        String loggingConfigFileString = "java.util.logging.config.file";
        if (System.getProperty(loggingConfigFileString) == null) {
            System.getProperties().setProperty(loggingConfigFileString, "log.config");
        }
        System.out.println("Log config file: " + System.getProperty(loggingConfigFileString));

        // 2. Statistics output path
        String statisticsPathString = "at.jku.anttracks.gui.printStatisticsPath";
        String printStatisticsPath = System.getProperty(statisticsPathString);
        System.out.println("Statistics result file: " + (printStatisticsPath == null ? "null" : printStatisticsPath));

        Runtime.getRuntime().addShutdownHook(new Thread((Runnable) () -> {
            if (printStatisticsPath != null) {
                ApplicationStatistics.getInstance()
                                     .export(new File(printStatisticsPath.replace("%d",
                                                                                  new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss").format(new Date(System.currentTimeMillis
                                                                                          ())))
                                                                         .replace(" ", "")));
            }
            ApplicationStatistics.getInstance().print();
        }, "Shutdown-thread"));

        // 3. Assertions
        boolean assertionsEnabled = false;
        assert assertionsEnabled = true;
        System.out.println("Assertions enabled: " + assertionsEnabled);

        for (File file : traceFiles) {
            try {
                SymbolsParser symbolsParser = new SymbolsParser(SymbolsFile.findSymbolsFileToTrace(file),
                                                                ClassDefinitionsFile.findClassDefinitionsFileToTrace(file),
                                                                features,
                                                                useCallContext);
                Symbols sym = symbolsParser.parse();

                MetaDataWriterConfig config = metaPath != null ? new MetaDataWriterConfig(sym.root + File.separator + metaPath, metaDumpCount, metaMajorGCsOnly) : null;
                HeapTraceParser parser = new HeapTraceParser(sym);
                System.out.println("helooooooooooooooooo im innnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnnn");
                if (config != null) {
                    parser.addHeapListener(new MetaDataWriterListener(config, Statistics.Companion::collect));
                }

                // List<Short> gcIds = new ArrayList<Short>();
                LinkedHashSet<ParserGCInfo> gcInfos = new LinkedHashSet<ParserGCInfo>();
                LinkedHashSet<Long> times = new LinkedHashSet<Long>();
                ArrayList<Integer> someCountIGuess = new ArrayList<Integer>();

                parser.addHeapListener(new HeapAdapter() {

                    @Override
                    public void phaseChanging(
                            @NotNull
                                    Object sender,
                            @NotNull
                                    ParserGCInfo from,
                            @NotNull
                                    ParserGCInfo to,
                            boolean failed,
                            long position,
                            @NotNull
                                    ParsingInfo parsingInfo,
                            boolean inParserTimeWindow) {
                        double progress = 1.0 * (position - parsingInfo.getFromByte()) / parsingInfo.getTraceLength();
                        // System.out.println(sender, from, to, parsingInfo);
                        System.out.println("Sender:" + sender);
                        System.out.println("from:" + from);
                        gcInfos.add(from);
                        times.add(from.getTime());
                        // gcIds.add(from.getId());

                        System.out.println("from time:" + from.getTime());
                        System.out.println("to:" + to);
                        gcInfos.add(to);
                        times.add(to.getTime());
                        // gcIds.add(to.getId());
                        System.out.println("failed:" + failed);
                        System.out.println("position:" + position);

                        // build heap ig
                        // DetailedHeap heap = HeapBuilder.constructHeap(sym, parsingInfo);
                        // System.out.println("Heap: " + heap);
                        // System.out.println("GC: " + heap.getGC());
                        // nvm, heap is already built in "sender"


                        // System.out.println("Heap: " + (DetailedHeap) sender);
                        // System.out.println("Chain: " + chain);
                        // System.out.println("GroupingNode: " + gn);


                        // System.out.println("parsingInfo: " + parsingInfo + ", parsingStartTime: " + parsingInfo.parsingStartTime + ", fromTime: " + parsingInfo.fromTime + ", toTime: " + parsingInfo.toTime + ", fromByte: " + parsingInfo.fromByte + ", toByte: " + parsingInfo.toByte + ", traceLength: " + parsingInfo.traceLength);
                        LOGGER.log(Level.INFO, () -> String.format("parsed %.2f%%", progress * 100));
                    }
                });

                DetailedHeap heap = parser.parse();
                System.out.println("Heap: " + heap);
                System.out.println("Heap spaces: " + heap.getSpacesUncloned());
                System.out.println("Number of objects: " + heap.getObjectCount());

                // HeapBuilder heapBuilder = new HeapBuilder(heap, sym, heap.getParsingInfo());

                System.out.println("gcInfos: " + gcInfos);
                System.out.println("Times: " + times);
                // heapBuilder.doParseGCInfo(0, gcIds.get(3));

                // ListClassificationTree classTree = new ListClassificationTree(heap, true, true, true, true);
                // // ListGroupingNode gn = new ListGroupingNode();
                Long[] timesArr = new Long[ times.size() ];
                timesArr = times.toArray( timesArr );
                System.out.println("TimesArr: " + timesArr);

                // for(int i = 0; i < times.size(); ++i) {
                //     classTree.getRoot().exportAsJSON(heap, chain, timesArr[i], new File("antracks_" + someCountIGuess.size() + ".json"), new File("default_" + someCountIGuess.size() + ".json"));
                //     someCountIGuess.add(someCountIGuess.size() + 1);
                // }
                
                System.out.println("Heap: " + heap);
                System.out.println("Heap spaces: " + heap.getSpacesUncloned());
                System.out.println("Number of objects: " + heap.getObjectCount());

                // try AppInfo
                AppInfo appInfo = new AppInfo("something", sym, file, null);
                // MemoryMappedFastHeap idxBasedHeap = new MemoryMappedFastHeap(heap, true, new ProgressListener() {
                //     @Override void fire(double progress, String newMessage) {
                //         System.out.println("AAAAAAAAAAAAAAAAAAAa progress: " + progress + " message: " + newMessage);
                //     }
                // });
                MemoryMappedFastHeap idxBasedHeap = new MemoryMappedFastHeap(heap);
                

                System.out.println("AppInfo.statistics: " + appInfo.getStatistics());
                System.out.println("MemoryMappedFastHeap.getObjectCount: " + idxBasedHeap.getObjectCount());

                for(int i = 0; i < times.size(); ++i) {
                    FastHeapInfo fastHeapInfo = new FastHeapInfo(appInfo, timesArr[i]);
                    fastHeapInfo.setHeap(idxBasedHeap);

                    TypeClassifier classifier1 = new TypeClassifier();
                    AllocationSiteClassifier classifier2 = new AllocationSiteClassifier();
                    CallSitesClassifier classifier3 = new CallSitesClassifier();
                    classifier1.setup(fastHeapInfo.getSymbolsSupplier(), fastHeapInfo.getFastHeapSupplier());
                    classifier2.setup(fastHeapInfo.getSymbolsSupplier(), fastHeapInfo.getFastHeapSupplier());
                    classifier3.setup(fastHeapInfo.getSymbolsSupplier(), fastHeapInfo.getFastHeapSupplier());
    
                    ClassifierChain chain = new ClassifierChain(classifier1, classifier2, classifier3);
                    SelectedClassifierInfo selectedClassifiers = new SelectedClassifierInfo(chain, new ArrayList<Filter>());
                    HeapStateClassificationInfo classificationInfo = new HeapStateClassificationInfo(fastHeapInfo, selectedClassifiers);

                    Thread.sleep(5000);
                    
                    ListClassificationTree classTree = groupHeapObjects(classificationInfo);
                    classTree.getRoot().exportAsJSON(classificationInfo.getHeapStateInfo().getFastHeapSupplier().get(),
                                                                  chain,
                                                                  timesArr[i],
                                                                  new File("outputs/antracks_" + someCountIGuess.size() + ".json"),
                                                                  new File("outputs/default_" + someCountIGuess.size() + ".json"));

                    // classificationInfo.getGrouping().getRoot().exportAsJSON(classificationInfo.getHeapStateInfo().getFastHeapSupplier().get(),
                    //                                               chain,
                    //                                               timesArr[i],
                    //                                               new File("outputs/antracks_" + someCountIGuess.size() + ".json"),
                    //                                               new File("outputs/default_" + someCountIGuess.size() + ".json"));
                }
                
            } catch (Throwable e) {
                e.printStackTrace(System.err);
                errors++;
            }
        }
        if (errors > 0) {
            System.err.println(errors + " errors found.");
        }
    }

}
