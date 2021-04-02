
package at.jku.anttracks.parser;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class Decompressor {

    private static final boolean BITWISE_COMPRESSION = true;
    private static final int INDEX_MAX_WIDTH = 2;
    private static final int INDEX_MASK = ((1 << (INDEX_MAX_WIDTH * 8)) - 1);
    private static final int FIRST_CUSTOM_INDEX = 1 << 8;
    private static final int MAX_INDEX = (((~0) & INDEX_MASK) >> INDEX_MAX_WIDTH);

    private final int limit;

    public Decompressor() {
        this(Integer.MAX_VALUE);
    }

    public Decompressor(int limit) {
        this.limit = limit;
    }

    public ByteBuffer decode(ByteBuffer buffer) throws IOException {
        ByteBufferInputStream compressed = new ByteBufferInputStream(buffer);
        ByteBufferOutputStream uncompressed = new ByteBufferOutputStream();
        decode(compressed, uncompressed);
        return uncompressed.consume();
    }

    private void decode(ByteBufferInputStream src, ByteBufferOutputStream dest) throws IOException {
        final Map<Integer, byte[]> dictionary = new HashMap<>();
        int nextIndex = FIRST_CUSTOM_INDEX;

        IntReader reader = BITWISE_COMPRESSION ? new BitwiseVarIntReader(src) : new VarIntReader(src);
        byte[] last = new byte[0];
        for (int index = reader.next(); index >= 0 && dest.size() < limit; index = reader.next()) {
            byte[] current;
            boolean mergeLastWithCurrent = false;
            if (index < FIRST_CUSTOM_INDEX) {
                current = new byte[]{(byte) (index & 0xFF)};
            } else {
                current = dictionary.get(index);
                if (current != null) {
                    assert current.length >= 2;
                } else {
                    current = new byte[]{last[0]};
                    dest.write(last);
                    mergeLastWithCurrent = true;
                }
            }
            dest.write(current);
            nextIndex = registerIndex(dictionary, last, current, nextIndex);
            if (mergeLastWithCurrent) {
                current = concat(last, current);
            }
            last = current;
        }
        reader.close();
    }

    private int registerIndex(Map<Integer, byte[]> dictionary, byte[] last, byte[] current, int nextIndex) {
        if (last.length != 0 && nextIndex <= MAX_INDEX) {
            byte[] sequence = concat(last, new byte[]{current[0]});
            dictionary.put(nextIndex, sequence);
            return nextIndex + 1;
        } else {
            return nextIndex;
        }
    }

    private static byte[] concat(byte[] a1, byte[] a2) {
        byte[] result = new byte[a1.length + a2.length];
        int i = 0;
        for (int j = 0; j < a1.length; j++) {
            result[i++] = a1[j];
        }
        for (int j = 0; j < a2.length; j++) {
            result[i++] = a2[j];
        }
        return result;
    }

    private static abstract class IntReader implements Closeable {
        public abstract int next() throws IOException;

        public abstract void close() throws IOException;
    }

    private static class VarIntReader extends IntReader {

        private final InputStream in;

        public VarIntReader(InputStream in) {
            this.in = in;
        }

        public int next() throws IOException {
            if (in.available() > 0) {
                int value = 0;
                int length = 0;
                for (; ; ) {
                    int raw = in.read() & 0xFF;
                    value = value | ((raw & ~(1 << 7)) << (7 * length));
                    length++;
                    if ((raw & (1 << 7)) == 0) {
                        break;
                    }
                }
                return value;
            } else {
                return -1;
            }
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    private static class BitwiseVarIntReader extends IntReader {
        private final InputStream in;
        private int width;
        private int in_byte_offset;
        private int b;

        public BitwiseVarIntReader(InputStream in) {
            this.in = in;
            width = 8;
            in_byte_offset = 8;
            b = 0;
        }

        public int next() throws IOException {
            int value = read();
            if (value == (1 << width) - 1) {
                width++;
                value = next();
                if (value == 0) {
                    return -1;
                }
            }
            return value;
        }

        private int read() throws IOException {
            int value = 0;
            int bits = width;
            do {
                if (in_byte_offset == 8) {
                    in_byte_offset = 0;
                    b = in.read() & 0xFF;
                }
                int bits_to_read = Math.min(8 - in_byte_offset, bits);
                int value_part_mask = (1 << bits_to_read) - 1;
                int value_part_in_place = b;
                int value_part = (value_part_in_place >> (8 - in_byte_offset - bits_to_read)) & value_part_mask;
                value = value | (value_part << (bits - bits_to_read));
                bits = bits - bits_to_read;
                in_byte_offset = in_byte_offset + bits_to_read;
            } while (bits > 0);
            return value;
        }

        @Override
        public void close() throws IOException {
            in.close();
        }
    }

    private static class ByteBufferOutputStream extends OutputStream {

        private ByteBuffer buffer;

        public ByteBufferOutputStream() {
            buffer = ByteBuffer.allocate(2);
        }

        public int size() {
            return buffer.position();
        }

        public ByteBuffer consume() {
            buffer.limit(buffer.position());
            buffer.rewind();
            return buffer;
        }

        @Override
        public void write(int b) throws IOException {
            if (buffer.position() == buffer.capacity()) {
                grow();
            }
            buffer.put((byte) b);
        }

        private void grow() {
            buffer.limit(buffer.position());
            buffer.rewind();
            ByteBuffer bufferNew = ByteBuffer.allocate(buffer.capacity() * 2);
            bufferNew.put(buffer);
            buffer = bufferNew;
        }

    }

    private static class ByteBufferInputStream extends InputStream {

        private final ByteBuffer buffer;

        public ByteBufferInputStream(ByteBuffer buffer) {
            this.buffer = buffer;
        }

        @Override
        public int available() {
            return buffer.limit() - buffer.position();
        }

        @Override
        public int read() throws IOException {
            return buffer.get();
        }

    }

}
