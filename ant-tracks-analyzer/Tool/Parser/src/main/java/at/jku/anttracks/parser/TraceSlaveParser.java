
package at.jku.anttracks.parser;

import at.jku.anttracks.heap.GarbageCollectionCause;
import at.jku.anttracks.heap.GarbageCollectionType;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.heap.space.SpaceMode;
import at.jku.anttracks.heap.space.SpaceType;
import at.jku.anttracks.heap.symbols.AllocatedType;
import at.jku.anttracks.heap.symbols.AllocationSite;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.heap.pointer.PtrEvent;
import at.jku.anttracks.util.MutableLong;
import at.jku.anttracks.util.TraceException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

import static at.jku.anttracks.heap.roots.RootPtr.RootType;
import static at.jku.anttracks.heap.symbols.AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN;
import static at.jku.anttracks.heap.symbols.AllocatedTypes.MIRROR_CLASS_NAME;
import static at.jku.anttracks.util.Consts.*;

public class TraceSlaveParser<W> {
    public static final long NULL_PTR = -1;

    // --------------------------------------------------------
    // ----------------- GCInfo -------------------------------
    // --------------------------------------------------------

    protected ParserGCInfo parseGCMeta(EventType eventType, int word) throws TraceException {
        GarbageCollectionType gcTypeId = GarbageCollectionType.Companion.parse(recoverValue(word, 1, 1));
        GarbageCollectionCause gcCause = symbols.causes.get(recoverValue(word, 2, 2));

        int flags = recoverValue(word, 3, 3);
        boolean concurrent = (flags & (1 << 1)) != 0;

        int id = getNextWord();
        long time = getNextDoubleWord();

        long base = getNextDoubleWord();
        relAddrFactory.setBase(base);

        return new ParserGCInfo(eventType, gcTypeId, gcCause, (short) id, time, concurrent);
    }

    // --------------------------------------------------------
    // ------------ AbstractTraceSlaveParser ------------------
    // --------------------------------------------------------

    // --------------------------------------------------------
    // ------------ Fields ------------------------------------
    // --------------------------------------------------------

    protected final W workspace;
    protected final RelAddrFactory relAddrFactory;
    protected final Symbols symbols;
    protected final Logger logger;

    protected static final int ARRAY_SMALL_LENGTH_INDEX = 3;
    protected static final int ALLOCATION_SITE_INDEX_1 = 1;
    protected static final int ALLOCATION_SITE_INDEX_2 = 2;
    protected static final int ALLOCATION_SITE_INDEX_3 = 3; // big allocSite
    protected static final int EVENT_TYPE_INDEX = 0;

    private final MutableLong size;
    private final BlockingQueue<ThreadLocalHeap> masterQueue;
    private final Decompressor decompressor;
    private final boolean check;

    private final ErrorHandler error;
    private final Thread worker;

    private ByteBuffer buffer;

    private final List<TraceParsingEventHandler> otherEventHandlers;
    private final TraceParsingEventHandler mainEventHandler;

    private final long[] ptrArr0 = new long[0];
    private final long[] ptrArr1 = new long[1];
    private final long[] ptrArr2 = new long[2];
    private final long[] ptrArr3 = new long[3];
    private final long[] ptrArr4 = new long[4];
    private final long[] ptrArr5 = new long[5];
    private final long[] ptrArr6 = new long[6];
    private final long[] ptrArr7 = new long[7];
    private final long[] ptrArr8 = new long[8];
    private final long[] ptrArr9 = new long[9];
    private final long[] ptrArr10 = new long[10];
    private final long[] ptrArr11 = new long[11];
    private final long[] ptrArr12 = new long[12];

    // --------------------------------------------------------
    // ------------ ctor --------------------------------------
    // --------------------------------------------------------

    public TraceSlaveParser(int id,
                            MutableLong size,
                            BlockingQueue<ThreadLocalHeap> masterQueue,
                            W workspace,
                            RelAddrFactory relAddrFactory,
                            Symbols symbols,
                            boolean test,
                            ErrorHandler error,
                            TraceParsingEventHandler mainEventHandler,
                            TraceParsingEventHandler... otherEventHandlers) {
        this.size = size;
        this.masterQueue = masterQueue;
        this.workspace = workspace;
        this.relAddrFactory = relAddrFactory;
        this.symbols = symbols;
        this.decompressor = new Decompressor();
        this.check = test;
        this.error = error;
        worker = new Thread(this::run, "TraceSlaveParser " + id);
        buffer = null;
        logger = Logger.getLogger(this.getClass().getSimpleName() + " " + id + " " + symbols.root);
        worker.start();

        this.mainEventHandler = mainEventHandler;
        this.otherEventHandlers = Arrays.asList(otherEventHandlers);
    }

    // --------------------------------------------------------
    // ------------ Methods ------------------------------------
    // --------------------------------------------------------

    public void interrupt() {
        worker.interrupt();
    }

    public void join() throws InterruptedException {
        worker.join();
    }

    private void run() {
        logger.log(Level.INFO, "slave started");
        assert Thread.currentThread() == worker;
        int parsedBuffers = 0;

        while (!worker.isInterrupted()) {
            try {
                ThreadLocalHeap threadLocalHeap = masterQueue.take();
                boolean cleanUp;
                synchronized (threadLocalHeap) {
                    if (threadLocalHeap.getState() == ThreadLocalHeap.STATE_IN_QUEUE) {
                        threadLocalHeap.setState(ThreadLocalHeap.STATE_IN_PROCESS);
                        cleanUp = false;
                    } else if (threadLocalHeap.getState() == ThreadLocalHeap.STATE_IN_QUEUE_FOR_CLEAN_UP) {
                        threadLocalHeap.setState(ThreadLocalHeap.STATE_IN_PROCESS);
                        cleanUp = true;
                    } else {
                        errorOnthreadLocalHeapState(threadLocalHeap);
                        return; // to make the compiler happy :-)
                    }
                }

                for (QueueEntry entry = threadLocalHeap.getQueue().poll(); entry != null; entry = threadLocalHeap.getQueue().poll()) {
                    buffer = entry.getBuffer();
                    long start = entry.getPosition();
                    long end = start + buffer.limit();
                    if (entry.isCompressed()) {
                        buffer = decompressor.decode(buffer);
                    }
                    if (buffer.order() != ByteOrder.LITTLE_ENDIAN) {
                        buffer.order(ByteOrder.LITTLE_ENDIAN);
                    }

                    while (buffer.position() != buffer.limit()) {
                        evaluateEvent(threadLocalHeap, start, end);
                    }

                    buffer = null; // to avoid memory leak with last buffer
                    parsedBuffers++;
                    synchronized (size) {
                        size.add(-(end - start));
                        size.notifyAll();
                    }
                }

                if (cleanUp) {
                    // Cleanups happen:
                    // 1. When a file has been read (i.e., when multiple trace files are generated)
                    // 2. When an event with SyncLevel = FULL is sent
                    cleanUp(threadLocalHeap);
                }
                parkThreadLocalHeap(threadLocalHeap);
            } catch (InterruptedException ie) {
                worker.interrupt();
            } catch (Throwable e) {
                logger.log(Level.SEVERE, "fatal error", e);
                error.report(e);
                worker.interrupt();
            }

        }

        logger.log(Level.INFO, "slave finished, handled {0} chunks", parsedBuffers);
    }

    private void parkThreadLocalHeap(ThreadLocalHeap threadLocalHeap) {
        synchronized (threadLocalHeap) {
            assert threadLocalHeap.getState() == ThreadLocalHeap.STATE_IN_PROCESS;
            if (threadLocalHeap.getQueue().isEmpty()) {
                threadLocalHeap.setState(ThreadLocalHeap.STATE_PARKED);
            } else {
                // this happens in rare cases where we did not get another
                // buffer (queue is empty) and we start parking while the master
                // is adding another entry to the queue
                // in this case we end up with a thread local heap that is
                // parked, but has an element in the queue which contains
                // important events that will be missed (like missing foreign
                // filler oops, holes in the heap, non-full LABs)
                threadLocalHeap.setState(ThreadLocalHeap.STATE_IN_QUEUE);
                masterQueue.add(threadLocalHeap);
            }
            threadLocalHeap.notifyAll();
        }
    }

    protected void cleanUp(ThreadLocalHeap threadLocalHeap) throws TraceException {
        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doCleanUp(threadLocalHeap);
        }
        mainEventHandler.doCleanUp(threadLocalHeap);
    }

    private void evaluateEvent(ThreadLocalHeap threadLocalHeap, long start, long end) throws TraceException {

        int word = getNextWord();
        long unsignedWord = word & 0x00000000ffffffffL;
        int recovered = recoverValue(unsignedWord, EVENT_TYPE_INDEX, EVENT_TYPE_INDEX);
        int toSpace = recovered & 0x3;

        EventType type = EventType.Companion.parse(recovered >> 2);
        switch (type) {
            case TLAB_ALLOC: {
                parseTlabAlloc(word, threadLocalHeap);
                break;
            }
            case PLAB_ALLOC: {
                parsePlabAlloc(word, threadLocalHeap);
                break;
            }
            case OBJ_ALLOC_FAST_IR: {
                parseObjAllocFastIr(type, word, threadLocalHeap);
                break;
            }
            case OBJ_ALLOC_FAST_C1:
            case OBJ_ALLOC_FAST_C2: {
                parseObjAllocFastCi(type, word, threadLocalHeap);
                break;
            }
            case OBJ_ALLOC_NORMAL_IR: {
                parseObjAllocNormalIr(type, word, threadLocalHeap);
                break;
            }
            case OBJ_ALLOC_NORMAL_C1:
            case OBJ_ALLOC_NORMAL_C2: {
                parseObjAllocNormalCi(type, word, threadLocalHeap);
                break;
            }
            case OBJ_ALLOC_SLOW: {
                parseObjAllocSlow(type, word, threadLocalHeap);
                break;
            }
            case OBJ_ALLOC_SLOW_IR:
            case OBJ_ALLOC_SLOW_C1:
            case OBJ_ALLOC_SLOW_C2:
            case OBJ_ALLOC_SLOW_IR_DEVIANT_TYPE:
            case OBJ_ALLOC_SLOW_C1_DEVIANT_TYPE:
            case OBJ_ALLOC_SLOW_C2_DEVIANT_TYPE: {
                parseObjAllocSlowCiIr_Deviant(type, word, threadLocalHeap);
                break;
            }
            case GC_START: {
                parseGCStart(word, start, end, threadLocalHeap);
                break;
            }
            case GC_END: {
                parseGCEnd(word, start, end, threadLocalHeap);
                break;
            }
            case GC_MOVE_FAST:
            case GC_MOVE_FAST_WIDE:
            case GC_MOVE_FAST_NARROW:
            case GC_MOVE_FAST_PTR:
            case GC_MOVE_FAST_WIDE_PTR: {
                parseGCMoveFast_WideOrNarrow_Ptr(type, word, toSpace, threadLocalHeap);
                break;
            }
            case GC_MOVE_SLOW:
            case GC_MOVE_SLOW_PTR: {
                parseGCMoveSlow(type, word, threadLocalHeap);
                break;
            }
            case GC_KEEP_ALIVE:
            case GC_KEEP_ALIVE_PTR: {
                parseGCKeepAlive(type, word, threadLocalHeap);
                break;
            }
            case GC_ROOT_PTR: {
                parseGCRootPtr(type, word, threadLocalHeap);
                break;
            }
            case GC_PTR_EXTENSION:
            case GC_PTR_UPDATE_PREMOVE:
            case GC_PTR_UPDATE_POSTMOVE:
            case GC_PTR_MULTITHREADED: {
                parseGCObjPtr(type, word, threadLocalHeap);
                break;
            }
            case GC_MOVE_REGION: {
                parseGCMoveRegion(type, word, threadLocalHeap);
                break;
            }
            case SYNC_OBJ_NARROW:
            case SYNC_OBJ: {
                parseSyncObj(type, word, threadLocalHeap);
                break;
            }
            case THREAD_ALIVE: {
                parseThreadAlive(type, word, threadLocalHeap);
                break;
            }
            case THREAD_DEATH: {
                parseThreadDeath(type, word, threadLocalHeap);
                break;
            }
            case OBJ_ALLOC_FAST_C2_DEVIANT_TYPE: {
                parseObjAllocFastC2DeviantType(type, word, threadLocalHeap);
                break;
            }
            case NOP:
                break;
            case GC_DEALLOCATION: {
                throw new Error("Not implemented");
            }
            case SPACE_CREATE: {
                parseSpaceCreate(word, threadLocalHeap);
                break;
            }
            case SPACE_ALLOC: {
                parseSpaceAlloc(word, threadLocalHeap);
                break;
            }
            case SPACE_RELEASE: {
                parseSpaceRelease(word, threadLocalHeap);
                break;
            }
            case SPACE_REDEFINE: {
                parseSpaceRedefine(word, threadLocalHeap);
                break;
            }
            case SPACE_DESTROY: {
                parseSpaceDestroy(word, threadLocalHeap);
                break;
            }
            case GC_INFO: {
                parseGCInfo(word, threadLocalHeap);
                break;
            }
            case GC_FAILED: {
                parseGCFailed(word, threadLocalHeap);
                break;
            }
            case GC_INTERRUPT: {
                parseGCInterrupt(word, threadLocalHeap);
                break;
            }
            case GC_CONTINUE: {
                parseGCContinue(word, threadLocalHeap);
                break;
            }
            case GC_TAG: {
                parseGCTag(word, threadLocalHeap);
                break;
            }
            default: {
                throw new TraceException("PARSE ERROR. Invalid trace file!. Unknown EventType=" + type);
            }
        }

        if (symbols.anchors && type.getMayBeFollowedByAnchor()) {
            int anchor = buffer.getInt();
            if ((anchor & ANCHOR_MASK) != ANCHOR_MASK) {
                throw new AssertionError("Anchor missing (" + (start + buffer.position()) + ")");
            }
            EventType check = EventType.Companion.parse((anchor & ~ANCHOR_MASK) >> (8 * 1));
            if (check != type) {
                throw new AssertionError("Anchor inconsistent (" + (start + buffer.position()) + ")");
            }
        }
    }

    private void parseGCTag(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int tagTextLength = getNextWord();
        String tagText = getString(tagTextLength);

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseGCTag(tagText, threadLocalHeap);
        }
        mainEventHandler.doParseGCTag(tagText, threadLocalHeap);
    }

    protected void parseSpaceRedefine(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int index = getNextWord();
        long startAddr = getNextDoubleWord();
        long size = getNextDoubleWord();
        // boolean postponed = getNextWord() > 0;
        // int source = getNextWord();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseSpaceRedefine(index, startAddr, size, threadLocalHeap);
        }
        mainEventHandler.doParseSpaceRedefine(index, startAddr, size, threadLocalHeap);
    }

    protected void parseSpaceDestroy(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int firstIndex = getNextWord();
        long nRegions = getNextDoubleWord();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseSpaceDestroy(firstIndex, nRegions, threadLocalHeap);
        }
        mainEventHandler.doParseSpaceDestroy(firstIndex, nRegions, threadLocalHeap);
    }

    protected void parseSpaceRelease(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int index = getNextWord();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseSpaceRelease(index, threadLocalHeap);
        }
        mainEventHandler.doParseSpaceRelease(index, threadLocalHeap);
    }

    protected void parseSpaceAlloc(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        SpaceType spaceType = SpaceType.values()[(word >> 18) & 0xFF];
        SpaceMode spaceMode = SpaceMode.values()[(word >> 10) & 0xFF];
        assert spaceType != null;
        assert spaceMode != null;
        int index = getNextWord();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseSpaceAlloc(index, spaceMode, spaceType, threadLocalHeap);
        }
        mainEventHandler.doParseSpaceAlloc(index, spaceMode, spaceType, threadLocalHeap);
    }

    protected void parseSpaceCreate(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        // int spaceType = (word >> 22) & 0b1111;
        int index = getNextWord();
        long startAddr = getNextDoubleWord();
        long size = getNextDoubleWord();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseSpaceCreate(index, startAddr, size, threadLocalHeap);
        }
        mainEventHandler.doParseSpaceCreate(index, startAddr, size, threadLocalHeap);
    }

    protected void parseGCInfo(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int index = word & 0x00FFFFFF;
        int id = getNextWord();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseGCInfo(index, id, threadLocalHeap);
        }
        mainEventHandler.doParseGCInfo(index, id, threadLocalHeap);
    }

    protected void parseGCFailed(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int index = word & 0x00FFFFFF;

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseGCFailed(index, threadLocalHeap);
        }
        mainEventHandler.doParseGCFailed(index, threadLocalHeap);
    }

    protected void parseGCStart(int word, long start, long end, ThreadLocalHeap threadLocalHeap) throws TraceException {
        ParserGCInfo info = parseGCMeta(EventType.GC_START, word);

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseGCStart(info, start, end, threadLocalHeap);
        }
        mainEventHandler.doParseGCStart(info, start, end, threadLocalHeap);

    }

    protected void parseGCEnd(int word, long start, long end, ThreadLocalHeap threadLocalHeap) throws TraceException {
        ParserGCInfo info = parseGCMeta(EventType.GC_END, word);
        int flags = recoverValue(word, 3, 3);
        boolean failed = (flags & 1) != 0;

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseGCEnd(info, start, end, failed, threadLocalHeap);
        }
        mainEventHandler.doParseGCEnd(info, start, end, failed, threadLocalHeap);
    }

    protected void parseGCInterrupt(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int id = getNextWord();
        long address = getNextDoubleWord();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseGCInterrupt(id, address, threadLocalHeap);
        }
        mainEventHandler.doParseGCInterrupt(id, address, threadLocalHeap);
    }

    protected void parseGCContinue(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int id = getNextWord();
        long address = getNextDoubleWord();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseGCContinue(id, address, threadLocalHeap);
        }
        mainEventHandler.doParseGCContinue(id, address, threadLocalHeap);
    }

    protected void parseGCMoveFast_WideOrNarrow_Ptr(EventType type, int word, int toSpace, ThreadLocalHeap threadLocalHeap) throws TraceException {
        SpaceType toSpaceType = RelAddrFactory.Companion.getDefinedSpaceOnly(toSpace);

        long toAddr = -1;
        long fromAddr = -1;

        if (type == EventType.GC_MOVE_FAST_NARROW) {
            int from = (int) (RelAddrFactory.Companion.getDefinedAddrOnly(recoverValue(word, 1, 3)) >> 2);
            fromAddr = relAddrFactory.create(from);
        } else if (type == EventType.GC_MOVE_FAST_WIDE || type == EventType.GC_MOVE_FAST_WIDE_PTR) {
            fromAddr = getNextDoubleWord();
        } else if (type == EventType.GC_MOVE_FAST || type == EventType.GC_MOVE_FAST_PTR) {
            fromAddr = relAddrFactory.create(getNextWord());
        } else {
            assert false;
            throw new TraceException("internal error");
        }

        long assignedAddr = doGCMove(type, fromAddr, toAddr, toSpaceType, threadLocalHeap);

        if (type == EventType.GC_MOVE_FAST_PTR || type == EventType.GC_MOVE_FAST_WIDE_PTR) {
            if (TraceParser.CONSISTENCY_CHECK) {
                assert getMoveTarget(fromAddr, toAddr, toSpaceType, threadLocalHeap) == assignedAddr :
                        String.format("Move target %,d does not match actual assigned address %,d",
                                      getMoveTarget(fromAddr, toAddr, toSpaceType, threadLocalHeap),
                                      assignedAddr);
            }
            long[] ptrs = parsePointers(word, assignedAddr, threadLocalHeap);
            doPtrEvent(type, fromAddr, assignedAddr, ptrs, threadLocalHeap);
        }
    }

    protected void parseGCMoveSlow(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        long fromAddr = getNextDoubleWord();
        long toAddr = getNextDoubleWord();

        doGCMove(type, fromAddr, toAddr, null, threadLocalHeap);
        if (type == EventType.GC_MOVE_SLOW_PTR) {
            long[] ptrs = parsePointers(word, toAddr, threadLocalHeap);
            doPtrEvent(type, fromAddr, toAddr, ptrs, threadLocalHeap);
        }
    }

    protected void parseGCKeepAlive(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        long addr = getNextDoubleWord();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doKeepAlive(type, addr, threadLocalHeap);
        }
        mainEventHandler.doKeepAlive(type, addr, threadLocalHeap);
        if (type == EventType.GC_KEEP_ALIVE_PTR) {
            long[] ptrs = parsePointers(word, addr, threadLocalHeap);
            doPtrEvent(type, addr, addr, ptrs, threadLocalHeap);
        }
    }

    protected void parseGCRootPtr(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {

        for (int i = 0; i < RootPtr.MAX_ROOTS_PER_EVENT; i++) {
            long ptr = getNextDoubleWord();
            if (ptr == -1) {    // not a nullptr (those are 0) but the convention for end of root block
                // no more roots in this block
                break;
            } else if (ptr == 0) {
                // NULLPTR
                ptr = NULL_PTR;    // because we use -1 for NULLPTRs
            }

            RootType rootType = RootType.values()[getNextWord()];
            switch (rootType) {
                case CLASS_LOADER_ROOT:
                    String loaderName = getString(getNextWord());
                    for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
                        TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
                        parser.doParseGCClassLoaderRootPtr(ptr, loaderName, threadLocalHeap);
                    }
                    mainEventHandler.doParseGCClassLoaderRootPtr(ptr, loaderName, threadLocalHeap);
                    break;

                case CLASS_ROOT:
                    int classId = getNextWord();
                    for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
                        TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
                        parser.doParseGCClassRootPtr(ptr, classId, threadLocalHeap);
                    }
                    mainEventHandler.doParseGCClassRootPtr(ptr, classId, threadLocalHeap);
                    break;

                case STATIC_FIELD_ROOT:
                    classId = getNextWord();
                    int offset = getNextWord();
                    for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
                        TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
                        parser.doParseGCStaticFieldRootPtr(ptr, classId, offset, threadLocalHeap);
                    }
                    mainEventHandler.doParseGCStaticFieldRootPtr(ptr, classId, offset, threadLocalHeap);
                    break;

                case LOCAL_VARIABLE_ROOT:
                    long threadId = getNextDoubleWord();
                    classId = getNextWord();
                    int methodId = getNextWord();
                    int slot = getNextWord();
                    for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
                        TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
                        parser.doParseGCLocalVariableRootPtr(ptr, threadId, classId, methodId, slot, threadLocalHeap);
                    }
                    mainEventHandler.doParseGCLocalVariableRootPtr(ptr, threadId, classId, methodId, slot, threadLocalHeap);
                    break;

                case VM_INTERNAL_THREAD_DATA_ROOT:
                    threadId = getNextDoubleWord();
                    for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
                        TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
                        parser.doParseGCVMInternalThreadDataRootPtr(ptr, threadId, threadLocalHeap);
                    }
                    mainEventHandler.doParseGCVMInternalThreadDataRootPtr(ptr, threadId, threadLocalHeap);
                    break;

                case CODE_BLOB_ROOT:
                    classId = getNextWord();
                    methodId = getNextWord();
                    for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
                        TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
                        parser.doParseGCCodeBlobRootPtr(ptr, classId, methodId, threadLocalHeap);
                    }
                    mainEventHandler.doParseGCCodeBlobRootPtr(ptr, classId, methodId, threadLocalHeap);
                    break;

                case JNI_LOCAL_ROOT:
                    threadId = getNextDoubleWord();
                    for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
                        TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
                        parser.doParseGCJNILocalRootPtr(ptr, threadId, threadLocalHeap);
                    }
                    mainEventHandler.doParseGCJNILocalRootPtr(ptr, threadId, threadLocalHeap);
                    break;

                case JNI_GLOBAL_ROOT:
                    boolean weak = getNextWord() != 0;
                    for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
                        TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
                        parser.doParseGCJNIGlobalRootPtr(ptr, weak, threadLocalHeap);
                    }
                    mainEventHandler.doParseGCJNIGlobalRootPtr(ptr, weak, threadLocalHeap);
                    break;

                // other roots
                case CLASS_LOADER_INTERNAL_ROOT:
                case UNIVERSE_ROOT:
                case SYSTEM_DICTIONARY_ROOT:
                case BUSY_MONITOR_ROOT:
                case INTERNED_STRING:
                case FLAT_PROFILER_ROOT:
                case MANAGEMENT_ROOT:
                case JVMTI_ROOT:
                    for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
                        TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
                        parser.doParseGCOtherRootPtr(ptr, rootType, threadLocalHeap);
                    }
                    mainEventHandler.doParseGCOtherRootPtr(ptr, rootType, threadLocalHeap);
                    break;

                case DEBUG_ROOT:
                    String vmCall = getString(getNextWord());
                    for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
                        TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
                        parser.doParseGCDebugRootPtr(ptr, vmCall, threadLocalHeap);
                    }
                    mainEventHandler.doParseGCDebugRootPtr(ptr, vmCall, threadLocalHeap);
                    break;

                default:
                    throw new TraceException(rootType + " is not a valid root type id!");
            }
        }
    }

    protected void parseGCObjPtr(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        long addr = getNextDoubleWord();
        long[] ptrs = parsePointers(word, addr, threadLocalHeap);
        doPtrEvent(type, -1, addr, ptrs, threadLocalHeap);
    }

    protected List<ObjectInfo> parseGCMoveRegion(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int numOfObjects = recoverValue(word, 1, 3);
        long fromAddr = getNextDoubleWord();
        long toAddr = getNextDoubleWord();
        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseGCMoveRegion(type, fromAddr, toAddr, numOfObjects, threadLocalHeap);
        }
        return mainEventHandler.doParseGCMoveRegion(type, fromAddr, toAddr, numOfObjects, threadLocalHeap);
    }

    protected void parseThreadAlive(EventType type, int header, ThreadLocalHeap threadLocalHeap) throws TraceException {
        final int MAX_NAME_LENGTH = 12;
        long id = getNextDoubleWord();
        StringBuilder name = new StringBuilder();
        end:
        for (int i = 0; i < MAX_NAME_LENGTH; i++) {
            int word = getNextWord();
            for (int j = 0; j < 4; j++) {
                char c = (char) ((word >> ((3 - j) * 8)) & 0xFF);
                if (c == '\0') {
                    break end;
                }
                name.append(c);
            }
        }

        String finalName = name.toString();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseThreadAlive(header, id, finalName, threadLocalHeap);
        }
        mainEventHandler.doParseThreadAlive(header, id, finalName, threadLocalHeap);
    }

    protected void parseThreadDeath(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        long id = getNextDoubleWord();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseThreadDeath(id, threadLocalHeap);
        }
        mainEventHandler.doParseThreadDeath(id, threadLocalHeap);
    }

    protected void parseSyncObj(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int allocationSiteId = recoverValue(word, 1, 3);
        int allocatedTypeId = getNextWord();
        AllocatedType allocatedType = symbols.types.getById(allocatedTypeId);

        long fromAddr = getNextDoubleWord();
        long toAddr = type == EventType.SYNC_OBJ ? getNextDoubleWord() : fromAddr;
        int length;
        if (allocatedType.internalName.startsWith("[")) {
            length = getNextWord();
        } else {
            length = UNDEFINED_LENGTH;
        }
        int size;
        // TODO: We reworked mirror classes, probably something has to be changed here!
        if (allocatedType.internalName.equals(MIRROR_CLASS_NAME)) {
            size = getNextWord();
        } else {
            size = 0;
        }

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseSyncObj(type, allocationSiteId, allocatedType, fromAddr, toAddr, length, size, threadLocalHeap);
        }
        mainEventHandler.doParseSyncObj(type,
                                        allocationSiteId,
                                        allocatedType,
                                        fromAddr,
                                        toAddr,
                                        length,
                                        size,
                                        threadLocalHeap);
    }

    protected void parseObjAllocFastCi(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_2);
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (((allocationSiteId >> 15) & 1) != 0) {
            allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_3);
        }
        AllocationSite allocationSite = symbols.sites.getById(allocationSiteId);
        assert allocationSite != null : "No allocation site found for id " + allocationSite;
        AllocatedType allocatedType = symbols.types.getById(allocationSite.getAllocatedTypeId());

        boolean isArray = allocatedType.isArray();
        int arrayLength = UNDEFINED_LENGTH;
        if (isArray) {
            arrayLength = recoverArrayLength(word);
        }

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseObjAllocFastCi(type, allocationSite, allocatedType, isArray, arrayLength, threadLocalHeap);
        }
        mainEventHandler.doParseObjAllocFastCi(type, allocationSite, allocatedType, isArray, arrayLength, threadLocalHeap);
    }

    protected void parseObjAllocFastC2DeviantType(EventType type, int header, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int allocationSiteId = recoverValue(header, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_2);
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (((allocationSiteId >> 15) & 1) != 0) {
            allocationSiteId = recoverValue(header, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_3);
        }
        AllocatedType allocatedType = symbols.types.getById(getNextWord());

        boolean isArray = allocatedType.isArray();
        int arrayLength = UNDEFINED_LENGTH;
        if (isArray) {
            arrayLength = recoverArrayLength(header);
        }

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseObjAllocFastC2DeviantType(type, header, allocationSiteId, allocatedType, isArray, arrayLength, threadLocalHeap);
        }
        mainEventHandler.doParseObjAllocFastC2DeviantType(type, header, allocationSiteId, allocatedType, isArray, arrayLength, threadLocalHeap);
    }

    protected void parseObjAllocFastIr(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_2);
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (((allocationSiteId >> 15) & 1) != 0) {
            allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_3);
        }

        AllocationSite allocationSite = symbols.sites.getById(allocationSiteId);

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseObjAllocFastIr(type, allocationSite, threadLocalHeap);
        }
        mainEventHandler.doParseObjAllocFastIr(type, allocationSite, threadLocalHeap);
    }

    protected void parseObjAllocNormalCi(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_2);
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (((allocationSiteId >> 15) & 1) != 0) {
            allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_3);
        }
        AllocationSite allocationSite = symbols.sites.getById(allocationSiteId);
        AllocatedType allocatedType = symbols.types.getById(allocationSite.getAllocatedTypeId());
        errorOnMirrorClass(allocatedType);

        boolean isArray = allocatedType.isArray();
        long addr = getNextDoubleWord();
        int arrayLength = UNDEFINED_LENGTH;
        if (isArray) {
            arrayLength = recoverArrayLength(word);
        }

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseObjAllocNormalCi(type, allocationSite, allocatedType, addr, isArray, arrayLength, threadLocalHeap);
        }
        mainEventHandler.doParseObjAllocNormalCi(type, allocationSite, allocatedType, addr, isArray, arrayLength, threadLocalHeap);
    }

    protected void parseObjAllocNormalIr(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {

        int allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_2);
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (((allocationSiteId >> 15) & 1) != 0) {
            allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_3);
        }
        AllocationSite allocationSite = symbols.sites.getById(allocationSiteId);
        long addr = getNextDoubleWord();

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseObjAllocNormalIr(type, allocationSiteId, allocationSite, addr, threadLocalHeap);
        }
        mainEventHandler.doParseObjAllocNormalIr(type, allocationSiteId, allocationSite, addr, threadLocalHeap);
    }

    protected void parseObjAllocSlowCiIr_Deviant(EventType eventType, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_2);

        boolean bigAllocSite = false;
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (((allocationSiteId >> 15) & 1) != 0) {
            allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_3);
            bigAllocSite = true;
        }

        AllocationSite allocationSite = symbols.sites.getById(allocationSiteId);
        long addr = getNextDoubleWord();

        int arrayLength = UNDEFINED_LENGTH;
        if (!bigAllocSite) {
            // all arrays have a small allocSite ID
            // But also some other classes (such as String) can have a small alloc site
            // They return an array length of 0
            arrayLength = recoverArrayLength(word);
        }

        int realAllocatedTypeId = -1;
        if (eventType == EventType.OBJ_ALLOC_SLOW_C1_DEVIANT_TYPE || eventType == EventType.OBJ_ALLOC_SLOW_IR_DEVIANT_TYPE || eventType == EventType.OBJ_ALLOC_SLOW_C2_DEVIANT_TYPE) {
            realAllocatedTypeId = getNextWord();
        }

        AllocatedType type = symbols.types.getById(allocationSite.getAllocatedTypeId());

        if (!type.isArray()) {
            arrayLength = -1;
        }

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseObjAllocSlowCiIr_Deviant(eventType, type, allocationSite, addr, type.isArray(), arrayLength, realAllocatedTypeId, threadLocalHeap);
        }
        mainEventHandler.doParseObjAllocSlowCiIr_Deviant(eventType,
                                                         type,
                                                         allocationSite,
                                                         addr,
                                                         type.isArray(),
                                                         arrayLength,
                                                         realAllocatedTypeId,
                                                         threadLocalHeap);
    }

    protected void parseObjAllocSlow(EventType type, int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_2);
        boolean bigAllocSite = false;
        // check if first bit is set, if yes --> big allocSite (3 byte), otherwise small allocSite (2 byte)
        if (((allocationSiteId >> 15) & 1) != 0) {
            allocationSiteId = recoverValue(word, ALLOCATION_SITE_INDEX_1, ALLOCATION_SITE_INDEX_3);
            bigAllocSite = true;
        }

        AllocationSite allocationSite = symbols.sites.getById(allocationSiteId);

        long addr = getNextDoubleWord();

        int arrayLength = UNDEFINED_LENGTH;
        if (!bigAllocSite) {
            // all arrays have a small allocSite ID
            // But also some other classes (such as String) can have a small alloc site
            // They return an array length of 0
            arrayLength = recoverArrayLength(word);
        }

        if (allocationSite.getAllocatedTypeId() == ALLOCATED_TYPE_IDENTIFIER_UNKNOWN) {
            int id = getNextWord();
            allocationSite = allocationSite.copy(id);
        }

        AllocatedType allocatedType = symbols.types.getById(allocationSite.getAllocatedTypeId());

        boolean isArray = false;
        int size = UNDEFINED_LENGTH;

        if (allocatedType instanceof AllocatedType.MirrorAllocatedType) {
            size = getNextWord();
            // Symbols file does not have correct size information for mirrors
            // This is corrected here (including padding)
            allocatedType.size = size;
            arrayLength = UNDEFINED_LENGTH;
        } else {
            isArray = allocatedType.internalName.startsWith("[");
        }

        if (!isArray) {
            arrayLength = UNDEFINED_LENGTH;
        }

        boolean mayBeFiller = mayBeFiller(symbols, allocationSite);

        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseObjAllocSlow(type, allocationSite, addr, isArray, arrayLength, size, mayBeFiller, threadLocalHeap);
        }
        mainEventHandler.doParseObjAllocSlow(type, allocationSite, addr, isArray, arrayLength, size, mayBeFiller, threadLocalHeap);
    }

    protected void parseTlabAlloc(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int size = (int) getNextDoubleWord();
        long addr = getNextDoubleWord();
        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParseTlabAlloc(size, addr, threadLocalHeap);
        }
        mainEventHandler.doParseTlabAlloc(size, addr, threadLocalHeap);
    }

    protected void parsePlabAlloc(int word, ThreadLocalHeap threadLocalHeap) throws TraceException {
        int size = (int) getNextDoubleWord();
        long addr = getNextDoubleWord();
        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doParsePlabAlloc(size, addr, threadLocalHeap);
        }
        mainEventHandler.doParsePlabAlloc(size, addr, threadLocalHeap);
    }

    // --------------------------------------------------------
    // ---------------- Helper methods ------------------------
    // --------------------------------------------------------

    protected int recoverArrayLength(int word) throws TraceException {
        int arrayLength = recoverValue(word, ARRAY_SMALL_LENGTH_INDEX, ARRAY_SMALL_LENGTH_INDEX);
        if (arrayLength == ARRAY_SIZE_MAX_SMALL) {
            arrayLength = getNextWord();
        }
        return arrayLength;
    }

    public static boolean mayBeFiller(Symbols symbols, AllocationSite allocationSite) {
        if (allocationSite.getAllocatedTypeId() == ALLOCATED_TYPE_IDENTIFIER_UNKNOWN) {
            return true;
        }

        AllocatedType allocatedType = symbols.types.getById(allocationSite.getAllocatedTypeId());

        return (allocatedType.internalName.equals("[I") || allocatedType.internalName.equals("Ljava/lang/Object;"));
    }

    protected long doGCMove(EventType type, long fromAddr, long toAddr, SpaceType toSpaceType, ThreadLocalHeap threadLocalHeap) throws TraceException {
        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doGCMove(type, fromAddr, toAddr, toSpaceType, threadLocalHeap);
        }
        return mainEventHandler.doGCMove(type, fromAddr, toAddr, toSpaceType, threadLocalHeap);
    }

    protected long getMoveTarget(long fromAddr, long toAddr, SpaceType toSpaceType, ThreadLocalHeap threadLocalHeap) throws TraceException {
        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.getMoveTarget(fromAddr, toAddr, toSpaceType, threadLocalHeap);
        }
        return mainEventHandler.getMoveTarget(fromAddr, toAddr, toSpaceType, threadLocalHeap);
    }

    protected void doPtrEvent(EventType eventType, long fromAddr, long toAddr, long[] ptrs, ThreadLocalHeap threadLocalHeap) throws TraceException {
        for (int parserNr = 0; parserNr < otherEventHandlers.size(); parserNr++) {
            TraceParsingEventHandler parser = otherEventHandlers.get(parserNr);
            parser.doPtrEvent(eventType, fromAddr, toAddr, ptrs, threadLocalHeap);
        }
        mainEventHandler.doPtrEvent(eventType, fromAddr, toAddr, ptrs, threadLocalHeap);
    }

    // --------------------------------------------------------
    // ----------------- Error methods ------------------------
    // --------------------------------------------------------
    // --------------------------------------------------------

    protected void errorOnthreadLocalHeapState(ThreadLocalHeap threadLocalHeap) throws TraceException {
        throw new TraceException("State is " + threadLocalHeap.getState() + ", but expected " + ThreadLocalHeap.STATE_IN_QUEUE + " or " + ThreadLocalHeap.STATE_IN_QUEUE_FOR_CLEAN_UP);
    }

    protected void errorOnMirrorClass(AllocatedType allocatedType) throws TraceException {
        if (allocatedType.internalName.equals(ObjectInfo.MIRROR_CLASS)) {
            throw new TraceException("PARSE ERROR. Expected no MIRROR_CLASS here");
        }
    }

    // --------------------------------------------------------
    // ------------------ Word methods ------------------------
    // --------------------------------------------------------

    // just consumes
    protected void getPointers(int word) throws TraceException {

        int ptrKinds = recoverValue(word, 1, 3);
        int i = 0;
        boolean encodingEnd = false;

        while (!encodingEnd && i < PtrEvent.MAX_PTRS_PER_EVENT) {
            int kind = (ptrKinds >> ((PtrEvent.MAX_PTRS_PER_EVENT - i - 1) * 2)) & 0x3;
            if (kind == PtrEvent.ENCODING_RELATIVE_PTR) {
                getNextWord();
            } else if (kind == PtrEvent.ENCODING_ABSOLUTE_PTR) {
                getNextDoubleWord();
            } else if (kind == PtrEvent.ENCODING_END) {
                encodingEnd = true;
            }
            i++;
        }

    }

    // returns absolute pointer addresses
    protected long[] parsePointers(int word, long ref, ThreadLocalHeap localHeap) throws TraceException {
        int ptrKinds = recoverValue(word, 1, 3);
        long lastRef = 0;

        if (ptrKinds == 0) {
            return ptrArr0;
        }

        long addr;
        int i = 0;
        while (i < PtrEvent.MAX_PTRS_PER_EVENT) {
            int kind = (ptrKinds >> ((PtrEvent.MAX_PTRS_PER_EVENT - i - 1) * 2)) & 0x3;
            if (kind == PtrEvent.ENCODING_RELATIVE_PTR) {
                addr = getNextWord();
                addr = addr + lastRef;
                lastRef = addr;
                addr *= symbols.heapWordSize;
                addr = ref - addr;
                ptrArr12[i] = addr;
            } else if (kind == PtrEvent.ENCODING_ABSOLUTE_PTR) {
                addr = getNextDoubleWord();
                assert addr > 0 : "Negative address while parsing absolute pointer";
                lastRef = (ref - addr) / symbols.heapWordSize;
                ptrArr12[i] = addr;
            } else if (kind == PtrEvent.ENCODING_NULL_PTR) {
                ptrArr12[i] = NULL_PTR;
            } else if (kind == PtrEvent.ENCODING_END) {
                switch (i) {
                    case 0:
                        return ptrArr0;
                    case 1:
                        System.arraycopy(ptrArr12, 0, ptrArr1, 0, 1);
                        return ptrArr1;
                    case 2:
                        System.arraycopy(ptrArr12, 0, ptrArr2, 0, 2);
                        return ptrArr2;
                    case 3:
                        System.arraycopy(ptrArr12, 0, ptrArr3, 0, 3);
                        return ptrArr3;
                    case 4:
                        System.arraycopy(ptrArr12, 0, ptrArr4, 0, 4);
                        return ptrArr4;
                    case 5:
                        System.arraycopy(ptrArr12, 0, ptrArr5, 0, 5);
                        return ptrArr5;
                    case 6:
                        System.arraycopy(ptrArr12, 0, ptrArr6, 0, 6);
                        return ptrArr6;
                    case 7:
                        System.arraycopy(ptrArr12, 0, ptrArr7, 0, 7);
                        return ptrArr7;
                    case 8:
                        System.arraycopy(ptrArr12, 0, ptrArr8, 0, 8);
                        return ptrArr8;
                    case 9:
                        System.arraycopy(ptrArr12, 0, ptrArr9, 0, 9);
                        return ptrArr9;
                    case 10:
                        System.arraycopy(ptrArr12, 0, ptrArr10, 0, 10);
                        return ptrArr10;
                    case 11:
                        System.arraycopy(ptrArr12, 0, ptrArr11, 0, 11);
                        return ptrArr11;
                }
            } else {
                assert false : "Unknown pointer encoding type";
            }
            i++;
        }
        return ptrArr12;
    }

    protected int recoverValue(long word, int from, int to) {
        int start = HEX_WORD >>> (8 * from);
        int value = start;

        for (int i = from; i < to; i++) {
            value = value | (value >> 8);
        }

        value = (int) ((word & value) >> (8 * (3 - to)));
        return value;
    }

    private final long NEXT_DOUBLE_WORD_MASK = (1L << 32) - 1;

    protected long getNextDoubleWord() throws TraceException {
        long low = (getNextWord()) & NEXT_DOUBLE_WORD_MASK;
        long high = (getNextWord()) & NEXT_DOUBLE_WORD_MASK;
        return low | (high << 32);
    }

    protected int getNextWord() throws TraceException {
        if (buffer == null || buffer.position() == buffer.capacity()) {
            throw new TraceException("Buffer is null or reached limit");
        }
        return buffer.getInt();
    }

    protected String getString(int length) throws TraceException {
        StringBuilder ret = new StringBuilder();
        end:
        for (int j = 0; j < length; j += 4) {
            int word1 = getNextWord();
            for (int k = 0; k < 4; k++) {
                char c = (char) ((word1 >> ((3 - k) * 8)) & 0xFF);
                if (c == '\0') {
                    break end;
                }
                ret.append(c);
            }
        }

        return ret.toString();
    }
}
