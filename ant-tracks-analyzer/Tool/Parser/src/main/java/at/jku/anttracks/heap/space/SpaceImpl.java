
package at.jku.anttracks.heap.space;

import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectStream;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.labs.Lab;
import at.jku.anttracks.util.Consts;
import at.jku.anttracks.util.TraceException;

import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class SpaceImpl {
    private final ConcurrentSkipListMap<Long, Lab> labs;
    private final boolean maintainConsistency;

    public SpaceImpl() {
        this(true);
    }

    public SpaceImpl(boolean maintainConsistency) {
        this(maintainConsistency, new ConcurrentSkipListMap<>());
    }

    public SpaceImpl(boolean maintainConsistency, ConcurrentSkipListMap<Long, Lab> labs) {
        this.labs = labs;
        this.maintainConsistency = maintainConsistency;
    }

    public long getFill() {
        Lab last = labs.values().stream().reduce((l1, l2) -> l1.addr > l2.addr ? l1 : l2).orElse(null);
        if (last == null) {
            return -1;
        }
        return last.addr + last.capacity();
    }

    public Map<Long, Lab> get() {
        return labs;
    }

    public void removeEmptyLabs() {
        List<Long> removeLabAdresses = new ArrayList<>();
        labs.forEach((labAddr, lab) -> {if (!lab.contains(labAddr)) { removeLabAdresses.add(labAddr); }});
        removeLabAdresses.forEach(labAddr -> labs.remove(labAddr));
    }


    public void iterate(DetailedHeap heap,
                        SpaceInfo space,
                        List<Filter> filter,
                        ObjectVisitor visitor,
                        ObjectVisitor.Settings visitorSettings,
                        ExecutorService threadPool,
                        List<ObjectStream.IterationListener> labListeners) {
        if (threadPool != null) {
            int threadN = Consts.getAVAILABLE_PROCESSORS();
            Lab[] labs = this.labs.values().toArray(new Lab[0]);
            for (int i = 0; i < threadN; i++) {
                int threadNr = i;
                threadPool.execute(() -> {
                    LabNotifier labNotifier = new LabNotifier();
                    for (int labId = threadNr; labId < labs.length; labId += threadN) {
                        labs[labId].iterate(heap, space, filter, visitor, visitorSettings);
                        if (labListeners != null) {
                            labNotifier.setObjectCount(labs[labId].getObjectCount());
                            labListeners.forEach(labNotifier);
                        }
                    }
                });
            }
        } else {
            for (Lab lab : labs.values()) {
                lab.iterate(heap, space, filter, visitor, visitorSettings);
                if (labListeners != null) {
                    labListeners.forEach(l -> l.objectsIterated(lab.getObjectCount()));
                }
            }
        }
    }

    public <I extends ObjectVisitor> List<Future<I>> iterateAsync(DetailedHeap heap,
                                                                  List<Filter> filter,
                                                                  SpaceInfo space,
                                                                  ObjectStream.ThreadVisitorGenerator<I> threadLocalDataGenerator,
                                                                  ObjectVisitor.Settings visitorSettings,
                                                                  ExecutorService threadPool,
                                                                  List<ObjectStream.IterationListener> listener) {
        int threadN = Consts.getAVAILABLE_PROCESSORS() * 2;
        Lab[] labs = this.labs.values().toArray(new Lab[0]);
        List<Future<I>> futures = new ArrayList<>();
        for (int i = 0; i < threadN; i++) {
            int threadNr = i;
            I visitor = threadLocalDataGenerator.generate();
            futures.add(threadPool.submit(() -> {
                for (int labId = threadNr; labId < labs.length; labId += threadN) {
                    labs[labId].iterate(heap, space, filter, visitor, visitorSettings);
                    if (listener != null) {
                        final int labIdFinal = labId;
                        listener.forEach(l -> l.objectsIterated(labs[labIdFinal].getObjectCount()));
                    }
                }
                return visitor;
            }));
        }
        return futures;
    }

    public void clear() {
        labs.clear();
    }

    public Lab remove(long addr) {
        return labs.remove(addr);
    }

    public void put(Lab lab, boolean allowReplace) throws TraceException {
        if (allowReplace) {
            synchronized (labs) { //double checked locking, do not modify or you will burn in hell!
                Entry<Long, Lab> prev = labs.floorEntry(lab.addr);
                if (prev != null && prev.getValue().end() > lab.addr) {
                    labs.remove(prev.getValue().addr);
                    Lab head = prev.getValue().sublab(prev.getValue().addr, lab.addr);
                    Lab tail = prev.getValue().sublab(lab.end(), prev.getValue().end());
                    if (head != null) {
                        labs.put(head.addr, head);
                    }
                    if (tail != null) {
                        labs.put(tail.addr, tail);
                    }
                }
            }
        }
        Lab replaced = labs.put(lab.addr, lab);
        if (maintainConsistency) {
            // do this checks afterwards because this method is not thread-safe
            if (replaced != null && replaced.capacity() > 0) {
                labs.put(replaced.addr, replaced);
                throw new TraceException(String.format("Lab @ %,d - %,d replaced another Lab %,d - %,d at the same address",
                                                       lab.addr,
                                                       lab.addr + lab.capacity(),
                                                       replaced.addr,
                                                       replaced.addr + replaced.capacity()));
            }

            Entry<Long, Lab> prev = labs.lowerEntry(lab.addr);
            if (prev != null) {
                Lab prevLab = prev.getValue();
                if (prevLab.addr + prevLab.capacity() > lab.addr) {
                    labs.remove(lab.addr);
                    throw new TraceException(String.format("Lab @ [%,d - %,d] (%,d) collides with previous Lab @ [%,d - %,d] (%,d)\n" + "Prev lab current_position " +
                                                                   "(top): %,d",
                                                           lab.addr,
                                                           lab.addr + lab.capacity(),
                                                           lab.capacity(),
                                                           prevLab.addr,
                                                           prevLab.addr + prevLab.capacity(),
                                                           prevLab.capacity(),
                                                           prevLab.addr + prevLab.position()));
                }
            }

            Entry<Long, Lab> next = labs.higherEntry(lab.addr);
            if (next != null) {
                Lab nextLab = next.getValue();
                if (lab.addr + lab.capacity() > nextLab.addr) {
                    labs.remove(lab.addr);
                    throw new TraceException(String.format("Lab @ %,d collides with next lab", lab.addr));
                }
            }
        }
    }

    public boolean containsExact(long addr) {
        return labs.containsKey(addr);
    }

    public void putAll(Map<Long, Lab> labs, boolean allowReplace) throws TraceException {
        for (Lab lab : labs.values()) {
            put(lab, allowReplace);
        }
    }

    public Lab findLab(long addr) throws TraceException {
        Entry<Long, Lab> mapping = labs.floorEntry(addr);
        Lab lab = mapping != null ? (addr <= mapping.getValue().end() ? mapping.getValue() : null) : null;
        if (lab == null) {
            throw new TraceException(String.format("Could not find lab at %,d", addr));
        }
        return lab;
    }

    public Lab findNearestLab(long addr) {
        Entry<Long, Lab> mapping = labs.floorEntry(addr);
        return mapping != null ? mapping.getValue() : null;
    }

    public void overwriteWith(Map<Long, Lab> fillers) throws TraceException {
        for (Lab filler : fillers.values()) {
            if (overwriteWith(filler)) {
                fillers.remove(filler.addr);
            }
        }
    }

    public boolean overwriteWith(Lab filler) throws TraceException {
        Entry<Long, Lab> mapping_start = labs.floorEntry(filler.bottom());
        Entry<Long, Lab> mapping_end = labs.floorEntry(filler.end());
        if (mapping_start == null || mapping_end == null) {
            return false;
        }
        Lab lab_start = mapping_start.getValue();
        Lab lab_end = mapping_end.getValue();
        if (!lab_start.isFull() || !lab_end.isFull()) {
            return false;
        }
        boolean start_contains_filler_start = lab_start.bottom() <= filler.bottom() && filler.bottom() < lab_start.end();
        boolean end_contains_filler_end = lab_end.bottom() <= filler.end() && filler.end() <= lab_end.end();
        boolean filler_start_hits_object_start_in_start_lab = start_contains_filler_start && lab_start.getObject(filler.bottom()) != null;
        boolean filler_end_hits_object_start_in_end = end_contains_filler_end && (lab_end.end() == filler.end() || lab_end.getObject(filler.end()) != null);

        if (filler_start_hits_object_start_in_start_lab && filler_end_hits_object_start_in_end) {
            /*
             * Lab lab_start_new = lab_start.sublab(lab_start.bottom(), filler.bottom()); Lab lab_end_new = lab_end.sublab(filler.end(),
             * lab_end.end()); if(lab_start_new == null && lab_end_new == null) return false; labs.remove(lab_start.bottom());
             * labs.remove(lab_end.bottom()); if(lab_start_new != null) put(lab_start_new); put(filler); if(lab_end_new != null)
             * put(lab_end_new);
             */
            return true;
        } else {
            return false;
        }
    }

    public void fillWith(Map<Long, Lab> fillers) throws TraceException {
        putAll(fillers, true);
        fillers.clear();
    }

    public void validate(long addr, boolean allowFragmentation) throws TraceException {
        for (; ; ) {
            Lab lab = labs.get(addr);
            if (lab == null) {
                Long nextAddr = labs.higherKey(addr);
                if (nextAddr == null) {
                    break;
                } else {
                    if (allowFragmentation) {
                        addr = nextAddr;
                        continue;
                    } else {
                        throw new TraceException(String.format("Heap not consecutive: Lab missing @ %,d", addr));
                    }
                }
            } else if (!lab.isFull() && !allowFragmentation && !lab.isExtendable()) {
                throw new TraceException(String.format("Lab not consecutive: Lab underfull @ %,d (%,d, %,d)", lab.bottom(), lab.top(), lab.end()));
            }
            assert addr == lab.addr;
            if (lab.capacity() == 0) {
                break;
            } else {
                addr = addr + lab.capacity();
            }
            if (addr > lab.addr && lab.addr != labs.lowerKey(addr)) {
                throw new TraceException(String.format("Heap not consecutive: Labs overlapping @ %,d - %,d", lab.addr, addr));
            }
        }
    }

    @Override
    public String toString() {
        StringBuilder str = new StringBuilder();
        str.append("Space @ \n");
        Collection<Lab> l = labs.values();

        for (Lab lab : new TreeSet<>(l)) {
            str.append(String.format("LAB @ %,d - %,d (%,d)\n", lab.addr, lab.addr + lab.capacity(), lab.capacity()));
        }
        return str.toString();
    }

    @Override
    public SpaceImpl clone() {
        ConcurrentSkipListMap<Long, Lab> clonedLabs = new ConcurrentSkipListMap<>();
        labs.entrySet().forEach(entry -> clonedLabs.put(entry.getKey(), entry.getValue().clone()));
        return new SpaceImpl(maintainConsistency, clonedLabs);
    }

    public void reduceSize() {
        labs.values().forEach(lab -> lab.reduceSize());
    }

    public int getLabCount() {
        return labs.size();
    }

    private class LabNotifier implements Consumer<ObjectStream.IterationListener> {
        int objectCount;

        public void setObjectCount(int objectCount) {
            this.objectCount = objectCount;
        }

        @Override
        public void accept(ObjectStream.IterationListener iterationListener) {
            iterationListener.objectsIterated(objectCount);
        }
    }
}
