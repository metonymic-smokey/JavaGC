
package at.jku.anttracks.util;

import java.lang.reflect.Array;
import java.util.*;
import java.util.function.Consumer;

/**
 * Represents a ring buffer, that is a stack that automatically removes old elements when new ones are added once a specified size is
 * reached.
 *
 * @param <E> The type of element in the ring buffer.
 * @author Peter Feichtinger
 */
public class AntRingBuffer<E> extends AbstractCollection<E> {

    /**
     * {@link Iterator} over the elements in this {@link AntRingBuffer} in reverse insertion order (the natural order for this data structure).
     *
     * @author Peter Feichtinger
     */
    private class Iter extends AbstractIterator<E> {
        /**
         * Original head for detecting concurrent modification.
         */
        private final int head = mHead;
        /**
         * Index past the last element that will be returned.
         */
        private final int fence = (mHead - 1) & mMask;
        /**
         * The index of the next element that will be returned.
         */
        private int cursor = (mTail - 1) & mMask;

        public Iter() {}

        @Override
        public boolean hasNext() {
            return cursor != fence;
        }

        @Override
        public E next() {
            if (cursor == fence) {
                throw new NoSuchElementException();
            }
            if (mHead != head) {
                throw new ConcurrentModificationException();
            }
            @SuppressWarnings("unchecked")
            final E result = (E) mStore[cursor--];
            cursor &= mMask;
            return result;
        }

        @Override
        public void forEachRemaining(Consumer<? super E> consumer) {
            Objects.requireNonNull(consumer);
            if (mHead != head) {
                throw new ConcurrentModificationException();
            }
            int c = cursor;
            cursor = fence;
            while (c != fence) {
                @SuppressWarnings("unchecked")
                final E e = (E) mStore[c--];
                c &= mMask;
                consumer.accept(e);
            }
        }
    }

    /**
     * {@link Spliterator} over the elements in this {@link AntRingBuffer} in reverse insertion order (the natural order for this data
     * structure). This spliterator is late-binding and fail-fast.
     *
     * @author Peter Feichtinger
     */
    private class Splitr implements Spliterator<E> {
        /**
         * Original head for detecting concurrent modification.
         */
        private int head;
        /**
         * Index past the last element that will be returned, -1 if unbound.
         */
        private int fence;
        /**
         * The index of the next element that will be returned.
         */
        private int cursor;

        /**
         * Create an unbound {@linkplain Splitr spliterator}.
         */
        public Splitr() {
            fence = -1;
        }

        private Splitr(int head, int cursor, int fence) {
            this.head = head;
            this.cursor = cursor;
            this.fence = fence;
        }

        /**
         * Bind to the source or check for concurrent modification if already bound.
         */
        private void bind() {
            if (fence < 0) {
                head = mHead;
                fence = (head - 1) & mMask;
                cursor = (mTail - 1) & mMask;
            } else if (mHead != head) {
                throw new ConcurrentModificationException();
            }
        }

        @Override
        public boolean tryAdvance(Consumer<? super E> consumer) {
            Objects.requireNonNull(consumer);
            bind();

            if (cursor != fence) {
                @SuppressWarnings("unchecked")
                final E result = (E) mStore[cursor--];
                cursor &= mMask;
                consumer.accept(result);
                return true;
            }
            return false;
        }

        @Override
        public Spliterator<E> trySplit() {
            bind();
            // Only split when there are at least two elements left
            if (cursor != fence && ((cursor - 1) & mMask) != fence) {
                final int split;
                if (cursor > fence) {
                    split = ((cursor + fence) / 2) & mMask;
                } else {
                    split = ((cursor + fence + mSize) / 2) & mMask;
                }
                final int newStart = cursor;
                cursor = split;
                return AntRingBuffer.this.new Splitr(head, newStart, split);
            }
            return null;
        }

        @Override
        public long estimateSize() {
            bind();
            return (cursor - fence) & mMask;
        }

        @Override
        public int characteristics() {
            return Spliterator.ORDERED | Spliterator.SIZED | Spliterator.SUBSIZED;
        }

        @Override
        public void forEachRemaining(Consumer<? super E> consumer) {
            Objects.requireNonNull(consumer);
            bind();

            int c = cursor;
            cursor = fence;
            while (c != fence) {
                @SuppressWarnings("unchecked")
                final E e = (E) mStore[c--];
                c &= mMask;
                consumer.accept(e);
            }
        }
    }

    final int mSize;
    final int mMask;
    final Object[] mStore;
    int mHead;
    int mTail;

    /**
     * Create a new {@link AntRingBuffer} with the specified size.
     *
     * @param size The maximum number of elements the ring buffer will contain.
     * @throws IllegalArgumentException If {@code size} &lt; 1 or {code size} &geq; 2<sup>30</sup>.
     */
    public AntRingBuffer(int size) {
        if (size < 1 || size >= (1 << 30)) {
            throw new IllegalArgumentException();
        }
        mSize = size;
        mStore = new Object[nextPower2(size + 1)];
        mMask = mStore.length - 1;
    }

    /**
     * Get the capacity of this ring buffer.
     *
     * @return The maximum number of elements this ring buffer will hold before evicting old ones.
     */
    public int capacity() {
        return mSize;
    }

    @Override
    public int size() {
        return (mTail - mHead) & mMask;
    }

    @Override
    public boolean isEmpty() {
        return mHead == mTail;
    }

    @Override
    public boolean contains(Object o) {
        for (int j = mHead; mHead != mTail; j = (j + 1) & mMask) {
            if (Objects.equals(o, mStore[j])) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Object[] toArray() {
        return copyToArray(new Object[size()]);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        final int size = size();
        if (a.length < size) {
            return copyToArray((T[]) Array.newInstance(a.getClass().getComponentType(), size));
        }
        copyToArray(a);
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    @SuppressWarnings("unchecked")
    private <T> T[] copyToArray(T[] dest) {
        assert dest.length >= size();
        final int end = (mHead - 1) & mMask;
        for (int j = (mTail - 1) & mMask, k = 0; j != end; j = (j - 1) & mMask, k++) {
            dest[k] = (T) mStore[j];
        }
        return dest;
    }

    @Override
    public boolean add(E e) {
        if (size() == mSize) {
            mStore[mHead++] = null;
            mHead &= mMask;
        }
        mStore[mTail++] = e;
        mTail &= mMask;
        return true;
    }

    /**
     * Get the element at the specified index.
     *
     * @param index The index, 0 for the last element added.
     * @return The element at the specified index.
     * @throws IndexOutOfBoundsException If {@code index} &lt; 0 or {@code index} &geq; {@link #size()}.
     */
    @SuppressWarnings("unchecked")
    public E get(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException();
        }
        return (E) mStore[(mTail - 1 - index) & mMask];
    }

    /**
     * Not supported.
     *
     * @throws UnsupportedOperationException Always.
     */
    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        for (Object o : c) {
            if (!contains(o)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Not supported.
     *
     * @throws UnsupportedOperationException Always.
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    /**
     * Not supported.
     *
     * @throws UnsupportedOperationException Always.
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        if (mTail < mHead) {
            Arrays.fill(mStore, mHead, mStore.length, null);
            Arrays.fill(mStore, 0, mTail, null);
        } else {
            Arrays.fill(mStore, mHead, mTail, null);
        }
        mHead = mTail = 0;
    }

    /**
     * Returns an iterator over the elements in this collection. The order of the elements is from the newest element to the oldest.
     */
    @Override
    public AbstractIterator<E> iterator() {
        return new Iter();
    }

    /**
     * Get a {@link Spliterator} over the elements in this ring buffer. See {@link Collection#spliterator()} for details.
     * <p>
     * The returned spliterator is late-binding and fail-fast. It will report characteristics {@link Spliterator#SIZED SIZED},
     * {@link Spliterator#SUBSIZED SUBSIZED} and {@link Spliterator#ORDERED ORDERED}. The order of the elements is from the newest element
     * to the oldest.
     *
     * @return A late-binding and fail-fast spliterator over the elements in this ring buffer.
     */
    @Override
    public Spliterator<E> spliterator() {
        return new Splitr();
    }

    /**
     * Find the next power of 2 greater than or equal to the specified value.
     *
     * @param value The value.
     * @return A power of 2 that is equal to {@code value} or larger, or 2<sup>30</sup> if {@code value} is larger than that.
     */
    private static int nextPower2(int value) {
        int result = value;
        result |= (result >>> 1);
        result |= (result >>> 2);
        result |= (result >>> 4);
        result |= (result >>> 8);
        result |= (result >>> 16);
        result++;
        if (result < 0) {
            return result >>> 1;
        }
        return result;
    }
}
