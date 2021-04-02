

package at.jku.anttracks.callcontext.util;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Stream;

/**
 * Represents an immutable list backed by an array. As opposed to {@linkplain Collections#unmodifiableList(List) unmodifiable List views},
 * this type is truly immutable and will never change.
 *
 * @param <E> The type of elements stored in the list.
 * @author Peter Feichtinger
 */
public class ImmutableList<E> extends AbstractList<E> implements RandomAccess {

    // Factory Methods ====================================================================================================================

    /**
     * Create an empty {@link ImmutableList}.
     *
     * @return An empty {@code ImmutableList} instance.
     */
    @SuppressWarnings("unchecked")
    public static <T> ImmutableList<T> empty() {
        // Safe unchecked cast: can't put anything into an ImmutableList
        return (ImmutableList<T>) EMPTY;
    }

    /**
     * Create an empty {@link ImmutableList}.
     *
     * @return An empty {@code ImmutableList} instance.
     */
    public static <T> ImmutableList<T> of() {
        return empty();
    }

    /**
     * Create an {@link ImmutableList} containing only the specified element.
     *
     * @param e1 The single element.
     * @return An {@link ImmutableList} which contains the specified element.
     */
    public static <T> ImmutableList<T> of(T e1) {
        return new ImmutableList<>(new Object[]{e1});
    }

    /**
     * Create an {@link ImmutableList} containing the specified elements.
     *
     * @param e1 The first element.
     * @param e2 The second element.
     * @return An {@link ImmutableList} which contains the specified elements.
     */
    public static <T> ImmutableList<T> of(T e1, T e2) {
        return new ImmutableList<>(new Object[]{e1, e2});
    }

    /**
     * Create an {@link ImmutableList} containing the specified elements.
     *
     * @param e1 The first element.
     * @param e2 The second element.
     * @param e3 The third element.
     * @return An {@link ImmutableList} which contains the specified elements.
     */
    public static <T> ImmutableList<T> of(T e1, T e2, T e3) {
        return new ImmutableList<>(new Object[]{e1, e2, e3});
    }

    /**
     * Create an {@link ImmutableList} containing the specified elements.
     *
     * @param e1 The first element.
     * @param e2 The second element.
     * @param e3 The third element.
     * @param e4 The fourth element.
     * @return An {@link ImmutableList} which contains the specified elements.
     */
    public static <T> ImmutableList<T> of(T e1, T e2, T e3, T e4) {
        return new ImmutableList<>(new Object[]{e1, e2, e3, e4});
    }

    /**
     * Create an {@link ImmutableList} containing the specified elements.
     *
     * @param e1 The first element.
     * @param e2 The second element.
     * @param e3 The third element.
     * @param e4 The fourth element.
     * @param e5 The fifth element.
     * @return An {@link ImmutableList} which contains the specified elements.
     */
    public static <T> ImmutableList<T> of(T e1, T e2, T e3, T e4, T e5) {
        return new ImmutableList<>(new Object[]{e1, e2, e3, e4, e5});
    }

    /**
     * Create an {@link ImmutableList} containing the specified elements.
     *
     * @param e1       The first element.
     * @param e2       The second element.
     * @param e3       The third element.
     * @param e4       The fourth element.
     * @param e5       The fifth element.
     * @param elements The remaining elements of the list.
     * @return An {@link ImmutableList} which contains the specified elements.
     */
    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <T> ImmutableList<T> of(T e1, T e2, T e3, T e4, T e5, T... elements) {
        final Object[] arr = new Object[5 + elements.length];
        arr[0] = e1;
        arr[1] = e2;
        arr[2] = e3;
        arr[3] = e4;
        arr[4] = e5;
        System.arraycopy(elements, 0, arr, 5, elements.length);
        return new ImmutableList<>(arr);
    }

    /**
     * Create an {@link ImmutableList} from the specified collection.
     *
     * @param source The collection to copy.
     * @return An {@code ImmutableList} with all elements from {@code source} in iteration order.
     */
    @SuppressWarnings("unchecked")
    public static <T> ImmutableList<T> of(Collection<? extends T> source) {
        if (source instanceof ImmutableList) {
            // Safe unchecked cast: can't put anything into an ImmutableList
            return (ImmutableList<T>) source;
        }
        if (source.isEmpty()) {
            return empty();
        }
        return new ImmutableList<>(source.toArray());
    }

    /**
     * Create an {@link ImmutableList} from the specified stream. This is a terminal operation on the stream.
     *
     * @param stream The stream to collect.
     * @return An immutable list containing all the elements returned from {@code stream}.
     */
    public static <T> ImmutableList<T> of(Stream<? extends T> stream) {
        return new ImmutableList<>(stream.toArray());
    }

    /**
     * Create a {@link Collector} that accumulates the input elements into an {@link ImmutableList}.
     *
     * @return A {@code Collector} which collects all the input elements into an {@code ImmutableList}, in encounter order.
     */
    public static <T> Collector<T, ?, ImmutableList<T>> collector() {
        return Collector.of(ArrayList::new, List::add, (BinaryOperator<List<T>>) (left, right) -> {
            left.addAll(right);
            return left;
        }, ImmutableList::of);
    }

    // Instance Members ===================================================================================================================

    private static final ImmutableList<?> EMPTY = new ImmutableList<>(new Object[0]);

    final E[] mStore;
    final int mStart;
    final int mEnd;

    /**
     * Create an {@link ImmutableList} backed by the specified array.
     *
     * @param store The backing array.
     */
    @SuppressWarnings("unchecked")
    private ImmutableList(Object[] store) {
        mStore = (E[]) store;
        mStart = 0;
        mEnd = store.length;
    }

    /**
     * Create an {@link ImmutableList} backed by the specified portion of the specified array.
     *
     * @param store The backing array.
     * @param start The start index, inclusive.
     * @param end   The end index, exclusive.
     */
    @SuppressWarnings("unchecked")
    private ImmutableList(Object[] store, int start, int end) {
        assert start > 0 && end <= store.length && start < end;
        mStore = (E[]) store;
        mStart = start;
        mEnd = end;
    }

    /**
     * Get whether this list may hold references to objects not accessible via its public methods. This is true when this list was created
     * by the {@link #subList(int, int)} method, which does not copy the backing array.
     *
     * @return {@code true} if this is a partial view of a backing array, {@code false} otherwise.
     * @see #copy()
     */
    public boolean isPartialView() {
        return mStart != 0 || mEnd != mStore.length;
    }

    /**
     * Create a copy of this list. This method is useful if {@link #isPartialView()} returns {@code true}, to get a copy of this list which
     * does not hold any references to objects not accessible via public methods.
     *
     * @return A copy of this list which does not hold references to any elements not accessible via public methods. Note that the returned
     * instance is not necessarily different from this instance (e.g. if this instance is not a partial view).
     */
    public ImmutableList<E> copy() {
        if (isPartialView()) {
            return new ImmutableList<>(Arrays.copyOfRange(mStore, mStart, mEnd));
        }
        return this;
    }

    /**
     * Determine whether the elements in this list are sorted according to the order induced by the specified {@link Comparator}.
     * <p>
     * All elements in this list must be <i>mutually comparable</i> using the specified comparator (that is, {@code c.compare(e1, e2)} must
     * not throw a {@code ClassCastException} for any elements {@code e1} and {@code e2} in the list).
     * <p>
     * If the specified comparator is {@code null} then all elements in this list must implement the {@link Comparable} interface and the
     * elements' {@linkplain Comparable natural ordering} should be used. This also means that there may not be any {@code null} elements in
     * this list.
     *
     * @param c The {@code Comparator} used to compare list elements. A {@code null} value indicates that the elements'
     *          {@linkplain Comparable natural ordering} should be used.
     * @return {@code true} if the elements in this list are sorted. The return value is also {@code true} if this list is empty or contains
     * only one element.
     * @throws ClassCastException   If this list contains elements that are not mutually comparable using the specified comparator.
     * @throws NullPointerException If {@code c} is {@code null} and any element in this list is {@code null}.
     */
    public boolean isSorted(Comparator<? super E> c) {
        if (size() < 2) {
            return true;
        }
        if (c == null) {
            for (int j = mStart, end = mEnd - 1; j != end; j++) {
                @SuppressWarnings("unchecked")
                final Comparable<E> a = (Comparable<E>) mStore[j];
                if (a.compareTo(mStore[j + 1]) > 0) {
                    return false;
                }
            }
            if (mStore[mEnd - 1] == null) {
                throw new NullPointerException();
            }
            return true;
        }
        for (int j = mStart, end = mEnd - 1; j != end; j++) {
            if (c.compare(mStore[j], mStore[j + 1]) > 0) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get an immutable list with the same elements as this list, sorted according to the order induced by the specified {@link Comparator}.
     * <p>
     * All elements in this list must be <i>mutually comparable</i> using the specified comparator (that is, {@code c.compare(e1, e2)} must
     * not throw a {@code ClassCastException} for any elements {@code e1} and {@code e2} in the list).
     * <p>
     * If the specified comparator is {@code null} then all elements in this list must implement the {@link Comparable} interface and the
     * elements' {@linkplain Comparable natural ordering} should be used. This also means that there may not be any {@code null} elements in
     * this list.
     *
     * @param c The {@code Comparator} used to compare list elements. A {@code null} value indicates that the elements'
     *          {@linkplain Comparable natural ordering} should be used.
     * @return A new {@link ImmutableList} list instance with the elements in sorted order, or this instance if the elements are already in
     * a sorted order.
     * @throws ClassCastException   If this list contains elements that are not mutually comparable using the specified comparator.
     * @throws NullPointerException If {@code c} is {@code null} and any element in this list is {@code null}.
     */
    @SuppressWarnings("unchecked")
    public ImmutableList<E> sorted(Comparator<? super E> c) {
        if (isSorted(c)) {
            return this;
        }
        final Object[] tmp = toArray();
        Arrays.sort(tmp, (Comparator<Object>) c);
        return new ImmutableList<>(tmp);
    }

    // List Members =======================================================================================================================

    @Override
    public int size() {
        return mEnd - mStart;
    }

    @Override
    public boolean contains(Object o) {
        for (int j = mStart; j < mEnd; j++) {
            if (Objects.equals(mStore[j], o)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Iterator<E> iterator() {
        return listIterator();
    }

    @Override
    public Object[] toArray() {
        return Arrays.copyOfRange(mStore, mStart, mEnd, Object[].class);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T[] toArray(T[] a) {
        final int size = size();
        if (a.length < size) {
            return (T[]) Arrays.copyOfRange(mStore, mStart, mEnd, a.getClass());
        }
        System.arraycopy(mStore, mStart, a, 0, size);
        if (a.length > size) {
            a[size] = null;
        }
        return a;
    }

    @Override
    public E get(int index) {
        if (index < 0 || index >= size()) {
            throw new IndexOutOfBoundsException();
        }
        return mStore[mStart + index];
    }

    @Override
    public int indexOf(Object o) {
        for (int j = mStart; j < mEnd; j++) {
            if (Objects.equals(mStore[j], o)) {
                return j - mStart;
            }
        }
        return -1;
    }

    @Override
    public int lastIndexOf(Object o) {
        for (int j = mEnd - 1; j >= mStart; j--) {
            if (Objects.equals(mStore[j], o)) {
                return j - mStart;
            }
        }
        return -1;
    }

    @Override
    public ListIterator<E> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<E> listIterator(int index) {
        return new ListIterator<E>() {
            int idx = index + mStart;

            @Override
            public boolean hasNext() {
                return idx < mEnd;
            }

            @Override
            public E next() {
                if (!hasNext()) {
                    throw new NoSuchElementException();
                }
                return mStore[idx++];
            }

            @Override
            public boolean hasPrevious() {
                return idx > mStart;
            }

            @Override
            public E previous() {
                if (!hasPrevious()) {
                    throw new NoSuchElementException();
                }
                return mStore[--idx];
            }

            @Override
            public int nextIndex() {
                return idx - mStart;
            }

            @Override
            public int previousIndex() {
                return nextIndex() - 1;
            }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void set(E e) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void add(E e) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public List<E> subList(int fromIndex, int toIndex) {
        if (fromIndex == 0 && toIndex == size()) {
            return this;
        }
        if (fromIndex < 0 || toIndex > size() || fromIndex > toIndex) {
            throw new IndexOutOfBoundsException();
        }
        if (fromIndex == toIndex) {
            return empty();
        }
        return new ImmutableList<>(mStore, fromIndex + mStart, toIndex + mStart);
    }

    @Override
    public Spliterator<E> spliterator() {
        return Arrays.spliterator(mStore, mStart, mEnd);
    }

    @Override
    public Stream<E> stream() {
        return Arrays.stream(mStore, mStart, mEnd);
    }

    // Unsupported Operations =============================================================================================================

    @Override
    public boolean add(E e) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean remove(Object o) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean addAll(int index, Collection<? extends E> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        throw new UnsupportedOperationException();
    }

    @Override
    public E set(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void add(int index, E element) {
        throw new UnsupportedOperationException();
    }

    @Override
    public E remove(int index) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeIf(Predicate<? super E> filter) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void replaceAll(UnaryOperator<E> operator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void sort(Comparator<? super E> c) {
        throw new UnsupportedOperationException();
    }
}
