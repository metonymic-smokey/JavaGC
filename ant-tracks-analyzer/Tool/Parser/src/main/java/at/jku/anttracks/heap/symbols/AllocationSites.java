
package at.jku.anttracks.heap.symbols;

import at.jku.anttracks.heap.symbols.AllocationSite.Location;
import at.jku.anttracks.util.Consts;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class AllocationSites implements Iterable<AllocationSite> {

    private static final int ATOMIC_COUNT_INIT = -1;
    private final int THRESHOLD = 0x800000;

    private final AllocationSite unkonwnAllocationSite;
    private AllocationSite[] smallAllocationSites;
    private AllocationSite[] bigAllocationSites;
    @SuppressWarnings("unused")
    private int firstSmallCustomId = -1;
    private final AtomicInteger prevSmallCustomId;
    @SuppressWarnings("unused")
    private int firstBigCustomId = -1;
    private final AtomicInteger prevBigCustomId;

    public AllocationSites() {
        unkonwnAllocationSite = new AllocationSite(AllocationSite.ALLOCATION_SITE_IDENTIFIER_UNKNOWN,
                                                   new Location[]{new Location("Unknown Allocation Site", -1)},
                                                   AllocatedType.ALLOCATED_TYPE_IDENTIFIER_UNKNOWN);
        smallAllocationSites = new AllocationSite[Consts.START_ARRAY_LEN];
        bigAllocationSites = new AllocationSite[Consts.START_ARRAY_LEN];
        prevSmallCustomId = new AtomicInteger(ATOMIC_COUNT_INIT);
        prevBigCustomId = new AtomicInteger(ATOMIC_COUNT_INIT);
    }

    public synchronized void add(AllocationSite allocationSite) {
        if (isBigAllocationSite(allocationSite.getId())) {
            final int id = allocationSite.getId() - THRESHOLD;
            if (id >= bigAllocationSites.length) {
                int newLength = bigAllocationSites.length;
                do {
                    newLength = (int) (newLength * 1.1);
                } while (id >= newLength);
                bigAllocationSites = Arrays.copyOf(bigAllocationSites, newLength);
            }
            bigAllocationSites[id] = allocationSite;
        } else {
            if (allocationSite.getId() >= smallAllocationSites.length) {
                int newLength = smallAllocationSites.length;
                do {
                    newLength = (int) (newLength * 1.1);
                } while (allocationSite.getId() >= newLength);
                smallAllocationSites = Arrays.copyOf(smallAllocationSites, newLength);
            }
            smallAllocationSites[allocationSite.getId()] = allocationSite;
        }
    }

    public AllocationSite getById(int id) {
        if (id == AllocationSite.ALLOCATION_SITE_IDENTIFIER_UNKNOWN) {
            return unkonwnAllocationSite;
        }
        if (isBigAllocationSite(id)) {
            return bigAllocationSites[id - THRESHOLD];
        }
        return smallAllocationSites[id];
    }

    /**
     * Add and get a copy of the specified {@link AllocationSite} with a new ID and the specified call sites.
     *
     * @param site      The allocation site to copy.
     * @param callSites The new call sites.
     * @return A copy of the allocation site with new ID and call sites, the new allocation site is automatically added to this collection.
     */
    public synchronized AllocationSite copy(AllocationSite site, Location[] callSites) {
        final int newId = getFreeId(isBigAllocationSite(site.getId()));
        final AllocationSite newSite = site.extendDynamic(newId, callSites);
        add(newSite);
        return newSite;
    }

    public int getBiggestId() {
        return THRESHOLD + bigAllocationSites.length;
    }

    // The atomic counts are not really needed any more, because this method is only called from the synchronized `copy` method, but I'll
    // leave them here for the time being.
    private int getFreeId(boolean bigAllocationSite) {
        if (bigAllocationSite) {
            if (prevBigCustomId.get() == ATOMIC_COUNT_INIT) {
                int idx = bigAllocationSites.length - 1;
                while (idx >= 0 && bigAllocationSites[idx] == null) {
                    idx--;
                }
                if (prevBigCustomId.compareAndSet(ATOMIC_COUNT_INIT, idx + THRESHOLD)) {
                    firstBigCustomId = idx + THRESHOLD + 1;
                }
            }
            return prevBigCustomId.incrementAndGet();
        }
        if (prevSmallCustomId.get() == ATOMIC_COUNT_INIT) {
            int idx = smallAllocationSites.length - 1;
            while (idx >= 0 && smallAllocationSites[idx] == null) {
                idx--;
            }
            if (prevSmallCustomId.compareAndSet(ATOMIC_COUNT_INIT, idx)) {
                firstSmallCustomId = idx + 1;
            }
        }
        return prevSmallCustomId.incrementAndGet();
    }

    private boolean isBigAllocationSite(int allocationSiteId) {
        return allocationSiteId >= THRESHOLD;
    }

    @Override
    public Iterator<AllocationSite> iterator() {
        return new Iterator<AllocationSite>() {

            AllocationSite[] target = smallAllocationSites;
            int i = 0;

            @Override
            public AllocationSite next() {
                return target[i++];
            }

            @Override
            public boolean hasNext() {
                if (i < target.length) {
                    return true;
                }
                if (target == smallAllocationSites) {
                    target = bigAllocationSites;
                    i = 0;
                    return hasNext();
                }
                return false;
            }
        };
    }

    // This returns the real number of non-null allocation sites
    // Do not use this method for iterating the allocation sites, there may be indices which are not assigned.
    // Use the iterator for visiting each allocation site
    public int count() {
        return (int) Stream.concat(Arrays.stream(smallAllocationSites), Arrays.stream(bigAllocationSites)).filter(Objects::nonNull).count();
    }

    public void complete() {
        Arrays.stream(unkonwnAllocationSite.getCallSites()).forEach(Location::resolve);
        Arrays.stream(smallAllocationSites).filter(Objects::nonNull).flatMap(it -> Arrays.stream(it.getCallSites())).forEach(Location::resolve);
        Arrays.stream(bigAllocationSites).filter(Objects::nonNull).flatMap(it -> Arrays.stream(it.getCallSites())).forEach(Location::resolve);
    }
}
