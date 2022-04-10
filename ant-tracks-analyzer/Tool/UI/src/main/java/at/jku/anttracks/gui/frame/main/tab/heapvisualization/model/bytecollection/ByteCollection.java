
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.bytecollection;

import java.io.IOException;
import java.util.logging.Logger;

/**
 * @author Christina Rammerstorfer
 */
public abstract class ByteCollection {
    private static final Logger LOG = Logger.getLogger(ByteCollection.class.getSimpleName());
    public static final int POINTER_ARRAY_LENGTH = 65536;

    protected enum Type {
        BYTE(1),
        SHORT(2),
        INT(4),
        LONG(8);

        public int sizeInBytes;

        Type(int size) {
            sizeInBytes = size;
        }
    }

    protected long curIndex;
    private Type currentType;
    private long shortStartIndex;
    private long intStartIndex;
    private long longStartIndex;
    private long values;
    protected Object data;
    protected int curArrayLevel;
    protected final int[] writeIndices;

    public ByteCollection() {
        curIndex = 0;
        curArrayLevel = 1;
        currentType = Type.BYTE;
        shortStartIndex = -1;
        intStartIndex = -1;
        longStartIndex = -1;
        values = 0;
        writeIndices = new int[4];
    }

    @SuppressWarnings("fallthrough")
    public void writeLong(long value) {
        switch (currentType) {
            case BYTE:
                shortStartIndex = curIndex;
            case SHORT:
                intStartIndex = curIndex;
            case INT:
                longStartIndex = curIndex;
                currentType = Type.LONG;
                break;
            default:
                break;
        }
        write((byte) (value >>> (7 * 8)));
        write((byte) (value >>> (6 * 8)));
        write((byte) (value >>> (5 * 8)));
        write((byte) (value >>> (4 * 8)));
        write((byte) (value >>> (3 * 8)));
        write((byte) (value >>> (2 * 8)));
        write((byte) (value >>> (1 * 8)));
        write((byte) (value));
        values++;
    }

    @SuppressWarnings("fallthrough")
    public void writeInt(int value) {
        switch (currentType) {
            case BYTE:
                shortStartIndex = curIndex;
            case SHORT:
                intStartIndex = curIndex;
                currentType = Type.INT;
                break;
            case LONG:
                throw new RuntimeException("Cannot write int after long");
            default:
                break;
        }
        write((byte) (value >>> (3 * 8)));
        write((byte) (value >>> (2 * 8)));
        write((byte) (value >>> (1 * 8)));
        write((byte) (value));
        values++;
    }

    public void writeShort(short value) {
        switch (currentType) {
            case BYTE:
                shortStartIndex = curIndex;
                currentType = Type.SHORT;
                break;
            case INT:
                throw new RuntimeException("Cannot write short after int");
            case LONG:
                throw new RuntimeException("Cannot write short after long");
            default:
                break;
        }
        write((byte) (value >>> (1 * 8)));
        write((byte) (value));
        values++;
    }

    public void writeUnknown(long value) {
        switch (currentType) {
            case LONG:
                writeLong(value);
                break;
            case INT:
                if (value > Integer.MAX_VALUE) {
                    writeLong(value);
                } else {
                    writeInt((int) value);
                }
                break;
            case SHORT:
                if (value > Integer.MAX_VALUE) {
                    writeLong(value);
                } else if (value > Short.MAX_VALUE) {
                    writeInt((int) value);
                } else {
                    writeShort((short) value);
                }
                break;
            case BYTE:
                if (value > Integer.MAX_VALUE) {
                    writeLong(value);
                } else if (value > Short.MAX_VALUE) {
                    writeInt((int) value);
                } else if (value > Byte.MAX_VALUE) {
                    writeShort((short) value);
                } else {
                    write((byte) value);
                    values++;
                }
                break;
        }
    }

    protected abstract void write(byte value);

    public int readInt(long index) {
        if (index >= curIndex) {
            throw new IndexOutOfBoundsException("Index (" + index + ") must not be greater than size (" + curIndex + ")");
        }
        if (index < 0) {
            throw new IndexOutOfBoundsException("Index must not be less than zero");
        }
        if (index < shortStartIndex || shortStartIndex < 0) {//Byte range
            return read(index);
        }
        long trueIndex = shortStartIndex + (index - shortStartIndex) * Type.SHORT.sizeInBytes;
        if (trueIndex < intStartIndex || intStartIndex < 0) {
            return (read(trueIndex) << (1 * 8) & 0x0000FF00) | (read(trueIndex + 1) & 0x000000FF);
        }
        long off = shortStartIndex + (intStartIndex - shortStartIndex) / Type.SHORT.sizeInBytes;
        trueIndex = intStartIndex + (index - off) * Type.INT.sizeInBytes;
        if (trueIndex < longStartIndex || longStartIndex < 0) {
            return read(trueIndex) << (3 * 8) | (read(trueIndex + 1) << (2 * 8) & 0x00FF0000) | (read(trueIndex + 2) << (1 * 8) & 0x0000FF00) | (read(trueIndex + 3) &
                                                                                                                                                         0x000000FF);
        }
        throw new IndexOutOfBoundsException("Index is within long range");
    }

    public long readLong(long index) {
        if (index >= curIndex) {
            throw new IndexOutOfBoundsException("Index must not be greater than size");
        }
        if (index < 0) {
            throw new IndexOutOfBoundsException("Index must not be less than zero");
        }
        if (index < shortStartIndex || shortStartIndex < 0) {//Byte range
            return read(index);
        }
        long trueIndex = shortStartIndex + (index - shortStartIndex) * Type.SHORT.sizeInBytes;
        if (trueIndex < intStartIndex || intStartIndex < 0) {
            return (read(trueIndex) << (1 * 8) & 0x0000FF00) | (read(trueIndex + 1) & 0x000000FF);
        }
        long off = shortStartIndex + (intStartIndex - shortStartIndex) / Type.SHORT.sizeInBytes;
        trueIndex = intStartIndex + (index - off) * Type.INT.sizeInBytes;
        if (trueIndex < longStartIndex || longStartIndex < 0) {
            return read(trueIndex) << (3 * 8) | (read(trueIndex + 1) << (2 * 8) & 0x00FF0000) | (read(trueIndex + 2) << (1 * 8) & 0x0000FF00) | (read(trueIndex + 3) &
                                                                                                                                                         0x000000FF);
        }
        off = off + (longStartIndex - intStartIndex) / Type.INT.sizeInBytes;
        trueIndex = longStartIndex + (index - off) * Type.LONG.sizeInBytes;
        long returnVal = 0x0;
        for (int i = 0; i < 8; i++) {
            returnVal = returnVal << 8;
            long read = read(trueIndex + i);
            read = read & 0x00_00_00_00_00_00_00_FFL;
            returnVal = read | returnVal;
        }
        return returnVal;
    }

    protected abstract byte read(long index);

    public long size() {
        return values;
    }

    /**
     * Searches the ByteCollection for the specified value using the
     * binary search algorithm.
     *
     * @param key the value to be searched for
     * @return index of the search key if it is contained in the array,
     * otherwise <tt>(<i>insertion point</i> - 1)</tt>
     */
    public long binarySearch(long key) {
        long low = 0;
        long high = values - 1;
        while (low <= high) {
            long mid = (low + high) / 2;
            long midVal = readLong(mid);

            if (midVal < key) {
                low = mid + 1;
            } else if (midVal > key) {
                high = mid - 1;
            } else {
                return mid; // key found
            }
        }
        return low - 1;  // key not found.
    }

    public static int[] getIndicesForPosition(long position, int level, int dataArrayLength, int pointerArrayLength) {
        switch (level) {
            case 1:
                return new int[]{(int) position};
            case 2:
                return new int[]{(int) (position / dataArrayLength), (int) (position % dataArrayLength)};
            case 3:
                return new int[]{(int) (position / (dataArrayLength * (long) pointerArrayLength)),
                                 (int) ((position / dataArrayLength) % pointerArrayLength),
                                 (int) (position % dataArrayLength)};
            case 4:
                return new int[]{(int) (position / (dataArrayLength * (long) pointerArrayLength * pointerArrayLength)),
                                 (int) ((position / (dataArrayLength * (long) pointerArrayLength) % pointerArrayLength)),
                                 (int) ((position / dataArrayLength) % pointerArrayLength),
                                 (int) (position % dataArrayLength)};
            default: //This should never happen
                return null;
        }
    }

    public static ByteCollection create(long estimatedNeededMemory) {
        long allocatedMemory = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        long presumableFreeMemory = Runtime.getRuntime().maxMemory() - allocatedMemory;
        StringBuilder b = new StringBuilder("presumable free memory: " + presumableFreeMemory + " bytes, estimated needed memory: " + estimatedNeededMemory + " bytes " +
                                                    "=> ");
        if (presumableFreeMemory > estimatedNeededMemory) {
            b.append("using memory");
            LOG.info(b.toString());
            return new MemoryByteCollection();
        }
        b.append("using memory-mapped file");
        LOG.info(b.toString());
        try {
            MMFByteCollection byteCollection = new MMFByteCollection();
            return byteCollection;
        } catch (IOException e) {
            LOG.warning("Could not create MMFByteCollection, use MemoryByteCollection instead");
            return new MemoryByteCollection();
        }
    }

    protected void increaseIndices(int dataArrayLength) {
        switch (curArrayLevel) {
            case 1:
                writeIndices[0]++;
                break;
            case 2:
                writeIndices[1]++;
                if (writeIndices[1] >= dataArrayLength) {
                    writeIndices[0]++;
                    writeIndices[1] = 0;
                }
                break;
            case 3:
                writeIndices[2]++;
                if (writeIndices[2] >= dataArrayLength) {
                    writeIndices[1]++;
                    writeIndices[2] = 0;
                    if (writeIndices[1] >= POINTER_ARRAY_LENGTH) {
                        writeIndices[0]++;
                        writeIndices[1] = 0;
                    }
                }
                break;
            case 4:
                writeIndices[3]++;
                if (writeIndices[3] >= dataArrayLength) {
                    writeIndices[2]++;
                    writeIndices[3] = 0;
                    if (writeIndices[2] >= POINTER_ARRAY_LENGTH) {
                        writeIndices[1]++;
                        writeIndices[2] = 0;
                        if (writeIndices[1] >= POINTER_ARRAY_LENGTH) {
                            writeIndices[0]++;
                            writeIndices[1] = 0;
                        }
                    }
                }
                break;
            default:
                break;
        }
        curIndex++;
    }

}
