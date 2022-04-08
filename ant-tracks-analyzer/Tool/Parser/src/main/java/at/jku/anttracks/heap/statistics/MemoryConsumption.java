
package at.jku.anttracks.heap.statistics;

public class MemoryConsumption {
    private long objects;
    private long bytes;

    public MemoryConsumption(long objects, long bytes) {
        this.objects = objects;
        this.bytes = bytes;
    }

    public MemoryConsumption() {

    }

    public long getObjects() {
        return objects;
    }

    public long getBytes() {
        return bytes;
    }

    public void addBytes(long bytes) {
        this.bytes += bytes;
    }

    public void incrementObjects() {
        this.objects++;
    }

    public void addObjects(long objects) {
        this.objects += objects;
    }

}
