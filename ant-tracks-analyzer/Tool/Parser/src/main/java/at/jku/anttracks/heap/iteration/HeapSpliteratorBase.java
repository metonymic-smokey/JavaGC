package at.jku.anttracks.heap.iteration;

import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectStream;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.heap.space.SpaceInfo;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectStream;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.heap.labs.Lab;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.heap.space.SpaceInfo;
import at.jku.anttracks.util.TraceException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public abstract class HeapSpliteratorBase {
    // Process a maximum of 250_000 objects per spliterator
    public static final long THRESHOLD = 250_000;

    SpliteratorRange range;
    final boolean current;

    private int currentInternalSpaceIndex = 0;
    private int currentLabIndex = 0;
    private int currentObjectIndex = -1;
    private long currentAddress;

    protected DetailedHeap heap;
    private Space[] spaces;
    private Lab[] labs;
    private boolean reloadLabs = true;

    boolean sorted;

    protected List<ObjectStream.IterationListener> listener;

    Consumer<ObjectStream.IterationListener> listenerNofifier = listener -> listener.objectsIterated(labs[currentLabIndex].getObjectCount());

    HeapSpliteratorBase(DetailedHeap heap, int startSpace, int startLab, int fenceSpace, int fenceLab, boolean current, boolean sorted) {
        this(heap, new SpliteratorRange(startSpace, startLab, fenceSpace, fenceLab), current, sorted);
    }

    public HeapSpliteratorBase(DetailedHeap heap, SpliteratorRange spliteratorRange, boolean current, boolean sorted) {
        this.heap = heap;
        this.current = current;
        this.listener = new ArrayList<>();
        setRange(spliteratorRange);

        setSorted(sorted);
    }

    public void setRange(SpliteratorRange range) {
        this.range = range;
        spaces = Arrays.stream(heap.getSpacesCloned())
                       .sequential()
                       .filter(x -> x != null && x.getId() >= range.startSpaceId && x.getId() < range.fenceSpaceId && x.getMode() != null && x.getType() != null && x
                               .getLabCount(current) > 0)
                       .toArray(Space[]::new);

        // Adjust start if necesarry
        if (spaces.length > 0 && spaces[0].getId() != range.startSpaceId) {
            range.startSpaceId = spaces[0].getId();
            range.startLabIndex = 0;
        }

        // Adjust end if necesarry
        if (spaces.length > 0 && spaces[spaces.length - 1].getId() + 1 != range.fenceSpaceId) {
            range.fenceSpaceId = spaces[spaces.length - 1].getId() + 1;
            range.fenceLabIndex = spaces[spaces.length - 1].getLabCount(current);
        }

        if (spaces.length > 0) {
            currentInternalSpaceIndex = 0;
            currentLabIndex = range.startLabIndex;
            currentObjectIndex = -1;
            labs = spaces[0].getLabs(current);
        }
    }

    public void setSorted(boolean sorted) {
        this.sorted = sorted;
        // TODO: Fence and co get f***ed up when sorting, we have to deal with this somehow
        if (sorted) {
            Arrays.sort(spaces, Comparator.comparingLong(space -> space == null ? -1 : space.getAddress()));
            throw new UnsupportedOperationException("Not yet implemented, use other streaming approach");
        }
    }

    public void setListener(List<ObjectStream.IterationListener> listener) {
        this.listener = listener;
    }

    protected boolean advanceStep() {
        try {
            if (currentObjectIndex != -1) {
                // Exclude the very first object
                currentAddress += labs[currentLabIndex].getObjectAtIndex(currentObjectIndex).getSize();
            } else {
                currentAddress = labs[currentLabIndex].addr;
            }
            currentObjectIndex++;

            if (currentObjectIndex == labs[currentLabIndex].getObjectCount()) {
                // Reached the end of the lab
                if (listener != null && listener.size() > 0) {
                    listener.forEach(listenerNofifier);
                }

                currentLabIndex++;
                currentObjectIndex = 0;

                if (currentInternalSpaceIndex + 1 == spaces.length && currentLabIndex == range.fenceLabIndex) {
                    return false;
                }

                if (currentLabIndex == labs.length) {
                    // Reached the end of the space
                    currentInternalSpaceIndex++;
                    currentLabIndex = 0;
                    currentObjectIndex = 0;

                    if (currentInternalSpaceIndex == spaces.length) {
                        return false;
                    }

                    labs = spaces[currentInternalSpaceIndex].getLabs(current);
                }

                currentAddress = labs[currentLabIndex].addr;
            }
            return true;
        } catch (TraceException e) {
            e.printStackTrace();
            return false;
        }
    }

    protected AddressHO getCurrentObject() throws TraceException {
        return labs[currentLabIndex].getObjectAtIndex(currentObjectIndex);
    }

    protected long getCurrentAddress() {
        return currentAddress;
    }

    protected SpaceInfo getCurrentSpaceInfo() {
        return spaces[currentInternalSpaceIndex].getInfo();
    }

    public long size() {
        int size = 0;
        for (int space = 0; space < spaces.length; space++) {
            if (spaces[space] == null) {
                continue;
            }
            Lab[] labs = spaces[space].getLabs(current);
            int fromLab = 0;
            int toLab = labs.length;
            if (space == 0) {
                fromLab = range.startLabIndex;
            }
            if (space == spaces.length - 1) {
                toLab = range.fenceLabIndex;
            }

            for (int lab = fromLab; lab < toLab; lab++) {
                size += labs[lab].getObjectCount();
            }
        }
        return size;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Spliterator[");
        sb.append(range.startSpaceId);
        sb.append(", ");
        sb.append(range.startLabIndex);
        sb.append(", ");
        sb.append(range.fenceSpaceId);
        sb.append(", ");
        sb.append(range.fenceLabIndex);
        sb.append("]");
        return sb.toString();
    }

    protected SpliteratorRange getLowerSpliteratorRange() {
        int lowSpaceId = range.startSpaceId;
        int midSpaceId = (lowSpaceId + range.fenceSpaceId) >>> 1; // split in middle
        if (lowSpaceId < midSpaceId) {
            int fenceLabIndex = heap.getSpacesUncloned()[midSpaceId - 1] != null && heap.getSpacesUncloned()[midSpaceId - 1].getType() != null && heap.getSpacesUncloned()
                    [midSpaceId - 1]
                    .getMode() != null ? heap.getSpacesUncloned()[midSpaceId - 1].getLabs(current).length : 0;
            return new SpliteratorRange(range.startSpaceId, range.startLabIndex, midSpaceId, fenceLabIndex);
        } else {
            assert range.startSpaceId + 1 == range.fenceSpaceId : "Spliterator must only spread 1 space";
            // Current spliterator only spans one space
            // Try to split only on lab basis
            int lowLabIndex = range.startLabIndex;
            int midLabIndex = (lowLabIndex + range.fenceLabIndex) >>> 1;
            if (lowLabIndex < midLabIndex) {
                return new SpliteratorRange(range.startSpaceId, range.startLabIndex, range.fenceSpaceId, midLabIndex);
            } else {
                return null;
            }
        }
    }

    protected SpliteratorRange getUpperSpliteratorRange() {
        int lowSpaceId = range.startSpaceId;
        int midSpaceId = (lowSpaceId + range.fenceSpaceId) >>> 1; // split in middle
        if (lowSpaceId < midSpaceId) {
            return new SpliteratorRange(midSpaceId, 0, range.fenceSpaceId, range.fenceLabIndex);
        } else {
            assert range.startSpaceId + 1 == range.fenceSpaceId : "Spliterator must only spread 1 space";
            // Current spliterator only spans one space
            // Try to split only on lab basis
            int lowLabId = range.startLabIndex;
            int midLabId = (lowLabId + range.fenceLabIndex) >>> 1;
            if (lowLabId < midLabId) {
                return new SpliteratorRange(range.startSpaceId, midLabId, range.fenceSpaceId, range.fenceLabIndex);
            } else {
                return null;
            }
        }
    }
}