

package at.jku.anttracks.callcontext.util;

import java.util.Iterator;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * A wrapper around an {@link Iterator} that maps elements using a specified mapping function.
 *
 * @param <T>    The type of element returned by the wrapped iterator.
 * @param <R>    The type of element returned by this iterator (which is the result type of the mapping function).
 * @param <Iter> The type of the wrapped iterator.
 * @author Peter Feichtinger
 */
public class IteratorMapper<T, R, Iter extends Iterator<T>> implements Iterator<R> {

    private final Iter mIt;
    private final Function<? super T, ? extends R> mMapper;

    /**
     * Create a new {@link IteratorMapper} for the specified iterator using the specified mapping function.
     *
     * @param it     The iterator to wrap.
     * @param mapper The mapping function used to map elements returned by {@code it} to the return type.
     */
    public IteratorMapper(Iter it, Function<? super T, ? extends R> mapper) {
        mIt = Objects.requireNonNull(it);
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
    public boolean hasNext() {
        return mIt.hasNext();
    }

    @Override
    public R next() {
        return mMapper.apply(mIt.next());
    }

    @Override
    public void remove() {
        mIt.remove();
    }

    @Override
    public void forEachRemaining(Consumer<? super R> action) {
        mIt.forEachRemaining(t -> action.accept(mMapper.apply(t)));
    }
}
