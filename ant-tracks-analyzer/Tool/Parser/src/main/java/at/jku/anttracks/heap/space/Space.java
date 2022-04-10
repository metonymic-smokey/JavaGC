
package at.jku.anttracks.heap.space;

import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.FrontBackObjectAccess;
import at.jku.anttracks.heap.ObjectStream;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.labs.AddressHO;
import at.jku.anttracks.heap.labs.Lab;
import at.jku.anttracks.heap.labs.MultiObjectLab;
import at.jku.anttracks.heap.labs.SingleObjectLab;
import at.jku.anttracks.heap.objects.ObjectInfo;
import at.jku.anttracks.parser.heap.pointer.PtrUpdateVisitor;
import at.jku.anttracks.util.CollectionsUtil;
import at.jku.anttracks.util.TraceException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import static at.jku.anttracks.heap.space.SpaceInfo.TransitionType.Accumulative;
import static at.jku.anttracks.heap.space.SpaceInfo.TransitionType.None;

public class Space implements FrontBackObjectAccess {

    static final long LAB_NOT_FOUND = -3;

    private final SpaceInfo info;
    private SpaceImpl front;
    private SpaceImpl back;
    private final Map<Long, Lab> pendingForeignFillers;

    public Space(String name) {
        this(name, new SpaceImpl());
    }

    public Space(String name, SpaceImpl front) {
        info = new SpaceInfo(name);
        this.front = front;
        back = new SpaceImpl();
        pendingForeignFillers = new ConcurrentSkipListMap<>();
        info.setTransitionType(None);
    }

    public long getFill() {
        long fill = info.getTransitionType() == None ? front.getFill() : back.getFill();
        if (fill < 0) {
            return getAddress();
        }
        return fill;
    }

    public boolean contains(long addr) {
        return info.contains(addr);
    }

    public Lab[] getLabs() {
        return getLabs(true);
    }

    public Lab[] getLabs(boolean current) {
        if (current) {
            switch (info.getTransitionType()) {
                case None:
                case ReplaceAll:
                    return CollectionsUtil.toArray(Lab.class, front.get().values());
                case Accumulative:
                    return CollectionsUtil.concat(Lab.class, back.get().values(), front.get().values());
                default:
                    assert false;
                    return null;
            }
        } else {
            switch (info.getTransitionType()) {
                case ReplaceAll:
                case Accumulative:
                    return CollectionsUtil.toArray(Lab.class, back.get().values());
                case None:
                    // TODO
                    throw new IllegalStateException("Cannot iterate last heap state while no GC is running! TODO: This currently occurs for G1 GC if new spaces " +
                                                            "are created " +
                                                            "during GC" + ". At the end of GC, " + "heap needs to be traversed, and new spaces throw this exception");
                default:
                    return null;
            }
        }
    }

    public void setId(short id) {
        this.info.id = id;
    }

    public int getId() {
        return info.id;
    }

    public long getEnd() {
        return info.getEnd();
    }

    public void resetAddressAndLengthWithPointers(long bottom, long end) {
        resetAddressAndLength(bottom, end - bottom);
    }

    public void resetAddressAndLength(long addr, long length) {
        setAddress(addr);
        setLength(length);
    }

    public void iterate(DetailedHeap heap, ObjectVisitor visitor, ObjectVisitor.Settings visitorSettings) {
        iterate(heap, visitor, visitorSettings, null);
    }

    public void iterate(DetailedHeap heap, ObjectVisitor visitor, ObjectVisitor.Settings visitorSettings, List<Filter> filter) {
        iterate(heap, visitor, visitorSettings, filter, true);
    }

    public void iterate(DetailedHeap heap, ObjectVisitor visitor, ObjectVisitor.Settings visitorSettings, List<Filter> filter, boolean current) {
        iterate(heap, visitor, visitorSettings, filter, current, null);
    }

    public void iterate(DetailedHeap heap,
                        ObjectVisitor visitor,
                        ObjectVisitor.Settings visitorSettings,
                        List<Filter> filter,
                        boolean current,
                        ExecutorService threadPool) {
        iterate(heap, visitor, visitorSettings, filter, current, threadPool, null);
    }

    @SuppressWarnings("fallthrough")
    public void iterate(DetailedHeap heap,
                        ObjectVisitor visitor,
                        ObjectVisitor.Settings visitorSettings,
                        List<Filter> filter,
                        boolean current,
                        ExecutorService threadPool,
                        List<ObjectStream.IterationListener> listener) {
        if (current) {
            if (getMode() == null && getType() == null) {
                // This happens if space has been released during GC, but the
                // datastructure is not updated yet
                return;
            }

            switch (info.getTransitionType()) {
                case Accumulative:
                    back.iterate(heap, info, filter, visitor, visitorSettings, threadPool, listener);
                case None:
                case ReplaceAll:
                    front.iterate(heap, info, filter, visitor, visitorSettings, threadPool, listener);
            }
        } else {
            switch (info.getTransitionType()) {
                case ReplaceAll:
                case Accumulative:
                    back.iterate(heap, info, filter, visitor, visitorSettings, threadPool, listener);
                    break;
                case None:
                    // TODO
//                    throw new IllegalStateException("Cannot iterate last heap state while no GC is running! TODO: This currently occurs for G1 GC if new spaces " +
//                                                            "are created " +
//                                                            "during GC" + ". At the end of GC, " + "heap needs to be traversed, and new spaces throw this exception");

                    // comment by eg: removed the IllegalStateException...this happens to me when selecting a "Metadata GC Threshold" gc end as start of a diffing window
                    // listeners for gc end events are triggered which try to iterate over the heap -> this exception is thrown :(
            }
        }
    }

    /**
     * Multi threaded, async iteration
     *
     * @param filter                      The filters that should be applied
     * @param threadLocalVisitorGenerator Generator to construct visitor, one per space
     * @param current                     Flag if current space state should be used (depending on space
     *                                    state), otherwise back is used
     * @param threadPool                  The thread-pool from which threads are requested
     * @return A list containing the visitors
     */
    @SuppressWarnings("fallthrough")
    public <I extends ObjectVisitor> List<Future<I>> iterateAsync(DetailedHeap heap,
                                                                  ObjectStream.ThreadVisitorGenerator<I> threadLocalVisitorGenerator,
                                                                  ObjectVisitor.Settings visitorSettings,
                                                                  List<Filter> filter,
                                                                  boolean current,
                                                                  ExecutorService threadPool,
                                                                  List<ObjectStream.IterationListener> listener) {
        if (current) {
            if (getType() == null) {
                // This happens if space has been released during GC, but the
                // datastructure is not updated yet
                return new ArrayList<>();
            }

            List<Future<I>> returns = new ArrayList<>();
            switch (info.getTransitionType()) {
                case Accumulative:
                    back.iterateAsync(heap, filter, info, threadLocalVisitorGenerator, visitorSettings, threadPool, listener).forEach(returns::add);
                case None:
                case ReplaceAll:
                    front.iterateAsync(heap, filter, info, threadLocalVisitorGenerator, visitorSettings, threadPool, listener).forEach(returns::add);
            }
            return returns;
        } else {
            switch (info.getTransitionType()) {
                case ReplaceAll:
                case Accumulative:
                    return back.iterateAsync(heap, filter, info, threadLocalVisitorGenerator, visitorSettings, threadPool, listener);
                case None:
                    // TODO
                    throw new IllegalStateException("Cannot iterate last heap state while no GC is running! TODO: This currently occurs for G1 GC if new spaces " +
                                                            "are created " +
                                                            "during GC" + ". At the end of GC, " + "heap needs to be traversed, and new spaces throw this exception");
            }
        }
        return null;
    }

    public boolean isBeingCollected() {
        return info.isBeingCollected();
    }

    public SpaceInfo.TransitionType getTransitionType() {
        return info.getTransitionType();
    }

    public void startTransition(SpaceInfo.TransitionType newTransition) throws TraceException {
        if (info.getTransitionType() == SpaceInfo.TransitionType.ReplaceAll) {
            throw new TraceException("Transition already in progress!");
        } else if (info.getTransitionType() == Accumulative) {
            commitTransition();
        }
        assert info.getTransitionType() == None;
        assert back.get().isEmpty() : info.name;
        info.setTransitionType(newTransition);
        swap();
        assert front.get().isEmpty() : info.name;
    }

    public void startTransition(SpaceInfo.TransitionType newTransition, long address) throws TraceException {
        assert newTransition == SpaceInfo.TransitionType.ReplaceAll;
        startTransition(newTransition);
        for (Lab lab : back.get().values()) {
            if (lab.bottom() < address) {
                if (lab.end() < address) {
                    front.put(lab, false);
                } else {
                    front.put(lab.sublab(lab.bottom(), address), false);
                }
            }
        }
    }

    @SuppressWarnings("fallthrough")
    public void commitTransition() throws TraceException {
        switch (info.getTransitionType()) {
            case None:
                throw new TraceException("No transition in progress!");
            case Accumulative:
                back.putAll(front.get(), false);
                swap();
            case ReplaceAll:
                back.clear();
                break;
        }
        info.setTransitionType(None);
        assert back.get().isEmpty() : info.name;
    }

    /**
     * Commits the current Transition up to {@code address} (exclusive) and
     * conserves all objects from address (inclusive) to {@code end}. <br>
     * This code makes the silent assumption that we sweep continuously from
     * {@code bottom} to {@code top}.
     */
    public void commitTransition(long address) throws TraceException {
        assert info.getTransitionType() == SpaceInfo.TransitionType.ReplaceAll;
        for (Lab lab : back.get().values()) {
            if (address <= lab.bottom()) {
                front.put(lab, false);
            } else if (address < lab.end()) {
                front.put(lab.sublab(address, lab.end()), false);
            }
        }
        commitTransition();
    }

    @SuppressWarnings("fallthrough")
    public void rollbackTransition() throws TraceException {
        switch (info.getTransitionType()) {
            case None:
                throw new TraceException("No transition in progress!");
            case ReplaceAll:
            case Accumulative:
                pendingForeignFillers.clear();
                back.putAll(front.get(), true);
                front.clear();
                swap();
                break;
        }
        info.setTransitionType(SpaceInfo.TransitionType.None);
        assert back.get().isEmpty() : info.name;
    }

    private void swap() {
        SpaceImpl tmp;
        tmp = back;
        back = front;
        front = tmp;
    }

    public void clear() {
        back.clear();
        front.clear();
        pendingForeignFillers.clear();
    }

    public ObjectInfo getObjectInfo(long addr, boolean canConsumeFromFillers) throws TraceException {
        AddressHO obj = checkIfObjectIsFiller(addr, canConsumeFromFillers);
        if (obj == null) {
            Lab lab;
            if (info.getTransitionType() == SpaceInfo.TransitionType.None) {
                lab = front.findLab(addr);
            } else {
                lab = back.findLab(addr);
            }
            if (lab == null) {
                return null;
            }
            obj = lab.getObject(addr);
        }

        errorOnObjectNotFound(obj, addr);
        assert obj.getSize() > 0;

        return obj.getInfo();
    }

    public AddressHO checkIfObjectIsFiller(long addr, boolean canConsumeFromFillers) throws TraceException {
        AddressHO obj = null;
        // first look in the fillers, because IF a filler has already been
        // allocated at that position, it has overwritten the object in from
        if (canConsumeFromFillers) {
            Lab filler = findLabInFiller(addr);
            if (filler != null) {
                obj = filler.getObject(addr);
            }
        }
        return obj;
    }

    public void iterateUpdatePointer(PtrUpdateVisitor iterator) throws TraceException {
        SpaceImpl state;
        if (iterator.front) {
            state = front;
        } else {
            state = back;
        }

        if (state.get().size() > 0) {
            Collection<Lab> labs = state.get().values();
            labs.stream().forEach(l -> l.iterateUpdatePointer(iterator, this));
        }
    }

	/*
    public ObjectInfoAge getNonFillerObjectInfoAge(long addr) throws TraceException {
		Lab lab = back.findLab(addr);
		errorOnLabNotFound(lab, addr);

		ObjectInfoAge objAge = lab.getObjectAge(addr);
		errorOnObjectNotFound(objAge.obj, addr);

		assert objAge.obj.size > 0;
		return objAge;
	}
	*/

    public AddressHO getNonFillerObject(long addr) throws TraceException {
        Lab lab = back.findLab(addr);
        errorOnLabNotFound(lab, addr);

        AddressHO obj = lab.getObject(addr);
        errorOnObjectNotFound(obj, addr);

        assert obj.getSize() > 0;
        return obj;
    }

    // +++++++++++++++++++++fresh array approach to pointer
    // handling+++++++++++++++++++++++++
    // the following functions require an objects address relativ to the space
    // it occupies, i.e.
    // objAddrRel = objAddrAbs - spaceContainingObject.address
    public Lab getLabForAddr(long objAddrRel, boolean inFront) throws TraceException {
        Lab filler = findLabInFiller(objAddrRel);
        if (filler != null) {
            return filler;
        }
        Lab lab = null;
        if (inFront) {
            lab = front.findLab(objAddrRel);
        } else {
            lab = back.findLab(objAddrRel);
        }
        return lab;

    }

    public Lab getNonFillerLabForAddr(long objAddrRel, boolean inFront) throws TraceException {
        Lab lab = null;
        if (inFront) {
            lab = front.findLab(objAddrRel);
        } else {
            lab = back.findLab(objAddrRel);
        }
        return lab;
    }

    // ++++++++++++++++++++++END of fresh array approach to pointer
    // handling++++++++++++++++++++++++++++++++++++++++++

    private Lab findLabInFiller(long addr) {
        return pendingForeignFillers.remove(addr);
    }

    public long assign(String thread, AddressHO obj, long addr) throws TraceException {
        return assign(thread, false, obj, addr);
    }

	/*
    public long assign(String thread, ObjectInfoAge objAge, long addr) throws TraceException {
		return assign(thread, false, objAge, addr);
	}
	*/

    public long assign(String thread, boolean isPotentialFiller, AddressHO obj, long addr) throws TraceException {
        if (isPotentialFiller) {
            Lab lab = new SingleObjectLab(thread, Lab.Kind.VIRTUAL, addr, obj);
            pendingForeignFillers.put(addr, lab);
        } else {
            Lab lab = tryToFindAdjustingLab(thread, addr);
            if (lab != null) {
                assignIntoAdjacentLab(lab, thread, addr, obj);
            } else {
                assignVirtualLab(thread, Lab.Kind.VIRTUAL, addr, obj);
            }
        }

        return addr;
    }

    public long assignIntoAdjacentLab(Lab lab, String thread, long addr, AddressHO obj) throws TraceException {
        MultiObjectLab mLab;
        if (lab instanceof SingleObjectLab) {
            mLab = ((SingleObjectLab) lab).toMultiObjectLab();
        } else {
            mLab = (MultiObjectLab) lab;
        }
        mLab.variableCapacity();
        long assignedAddress = mLab.tryAllocate(addr, obj);
        assert assignedAddress == addr : "Object could not be allocated in adjusting LAB";
        mLab.resetCapacity();

        if (mLab != lab) {
            exchangeLabs(lab, mLab);
        }

        return assignedAddress;
    }

    private void exchangeLabs(Lab oldLab, Lab newLab) throws TraceException {
        assert front.findLab(oldLab.addr) == oldLab : "Old lab has not been found";
        assert oldLab.addr == newLab.addr : "Old and new lab have to be at the same address";
        Lab remove = front.remove(oldLab.addr);
        assert remove == oldLab : "The removed lab must be the old lab";
        front.put(newLab, false);
    }

    // This method is called if an object would be allocated as single object LAB, but we try to merge with an adjusting LAB
    private Lab tryToFindAdjustingLab(String thread, long addr) {
        Lab nearest = front.findNearestLab(addr);
        if (nearest != null && nearest.thread.equals(thread) && nearest.top() == addr && (nearest.kind == Lab.Kind.VIRTUAL || nearest.kind == Lab.Kind.REGION_VIRTUAL)) {
            return nearest;
        }
        return null;
    }

	/*
    public long assign(String thread, boolean isPotentialFiller, ObjectInfoAge objAge, long addr)
			throws TraceException {
		if (isPotentialFiller) {
			Lab lab = new SingleObjectLab(thread, Lab.Kind.VIRTUAL, addr, objAge.obj, objAge.age + 1);
			pendingForeignFillers.put(addr, lab);
		} else {
			assignVirtualLab(thread, Lab.Kind.VIRTUAL, addr, objAge);
		}

		return addr;
	}
	*/

    public Lab assignRealLab(String thread, Lab.Kind kind, long addr, long size) throws TraceException {
        Lab realLab = new MultiObjectLab(thread, kind, addr, (int) size);

        assignLab(realLab);
        return realLab;
    }

    public Lab assignVirtualLab(String thread, Lab.Kind kind, long addr, AddressHO obj) throws TraceException {
        Lab virtualLab = new SingleObjectLab(thread, kind, addr, obj);

        assignLab(virtualLab);
        return virtualLab;
    }

	/*
    public Lab assignVirtualLab(String thread, int kind, long addr, ObjectInfoAge objAge) throws TraceException {
		Lab virtualLab = new SingleObjectLab(thread, kind, addr, objAge.obj, objAge.age + 1);

		assignLab(virtualLab);

		return virtualLab;
	}
	*/

    public void assignLab(Lab lab) throws TraceException {
        front.put(lab, false);
    }

    public void insertFillers(List<Lab> labs) throws TraceException {
        for (Lab lab : labs) {
            if (!lab.isFull()) {
                long missingFillerAddr = lab.addr + lab.position();
                long missingFillerSize = lab.capacity() - lab.position();

                Lab filler = pendingForeignFillers.get(missingFillerAddr); // just
                // for
                // experimentation
                // create
                // longs
                // not
                // via
                // autoboxing
                // (cache
                // not
                // used
                // anyway
                // for
                // that
                // range)
                // do we have more than one filler per LAB? why do we have an if
                // check then?
                if (filler != null && missingFillerSize <= filler.capacity()) {
                    AddressHO fillerObject = filler.getObject(filler.addr);
                    long addr = lab.tryAllocate(filler.addr, fillerObject);
                    assert addr != Lab.OBJECT_NOT_ASSIGNED;
                    pendingForeignFillers.remove(missingFillerAddr);
                }
            }
        }
    }

    private void errorOnLabNotFound(Lab lab, long fromAddr) throws TraceException {
        if (lab == null) {
            throw new TraceException(String.format("No lab found for obj on GC Move. Addr = %,d\n" + "Transition: %s\n" + "Type: %s",
                                                   fromAddr,
                                                   info.getTransitionType(),
                                                   getType()));
        }

    }

    private void errorOnObjectNotFound(AddressHO obj, long fromAddr) throws TraceException {
        if (obj == null) {
            throw new TraceException(String.format("Obj not found on GC Move. Addr = %,d", fromAddr));
        }
    }

    public void assignLabsIncorrectlyTreatedAsFillers(boolean allowUnusedFillers) throws TraceException {
        front.overwriteWith(pendingForeignFillers);
        front.fillWith(pendingForeignFillers);
        if (!allowUnusedFillers && pendingForeignFillers.size() > 0) {
            throw new TraceException("Unused fillers detected");
        }
    }

    public void validate(boolean allowFragmentation) throws TraceException {
        front.validate(getAddress(), allowFragmentation);
        back.validate(getAddress(), allowFragmentation);
        if (pendingForeignFillers.size() > 0) {
            throw new TraceException("Unused fillers detected");
        }
    }

    @Override
    public String toString() {
        /*
        StringBuilder string = new StringBuilder();
        string.append(toShortString());
        for (Lab lab : front.get().values()) {
            string.append(lab);
        }
        return string.toString();
        */
        return toShortString();
    }

    public String toShortString() {
        return "Space (id " + getId() + "): " + info.toString() + (info.getTransitionType() != SpaceInfo.TransitionType.None ?
                                                                   (" (" + info.getTransitionType() + ")") :
                                                                   " (No transition)");
    }

    public int getLabCount() {
        return getLabCount(true);
    }

    public int getLabCount(boolean current) {
        if (current) {
            switch (info.getTransitionType()) {
                case None:
                case ReplaceAll:
                    return front.getLabCount();
                case Accumulative:
                    return back.getLabCount() + front.getLabCount();
                default:
                    assert false;
                    return 0;
            }
        } else {
            switch (info.getTransitionType()) {
                case ReplaceAll:
                case Accumulative:
                    return back.getLabCount();
                case None:
                    // TODO
                    throw new IllegalStateException("Cannot iterate last heap state while no GC is running! TODO: This currently occurs for G1 GC if new spaces " +
                                                            "are created " +
                                                            "during GC" + ". At the end of GC, " + "heap needs to be traversed, and new spaces throw this exception");
                default:
                    return 0;
            }
        }
    }

    public long getAddress() {
        return info.getAddress();
    }

    public void setAddress(long addr) {
        info.setAddress(addr);
    }

    public long getLength() {
        return info.getLength();
    }

    public void setLength(long length) {
        info.setLength(length);
    }

    public SpaceMode getMode() {
        return info.getMode();
    }

    public void setMode(SpaceMode mode) {
        info.setMode(mode);
    }

    public SpaceType getType() {
        return info.getType();
    }

    public void setType(SpaceType type) {
        if (type != null && info.getType() == null && info.getTransitionType() == SpaceInfo.TransitionType.None) {
            clear();
        }
        info.setType(type);
    }

    public String getName() {
        return info.name;
    }

    public SpaceInfo getInfo() {
        return info;
    }

    @Override
    public Space clone() {
        Space cloned = new Space(info.name, front.clone());
        cloned.setAddress(getAddress());
        cloned.setLength(getLength());
        cloned.setMode(getMode());
        cloned.setType(getType());
        cloned.back = back.clone();
        cloned.info.setTransitionType(info.getTransitionType());
        return cloned;
    }

    public void reduceSize() {
        front.reduceSize();
    }

    public SpaceImpl getFrontLabs() {
        return front;
    }

    public SpaceImpl getBackLabs() {
        return back;
    }

    public SpaceImpl getCurrentLabs() {
        return info.getTransitionType() == None ? front : back;
    }

    public Lab getLab(long addr, boolean inFront) throws TraceException { return inFront ? getLabInFront(addr) : getLabInBack(addr); }

    public Lab getCurrentLab(long addr) throws TraceException {
        return getCurrentLabs().findLab(addr);
    }

    public Lab getLabInFront(long addr) throws TraceException { return front.findLab(addr); }

    public Lab getLabInBack(long addr) throws TraceException { return back.findLab(addr); }

    @Override
    public AddressHO getObjectInFront(long objAddr) throws TraceException {
        return front.findLab(objAddr).getObject(objAddr);
    }

    @Override
    public AddressHO getObjectInBack(long objAddr) throws TraceException {
        return back.findLab(objAddr).getObject(objAddr);
    }

    @Override
    public AddressHO getObject(long objAddr) throws TraceException {
        return getCurrentLab(objAddr).getObject(objAddr);
    }

    public void removeEmptyLabs() {
        front.removeEmptyLabs();
        back.removeEmptyLabs();
    }
}
