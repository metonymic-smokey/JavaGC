
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.bytecollection;

/**
 * @author Christina Rammmerstorfer
 */
class MemoryByteCollection extends ByteCollection {
    private static final int ARRAY_LENGTH = POINTER_ARRAY_LENGTH * 8;

    protected MemoryByteCollection() {
        super();
        data = new byte[ARRAY_LENGTH];
    }

    @Override
    protected byte read(long index) {
        switch (curArrayLevel) {
            case 1:
                return ((byte[]) data)[(int) index];
            case 2:
                int[] indices = getIndicesForPosition(index, curArrayLevel, ARRAY_LENGTH, POINTER_ARRAY_LENGTH);
                return ((byte[][]) data)[indices[0]][indices[1]];
            case 3:
                indices = getIndicesForPosition(index, curArrayLevel, ARRAY_LENGTH, POINTER_ARRAY_LENGTH);
                return ((byte[][][]) data)[indices[0]][indices[1]][indices[2]];
            case 4:
                indices = getIndicesForPosition(index, curArrayLevel, ARRAY_LENGTH, POINTER_ARRAY_LENGTH);
                return ((byte[][][][]) data)[indices[0]][indices[1]][indices[2]][indices[3]];
            default:
                throw new RuntimeException("Illegal current array level, aborting read.");
        }
    }

    @Override
    protected void write(byte value) {
        switch (curArrayLevel) {
            case 1:
                if (writeIndices[0] >= ARRAY_LENGTH) {
                    curArrayLevel++;
                    byte[][] arr = new byte[POINTER_ARRAY_LENGTH][];
                    arr[0] = (byte[]) data;
                    arr[1] = new byte[ARRAY_LENGTH];
                    arr[1][0] = value;
                    writeIndices[0] = 1;
                    writeIndices[1] = 0;
                    data = arr;
                } else {
                    ((byte[]) data)[writeIndices[0]] = value;
                }
                break;
            case 2:
                if (writeIndices[0] >= POINTER_ARRAY_LENGTH) {
                    curArrayLevel++;
                    byte[][][] arr = new byte[POINTER_ARRAY_LENGTH][][];
                    arr[0] = (byte[][]) data;
                    arr[1] = new byte[POINTER_ARRAY_LENGTH][];
                    arr[1][0] = new byte[ARRAY_LENGTH];
                    arr[1][0][0] = value;
                    data = arr;
                    writeIndices[0] = 1;
                    writeIndices[1] = 0;
                    writeIndices[2] = 0;
                } else {
                    if (((byte[][]) data)[writeIndices[0]] == null) {
                        ((byte[][]) data)[writeIndices[0]] = new byte[ARRAY_LENGTH];
                    }
                    ((byte[][]) data)[writeIndices[0]][writeIndices[1]] = value;
                }
                break;
            case 3:
                if (writeIndices[0] >= POINTER_ARRAY_LENGTH) {
                    curArrayLevel++;
                    byte[][][][] arr = new byte[POINTER_ARRAY_LENGTH][][][];
                    arr[0] = (byte[][][]) data;
                    arr[1] = new byte[POINTER_ARRAY_LENGTH][][];
                    arr[1][0] = new byte[POINTER_ARRAY_LENGTH][];
                    arr[1][0][0] = new byte[ARRAY_LENGTH];
                    arr[1][0][0][0] = value;
                    data = arr;
                    writeIndices[0] = 1;
                    writeIndices[1] = 0;
                    writeIndices[2] = 0;
                    writeIndices[3] = 0;
                } else {
                    if ((((byte[][][]) data)[writeIndices[0]] == null)) {
                        ((byte[][][]) data)[writeIndices[0]] = new byte[POINTER_ARRAY_LENGTH][];
                        ((byte[][][]) data)[writeIndices[0]][0] = new byte[ARRAY_LENGTH];
                    } else if ((((byte[][][]) data)[writeIndices[0]][writeIndices[1]] == null)) {
                        ((byte[][][]) data)[writeIndices[0]][writeIndices[1]] = new byte[ARRAY_LENGTH];
                    }
                    ((byte[][][]) data)[writeIndices[0]][writeIndices[1]][writeIndices[2]] = value;
                }
                break;
            case 4:
                if ((((byte[][][][]) data)[writeIndices[0]] == null)) {
                    ((byte[][][][]) data)[writeIndices[0]] = new byte[POINTER_ARRAY_LENGTH][][];
                    ((byte[][][][]) data)[writeIndices[0]][0] = new byte[POINTER_ARRAY_LENGTH][];
                    ((byte[][][][]) data)[writeIndices[0]][0][0] = new byte[ARRAY_LENGTH];
                } else if (((byte[][][][]) data)[writeIndices[0]][writeIndices[1]] == null) {
                    ((byte[][][][]) data)[writeIndices[0]][writeIndices[1]] = new byte[POINTER_ARRAY_LENGTH][];
                    ((byte[][][][]) data)[writeIndices[0]][writeIndices[1]][0] = new byte[ARRAY_LENGTH];
                } else if ((((byte[][][][]) data)[writeIndices[0]][writeIndices[1]][writeIndices[2]] == null)) {
                    ((byte[][][][]) data)[writeIndices[0]][writeIndices[1]][writeIndices[2]] = new byte[ARRAY_LENGTH];
                }
                ((byte[][][][]) data)[writeIndices[0]][writeIndices[1]][writeIndices[2]][writeIndices[3]] = value;
                break;
            default:
                throw new RuntimeException("Illegal current array level, aborting write.");
        }
        increaseIndices(ARRAY_LENGTH);
    }
}
