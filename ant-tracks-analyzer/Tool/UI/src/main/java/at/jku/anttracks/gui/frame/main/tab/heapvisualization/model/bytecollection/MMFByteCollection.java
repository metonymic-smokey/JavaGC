
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.bytecollection;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;

/**
 * @author Christina Rammerstorfer
 */
class MMFByteCollection extends ByteCollection {

    private static final int BUFFER_LENGTH = Integer.MAX_VALUE;

    protected MMFByteCollection() throws IOException {
        super();
        data = createNewBuffer();
    }

    private MappedByteBuffer createNewBuffer() throws IOException {
        File f = Files.createTempFile("anttracksvisualization", ".tmp").toFile();
        f.deleteOnExit();
        RandomAccessFile file = new RandomAccessFile(f, "rw");
        MappedByteBuffer buffer = file.getChannel().map(FileChannel.MapMode.READ_WRITE, 0, BUFFER_LENGTH);
        file.close();
        return buffer;
    }

    @Override
    protected void write(byte value) {
        try {
            switch (curArrayLevel) {
                case 1:
                    if (writeIndices[0] >= BUFFER_LENGTH) {
                        curArrayLevel++;
                        MappedByteBuffer[] arr = new MappedByteBuffer[POINTER_ARRAY_LENGTH];
                        arr[0] = (MappedByteBuffer) data;
                        arr[1] = createNewBuffer();
                        arr[1].put(value);
                        data = arr;
                        writeIndices[0] = 1;
                        writeIndices[1] = 0;
                    } else {
                        ((MappedByteBuffer) data).put(value);
                    }
                    break;
                case 2:
                    if (writeIndices[0] >= POINTER_ARRAY_LENGTH) {
                        curArrayLevel++;
                        MappedByteBuffer[][] arr = new MappedByteBuffer[POINTER_ARRAY_LENGTH][];
                        arr[0] = (MappedByteBuffer[]) data;
                        arr[1] = new MappedByteBuffer[POINTER_ARRAY_LENGTH];
                        arr[1][0] = createNewBuffer();
                        arr[1][0].put(value);
                        data = arr;
                        writeIndices[0] = 1;
                        writeIndices[1] = 0;
                        writeIndices[2] = 0;
                    } else {
                        if (((MappedByteBuffer[]) data)[writeIndices[0]] == null) {
                            ((MappedByteBuffer[]) data)[writeIndices[0]] = createNewBuffer();
                        }
                        ((MappedByteBuffer[]) data)[writeIndices[0]].put(value);
                    }
                    break;
                case 3:
                    if (writeIndices[0] >= POINTER_ARRAY_LENGTH) {
                        curArrayLevel++;
                        MappedByteBuffer[][][] arr = new MappedByteBuffer[POINTER_ARRAY_LENGTH][][];
                        arr[0] = (MappedByteBuffer[][]) data;
                        arr[1] = new MappedByteBuffer[POINTER_ARRAY_LENGTH][];
                        arr[1][0] = new MappedByteBuffer[POINTER_ARRAY_LENGTH];
                        arr[1][0][0] = createNewBuffer();
                        arr[1][0][0].put(value);
                        data = arr;
                        writeIndices[0] = 1;
                        writeIndices[1] = 0;
                        writeIndices[2] = 0;
                        writeIndices[3] = 0;
                    } else {
                        if (((MappedByteBuffer[][]) data)[writeIndices[0]] == null) {
                            ((MappedByteBuffer[][]) data)[writeIndices[0]] = new MappedByteBuffer[POINTER_ARRAY_LENGTH];
                            ((MappedByteBuffer[][]) data)[writeIndices[0]][0] = createNewBuffer();
                        } else if (((MappedByteBuffer[][]) data)[writeIndices[0]][writeIndices[1]] == null) {
                            ((MappedByteBuffer[][]) data)[writeIndices[0]][writeIndices[1]] = createNewBuffer();
                        }
                        ((MappedByteBuffer[][]) data)[writeIndices[0]][writeIndices[1]].put(value);
                    }
                    break;
                case 4:
                    if ((((MappedByteBuffer[][][]) data)[writeIndices[0]] == null)) {
                        ((MappedByteBuffer[][][]) data)[writeIndices[0]] = new MappedByteBuffer[POINTER_ARRAY_LENGTH][];
                        ((MappedByteBuffer[][][]) data)[writeIndices[0]][0] = new MappedByteBuffer[POINTER_ARRAY_LENGTH];
                        ((MappedByteBuffer[][][]) data)[writeIndices[0]][0][0] = createNewBuffer();
                    } else if ((((MappedByteBuffer[][][]) data)[writeIndices[0]][writeIndices[1]] == null)) {
                        ((MappedByteBuffer[][][]) data)[writeIndices[0]][writeIndices[1]] = new MappedByteBuffer[POINTER_ARRAY_LENGTH];
                        ((MappedByteBuffer[][][]) data)[writeIndices[0]][writeIndices[1]][0] = createNewBuffer();
                    } else if ((((MappedByteBuffer[][][]) data)[writeIndices[0]][writeIndices[1]][writeIndices[2]] == null)) {
                        ((MappedByteBuffer[][][]) data)[writeIndices[0]][writeIndices[1]][writeIndices[2]] = createNewBuffer();
                    }
                    ((MappedByteBuffer[][][]) data)[writeIndices[0]][writeIndices[1]][writeIndices[2]].put(value);
                    break;
                default:
                    throw new RuntimeException("Illegal current array level, aborting write.");
            }
            increaseIndices(BUFFER_LENGTH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    protected byte read(long index) {
        switch (curArrayLevel) {
            case 1:
                return ((MappedByteBuffer) data).get((int) index);
            case 2:
                int[] indices = getIndicesForPosition(index, curArrayLevel, BUFFER_LENGTH, POINTER_ARRAY_LENGTH);
                return ((MappedByteBuffer[]) data)[indices[0]].get(indices[1]);
            case 3:
                indices = getIndicesForPosition(index, curArrayLevel, BUFFER_LENGTH, POINTER_ARRAY_LENGTH);
                return ((MappedByteBuffer[][]) data)[indices[0]][indices[1]].get(indices[2]);
            case 4:
                indices = getIndicesForPosition(index, curArrayLevel, BUFFER_LENGTH, POINTER_ARRAY_LENGTH);
                return ((MappedByteBuffer[][][]) data)[indices[0]][indices[1]][indices[2]].get(indices[3]);
            default:
                throw new RuntimeException("Illegal current array level, aborting read.");
        }

    }
}
