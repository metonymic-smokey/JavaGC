
package at.jku.anttracks.heap.io;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.GarbageCollectionCause;
import at.jku.anttracks.heap.GarbageCollectionLookup;
import at.jku.anttracks.heap.GarbageCollectionType;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.parser.ParsingInfo;
import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.util.Consts;

import java.io.*;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HeapIndexReader implements AutoCloseable {
    private final DataInputStream in;
    private final String path;
    private final Symbols symbols;
    private final Logger logger = Logger.getLogger(this.getClass().getSimpleName());

    public HeapIndexReader(String heapReaderPath, Symbols symbols) throws IOException {
        this.symbols = symbols;
        path = heapReaderPath;
        in = new DataInputStream(new BufferedInputStream(BaseFile.openR(heapReaderPath + File.separator + Consts.HEAP_INDEX_META_FILE)));
    }

    private ArrayList<HeapIndexEntry> read() throws IOException {
        ArrayList<HeapIndexEntry> heapIndex = new ArrayList<>();
        int magic = in.readInt();
        assert (magic == Consts.HEAP_FILES_MAGIC_PREFIX) : "Index file must start with correct magic number";

        try {
            while (true) {
                long position = in.readLong();
                GarbageCollectionType gcType = GarbageCollectionType.Companion.parse(in.readInt());
                EventType gcMeta = EventType.Companion.parse(in.readInt());
                GarbageCollectionCause gcCause = symbols.causes.get(in.readInt());
                long time = in.readLong();
                int gcCount = in.readInt();
                boolean fileExists = in.readBoolean();
                heapIndex.add(new HeapIndexEntry(position, gcType, gcMeta, gcCause, time, gcCount, fileExists));
            }
        } catch (EOFException e) {
            in.close();
        }

        return heapIndex;
    }

    public HeapPosition getRangeFromLastHeapDumpToGivenTime(long time) throws IOException {
        ArrayList<HeapIndexEntry> heapIndex = read();
        boolean found = false;
        HeapIndexEntry index = null;
        long toPosition = 0;
        int i = 0;
        int size = heapIndex.size();

        while (!found && i < size) {
            index = heapIndex.get(i);
            if (index.time == time) {
                found = true;
                toPosition = index.position;
            }

            i++;
        }

        HeapPosition position = new HeapPosition(-1, -1, toPosition);

        if (!found) {
            logger.log(Level.WARNING, "Tried to obtain an entry from the GC index file which does not exist!");
        } else {
            found = false;
            do {
                i--;
                index = heapIndex.get(i);
                if (index.heapDumpFileExists) {
                    found = true;
                }
            } while (!found && i > 0);

            if (found) {
                // fromPosition = latests GC dump
                // toPosition = how far to parse (either _before_ GCstart or _after_ GCend)
                // For more information see call hierarchy of HeapIndexWriter#write()
                position = new HeapPosition(index.fileName, index.position, toPosition);
            }
        }

        return position;
    }

    public DetailedHeap getHeap(int fileName, Symbols symbols, ParsingInfo parsingInfo) {
        DetailedHeap heap = null;
        try (HeapReader heapReader = new HeapReader(path, fileName, symbols)) {
            heap = heapReader.read(parsingInfo);
        } catch (IOException e) {
            e.printStackTrace();
            return heap;
        }
        return heap;
    }

    @Override
    public void close() throws IOException {
        in.close();
    }

    public long getGCTime(GarbageCollectionLookup gc) throws IOException {
        ArrayList<HeapIndexEntry> heapIndex = read();
        int matchedId = 0; // Is only incremented if matching GC type is found
        for (HeapIndexEntry entry : heapIndex) {
            if (!entry.matches(gc)) {
                continue;
            }
            if (matchedId != gc.nth) {
                matchedId++;
                continue;
            }
            return entry.time;
        }
        return -1;
    }
}
