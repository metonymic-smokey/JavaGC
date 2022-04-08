
package at.jku.anttracks.heap.io;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.labs.Lab;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.heap.symbols.AllocationSites;
import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.parser.ParserGCInfo;
import at.jku.anttracks.parser.ParsingInfo;
import at.jku.anttracks.parser.heap.ThreadInfo;
import at.jku.anttracks.parser.io.BaseFile;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import static at.jku.anttracks.heap.io.HeapReader.VERSION;
import static at.jku.anttracks.util.Consts.HEAP_FILES_MAGIC_PREFIX;

public class HeapWriter implements AutoCloseable {
    private final DataOutputStream out;

    public HeapWriter(String path) throws IOException {
        out = new DataOutputStream(new BufferedOutputStream(BaseFile.openW(path)));
    }

    public HeapWriter(String path, int fileName) throws IOException {
        out = new DataOutputStream(new BufferedOutputStream(BaseFile.openW(path + File.separator + fileName)));
    }

    public void write(DetailedHeap heap, ExecutorService executor) throws IOException {
        if (heap == null) {
            throw new NullPointerException();
        }
        if (heap.getGC().getEventType() != EventType.GC_END) {
            throw new IllegalArgumentException("Cannot write heap when a GC is in progress");
        }
        out.writeInt(HEAP_FILES_MAGIC_PREFIX);
        out.writeInt(VERSION);

        writeGCInfo(out, heap.getGC());

        // writeParsingInfo(out, heap.getParsingInfo());

        Map<ObjectInfo, Integer> prototypes = writeObjectInfoCache(out, heap);

        boolean pointers = heap.getSymbols().expectPointers;

        Space[] spaces = heap.getSpacesCloned();
        out.writeInt(spaces.length);
        for (Space space : spaces) {
            writeSpace(heap, space, prototypes, pointers, executor);
        }

        if (pointers) {
            List<RootPtr> roots = heap.rootPtrs.values().stream().flatMap(x -> x.stream()).collect(Collectors.toList());
            out.writeInt(roots.size());
            for (RootPtr root : roots) {
                byte[] metadata = root.getMetadata();
                out.write(metadata);
            }
        }

        Collection<ThreadInfo> threads = heap.threadsById.values();
        out.writeInt(threads.size());
        for (ThreadInfo t : threads) {
            out.writeLong(t.threadId);
            out.writeUTF(t.threadName);
            out.writeUTF(t.internalThreadName);
            out.writeBoolean(t.isAlive());

            // write callstack
            out.writeInt(t.getStackDepth());
            out.write(t.getCallstackMetadata());
        }
    }

    private void writeParsingInfo(DataOutputStream out, ParsingInfo parsingInfo) throws IOException {
        out.writeLong(parsingInfo.getFromTime());
        out.writeLong(parsingInfo.getToTime());
        out.writeLong(parsingInfo.getFromByte());
        out.writeLong(parsingInfo.getToByte());
        out.writeLong(parsingInfo.getTraceLength());
    }

    private void writeGCInfo(DataOutputStream out, ParserGCInfo gc) throws IOException {
        out.writeInt(gc.getEventType().getId());
        out.writeInt(gc.getType().getId());
        out.writeInt(gc.getCause().getId());
        out.writeShort(gc.getId());
        out.writeLong(gc.getTime());
        out.writeBoolean(gc.getConcurrent());
    }

    private Map<ObjectInfo, Integer> writeObjectInfoCache(DataOutputStream out, DetailedHeap heap) throws IOException {
        Map<ObjectInfo, Integer> map = new HashMap<>();
        int size = heap.getCache().getSize();
        out.writeInt(size);

        int id = 0;
        for (ObjectInfo objInfo : heap.getCache().getInfos()) {
            map.put(objInfo, id);
            out.writeInt(id++);
            writeObjectInfo(objInfo, heap.getSymbols().sites);
        }

        return map;
    }

    private void writeObjectInfo(ObjectInfo prototype, AllocationSites sites) throws IOException {
        out.writeUTF(prototype.thread);
        out.writeInt(prototype.type.id);
        out.writeInt(sites.getById(prototype.allocationSite.getId()).getOriginalID());
        out.writeInt(prototype.eventType.getId());
        if (prototype.isMirror) {
            out.writeInt(0);
            out.writeInt(prototype.size);
        } else if (!prototype.isArray) {
            out.writeInt(1);
        } else {
            out.writeInt(2);
            out.writeInt(prototype.arrayLength);
        }
    }

    private void writeSpace(DetailedHeap heap, Space space, Map<ObjectInfo, Integer> prototypes, boolean pointers, ExecutorService executor) throws IOException {

        if (space == null) {
            out.writeByte(0);
            return;
        }

        out.writeByte(1);
        out.writeUTF(space.getName());
        out.writeLong(space.getAddress());
        out.writeLong(space.getLength());
        out.writeInt(space.getType() != null ? space.getType().ordinal() : -1);
        out.writeInt(space.getMode() != null ? space.getMode().ordinal() : -1);
        Lab[] labs = space.getLabs();
        out.writeInt(labs.length);

        for (Lab lab : labs) {
            writeLab(heap, space, lab, prototypes, pointers);
        }
    }

    @SuppressWarnings("serial")
    private static class IORuntimeException extends RuntimeException {
        public IORuntimeException(IOException e) {
            super(e);
        }
    }

    private void writeLab(DetailedHeap heap, Space space, Lab lab, final Map<ObjectInfo, Integer> prototypes, boolean pointers) throws IOException {
        out.writeUTF(lab.thread);
        out.writeInt(lab.kind.id);
        out.writeLong(lab.addr);
        out.writeInt(lab.capacity());
        out.writeInt(lab.getObjectCount());
        try {
            lab.iterate(heap,
                        space.getInfo(),
                        null,
                        (address, object, spaze, rootPtrs) -> {
                            try {
                                Integer prototypeNr = prototypes.get(object.getInfo());
                                out.writeInt(prototypeNr);
                                int ptrCount = Math.max(object.getPointerCount(), 0);
                                out.writeInt(ptrCount);
                                for (int ptrNr = 0; ptrNr < ptrCount; ptrNr++) {
                                    out.writeLong(object.getPointer(ptrNr));
                                }
                            } catch (IOException e) {
                                throw new IORuntimeException(e);
                            }
                        },
                        new ObjectVisitor.Settings(false));
        } catch (IORuntimeException iore) {
            throw new IOException(iore);
        }
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

}
