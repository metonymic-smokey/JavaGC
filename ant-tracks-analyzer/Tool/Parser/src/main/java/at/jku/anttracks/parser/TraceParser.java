
package at.jku.anttracks.parser;

import at.jku.anttracks.heap.GarbageCollectionLookup;
import at.jku.anttracks.heap.HeapListener;
import at.jku.anttracks.heap.io.HeapIndexReader;
import at.jku.anttracks.heap.io.HeapPosition;
import at.jku.anttracks.heap.io.MetaDataConfig;
import at.jku.anttracks.heap.io.MetaDataReaderConfig;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.util.Consts;
import at.jku.anttracks.util.MutableLong;
import at.jku.anttracks.util.TraceException;
import javafx.beans.property.BooleanProperty;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

public abstract class TraceParser<W> {
    public final Symbols symbols;
    public final long fromTime;
    public final long toTime;
    public W workspace;

    private final MetaDataConfig readerConfig;
    protected final Logger logger;

    private long objectsAllocated = 0;
    private long extendedStackFramesStatic = 0;
    private long extendedStackFramesDynamic = 0;

    public static boolean CONSISTENCY_CHECK;
    public static final boolean MULTITHREADING = true;
    public static final long MAX_QUEUE_SIZE = getMaxQueueSize();

    private volatile Throwable error;

    private List<BiFunction<W, ParsingInfo, TraceParsingEventHandler>> additionalEventHandlerSupplier;
    protected CopyOnWriteArrayList<HeapListener> heapListeners = new CopyOnWriteArrayList<>();

    /**
     * Constructor to parse the whole file and writing meta-data the WriterConfig's directory
     *
     * @param symbols
     * @throws Exception
     */
    public TraceParser(Symbols symbols) throws Exception {
        this.symbols = symbols;
        this.readerConfig = null;
        this.fromTime = 0;
        this.toTime = 0;

        this.additionalEventHandlerSupplier = new ArrayList<>();

        logger = Logger.getLogger(this.getClass().getSimpleName() + " " + symbols.root);
    }

    /**
     * Parse from a given GC to another given GC.
     * Watch out! This assumes that meta-data already exists!
     *
     * @param symbols
     * @param readerConfig
     * @param fromGc
     * @param toGc
     * @throws Exception
     */
    public TraceParser(Symbols symbols, MetaDataReaderConfig readerConfig, GarbageCollectionLookup fromGc, GarbageCollectionLookup toGc) throws Exception {
        this.symbols = symbols;
        assert readerConfig != null : "GC lookup relies on metadata";
        this.readerConfig = readerConfig;
        try (HeapIndexReader heapIndexReaderFrom = new HeapIndexReader(readerConfig.path, symbols)) {
            this.fromTime = heapIndexReaderFrom.getGCTime(fromGc);
        }
        try (HeapIndexReader heapIndexReaderFrom = new HeapIndexReader(readerConfig.path, symbols)) {
            this.toTime = heapIndexReaderFrom.getGCTime(toGc);
        }

        logger = Logger.getLogger(TraceParser.class.getSimpleName() + " " + symbols.root);

        this.additionalEventHandlerSupplier = new ArrayList<>();
    }

    /**
     * Parse from a given time to another given time.
     * Watch out! This assumes that meta-data already exists!
     *
     * @param symbols
     * @param readerConfig
     * @param fromTime
     * @param toTime
     * @throws Exception
     */
    public TraceParser(Symbols symbols, MetaDataReaderConfig readerConfig, long fromTime, long toTime) throws Exception {
        this.symbols = symbols;
        assert readerConfig != null : "GC lookup relies on metadata";
        this.readerConfig = readerConfig;
        this.fromTime = fromTime;
        this.toTime = toTime;
        logger = Logger.getLogger(TraceParser.class.getSimpleName() + " " + symbols.root);

        this.additionalEventHandlerSupplier = new ArrayList<>();
    }

    public W parse() throws IOException, TraceException, InterruptedException {
        return parse(null);
    }

    public W parse(BooleanProperty cancellationToken) throws IOException, TraceException, InterruptedException {
        logger.log(Level.INFO, "preparing to parse trace");

        File file = new File(symbols.root + File.separator + symbols.trace);

        TraceScannerFactory factory = null;

        ParsingInfo parsingInfo;
        if (readerConfig == null) {
            assert fromTime == 0 : "If no reader config is given, we must start parsing at the beginning";
            logger.log(Level.INFO, "parsing trace from beginning with empty workspace");
            factory = TraceScannerFactory.create(new File(symbols.root), symbols.header, file);
            parsingInfo = new ParsingInfo(System.currentTimeMillis(), fromTime, toTime, factory.getFrom(), factory.getTo(), factory.getLength());
            workspace = generatePlainWorkspace(factory, parsingInfo);
        } else {
            HeapIndexReader heapIndexReaderFrom = new HeapIndexReader(readerConfig.path, symbols);
            HeapPosition heapPositionFrom = heapIndexReaderFrom.getRangeFromLastHeapDumpToGivenTime(fromTime);

            long to;
            try (HeapIndexReader heapIndexReaderTo = new HeapIndexReader(readerConfig.path, symbols)) {
                to = heapIndexReaderTo.getRangeFromLastHeapDumpToGivenTime(toTime).toPosition;
            }
            factory = TraceScannerFactory.create(new File(symbols.root), symbols.header, file, heapPositionFrom.fromPosition, to);

            logger.log(Level.INFO, "parsing trace, starting at " + heapPositionFrom.toString());
            logger.log(Level.INFO, "building workspace");
            parsingInfo = new ParsingInfo(System.currentTimeMillis(), fromTime, toTime, factory.getFrom(), factory.getTo(), factory.getLength());
            workspace = generateWorkspaceFromMetaData(heapIndexReaderFrom, heapPositionFrom, parsingInfo);
            heapIndexReaderFrom.close();
            logger.log(Level.INFO, "workspace built");
        }

        logger.log(Level.INFO, "parsing trace (length = {0}b)", factory.getLength());
        long time = System.currentTimeMillis();

        try {
            for (Scanner s = factory.getNext(); s != null; s = factory.getNext()) {
                try (Scanner scanner = s) {
                    if (scanner.getPosition() == 0) {
                        TraceFileInfo info = TraceFile.readTraceFileInfo(new InputStream() {
                            @Override
                            public int read() throws IOException {
                                return (scanner.getByte()) & 0xFF;
                            }
                        });
                        if (!Arrays.equals(symbols.header, info.getHeader())) {
                            throw new TraceException("Headers do not match!");
                        }
                    }
                    parse(workspace, scanner, parsingInfo, cancellationToken);
                }
            }

            doParseCleanupAfterSuccessfulParse(workspace);

        } catch (Exception ex) {
            ex.printStackTrace();
        } finally {
            doRemoveListenersOnCompletion(workspace);
            heapListeners.clear();
        }

        doWorkspaceCompletion(workspace);

        time = System.currentTimeMillis() - time;
        logger.log(Level.INFO, "parsed trace in {0}s", (1.0 * time / 1000));

        return workspace;
    }

    protected abstract void doWorkspaceCompletion(W workspace) throws TraceException;

    protected abstract void doRemoveListenersOnCompletion(W workspace);

    protected abstract W generateWorkspaceFromMetaData(HeapIndexReader heapIndexReader, HeapPosition heapPosition, ParsingInfo parsingInfo)
            throws IOException;

    protected abstract W generatePlainWorkspace(TraceScannerFactory factory, ParsingInfo parsingInfo) throws IOException;

    private void parse(W workspace, Scanner scanner, ParsingInfo parsingInfo, BooleanProperty cancellationToken)
            throws IOException, TraceException, InterruptedException {
        logger.log(Level.INFO, "parsing trace @ {0}", scanner.getGlobalPosition());

        final Thread self = Thread.currentThread();
        final MutableLong queueSize = new MutableLong(0);
        final BlockingQueue<QueueEntry> chunks = new LinkedBlockingQueue<>();
        final BlockingQueue<ThreadLocalHeap> masterQueue = new LinkedBlockingQueue<>();
        final Map<String, ThreadLocalHeap> threadLocalHeaps = new HashMap<>();

        logger.log(Level.INFO, "starting slaves");
        final List<TraceSlaveParser<W>> slaves = startSlaveThreads(queueSize, masterQueue, workspace, e -> abort(e, self), parsingInfo);

        logger.log(Level.INFO, "starting master");
        final Thread master = new Thread(() -> processQueueEntries(chunks, masterQueue, threadLocalHeaps, slaves, e -> abort(e, self)), "Trace Parser Master");
        master.start();

        try {
            dispatch(scanner, chunks, queueSize, cancellationToken);
            master.join();

            logger.log(Level.INFO, "waiting for slaves");
            interruptSlaves(masterQueue, slaves);
            joinSlaves(slaves);
            if (cancellationToken != null && cancellationToken.get()) {
                throw new InterruptedException("Parser has been cancelled using cancellation token");
            }
        } catch (InterruptedException ie) {
            master.interrupt();
            interruptSlaves(masterQueue, slaves);
            if (error != null) {
                throw new TraceException(error);
            } else {
                throw ie;
            }
        }

        logger.info(() -> {
            if (!symbols.useCallContext()) {
                return "Call context analysis disabled, no stack statistics available.";
            }
            for (ThreadLocalHeap heap : threadLocalHeaps.values()) {
                objectsAllocated += heap.getObjectsAllocated();
                extendedStackFramesStatic += heap.getExtendedStackFramesStatic();
                extendedStackFramesDynamic += heap.getExtendedStackFramesDynamic();
            }
            String msg = "%,d objects allocated, with %,d more stack frames from static information and %,d more from dynamic context.";
            return String.format(msg, objectsAllocated, extendedStackFramesStatic, extendedStackFramesDynamic);
        });
        if (symbols.useDynamicCallContext()) {
            logger.info(() -> {
                final String msg = "Tried to expand allocation site %,d times, failed %,d times, succeeded %,d times.";
                final long tries = symbols.getAllocationSiteCreationAttempts();
                final long fails = symbols.getAllocationSiteCreationFailures();
                return String.format(msg, tries, fails, tries - fails);
            });
            logger.info(() -> {
                final long exhausted = symbols.getAllocationContextExhausted();
                return String.format("Previous allocations exhausted for %,d allocation sites.", exhausted);
            });
        }
    }

    private void dispatch(Scanner scanner, BlockingQueue<QueueEntry> chunks, MutableLong queueSize, BooleanProperty cancellationToken)
            throws InterruptedException, TraceException {
        final Thread self = Thread.currentThread();
        logger.log(Level.INFO, "starting IO");
        final int COMPRESSED_LOCATION = (Integer.SIZE - 1);
        final int COMPRESSED_MASK = 1 << COMPRESSED_LOCATION;
        final int SYNC_LOCATION = Integer.SIZE - 3;
        final int SYNC_MASK = 3 << SYNC_LOCATION;
        final int LENGTH_MASK = ~(COMPRESSED_MASK | SYNC_MASK);

        // At this location, the trace file is read buffer by buffer.
        // Each buffer may contain multiple events.
        // Following data is read per buffer:
        // 1. Thread
        // 2. Meta-data
        // - a.) Length
        // - b.) Is Compressed
        // - c.) Sync-Level
        // 3. Position
        // 4. Data (Byte-Buffer)
        // All of this data is stored in a (size-limited) BlockingQueue<QueueEntry> queue, which is
        // processed by the processQueueEntries() method (run by the master).

        try {
            for (String thread = scanner.getThread(symbols.heapWordSize);
                 thread != null && error == null && !self.isInterrupted() && (cancellationToken == null || !cancellationToken.get());
                 thread = scanner.getThread(symbols.heapWordSize)) {
                int metadata = scanner.getWord();
                int length = metadata & LENGTH_MASK;
                boolean isCompressed = (metadata & COMPRESSED_MASK) != 0;
                SyncLevel sync = SyncLevel.parse((metadata & SYNC_MASK) >>> SYNC_LOCATION);
                long position = scanner.getGlobalPosition();
                synchronized (queueSize) {
                    queueSize.add(length);
                    queueSize.notifyAll();
                    while (queueSize.get() > MAX_QUEUE_SIZE) {
                        // System.out.println(String.format("Dispatcher waiting, queue size: %,d bytes", queueSize.get()));
                        queueSize.wait();
                    }
                }
                ByteBuffer buffer = scanner.getBuffer(length);
                QueueEntry queueEntry = new QueueEntry(thread, buffer, position, isCompressed, sync);
                chunks.put(queueEntry);
                // System.out.println(String.format("Current queue size: %,d bytes, last buffer length: %,d", queueSize.get(), length));
                // System.out.println(queueEntry);
            }
        } catch (IOException ioException) {
            // This case can occur when the trace file has been cut off at the end because the recording VM has been shut down ungracefully (for example by running out of memory)
            logger.log(Level.WARNING, "IO finished UNEXPECTEDLY ({0} undispatched chunks ahead) ({1} bytes ahead)", new Object[]{chunks.size(), queueSize.get()});
            chunks.put(QueueEntry.Companion.getNULL());
            return;
        }

        logger.log(Level.INFO, "IO finished ({0} undispatched chunks ahead) ({1} bytes ahead)", new Object[]{chunks.size(), queueSize.get()});
        chunks.put(QueueEntry.Companion.getNULL());
    }

    protected abstract void doParseCleanupAfterSuccessfulParse(W workspace) throws TraceException;

    private synchronized void abort(Throwable e, Thread thread) {
        if (error == null) {
            logger.log(Level.SEVERE, "error reported");
            error = e;
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    // This method runs in a separate thread and takes care of the buffer queue.
    // It checks if the queue contains an buffer, and if so, the entry gets
    // added to the respective ThreadLocalHeap
    private void processQueueEntries(BlockingQueue<QueueEntry> chunks,
                                     BlockingQueue<ThreadLocalHeap> queue,
                                     Map<String, ThreadLocalHeap> threads,
                                     List<TraceSlaveParser<W>> slaves,
                                     ErrorHandler error) {
        logger.log(Level.INFO, "master started");
        final Thread self = Thread.currentThread();
        int buffers = 0;

        while (!self.isInterrupted()) {
            try {
                QueueEntry entry = chunks.take();
                if (entry == QueueEntry.Companion.getNULL()) {
                    // NULL entry gets added at the end of read() -> File
                    // parsing finished
                    waitUntilParked(threads);
                    insertCleanUps(queue, threads);
                    waitUntilParked(threads);
                    self.interrupt();
                } else {
                    processQueueEntry(queue, threads, entry);
                    buffers++;
                }
            } catch (InterruptedException ie) {
                self.interrupt();
            } catch (Throwable e) {
                logger.log(Level.SEVERE, "fatal error occured", e);
                error.report(e);
                self.interrupt();
            }
        }
        logger.log(Level.INFO, "master finished, dispatched {0} chunks", buffers);
    }

    // This method processes a single buffer (QueueEntry)
    // This method takes care of the sync level, and assigns a QueueEntry to the
    // master queue.
    // The master queue then is processes by the slave parser(s).
    private void processQueueEntry(BlockingQueue<ThreadLocalHeap> queue, Map<String, ThreadLocalHeap> threads, QueueEntry chunk) throws InterruptedException, Exception {
        if (chunk.getSync() == SyncLevel.FULL) {
            waitUntilParked(threads);
            insertCleanUps(queue, threads);
            waitUntilParked(threads);
            for (ThreadLocalHeap heap : threads.values()) {
                objectsAllocated += heap.getObjectsAllocated();
                extendedStackFramesStatic += heap.getExtendedStackFramesStatic();
                extendedStackFramesDynamic += heap.getExtendedStackFramesDynamic();
            }
            // MW 2018/11/13: Why this? To save space?
            // Can we get rid of this if we want to split up tracefile in thread-local trace files?
            for (String thread : threads.keySet().toArray(new String[threads.size()])) {
                ThreadLocalHeap tlh = threads.get(thread);
                if (tlh.getCurrentLabs().size() == 0) {
                    threads.remove(thread);
                }
            }
        } else if (chunk.getSync() == SyncLevel.ENSURE_ORDER) {
            waitUntilParked(threads);
        } else {
            assert chunk.getSync() == SyncLevel.NONE;
        }

        ThreadLocalHeap threadLocalHeap = threads.get(chunk.getThread());

        if (threadLocalHeap == null) {
            threadLocalHeap = addNewThreadLocalHeap(queue, threads, chunk.getThread());
        }
        addToThreadLocalHeap(queue, chunk, threadLocalHeap);

        if (chunk.getSync() != SyncLevel.NONE || !MULTITHREADING) {
            waitUntilParked(threadLocalHeap);
        }
    }

    private List<TraceSlaveParser<W>> startSlaveThreads(MutableLong queueSize,
                                                        BlockingQueue<ThreadLocalHeap> queue,
                                                        W workspace,
                                                        ErrorHandler handler,
                                                        ParsingInfo parsingInfo)
            throws IOException {
        return startSlaveThreads(queueSize, queue, workspace, handler, CONSISTENCY_CHECK, parsingInfo);
    }

    protected List<TraceSlaveParser<W>> startSlaveThreads(MutableLong queueSize,
                                                          BlockingQueue<ThreadLocalHeap> masterQueue,
                                                          W workspace,
                                                          ErrorHandler handler,
                                                          boolean check,
                                                          ParsingInfo parsingInfo) throws IOException {
        RelAddrFactory relAddrFactory = new RelAddrFactory(symbols.heapWordSize);

        int slaves = 1;
        if (MULTITHREADING) {
            slaves = Consts.getAVAILABLE_PROCESSORS();
        }

        List<TraceSlaveParser<W>> result = new ArrayList<>();
        for (int i = 0; i < slaves; i++) {
            TraceSlaveParser<W> parserThread = new TraceSlaveParser<>(i,
                                                                      queueSize,
                                                                      masterQueue,
                                                                      workspace,
                                                                      relAddrFactory,
                                                                      symbols,
                                                                      check,
                                                                      handler,
                                                                      createMainEventHandler(parsingInfo),
                                                                      additionalEventHandlerSupplier.stream()
                                                                                                    .map(supplier -> supplier.apply(workspace, parsingInfo))
                                                                                                    .toArray(TraceParsingEventHandler[]::new));
            result.add(parserThread);
        }
        return result;
    }

    private void insertCleanUps(BlockingQueue<ThreadLocalHeap> queue, Map<String, ThreadLocalHeap> threads) throws InterruptedException {
        for (String key : threads.keySet()) {
            ThreadLocalHeap threadLocalHeap = threads.get(key);
            synchronized (threadLocalHeap) {
                assert (threadLocalHeap.getState() == ThreadLocalHeap.STATE_PARKED);
                threadLocalHeap.setState(ThreadLocalHeap.STATE_IN_QUEUE_FOR_CLEAN_UP);
                queue.add(threadLocalHeap);
            }
        }
    }

    private void waitUntilParked(Map<String, ThreadLocalHeap> threadMap) throws InterruptedException {
        for (String key : threadMap.keySet()) {
            ThreadLocalHeap threadLocalHeap = threadMap.get(key);
            waitUntilParked(threadLocalHeap);
        }
    }

    private void waitUntilParked(ThreadLocalHeap threadLocalHeap) throws InterruptedException {
        synchronized (threadLocalHeap) {
            while (threadLocalHeap.getState() != ThreadLocalHeap.STATE_PARKED) {
                threadLocalHeap.wait();
            }
        }

    }

    private ThreadLocalHeap addNewThreadLocalHeap(BlockingQueue<ThreadLocalHeap> queue, Map<String, ThreadLocalHeap> threads, String thread)
            throws InterruptedException {
        ThreadLocalHeap threadLocalHeap = createNewThreadLocalHeap(thread);
        threads.put(thread, threadLocalHeap);
        queue.add(threadLocalHeap);
        return threadLocalHeap;
    }

    protected ThreadLocalHeap createNewThreadLocalHeap(String thread) {
        return new ThreadLocalHeap(thread, ThreadLocalHeap.STATE_IN_QUEUE, (short) -1, EventType.GC_END);
    }

    private void addToThreadLocalHeap(BlockingQueue<ThreadLocalHeap> masterQueue, QueueEntry entry, ThreadLocalHeap threadLocalHeap) throws Exception {
        if (threadLocalHeap == null) {
            throw new TraceException("TLH must already exist.");
        }
        synchronized (threadLocalHeap) {
            threadLocalHeap.getQueue().add(entry);

            if (threadLocalHeap.getState() == ThreadLocalHeap.STATE_PARKED) {
                threadLocalHeap.setState(ThreadLocalHeap.STATE_IN_QUEUE);
                masterQueue.add(threadLocalHeap);
            }
        }
    }

    private void interruptSlaves(BlockingQueue<ThreadLocalHeap> queue, List<TraceSlaveParser<W>> slaves) {
        for (TraceSlaveParser<W> thread : slaves) {
            thread.interrupt();
        }
    }

    private void joinSlaves(List<TraceSlaveParser<W>> slaves) throws InterruptedException {
        for (TraceSlaveParser<W> thread : slaves) {
            thread.join();
        }
    }

    protected abstract TraceParsingEventHandler createMainEventHandler(ParsingInfo parsingInfo);

    public void addEventHandler(BiFunction<W, ParsingInfo, TraceParsingEventHandler> handlerSupplier) {
        this.additionalEventHandlerSupplier.add(handlerSupplier);
    }

    private static long getMaxQueueSize() {
        long memory = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax();
        long size = (long) (memory * 0.01); // 1% of maximum heap may be used by input queue
        size = Math.max(size, 1024 * 50); // Minimum size 50kB
        System.out.println("Max queue size: " + size + " bytes");
        return size;
    }

    public void addHeapListener(HeapListener l) {
        heapListeners.add(l);
    }

    public void removeHeapListener(HeapListener l) {
        heapListeners.remove(l);
    }
}