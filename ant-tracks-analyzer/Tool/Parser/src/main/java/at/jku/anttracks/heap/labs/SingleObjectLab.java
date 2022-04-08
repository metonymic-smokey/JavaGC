
package at.jku.anttracks.heap.labs;

import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.ObjectVisitor;
import at.jku.anttracks.heap.space.Space;
import at.jku.anttracks.heap.space.SpaceInfo;
import at.jku.anttracks.parser.heap.pointer.PtrUpdateVisitor;
import at.jku.anttracks.util.TraceException;

import java.util.Collections;
import java.util.List;

public final class SingleObjectLab extends Lab {

    private AddressHO object;

    public SingleObjectLab(String thread, Kind virtualOrFiller, long addr, AddressHO obj) {
        super(thread, virtualOrFiller, addr);
        this.object = obj;
        this.object.setTag(UNSET_FORWARDING_ADDR);
    }

    @Override
    public int capacity() {
        return object.getSize();
    }

    @Override
    public int position() {
        return object.getSize();
    }

    @Override
    public int getObjectCount() {
        return 1;
    }

    @Override
    public void iterate(DetailedHeap heap,
                        SpaceInfo space,
                        List<Filter> filter,
                        ObjectVisitor visitor,
                        ObjectVisitor.Settings visitorSettings) {
        boolean accept = true;
        if (filter != null) {
            for (Filter f : filter) {
                try {
                    if (!f.classify(object,
                                    addr,
                                    object.getInfo(),
                                    space,
                                    object.getType(),
                                    object.getSize(),
                                    object.isArray(),
                                    object.getArrayLength(),
                                    object.getSite(),
                                    new long[0], // TODO Removed from-pointers at the moment, probably reintroduce them later
                                    new long[0], // TODO Pointers are not stored as pointer array at the moment, think about
                                    // something here
                                    object.getEventType(),
                                    visitorSettings.getRootPointerInfoNeeded() ? heap.rootPtrs.get(addr) : Collections.emptyList(),
                                    -1, // TODO Age
                                    object.getInfo().thread,
                                    heap.getExternalThreadName(object.getInfo().thread))) {
                        accept = false;
                        break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    accept = false;
                    break;
                }
            }
        }
        if (accept) {
            visitor.visit(addr,
                          object,
                          space,
                          visitorSettings.getRootPointerInfoNeeded() ? heap.rootPtrs.getOrDefault(addr, Collections.emptyList()) : Collections.emptyList());
        }
    }

    @Override
    public long tryAllocate(long objAddr, AddressHO obj) throws TraceException {
        throw new TraceException("Trying to allocate in SingleObjectLab, but object has already been set during Lab construction (see constructor)");
        //return objAddr == addr ? addr : OBJECT_NOT_ASSIGNED;
    }

    @Override
    public AddressHO getObject(long objAddr) throws TraceException {
        return object;
    }

    @Override
    public AddressHO getObjectAtIndex(int index) throws TraceException {
        if (index > 0) {
            throw new TraceException("SingleObjectLab may only contain a single object");
        }
        return object;
    }

    @Override
    public void resetForwardingAddresses() {
        object.setTag(UNSET_FORWARDING_ADDR);
    }

	/*
    @Override
	public void setAge(long objAddr, int age) throws TraceException {
		this.age = age + 1;
	}
	*/

	/*
    @Override
	public ObjectInfoAge getObjectAge(long objAddr) throws TraceException {
		ObjectInfo obj = getObject(objAddr);
		return new ObjectInfoAge(obj, age);
	}
	*/

    @Override
    public Lab clone() {
        return new SingleObjectLab(thread, kind, addr, object);
    }

    @Override
    public void reduceSize() {
        return;
    }

    @Override
    public int getAddressIndex(long objAddr) throws TraceException {
        if (objAddr != addr) {
            throw new TraceException(String.format("Given address %,d does not point to the object of this single object lab!", objAddr));
        }
        return 0;
    }

    public MultiObjectLab toMultiObjectLab() {
        MultiObjectLab lab = new MultiObjectLab(thread, Kind.REGION_VIRTUAL, addr, capacity());
        try {
            lab.tryAllocate(addr, object);
        } catch (TraceException e) {
            e.printStackTrace();
        }
        return lab;
    }

    @Override
    public void iterateUpdatePointer(PtrUpdateVisitor iterator, Space space) {
        iterator.visit(space, this, addr, object);
    }
}
