
package at.jku.anttracks.util;

import java.lang.reflect.Array;
import java.util.*;

public final class CollectionsUtil {

    private CollectionsUtil() {
        throw new Error("No instance for you! :p");
    }

    public static <E> E[] toArray(Class<E> clazz, Collection<E> collection) {
        return concat(clazz, collection);
    }

    @SafeVarargs
    @SuppressWarnings("varargs")
    public static <E> E[] concat(Class<E> clazz, Collection<E>... collections) {
        @SuppressWarnings("unchecked")
        E[] array = (E[]) Array.newInstance(clazz, Arrays.stream(collections).mapToInt(c -> c.size()).sum());
        int index = 0;
        for (Collection<E> collection : collections) {
            copyTo(collection, array, index);
            index += collection.size();
        }
        return array;
    }

    private static <E> E[] copyTo(Collection<E> source, E[] destination, int start) {
        int index = start;
        for (E e : source) {
            destination[index++] = e;
        }
        return destination;
    }

    public static <E, K, V> Map<K, V> map(Iterable<E> elements, Mapper<E, K> keymapper, Mapper<E, V> valuemapper) {
        return map(elements, keymapper, valuemapper, () -> new HashMap<K, V>());
    }

    public static <E, K, V, M extends Map<K, V>> M map(Iterable<E> elements, Mapper<E, K> keymapper, Mapper<E, V> valuemapper, Factory<M> factory) {
        M map = factory.create();
        elements.forEach(e -> map.put(keymapper.map(e), valuemapper.map(e)));
        return map;
    }

    /**
     * Get the last element in the specified list.
     *
     * @param list The list.
     * @return The last element in the list, that is the element at index {@code list.size() - 1}.
     * @throws NoSuchElementException If {@code list} is empty.
     */
    public static <T> T last(List<? extends T> list) {
        if (list.isEmpty()) {
            throw new NoSuchElementException();
        }
        return list.get(list.size() - 1);
    }

    @FunctionalInterface
    public static interface Factory<T> {
        public abstract T create();
    }

    @FunctionalInterface
    public static interface Mapper<S, D> {
        public abstract D map(S source);
    }

}
