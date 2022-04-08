
package at.jku.anttracks.heap.space;

public class SpaceInfo {
    public short id;

    public enum TransitionType {
        None,
        // No current GC (mutator phase)
        ReplaceAll,
        // ParallelOldGC: Major GC, G1: Major GC
        Accumulative // ParallelOldGC: Minor GC, G1: Minor GC
    }

    public final String name;

    private long address;
    private long length;
    private SpaceMode mode;
    private SpaceType type;

    private TransitionType transition;

    public SpaceInfo(String name) {
        this.name = name;
    }

    public long getAddress() {
        return address;
    }

    public long getEnd() {
        return address + length;
    }

    public void setAddress(long addr) {
        address = addr;
    }

    public long getLength() {
        return length;
    }

    public void setLength(long length) {
        this.length = length;
    }

    public SpaceMode getMode() {
        return mode;
    }

    public void setMode(SpaceMode mode) {
        this.mode = mode;
    }

    public SpaceType getType() {
        return type;
    }

    public void setType(SpaceType type) {
        this.type = type;
    }

    public TransitionType getTransitionType() {
        return transition;
    }

    public void setTransitionType(TransitionType transition) {
        this.transition = transition;
    }

    public boolean isBeingCollected() {
        return getTransitionType() == SpaceInfo.TransitionType.ReplaceAll;
    }

    public boolean contains(long addr) {
        return this.address <= addr && addr < getEnd();
    }

    @Override
    public String toString() {
        return String.format("%s @ %,d - %,d (Length: %,d) (Mode: %s, Type: %s)", name, getAddress(), getAddress() + getLength(), getLength(), getMode(), getType());
    }
}
