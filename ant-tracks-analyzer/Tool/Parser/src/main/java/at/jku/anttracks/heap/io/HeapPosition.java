
package at.jku.anttracks.heap.io;

public class HeapPosition {
    public final int fileName;
    public final long fromPosition;
    public final long toPosition;

    public HeapPosition(int fileName, long from, long to) {
        this.fileName = fileName;
        this.fromPosition = from;
        this.toPosition = to;
    }

    @Override
    public String toString() {
        return String.format("Heap File \"%s\" [heap file pos: %d, selected pos: %d]", fileName, fromPosition, toPosition);
    }
}
