
package at.jku.anttracks.parser;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class TraceScannerFactory {

    private final TraceFile.Access[] files;
    private final long from, to;
    private int index;

    public TraceScannerFactory(TraceFile.Access[] files) {
        this(files, 0, getLength(files));
    }

    public TraceScannerFactory(TraceFile.Access[] files, long from, long to) {
        this.files = Arrays.copyOf(files, files.length);
        this.from = from;
        this.to = to;
        this.index = 0;

        skip();
    }

    private void skip() {
        long cursor = 0;
        while (index < files.length && cursor + files[index].length() <= from) {
            cursor += files[index].length();
            index++;
        }
    }

    public long getFrom() {
        return from;
    }

    public long getTo() {
        return to;
    }

    public long getLength() {
        return to - from;
    }

    public Scanner getNext() throws IOException {
        Scanner result;
        if (index < files.length) {
            TraceFile.Access file = files[index];
            long cursor = getCursor();
            long from = cursor < this.from ? (this.from - cursor) : 0;
            long to = cursor + file.length() < this.to ? file.length() : (this.to - cursor);
            result = to - from > 0 ? file.open(cursor, from, to) : null;
            index++;
        } else {
            result = null;
        }
        return result;
    }

    private long getCursor() {
        return getLength(files, index);
    }

    private static long getLength(TraceFile.Access[] files) {
        return getLength(files, files.length);
    }

    private static long getLength(TraceFile.Access[] files, int length) {
        return Arrays.asList(files).stream().limit(length).mapToLong(f -> f.length()).sum();
    }

    public static TraceScannerFactory create(File container, int[] header, File example) throws IOException {
        return create(container, header, example, null, null);
    }

    public static TraceScannerFactory create(File container, int[] header, File example, Long from, Long to) throws IOException {
        TraceFile.Access[] files = TraceFile.open(container, header, example);
        return from != null && to != null ? new TraceScannerFactory(files, from, to) : new TraceScannerFactory(files);
    }

}
