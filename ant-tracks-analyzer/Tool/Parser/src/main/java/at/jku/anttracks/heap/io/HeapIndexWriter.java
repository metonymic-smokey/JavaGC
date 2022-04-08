
package at.jku.anttracks.heap.io;

import at.jku.anttracks.heap.GarbageCollectionCause;
import at.jku.anttracks.heap.GarbageCollectionType;
import at.jku.anttracks.parser.EventType;
import at.jku.anttracks.parser.io.BaseFile;
import at.jku.anttracks.util.Consts;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public class HeapIndexWriter implements AutoCloseable {

    private final DataOutputStream out;

    public HeapIndexWriter(String path) throws IOException {
        out = new DataOutputStream(new BufferedOutputStream(BaseFile.openW(path + File.separator + Consts.HEAP_INDEX_META_FILE)));
        out.writeInt(Consts.HEAP_FILES_MAGIC_PREFIX);
    }

    public void write(long position, GarbageCollectionType gcType, EventType gcMeta, GarbageCollectionCause cause, long time, int gcCount, boolean fileExists)
            throws IOException {
        out.writeLong(position);
        out.writeInt(gcType.getId());
        out.writeInt(gcMeta.getId());
        out.writeInt(cause.getId());
        out.writeLong(time);
        out.writeInt(gcCount);
        out.writeBoolean(fileExists);
        flush();
        // TODO various trace files
    }

    public void flush() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

}
