

package at.jku.anttracks.callcontext.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.function.Function;

/**
 * A wrapper around a {@link Iterator} that maps elements using a specified mapping function and supports lookahead and lookbehind of one
 * element, respectively. Note that the behavior is undefined if the backing iterator is changed while the {@code PeekIteratorMapper}
 * wrapping it is used.
 *
 * @param <T>    The type of element returned by the wrapped iterator.
 * @param <R>    The type of element returned by this iterator (which is the result type of the mapping function).
 * @param <Iter> The type of the wrapped iterator.
 * @author Peter Feichtinger
 * @see IteratorMapper
 * @see PeekIterator
 */
public class PeekIteratorMapper<T, R, Iter extends Iterator<T>> extends PeekIterator<R> {

    private final Iter mIt;
    private final boolean mSupportsLookahead;
    private final Function<? super T, ? extends R> mMapper;
    private boolean mPeeked;
    private R mNext;
    private boolean mHasPrevious;
    private R mPrevious;

    /**
     * Create a new {@link PeekIteratorMapper} from the specified {@link Iterable}.
     *
     * @param it     The iterable.
     * @param mapper The mapping function used to map elements returned by {@code it}'s iterator to the return type.
     * @return A {@link PeekIteratorMapper} for {@code it} using {@code mapper}.
     */
    public static <T, R> PeekIteratorMapper<T, R, Iterator<T>> of(Iterable<T> it, Function<? super T, ? extends R> mapper) {
        return new PeekIteratorMapper<>(it.iterator(), mapper);
    }

    /**
     * Create a new {@link PeekIteratorMapper} for the specified {@link Iterator} using the specified mapping function.
     *
     * @param it     The peek iterator to wrap. When {@code it} implements {@link PeekIterator}, this class uses {@code it}'s
     *               {@link PeekIterator#peek() peek()} method for lookahead.
     * @param mapper The mapping function used to map elements returned by {@code it} to the return type.
     */
    public PeekIteratorMapper(Iter it, Function<? super T, ? extends R> mapper) {
        mIt = Objects.requireNonNull(it);
        mSupportsLookahead = (mIt instanceof PeekIterator);
        mMapper = Objects.requireNonNull(mapper);
    }

    /**
     * Get the iterator wrapped by this iterator.
     *
     * @return The iterator wrapped by this iterator.
     */
    public Iter getIterator() {
        return mIt;
    }

    @Override
    public R peek() {
        if (!mPeeked) {
            mNext = mMapper.apply(mSupportsLookahead ? ((PeekIterator<T>) mIt).peek() : mIt.next());
            mPeeked = true;
        }
        return mNext;
    }

    @Override
    public boolean hasNext() {
        return mPeeked || mIt.hasNext();
    }

    @Override
    public R next() {
        final R result;
        if (mPeeked) {
            result = mNext;
            mPeeked = false;
            mNext = null;
            if (mSupportsLookahead) {
                mIt.next();
            }
        } else {
            result = mMapper.apply(mIt.next());
        }
        mHasPrevious = true;
        mPrevious = result;
        return result;
    }

    @Override
    public boolean hasPrevious() {
        return mHasPrevious;
    }

    @Override
    public R previous() {
        if (!mHasPrevious) {
            throw new NoSuchElementException();
        }
        return mPrevious;
    }

    @Override
    public void remove() {
        if (mPeeked) {
            throw new IllegalStateException("Cannot remove, already peeked an element.");
        }
        mIt.remove();
    }
}
