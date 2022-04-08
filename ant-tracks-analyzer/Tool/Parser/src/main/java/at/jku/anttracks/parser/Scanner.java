
package at.jku.anttracks.parser;

import at.jku.anttracks.util.TraceException;

import java.io.Closeable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public abstract class Scanner implements Closeable {
    private final static char[] signatures = {'Z', 'B', 'C', 'S', 'I', 'J', 'F', 'D', 'L', 'V', '['};
    public final static int META_BUFFER_INFO = 12;

    private final long globalOffset;

    protected Scanner(long globalOffset) {
        this.globalOffset = globalOffset;
    }

    public long getGlobalPosition() {
        return globalOffset + getPosition();
    }

    public abstract long getPosition();

    public abstract void skip(int length) throws IOException;

    public abstract boolean isAvailable(int length);

    public abstract long getLong() throws IOException;

    public abstract int getInt() throws IOException;

    public abstract short getShort() throws IOException;

    public abstract byte getByte() throws IOException;

    public abstract byte[] get(int length) throws IOException;

    public int getWord() throws IOException {
        return getInt();
    }

    public char getChar() throws IOException {
        return (char) getByte();
    }

    /*
     * Methods used by TraceFile and SymbolFile
     */

    public int[] getHeader() throws IOException {
        int magic = getInt();
        if (magic != 0xC0FFEE) {
            throw new IOException("Cannot find magic sequence!");
        }
        int[] header = new int[getInt()];
        for (int i = 0; i < header.length; i++) {
            header[i] = getInt();
        }
        return header;
    }

    /*
     * Methods used by TraceFile only
     */

    public String getThread(int heapWordSize) throws IOException {
        assert heapWordSize == 8 : "Adjust buffer (heapWordSize != 8)";

        if (isAvailable(8)) {
            return "0x" + Long.toHexString(getLong());
        } else {
            return null;
        }
    }

    public ByteBuffer getBuffer(int length) throws IOException, TraceException {
        byte[] arr = get(length);
        assert arr.length == length;
        ByteBuffer buffer = ByteBuffer.wrap(arr);
        assert buffer.limit() == arr.length;
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        return buffer;
    }

    /*
     * Methods used by SymbolFile Only
     */

    public int getMagicByte() throws IOException {
        if (isAvailable(1)) {
            return getByte();
        } else {
            return 0;
        }
    }

    public String getNullTerminatedString(boolean removeSeperator) throws IOException, TraceException {
        ArrayList<Byte> signature = new ArrayList<Byte>();
        byte currentByte = getByte();

        addNextByteToSignature(' ', signature, currentByte);
        if (removeSeperator) {
            signature.remove(signature.size() - 1);
        }
        return byteListToString(signature);
    }

    public String getString(char separator) throws IOException, TraceException {
        ArrayList<Byte> signature = new ArrayList<Byte>();
        byte currentByte = getByte();

        if (separator == ')') {
            addNextByteToSignature(separator, signature, currentByte);
            currentByte = getByte();
        }

        reconstructType(signature, currentByte);
        return byteListToString(signature);
    }

    public String getStringZeroTerminated() throws IOException {
        StringBuilder string = new StringBuilder();
        for (char c = getChar(); c != '\0'; c = getChar()) {
            string.append(c);
        }
        return string.toString();
    }

    private boolean signaturesContain(byte currentByte) {
        for (int i = 0; i < signatures.length; i++) {
            if (currentByte == signatures[i]) {
                return true;
            }
        }
        return false;
    }

    private void reconstructType(ArrayList<Byte> signature, byte currentByte) throws IOException, TraceException {
        if (signaturesContain(currentByte)) {
            if (currentByte == '[') {
                signature.add(currentByte); // add [
                currentByte = getByte();
                reconstructType(signature, currentByte);
            } else if (currentByte == 'L') {
                addNextByteToSignature(';', signature, currentByte);
            } else {
                signature.add(currentByte); // add elementary type
            }
        } else {
            throw new TraceException("Invalid symbol file.");
        }

    }

    private void addNextByteToSignature(char separator, ArrayList<Byte> word, byte currentByte) throws IOException {
        do {
            word.add(currentByte);
            currentByte = getByte();
        } while (currentByte != separator);

        word.add(currentByte); // add separator
    }

    public String byteListToString(List<Byte> list) {
        if (list == null) {
            return null;
        }
        byte[] array = new byte[list.size()];
        int i = 0;
        for (Byte current : list) {
            array[i] = current;
            i++;
        }
        return new String(array);
    }

}
