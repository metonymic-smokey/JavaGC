package at.jku.anttracks.heap;

import at.jku.anttracks.heap.labs.IndexHeapObject;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.heap.space.SpaceInfo;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;

public class MemoryMappedFastHeap extends IndexBasedHeap {
    //================================================================================
    // fields
    //================================================================================

    //constant fields
    public static final String TMP_DIR = "mem_tmp";
    public static final String ADDRESSES_PREFIX = "addr";
    public static final String FROM_POINTERS_OFF_PREFIX = "fp_off";
    public static final String FROM_POINTERS_PREFIX = "fp";
    public static final String TO_POINTERS_OFF_PREFIX = "tp_off";
    public static final String TO_POINTERS_PREFIX = "tp";
    public static final String OBJECT_INFO_ID_PREFIX = "oid";
    public static final String FILE_EXTENSION = ".dat";

    public static final int EOF = 42;

    // instance fields
    private Path[] dataFiles;
    private MappedByteBuffer addresses;
    private MappedByteBuffer fromPointersOffsets;
    private MappedByteBuffer fromPointers;
    private MappedByteBuffer toPointersOffsets;
    private MappedByteBuffer toPointers;
    private MappedByteBuffer objectInfos;

    SpaceInfo[] spaceInfos;
    long[] spaceStartAddresses;
    // short[] born;

    public MemoryMappedFastHeap(DetailedHeap heap) {
        super(heap);
        System.out.println("IM IN MEMORY MAPPED FAST HEAP HAHAHAHAHH");
    }

    @Override
    protected void store(SpaceInfo[] spaceInfos,
                         long[] spaceStartAddresses,
                         IndexHeapObject[] objectsArray) {
        this.spaceInfos = spaceInfos;
        this.spaceStartAddresses = spaceStartAddresses;

        dataFiles = new Path[6];
        int nrOfFromPointers = calcNrOfFromPointers(objectsArray);
        int nrOfToPointers = calcNrOfToPointers(objectsArray);
        initMappedByteBuffers(nrOfFromPointers, nrOfToPointers);

        // write adresses
        for (int i = 0; i < objectsArray.length; i++) {
            this.addresses.putLong(objectsArray[i].getAddress());
        }

        // TODO: Write born array

        //write object info IDs
        for (int i = 0; i < objectsArray.length; i++) {
            this.objectInfos.putInt(objectsArray[i].getInfo().id);
        }

        // write from-pointers
        writeOffsetAndFromPointerFile(this.fromPointersOffsets, this.fromPointers, objectsArray);

        // write to-pointers
        writeOffsetAndToPointerFile(this.toPointersOffsets, this.toPointers, objectsArray);

    }

    //================================================================================
    // getters
    //================================================================================

    public long getAddress(int objIndex) {
        if (!valid(objIndex)) {
            return NULL_INDEX;
        }
        return (int) addresses.getLong(objIndex * Long.BYTES);
    }

    public int[] getToPointers(int objIndex) {
        if (!valid(objIndex)) {
            return null;
        }
        return getPointers(objIndex, toPointersOffsets, toPointers);
    }

    public int[] getFromPointers(int objIndex) {
        if (!valid(objIndex)) {
            return null;
        }
        return getPointers(objIndex, fromPointersOffsets, fromPointers);
    }

    @Override
    public ObjectInfo getObjectInfo(int objIndex) {
        if (!valid(objIndex)) {
            return null;
        }
        int id = objectInfos.getInt(objIndex * Integer.BYTES);
        return objectInfoCache.get(id);
    }

    @Override
    public int toIndex(long address) {
        // TODO implement binary search for memory-mapped files
        for (int i = 0; i < objectCount; i++) {
            if (addresses.getLong(i * Long.BYTES) == address) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public short getBorn(int objIndex) {
        // TODO: Read born once we have a file for them
        // return born[objIndex];
        return -1;
    }

    //================================================================================
    // File Management
    //================================================================================

    // public
    @Override
    public void clear() {
        for (Path path : dataFiles) {
            try {
                Files.delete(path);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    // private
    private void writeOffsetAndFromPointerFile(MappedByteBuffer offsetFile, MappedByteBuffer ptFile, IndexHeapObject[] objectsArray) {
        int counter = 0;
        for (int i = 0; i < objectsArray.length; i++) {
            IndexHeapObject heapObject = objectsArray[i];

            //write offsets
            if (i >= 1) {
                offsetFile.putInt(offsetFile.getInt((i - 1) * Integer.BYTES) + counter);
            } else {
                offsetFile.putInt(counter);
            }
            counter = 0;

            //write referenced objects
            for (long j = offsetFile.getInt(i * Integer.BYTES); j < heapObject.pointedFromIndices.length + offsetFile.getInt(i * Integer.BYTES); j++) {
                ptFile.putInt(heapObject.pointedFromIndices[counter]);
                counter++;
            }
        }
        offsetFile.putInt(offsetFile.getInt((objectsArray.length - 1) * Integer.BYTES));

        ptFile.putInt(EOF);

        // write buffer contents to file
        offsetFile.force();
        ptFile.force();
        offsetFile.rewind();
        ptFile.rewind();
    }

    private void writeOffsetAndToPointerFile(MappedByteBuffer offsetFile, MappedByteBuffer ptFile, IndexHeapObject[] objectsArray) {
        int counter = 0;
        for (int i = 0; i < objectsArray.length; i++) {
            IndexHeapObject heapObject = objectsArray[i];

            //write offsets
            if (i >= 1) {
                offsetFile.putInt(offsetFile.getInt((i - 1) * Integer.BYTES) + counter);
            } else {
                offsetFile.putInt(counter);
            }
            counter = 0;

            //write referenced objects
            for (long j = offsetFile.getInt(i * Integer.BYTES); j < heapObject.pointsToIndices.length + offsetFile.getInt(i * Integer.BYTES); j++) {
                ptFile.putInt(heapObject.pointsToIndices[counter]);
                counter++;
            }
        }
        offsetFile.putInt(offsetFile.getInt((objectsArray.length - 1) * Integer.BYTES));

        ptFile.putInt(EOF);

        // write buffer contents to file
        offsetFile.force();
        ptFile.force();
        offsetFile.rewind();
        ptFile.rewind();
    }

    private int calcNrOfToPointers(IndexHeapObject[] objects) {
        int nPointers = 0;
        for (IndexHeapObject obj : objects) {
            nPointers += obj.pointsTo.length;
        }
        return nPointers;
    }

    private int calcNrOfFromPointers(IndexHeapObject[] objects) {
        int nPointers = 0;
        for (IndexHeapObject obj : objects) {
            nPointers += obj.pointedFrom.length;
        }
        return nPointers;
    }

    private int[] getPointers(int objIndex, MappedByteBuffer offsetFile, MappedByteBuffer ptFile) {
        int offset = offsetFile.getInt(objIndex * Integer.BYTES);
        int limit = (offsetFile.limit() / Integer.BYTES);

        if (objIndex == limit - 1) {
            //last obj/pointers of file
            int pos = 0;
            int ptr = ptFile.getInt((offset + pos) * Integer.BYTES);
            ArrayList<Integer> pointers = new ArrayList<>();
            while (ptr != EOF) {
                pointers.add(ptr);
                pos++;
                ptr = ptFile.getInt((offset + pos) * Integer.BYTES);
            }
            return pointers.stream().mapToInt(i -> i).toArray();
        } else {
            //not last obj
            int nrPtrs = offsetFile.getInt((objIndex + 1) * Integer.BYTES) - offset;
            if (nrPtrs > 0) {
                ptFile.position(offset * Integer.BYTES);
                byte[] vals = new byte[nrPtrs * Integer.BYTES];
                ptFile.get(vals, 0, nrPtrs * Integer.BYTES);
                IntBuffer intBuf =
                        ByteBuffer.wrap(vals)
                                  .order(ByteOrder.BIG_ENDIAN)
                                  .asIntBuffer();
                int[] intVals = new int[intBuf.remaining()];
                intBuf.get(intVals);
                return intVals;
            } else {
                return null;
            }
        }
    }

    private void initMappedByteBuffers(int nrOfFromPointers, int nrOfToPointers) {
        dataFiles[0] = getPathString(ADDRESSES_PREFIX);
        dataFiles[1] = getPathString(FROM_POINTERS_PREFIX);
        dataFiles[2] = getPathString(FROM_POINTERS_OFF_PREFIX);
        dataFiles[3] = getPathString(TO_POINTERS_PREFIX);
        dataFiles[4] = getPathString(TO_POINTERS_OFF_PREFIX);
        dataFiles[5] = getPathString(OBJECT_INFO_ID_PREFIX);

        try {
            for (Path path : dataFiles) {
                path.toFile().getParentFile().mkdirs();
                if (Files.exists(path)) {
                    Files.delete(path);
                }
                Files.createFile(path);
            }

            this.addresses =
                    new RandomAccessFile(dataFiles[0].toAbsolutePath().toString(), "rw")
                            .getChannel()
                            .map(FileChannel.MapMode.READ_WRITE, 0, (super.objectCount + 1) * Long.BYTES);
            this.fromPointers =
                    new RandomAccessFile(dataFiles[1].toAbsolutePath().toString(), "rw")
                            .getChannel()
                            .map(FileChannel.MapMode.READ_WRITE, 0, (nrOfFromPointers + 1) * Integer.BYTES);
            this.fromPointersOffsets =
                    new RandomAccessFile(dataFiles[2].toAbsolutePath().toString(), "rw")
                            .getChannel()
                            .map(FileChannel.MapMode.READ_WRITE, 0, (super.objectCount + 1) * Integer.BYTES);
            this.toPointers =
                    new RandomAccessFile(dataFiles[3].toAbsolutePath().toString(), "rw")
                            .getChannel()
                            .map(FileChannel.MapMode.READ_WRITE, 0, (nrOfToPointers + 1) * Integer.BYTES);
            this.toPointersOffsets =
                    new RandomAccessFile(dataFiles[4].toAbsolutePath().toString(), "rw")
                            .getChannel()
                            .map(FileChannel.MapMode.READ_WRITE, 0, (super.objectCount + 1) * Integer.BYTES);
            this.objectInfos =
                    new RandomAccessFile(dataFiles[5].toAbsolutePath().toString(), "rw")
                            .getChannel()
                            .map(FileChannel.MapMode.READ_WRITE, 0, (super.objectCount + 1) * Integer.BYTES);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Path getTempDir() {
        String workingDir = System.getProperty("user.dir");
        return Paths.get(workingDir, TMP_DIR);
    }

    private Path getPathString(String file) {
        return Paths.get(getTempDir().toAbsolutePath().toString(), file + super.gcNo + FILE_EXTENSION);
    }

    public SpaceInfo getSpace(int objIndex) {
        long address = getAddress(objIndex);
        int spaceIndex = Arrays.binarySearch(spaceStartAddresses, address);
        if (spaceIndex < 0) {
            // address is not the start of a space
            spaceIndex = Math.abs(spaceIndex);
            spaceIndex -= 2;
        }

        return spaceInfos[spaceIndex];
    }
}
