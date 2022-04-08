
package at.jku.anttracks.util;

public class LimitingIterator<T> extends AbstractIterator<T> {

    private final AbstractIterator<T> iterator;
    private final int max;
    private int n;

    public LimitingIterator(AbstractIterator<T> iterator, int max) {
        this.iterator = iterator;
        this.max = max;
    }

    @Override
    public boolean hasNext() {
        return n < max && iterator.hasNext();
    }

    @Override
    public T next() {
        return n++ < max ? iterator.next() : null;
    }

}
