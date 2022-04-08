
package at.jku.anttracks.parser.io;

import at.jku.anttracks.parser.Scanner;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

public final class MappedFileScanner extends Scanner {
    private final static long MEMORY_LOAD = 1024 * 2014;

    private final FileInputStream stream;
    private final FileChannel inChannel;
    private final long totalLoads;
    private final long remainder;
    private final long load;
    private long totalBytesLoaded;
    private long currentLoads;
    private MappedByteBuffer buffer;

    public MappedFileScanner(long globalOffset, FileInputStream stream, long from, long to) throws IOException {
        super(globalOffset);
        this.stream = stream;
        stream.skip(from);
        inChannel = stream.getChannel();
        long fileLength = to - from;
        if (fileLength < MEMORY_LOAD) {
            totalLoads = 1;
            remainder = 0;
            load = fileLength;
        } else {
            totalLoads = fileLength / MEMORY_LOAD;
            remainder = fileLength % MEMORY_LOAD;
            load = MEMORY_LOAD;
        }
        totalBytesLoaded = from;
        currentLoads = 0;
        buffer = null;
    }

    private void ensureMappedBufferLoaded(int length) throws IOException {
        ensureMappedBufferLoaded(length, false);
    }

    private boolean ensureMappedBufferLoaded(int length, boolean allowEOF) throws IOException {
        assert length <= MEMORY_LOAD;
        boolean loaded;
        if (buffer == null || buffer.position() == buffer.limit()) {
            loaded = loadMappedBuffer();
        } else if (exceedsMappedBufferLimit(length)) {
            loaded = reloadMappedBuffer();
        } else {
            loaded = true;
        }
        if (!loaded && !allowEOF) {
            throw new IOException("Could not load new data!");
        }
        assert !loaded || !exceedsMappedBufferLimit(length);
        return loaded;
    }

    private boolean loadMappedBuffer() throws IOException {
        return loadMappedBuffer(0);
    }

    private boolean loadMappedBuffer(long fixedLoad) throws IOException {
        if (currentLoads < totalLoads && load > 0) {
            mapInMemory(load + fixedLoad);
            currentLoads++;
        } else if (currentLoads == totalLoads && remainder > 0) {
            mapInMemory(remainder + fixedLoad);
            currentLoads++;
        } else {
            return false;
        }

        return true;
    }

    private boolean reloadMappedBuffer() throws IOException {
        long missedReading = buffer.limit() - buffer.position();
        long fixedLoad = missedReading;
        totalBytesLoaded -= missedReading;

        return loadMappedBuffer(fixedLoad);
    }

    private void mapInMemory(long length) throws IOException {
        buffer = inChannel.map(FileChannel.MapMode.READ_ONLY, totalBytesLoaded, length);
        buffer.load();
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        totalBytesLoaded += length;
    }

    private boolean exceedsMappedBufferLimit(int length) {
        return buffer.position() + length > buffer.limit();
    }

    @Override
    public void close() throws IOException {
        inChannel.close();
        stream.close();
    }

    @Override
    public long getPosition() {
        return totalBytesLoaded - (buffer != null ? (buffer.limit() - buffer.position()) : 0);
    }

    @Override
    public void skip(int length) throws IOException {
        if (length > MEMORY_LOAD) {
            throw new IOException("MEMORY_LOAD smaller than skip length");
        }
        ensureMappedBufferLoaded(length);
        assert buffer.position() + length <= buffer.limit();
        buffer.position(buffer.position() + length);
    }

    @Override
    public boolean isAvailable(int length) {
        try {
            return ensureMappedBufferLoaded(length, true);
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public byte getByte() throws IOException {
        ensureMappedBufferLoaded(1);
        return buffer.get();
    }

    @Override
    public short getShort() throws IOException {
        ensureMappedBufferLoaded(2);
        return buffer.getShort();
    }

    @Override
    public int getInt() throws IOException {
        ensureMappedBufferLoaded(4);
        return buffer.getInt();
    }

    @Override
    public long getLong() throws IOException {
        ensureMappedBufferLoaded(8);
        return buffer.getLong();
    }

    @Override
    public byte[] get(int length) throws IOException {
        if (length > MEMORY_LOAD) {
            throw new IOException("MEMORY_LOAD smaller than buffer length");
        }
        ensureMappedBufferLoaded(length);
        byte[] result = new byte[length];
        buffer.get(result);
        return result;
    }
}
