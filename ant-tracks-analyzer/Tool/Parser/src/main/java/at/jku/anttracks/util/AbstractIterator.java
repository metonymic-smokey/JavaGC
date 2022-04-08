
package at.jku.anttracks.util;

import java.util.Iterator;

public abstract class AbstractIterator<E> implements Iterator<E> {

    @Override
    public abstract boolean hasNext();

    @Override
    public abstract E next();

}
