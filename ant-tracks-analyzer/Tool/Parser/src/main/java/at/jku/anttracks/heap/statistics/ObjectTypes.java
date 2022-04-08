
package at.jku.anttracks.heap.statistics;

public class ObjectTypes {
    private long instances;
    private long smallArrays;
    private long bigArrays;
    private long instancesBytes;
    private long smallArraysBytes;
    private long bigArraysBytes;

    public ObjectTypes(long instances, long smallArrays, long bigArrays) {
        this.instances = instances;
        this.smallArrays = smallArrays;
        this.bigArrays = bigArrays;
    }

    public ObjectTypes() {

    }

    public long getInstances() {
        return instances;
    }

    public void incrementInstances(long bytes) {
        this.instances++;
        this.instancesBytes += bytes;
    }

    public long getSmallArrays() {
        return smallArrays;
    }

    public void incrementSmallArrays(long bytes) {
        this.smallArrays++;
        this.smallArraysBytes += bytes;
    }

    public long getBigArrays() {
        return bigArrays;
    }

    public void incrementBigArrays(long bytes) {
        this.bigArrays++;
        this.bigArraysBytes += bytes;
    }

    public void addInstances(long instances, long instancesBytes) {
        this.instances += instances;
        this.instancesBytes += instancesBytes;
    }

    public void addSmallArrays(long smallArrays, long smallArraysBytes) {
        this.smallArrays += smallArrays;
        this.smallArraysBytes += smallArraysBytes;
    }

    public void addBigArrays(long bigArrays, long bigArraysBytes) {
        this.bigArrays += bigArrays;
        this.bigArraysBytes += bigArraysBytes;
    }

    public void clear() {
        this.instances = 0;
        this.smallArrays = 0;
        this.bigArrays = 0;
        this.instancesBytes = 0;
        this.smallArraysBytes = 0;
        this.bigArraysBytes = 0;
    }

    public long getInstancesBytes() {
        return instancesBytes;
    }

    public long getSmallArraysBytes() {
        return smallArraysBytes;
    }

    public long getBigArraysBytes() {
        return bigArraysBytes;
    }
}
