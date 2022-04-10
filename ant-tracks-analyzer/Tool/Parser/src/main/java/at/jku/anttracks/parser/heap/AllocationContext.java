
package at.jku.anttracks.parser.heap;

import at.jku.anttracks.callcontext.util.PeekIterator;
import at.jku.anttracks.heap.symbols.AllocationSite;
import at.jku.anttracks.heap.symbols.AllocationSite.Location;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.util.AbstractIterator;
import at.jku.anttracks.util.AntRingBuffer;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * Represents the allocation context at one specific point in time and provides iterators for lookup, creation and insertion of extended
 * allocation sites.
 *
 * @author Peter Feichtinger
 */
public class AllocationContext extends PeekIterator<AllocationSite> {

    // We need to have two things for efficient creation and lookup of dynamic allocation sites:
    // + A consistent way of ignoring or using a particular entry in the list of previous allocations for creation and lookup.
    // | The strategies might not be exactly equivalent, since the available information might differ between creation and lookup.
    // | However, they should work in such a way that a valid allocation trace can always be used to create a dynamic allocation
    // | site, if at all possible, but a valid allocation trace might yield a false-negative when used for lookup. There is of
    // | course a performance cost associated with a false-negative, since an unnecessary dynamic allocation site will be created
    // ` in that case, so the rate of false-negatives should be balanced with the cost of lookup and allocation site creation.
    // + Some way of getting information about the allocation trace used for allocation site creation to the place where the
    // | created allocation site is inserted into the lookup tree.
    // | We can't just use the whole trace for insertion in case of null allocation sites (cases where an allocation site could
    // | not be expanded from an allocation trace), since the lookup will fail way too often, leading to frequent attempts in
    // | extending allocation sites where that will be impossible. In case of successfully expanded allocation sites, it's just
    // ` a matter of memory consumption to use the whole trace, because the lookup will succeed early anyway.

    private class LookupIterator extends AbstractIterator<String> {
        protected int idx = 0;

        LookupIterator() {}

        protected String type(AllocationSite site) {
            return mSymbols.types.getById(site.getAllocatedTypeId()).internalName;
        }

        private boolean fillCache(int cacheIdx) {
            int lookupIdx = mLookupCache[mLookupCacheSize - 1];
            do {
                if (lookupIdx + 1 >= mSize) {
                    return false;
                }
                final Location[] trace = get(lookupIdx).getCallSites();
                if (trace[0].getSignature().equals(get(++lookupIdx).getCallSites()[0].getSignature())) {
                    addToCache(lookupIdx);
                } else {
                    int j = 0;
                    do {
                        if (stackContains(get(lookupIdx).getCallSites(), trace[j].getSignature())) {
                            lookupIdx++;
                        } else {
                            j++;
                        }
                    } while (lookupIdx < mSize && j < trace.length);
                    if (lookupIdx < mSize) {
                        addToCache(lookupIdx);
                    } else {
                        return false;
                    }
                }
            } while (cacheIdx >= mLookupCacheSize);
            return true;
        }

        private void addToCache(int lookupIdx) {
            final int length = mLookupCache.length;
            if (mLookupCacheSize == length) {
                mLookupCache = Arrays.copyOf(mLookupCache, length + length / 2);
            }
            mLookupCache[mLookupCacheSize++] = lookupIdx;
        }

        @Override
        public boolean hasNext() {
            if (idx < mLookupCacheSize) {
                return true;
            }
            if (mLookupCacheSize == 0) {
                return false;
            }
            return fillCache(idx);
        }

        @Override
        public String next() {
            if (!hasNext()) {
                throw new NoSuchElementException();
            }
            return type(get(mLookupCache[idx++]));
        }
    }

    private class InsertIterator extends LookupIterator {
        private final boolean isNull;

        InsertIterator(boolean isNull) {
            this.isNull = isNull;
        }

        @Override
        public boolean hasNext() {
            if (!super.hasNext()) {
                return false;
            }
            if (isNull && mLookupCache[idx] > mCreateCount) {
                return false;
            }
            return true;
        }
    }

    Symbols mSymbols;
    AntRingBuffer<AllocationSite> mSource;
    int mSize;

    /**
     * List of indices into {@link #mSource} of the elements used for lookup and insertion.
     */
    int[] mLookupCache = new int[8];
    int mLookupCacheSize;

    int mCreateCount;

    /**
     * Create a new AllocationContext. Note that the instance cannot be used before {@link #assign(AllocationSite, AntRingBuffer, Symbols)} has
     * been called.
     */
    public AllocationContext() {}

    public AllocationContext assign(AllocationSite original, AntRingBuffer<AllocationSite> context, Symbols symbols) {
        mSymbols = Objects.requireNonNull(symbols);
        mSource = context;
        mSize = mSource.size();

        mLookupCacheSize = 0;
        mCreateCount = 0;
        skipOriginal(Objects.requireNonNull(original));
        return this;
    }

    private void skipOriginal(AllocationSite originalSite) {
        final Location[] trace = originalSite.getCallSites();
        int lookupIdx = 0;
        for (int j = 0; lookupIdx < mSize && j < trace.length; ) {
            if (stackContains(get(lookupIdx).getCallSites(), trace[j].getSignature())) {
                lookupIdx++;
            } else {
                j++;
            }
        }
        if (lookupIdx < mSize) {
            assert mLookupCacheSize == 0;
            mLookupCache[0] = lookupIdx;
            mLookupCacheSize = 1;
        }
    }

    AllocationSite get(int idx) {
        return mSource.get(idx);
    }

    /**
     * Get an iterator for allocation site lookup.
     *
     * @return An iterator for looking up an existing allocation site from allocation context.
     */
    public LookupIterator lookupIterator() {
        return new LookupIterator();
    }

    /**
     * Get an iterator for allocation site insertion.
     *
     * @param isNull {@code true} if there is no allocation site to be inserted, {@code false} otherwise.
     * @return An iterator for inserting an extended allocation site (or {@code null}) into a lookup tree.
     */
    public InsertIterator insertionIterator(boolean isNull) {
        return new InsertIterator(isNull);
    }

    /**
     * Get the current returned element count.
     *
     * @return The number elements returned by {@link #next()} so far.
     */
    public int getCount() {
        return mCreateCount;
    }

    /**
     * Consume all allocation sites from this iterator for which the stack trace contains, in order, methods from the specified stack trace.
     *
     * @param trace The stack trace to be consumed.
     * @return The index past the last method for which allocation sites were consumed (if allocation sites were consumed for all elements
     * in {@code trace}, then this will be {@code trace.length}).
     */
    public int skipTrace(Location[] trace) {
        int j = 0;
        while (mCreateCount < mSize && j < trace.length) {
            if (stackContains(get(mCreateCount).getCallSites(), trace[j].getSignature())) {
                mCreateCount++;
            } else {
                j++;
            }
        }
        return j;
    }

    @Override
    public boolean hasNext() {
        return mCreateCount < mSize;
    }

    @Override
    public AllocationSite next() {
        if (mCreateCount >= mSize) {
            throw new NoSuchElementException();
        }
        return get(mCreateCount++);
    }

    @Override
    public AllocationSite peek() {
        if (mCreateCount >= mSize) {
            throw new NoSuchElementException();
        }
        return get(mCreateCount);
    }

    @Override
    public boolean hasPrevious() {
        return mCreateCount > 0;
    }

    @Override
    public AllocationSite previous() {
        if (mCreateCount == 0) {
            throw new NoSuchElementException();
        }
        return get(mCreateCount - 1);
    }

    /**
     * Determine whether the specified call stack contains an entry for the specified method.
     *
     * @param stack  The call stack to search.
     * @param method The method to search for.
     * @return {@code true} if {@code stack} contains a {@link Location} with method {@code method}.
     */
    static boolean stackContains(Location[] stack, String method) {
        for (Location l : stack) {
            if (l.getSignature().equals(method)) {
                return true;
            }
        }
        return false;
    }
}
