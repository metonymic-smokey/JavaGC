
package at.jku.anttracks.parser.io;

import at.jku.anttracks.parser.Scanner;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

public final class InputStreamScanner extends Scanner {

    private final InputStream in;
    private byte[] buffer;
    private final long to;
    private long position;

    public InputStreamScanner(InputStream in) throws IOException {
        this(0, in, 0, Long.MAX_VALUE);
    }

    public InputStreamScanner(long globalOffset, InputStream in, long from, long to) throws IOException {
        super(globalOffset);
        in.skip(from);
        this.in = new BufferedInputStream(in);
        buffer = new byte[8];
        this.to = to;
        position = from;
    }

    @Override
    public void close() throws IOException {
        in.close();
        buffer = null;
    }

    @Override
    public long getPosition() {
        return position;
    }

    @Override
    public void skip(int length) throws IOException {
        if (position + length > to) {
            throw new IOException("Not enough data available");
        }
        long top = 0;
        for (long skipped; top < length && (skipped = in.skip(length - top)) > 0; ) {
            top += skipped;
        }
        if (top != length) {
            throw new IOException("Illegal amount of data read!");
        } else {
            position += top;
        }
    }

    @Override
    public boolean isAvailable(int length) {
        assert in.markSupported();
        try {
            in.mark(length);
            get(length);
            in.reset();
            position -= length;
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    @Override
    public long getLong() throws IOException {
        return longToLittleEndian(get(8, true));
    }

    @Override
    public int getInt() throws IOException {
        return intToLittleEndian(get(4, true));
    }

    @Override
    public short getShort() throws IOException {
        return shortToLittleEndian(get(2, true));
    }

    @Override
    public byte getByte() throws IOException {
        return get(1, true)[0];
    }

    @Override
    public byte[] get(int length) throws IOException {
        return get(length, false);
    }

    private byte[] get(int length, boolean mayShare) throws IOException {
        byte[] buffer = this.buffer;
        if (!mayShare || buffer.length < length) {
            buffer = new byte[length];
        }
        read(buffer, length);
        return buffer;
    }

    private void read(byte[] buffer, int length) throws IOException {
        if (position + length > to) {
            throw new IOException("Not enough data available");
        }
        assert buffer.length >= length;
        int top = 0;
        for (int read = in.read(buffer, 0, length); read > 0 && top < buffer.length; read = in.read(buffer, top, length - top)) {
            top += read;
        }
        if (top != length) {
            throw new IOException("Illegal amount of data read!");
        } else {
            position += top;
        }
    }

    private static short shortToLittleEndian(byte[] src) {
        return (short) toLittleEndian(src, 2);
    }

    private static int intToLittleEndian(byte[] src) {
        return (int) toLittleEndian(src, 4);
    }

    private static long longToLittleEndian(byte[] src) {
        return toLittleEndian(src, 8);
    }

    private static long toLittleEndian(byte[] src, int size) {
        long dest = 0;
        for (int i = 0; i < size; i++) {
            dest <<= 8;
            dest |= src[size - i - 1] & 0xFF;
        }
        return dest;
    }
}
