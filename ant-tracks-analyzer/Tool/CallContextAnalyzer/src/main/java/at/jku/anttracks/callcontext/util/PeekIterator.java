

package at.jku.anttracks.callcontext.util;

import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * An {@link Iterator} that supports lookahead and lookbehind of one element, respectively.
 *
 * @param <T> The type of elements returned by this iterator.
 * @author Peter Feichtinger
 */
public abstract class PeekIterator<T> implements Iterator<T> {

    /**
     * Implementation of {@link PeekIterator}. Note that the behavior is undefined if the backing iterator is changed while an instance of
     * this class uses it.
     *
     * @param <T> The type of elements returned by this iterator.
     * @author Peter Feichtinger
     */
    public static class PeekIteratorImpl<T> extends PeekIterator<T> {
        private final Iterator<T> mIt;
        private boolean mPeeked;
        private T mNext;
        private boolean mHasPrevious;
        private T mPrevious;

        /**
         * Create a new {@link PeekIterator.PeekIteratorImpl PeekIteratorImpl} wrapping the
         * specified
         * iterator.
         *
         * @param it The iterator to wrap.
         */
        public PeekIteratorImpl(Iterator<T> it) {
            mIt = Objects.requireNonNull(it);
        }

        @Override
        public T peek() {
            if (!mPeeked) {
                mNext = mIt.next();
                mPeeked = true;
            }
            return mNext;
        }

        @Override
        public boolean hasNext() {
            return mPeeked || mIt.hasNext();
        }

        @Override
        public T next() {
            final T result;
            if (mPeeked) {
                result = mNext;
                mPeeked = false;
                mNext = null;
            } else {
                result = mIt.next();
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
        public T previous() {
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

    /**
     * Create a new {@link PeekIterator} from the specified {@link Iterable}. Note that the behavior of the returned iterator is undefined
     * if {@code itr} is changed while the returned iterator is used.
     *
     * @param itr The iterable.
     * @return A {@link PeekIterator} for {@code itr}.
     */
    public static <T> PeekIterator<T> of(Iterable<T> itr) {
        return new PeekIteratorImpl<>(itr.iterator());
    }

    /**
     * Get the next element without advancing the iterator.
     *
     * @return The next element.
     * @throws NoSuchElementException If there are no more elements.
     */
    public abstract T peek();

    /**
     * Determine whether there is a previous element.
     *
     * @return {@code true} if {@link #next()} has been called and {@link #previous()} may be called, {@code false} otherwise.
     */
    public abstract boolean hasPrevious();

    /**
     * Get the previous element.
     *
     * @return The last element returned by {@link #next()}.
     * @throws NoSuchElementException If {@link #next()} has not been called yet.
     */
    public abstract T previous();

    /**
     * {@inheritDoc}
     * <p>
     * This method can not be called to remove a peeked element.
     *
     * @throws UnsupportedOperationException If the wrapped iterator does not support removal.
     * @throws IllegalStateException         If {@link #next()} has not yet been called, or if either {@link #remove()} or
     *                                       {@link #peek()} have already been
     *                                       called
     *                                       after the last call to {@link #next()}
     */
    @Override
    public void remove() {
        Iterator.super.remove();
    }
}
