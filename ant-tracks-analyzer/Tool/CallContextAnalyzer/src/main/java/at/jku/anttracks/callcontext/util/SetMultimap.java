

package at.jku.anttracks.callcontext.util;

import java.io.Serializable;
import java.util.*;
import java.util.AbstractMap.SimpleImmutableEntry;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * A collection that maps keys to values, similar to {@link Map}, but in which each key may be associated with <i>multiple</i> values. You
 * can visualize the contents of a multimap either as a map from keys to <i>nonempty</i> collections of values:
 * <ul>
 * <li>a -> 1, 2
 * <li>b -> 3
 * </ul>
 * ... or as a single "flattened" collection of key-value pairs:
 * <ul>
 * <li>a -> 1
 * <li>a -> 2
 * <li>b -> 3
 * </ul>
 *
 * @param <K> The type of key
 * @param <V> The value type
 * @author Peter Feichtinger
 */
public class SetMultimap<K, V> implements Serializable {

    /**
     * A set for a particular key that knows how to add and remove values properly.
     *
     * @author Peter Feichtinger
     */
    private class ValueSet extends AbstractSet<V> {
        final K key;

        public ValueSet(K key) {
            this.key = key;
        }

        @Override
        public int size() {
            final Set<V> set = mMap.get(key);
            return (set != null ? set.size() : 0);
        }

        @Override
        public boolean isEmpty() {
            return !mMap.containsKey(key);
        }

        @Override
        public boolean contains(Object o) {
            final Set<V> set = mMap.get(key);
            return (set != null ? set.contains(o) : false);
        }

        @Override
        public Iterator<V> iterator() {
            final Set<V> set = mMap.get(key);
            if (set == null) {
                return Collections.emptyIterator();
            }
            return new Iterator<V>() {
                private final Iterator<V> it = set.iterator();

                @Override
                public boolean hasNext() {
                    return it.hasNext();
                }

                @Override
                public V next() {
                    return it.next();
                }

                @Override
                public void remove() {
                    it.remove();
                    // Don't update the size in case the set is removed from the multimap while an iteration is in progress
                    if (mMap.containsKey(key)) {
                        mSize--;
                    }
                    if (set.isEmpty()) {
                        mMap.remove(key);
                    }
                }

                @Override
                public void forEachRemaining(Consumer<? super V> action) {
                    // Use backing collection version
                    it.forEachRemaining(action);
                }
            };
        }

        @Override
        public boolean add(V e) {
            return SetMultimap.this.put(key, e);
        }

        @Override
        public boolean remove(Object o) {
            return SetMultimap.this.remove(key, o);
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            final Set<V> set = mMap.get(key);
            return (set != null ? set.containsAll(c) : c.isEmpty());
        }

        @Override
        public boolean addAll(Collection<? extends V> c) {
            return SetMultimap.this.putAll(key, c);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            final Set<V> set = mMap.get(key);
            if (set != null) {
                final int sizeBefore = set.size();
                if (set.retainAll(c)) {
                    mSize -= sizeBefore - set.size();
                    if (set.isEmpty()) {
                        mMap.remove(key);
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            final Set<V> set = mMap.get(key);
            if (set != null) {
                final int sizeBefore = set.size();
                if (set.removeAll(c)) {
                    mSize -= sizeBefore - set.size();
                    if (set.isEmpty()) {
                        mMap.remove(key);
                    }
                    return true;
                }
            }
            return false;
        }

        @Override
        public void clear() {
            final Set<V> set = mMap.remove(key);
            if (set != null) {
                mSize -= set.size();
            }
        }

        @Override
        public Spliterator<V> spliterator() {
            final Set<V> set = mMap.get(key);
            if (set != null) {
                return set.spliterator();
            }
            return Spliterators.emptySpliterator();
        }

        @Override
        public void forEach(Consumer<? super V> action) {
            final Set<V> set = mMap.get(key);
            if (set != null) {
                set.forEach(action);
            }
        }
    }

    /**
     * Key set view onto the multimap. Doesn't support {@linkplain Set#add(Object) addition}; supports removal through
     * {@link Set#remove(Object) remove} and the {@linkplain Iterator#remove() iterator}.
     *
     * @author Peter Feichtinger
     */
    private class KeySet extends AbstractSet<K> {
        public KeySet() {}

        @Override
        public int size() {
            return mMap.size();
        }

        @Override
        public boolean contains(Object o) {
            return mMap.containsKey(o);
        }

        @Override
        public Iterator<K> iterator() {
            return new EntryIterator<K>() {
                @Override
                protected K getValue(Map.Entry<K, Set<V>> entry) {
                    return entry.getKey();
                }
            };
        }

        @Override
        public Object[] toArray() {
            return mMap.keySet().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return mMap.keySet().toArray(a);
        }

        @Override
        public boolean add(K e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            return SetMultimap.this.removeAll(o) != null;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return mMap.keySet().containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends K> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean changed = false;
            for (Object o : c) {
                changed |= remove(o);
            }
            return changed;
        }

        @Override
        public void clear() {
            SetMultimap.this.clear();
        }

        @Override
        public Spliterator<K> spliterator() {
            return mMap.keySet().spliterator();
        }

        @Override
        public void forEach(Consumer<? super K> action) {
            mMap.keySet().forEach(action);
        }
    }

    /**
     * Set view of entries onto this multimap. Supports removal through {@link Set#remove(Object) remove} and the
     * {@linkplain Iterator#remove() iterator}; doesn't support {@linkplain Set#add(Object) addition}; doesn't support
     * {@linkplain Map.Entry#setValue(Object) setting values}.
     *
     * @author Peter Feichtinger
     */
    private class EntrySet extends AbstractSet<Map.Entry<K, Set<V>>> {
        public EntrySet() {}

        @Override
        public int size() {
            return mMap.size();
        }

        @Override
        public boolean contains(Object o) {
            return mMap.entrySet().contains(o);
        }

        @Override
        public Iterator<Map.Entry<K, Set<V>>> iterator() {
            return new EntryIterator<Map.Entry<K, Set<V>>>() {
                @Override
                protected Map.Entry<K, Set<V>> getValue(Map.Entry<K, Set<V>> entry) {
                    final K key = entry.getKey();
                    return new SimpleImmutableEntry<>(key, SetMultimap.this.get(key));
                }
            };
        }

        @Override
        public boolean add(Map.Entry<K, Set<V>> e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                final Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                final Set<V> candidate = mMap.get(entry.getKey());
                if (candidate != null && candidate.equals(entry.getValue())) {
                    SetMultimap.this.removeAll(entry.getKey());
                    return true;
                }
            }
            return false;
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return mMap.entrySet().containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends Map.Entry<K, Set<V>>> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean changed = false;
            for (Object o : c) {
                changed |= remove(o);
            }
            return changed;
        }

        @Override
        public void clear() {
            SetMultimap.this.clear();
        }
    }

    /**
     * Collection view onto the value sets of this multimap. Doesn't support {@linkplain Collection#add(Object) addition}; supports removal
     * through {@link Collection#remove(Object) remove} and the {@linkplain Iterator#remove() iterator}.
     *
     * @author Peter Feichtinger
     */
    private class ValueSets extends AbstractCollection<Set<V>> {
        public ValueSets() {}

        @Override
        public int size() {
            return mMap.size();
        }

        @Override
        public boolean contains(Object o) {
            return mMap.containsValue(o);
        }

        @Override
        public Iterator<Set<V>> iterator() {
            return new EntryIterator<Set<V>>() {
                @Override
                protected Set<V> getValue(Map.Entry<K, Set<V>> entry) {
                    return SetMultimap.this.get(entry.getKey());
                }
            };
        }

        @Override
        public boolean add(Set<V> e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean addAll(Collection<? extends Set<V>> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            SetMultimap.this.clear();
        }
    }

    /**
     * Map view onto this multimap. Supports {@linkplain Map#put(Object, Object) addition} and {@linkplain Map#remove(Object) removal}; the
     * key set and entry set support {@linkplain Set#remove(Object) removal} but not {@linkplain Set#add(Object) addition}; the value
     * collection supports {@linkplain Collection#remove(Object) removal} but not {@linkplain Collection#add(Object) addition}; the entries
     * don't support {@linkplain Map.Entry#setValue(Object) setting values}.
     *
     * @author Peter Feichtinger
     */
    private class AsMap extends AbstractMap<K, Set<V>> {
        public AsMap() {}

        @Override
        public int size() {
            return mMap.size();
        }

        @Override
        public boolean isEmpty() {
            return mMap.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return mMap.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return mMap.containsValue(value);
        }

        @SuppressWarnings("unchecked")
        @Override
        public Set<V> get(Object key) {
            if (mMap.containsKey(key)) {
                return SetMultimap.this.get((K) key);
            }
            return null;
        }

        @Override
        public Set<V> put(K key, Set<V> value) {
            final Set<V> old = SetMultimap.this.removeAll(key);
            SetMultimap.this.putAll(key, value);
            return old;
        }

        @Override
        public Set<V> remove(Object key) {
            return SetMultimap.this.removeAll(key);
        }

        @Override
        public void clear() {
            SetMultimap.this.clear();
        }

        @Override
        public Set<K> keySet() {
            return SetMultimap.this.keySet();
        }

        @Override
        public Collection<Set<V>> values() {
            return SetMultimap.this.valueSets();
        }

        @Override
        public Set<Map.Entry<K, Set<V>>> entrySet() {
            return SetMultimap.this.entrySet();
        }
    }

    /**
     * Collection view onto the values in this multimap. Doesn't support {@linkplain Collection#add(Object) addition}; supports removal
     * through {@link Collection#remove(Object) remove} and the {@linkplain Iterator#remove() iterator}.
     *
     * @author Peter Feichtinger
     */
    private class Values extends AbstractCollection<V> {
        public Values() {}

        @Override
        public int size() {
            return mSize;
        }

        @Override
        public boolean contains(Object o) {
            return SetMultimap.this.containsValue(o);
        }

        @Override
        public Iterator<V> iterator() {
            return new ValueIterator<V>() {
                @Override
                protected V getValue(K key, V value) {
                    return value;
                }
            };
        }

        @Override
        public boolean add(V e) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean containsAll(Collection<?> c) {
            return mMap.values().stream().flatMap(Set::stream).collect(Collectors.toSet()).containsAll(c);
        }

        @Override
        public boolean addAll(Collection<? extends V> c) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clear() {
            SetMultimap.this.clear();
        }
    }

    /**
     * Flat set view onto the entries in this multimap. Supports {@linkplain Collection#add(Object) addition}; supports removal through
     * {@link Collection#remove(Object) remove} and the {@linkplain Iterator#remove() iterator}; doesn't support
     * {@linkplain Map.Entry#setValue(Object) setting values}.
     *
     * @author Peter Feichtinger
     */
    private class Entries extends AbstractSet<Map.Entry<K, V>> {
        public Entries() {}

        @Override
        public int size() {
            return mSize;
        }

        @Override
        public boolean contains(Object o) {
            if (o instanceof Map.Entry) {
                final Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                final Set<V> set = mMap.get(entry.getKey());
                if (set != null) {
                    return set.contains(entry.getValue());
                }
            }
            return false;
        }

        @Override
        public Iterator<Map.Entry<K, V>> iterator() {
            return new ValueIterator<Map.Entry<K, V>>() {
                @Override
                protected Map.Entry<K, V> getValue(K key, V value) {
                    return new SimpleImmutableEntry<>(key, value);
                }
            };
        }

        @Override
        public boolean add(Map.Entry<K, V> e) {
            return SetMultimap.this.put(e.getKey(), e.getValue());
        }

        @SuppressWarnings("unchecked")
        @Override
        public boolean remove(Object o) {
            if (o instanceof Map.Entry) {
                final Map.Entry<?, ?> entry = (Map.Entry<?, ?>) o;
                if (mMap.containsKey(entry.getKey())) {
                    return SetMultimap.this.remove((K) entry.getKey(), entry.getValue());
                }
            }
            return false;
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            boolean changed = false;
            for (Object o : c) {
                changed |= remove(o);
            }
            return changed;
        }

        @Override
        public void clear() {
            SetMultimap.this.clear();
        }
    }

    /**
     * Iterator over entries of the backing map. Supports {@linkplain Iterator#remove() removal} of entries (that is, whole sets of values).
     *
     * @param <T> The type returned by the iterator.
     * @author Peter Feichtinger
     */
    private abstract class EntryIterator<T> implements Iterator<T> {
        private final Iterator<Map.Entry<K, Set<V>>> it = mMap.entrySet().iterator();
        private int currentSize;

        public EntryIterator() {}

        @Override
        public boolean hasNext() {
            return it.hasNext();
        }

        @Override
        public T next() {
            final Map.Entry<K, Set<V>> entry = it.next();
            currentSize = entry.getValue().size();
            return getValue(entry);
        }

        /**
         * Get the value to be returned from the specified entry.
         *
         * @param entry The current entry from the backing map.
         * @return The value to be returned from {@link #next()}.
         */
        protected abstract T getValue(Map.Entry<K, Set<V>> entry);

        @Override
        public void remove() {
            it.remove();
            mSize -= currentSize;
            currentSize = 0;
        }
    }

    /**
     * Iterator over values in the multimap. Supports {@linkplain Iterator#remove() removal} of entries (that is, single mappings).
     *
     * @param <T> The type returned by the iterator.
     * @author Peter Feichtinger
     */
    private abstract class ValueIterator<T> implements Iterator<T> {
        private final Iterator<Map.Entry<K, Set<V>>> entries = mMap.entrySet().iterator();
        private Map.Entry<K, Set<V>> current;
        private Iterator<V> it;

        public ValueIterator() {}

        @Override
        public boolean hasNext() {
            if (it != null && it.hasNext()) {
                return true;
            }
            return entries.hasNext();
        }

        @Override
        public T next() {
            if (it == null || !it.hasNext()) {
                current = entries.next();
                it = current.getValue().iterator();
                assert it.hasNext();
            }
            return getValue(current.getKey(), it.next());
        }

        protected abstract T getValue(K key, V value);

        @Override
        public void remove() {
            if (it == null) {
                throw new IllegalStateException();
            }
            it.remove();
            // Don't update the size in case the set is removed from the multimap while an iteration is in progress
            if (mMap.containsKey(current.getKey())) {
                mSize--;
            }
            if (current.getValue().isEmpty()) {
                assert !it.hasNext();
                entries.remove();
            }
        }
    }

    private static final long serialVersionUID = 4438980422002011034L;

    final Map<K, Set<V>> mMap = new HashMap<>();
    int mSize = 0;

    private transient KeySet mKeySet = null;
    private transient EntrySet mEntrySet = null;
    private transient ValueSets mValueSets = null;
    private transient AsMap mAsMap = null;
    private transient Values mValues = null;
    private transient Entries mEntries = null;

    /**
     * Create an empty {@link SetMultimap}.
     */
    public SetMultimap() {

    }

    /**
     * Create a {@link SetMultimap} that contains all mappings in the specified multimap.
     *
     * @param other The multimap to copy.
     */
    public SetMultimap(SetMultimap<? extends K, ? extends V> other) {
        this.putAll(other);
    }

    /**
     * Returns the number of key-value pairs in this multimap.
     * <p>
     * <b>Note:</b> this method does not return the number of <i>distinct keys</i> in the multimap, which is given by {@link #keySet()
     * keySet().size()} or {@link #asMap() asMap().size()}.
     *
     * @return The number of key-value pairs in this multimap.
     */
    public int size() {
        assert mSize == mMap.values().stream().mapToInt(Set::size).sum();
        return mSize;
    }

    /**
     * Get whether this multimap is empty.
     *
     * @return {@code true} if this multimap contains no key-value pairs.
     */
    public boolean isEmpty() {
        return mMap.isEmpty();
    }

    /**
     * Determine if this multimap contains at least one key-value pair with the specified key.
     *
     * @param key The key to look for.
     * @return {@code true} if there is at least one value mapped to {@code key}.
     */
    public boolean containsKey(K key) {
        return mMap.containsKey(key);
    }

    /**
     * Determine if this multimap contains at least one key-value pair with the specified value.
     *
     * @param value The value to look for.
     * @return {@code true} if there is at least one mapping with {@code value}.
     */
    public boolean containsValue(Object value) {
        return mMap.values().stream().anyMatch(set -> set.contains(value));
    }

    /**
     * Determine if there is a mapping from the specified key to the specified value.
     *
     * @param key   The key to look for.
     * @param value The value to look for.
     * @return {@code true} if there is a mapping from {@code key} to {@code value}.
     */
    public boolean containsEntry(K key, Object value) {
        final Set<V> set = mMap.get(key);
        if (set != null) {
            return set.contains(value);
        }
        return false;
    }

    /**
     * Get the values associated with the specified key. Note that when {@link #containsKey(Object)} returns {@code false}, this returns an
     * empty collection, not {@code null}.
     *
     * @param key The key to get values for.
     * @return A set view of values to which {@code key} is mapped. Changes to the returned set will update the underlying multimap, and
     * vice versa.
     */
    public Set<V> get(K key) {
        return new ValueSet(key);
    }

    /**
     * Store a key-value pair in this map.
     *
     * @param key   The key.
     * @param value The value to add.
     * @return {@code true} if the method increased the size of this multimap, or {@code false} if this multimap already contained the
     * key-value pair.
     * @see #putAll(Object, Iterable)
     */
    public boolean put(K key, V value) {
        if (mMap.computeIfAbsent(key, k -> new HashSet<>()).add(value)) {
            mSize++;
            return true;
        }
        return false;
    }

    /**
     * Store a key-value pair in this multimap for each of {@code values}, all using the specified key.
     *
     * @param key    The key.
     * @param values The values to be mapped to {@code key}.
     * @return {@code true} if this multimap changed.
     * @see #put(Object, Object)
     * @see #computeIfAbsent(Object, Function)
     * @see #putAllIfAbsent(Object, Iterable)
     */
    public boolean putAll(K key, Iterable<? extends V> values) {
        final Iterator<? extends V> it = values.iterator();
        if (!it.hasNext()) {
            return false;
        }
        final Set<V> set = mMap.computeIfAbsent(key, k -> new HashSet<>());
        boolean changed = false;
        while (it.hasNext()) {
            if (set.add(it.next())) {
                mSize++;
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Remove all values associated with the specified key. Note that changes to the returned collection will <em>not</em> be reflected in
     * this multimap.
     *
     * @param key The key to remove.
     * @return The set of values mapped to {@code key}, or {@code null} if there were no mappings to {@code key}.
     */
    public Set<V> removeAll(Object key) {
        final Set<V> set = mMap.remove(key);
        if (set != null) {
            mSize -= set.size();
        }
        return set;
    }

    /**
     * Remove a single key-value pair with the specified key and value from this multimap, if it exists.
     *
     * @param key   The key.
     * @param value The value to remove.
     * @return {@code true} if this multimap changed.
     */
    public boolean remove(K key, Object value) {
        final Set<V> set = mMap.get(key);
        if (set != null && set.remove(value)) {
            mSize--;
            if (set.isEmpty()) {
                mMap.remove(key);
            }
            return true;
        }
        return false;
    }

    /**
     * Add all key-value pairs in the specified multimap to this multimap.
     *
     * @param m The multimap to add to this multimap.
     * @return {@code true} if this multimap changed.
     */
    public boolean putAll(SetMultimap<? extends K, ? extends V> m) {
        boolean changed = false;
        for (Map.Entry<? extends K, ? extends Set<? extends V>> entry : m.mMap.entrySet()) {
            changed |= putAll(entry.getKey(), entry.getValue());
        }
        return changed;
    }

    /**
     * Remove all key-value pairs from the multimap, leaving it {@linkplain #isEmpty empty}.
     */
    public void clear() {
        mMap.clear();
        mSize = 0;
    }

    /**
     * If the specified key is not associated with any values, attempt to compute its values using the given mapping function and enter them
     * into this multimap unless {@code null}. If the function returns {@code null} no mappings are recorded.
     *
     * @param key             The key.
     * @param mappingFunction The function used to compute the values to be mapped to {@code key}.
     * @return The current (existing or computed) value associated with the specified key, or {@code null} if the computed value is
     * {@code null}.
     * @see #putAll(Object, Iterable)
     * @see #putAllIfAbsent(Object, Iterable)
     */
    public Set<V> computeIfAbsent(K key, Function<? super K, ? extends Iterable<? extends V>> mappingFunction) {
        Objects.requireNonNull(mappingFunction);
        if (!mMap.containsKey(key)) {
            final Iterable<? extends V> values = mappingFunction.apply(key);
            if (values == null) {
                return null;
            }
            putAll(key, values);
        }
        return get(key);
    }

    /**
     * If the specified key is not associated with any values, associate it with the given values.
     *
     * @param key    The key.
     * @param values The values to be mapped to {@code key}.
     * @return {@code null} if there were no values mapped to {@code key}, the values mapped to {@code key} otherwise.
     * @see #putAll(Object, Iterable)
     * @see #computeIfAbsent(Object, Function)
     */
    public Set<V> putAllIfAbsent(K key, Iterable<? extends V> values) {
        Objects.requireNonNull(values);
        if (mMap.containsKey(key)) {
            return get(key);
        }
        putAll(key, values);
        return null;
    }

    /**
     * Get the set of <i>distinct</i> keys contained in this multimap. Note that the key set contains a key if and only if that key is
     * mapped to at least one value.
     *
     * @return A set view of the keys contained in this map. Changes to the returned set will update the underlying multimap, and vice
     * versa. However, <i>adding</i> to the returned set is not possible.
     */
    public Set<K> keySet() {
        if (mKeySet == null) {
            mKeySet = new KeySet();
        }
        return mKeySet;
    }

    /**
     * Get the values in this multimap.
     *
     * @return A view of the values in this multimap. Changes to the returned collection will update the underlying multimap, and vice
     * versa. However, <i>adding</i> to the returned collection is not possible.
     */
    public Collection<V> values() {
        if (mValues == null) {
            mValues = new Values();
        }
        return mValues;
    }

    /**
     * Get the entries in this multimap.
     *
     * @return A view of the entries in this multimap. Changes to the returned set will update the underlying map, and vice versa. The
     * entries do not support {@link java.util.Map.Entry#setValue(Object) setValue}.
     */
    public Set<Map.Entry<K, V>> entries() {
        if (mEntries == null) {
            mEntries = new Entries();
        }
        return mEntries;
    }

    /**
     * Get a view onto this multimap as a map from keys to sets of values. Note that {@code asMap().get(k)} is equivalent to
     * {@link #get(Object) get(k)} only when {@code k} is a key contained in the multimap; otherwise it returns {@code null} as opposed to
     * an empty collection.
     *
     * @return A map view of this multimap. Changes to the returned map or the collections that serve as its values will update the
     * underlying multimap, and vice versa. The map entries do not support {@link java.util.Map.Entry#setValue(Object) setValue};
     * adding to the map is possible through {@link Map#put(Object, Object) put}, but not through {@link Set#add(Object)
     * entrySet().add}.
     */
    public Map<K, Set<V>> asMap() {
        if (mAsMap == null) {
            mAsMap = new AsMap();
        }
        return mAsMap;
    }

    /**
     * Get the contents of this multimap as a set of entries from keys to value sets.
     *
     * @return A view of the entries in this multimap, see {@link EntrySet} for details.
     */
    Set<Map.Entry<K, Set<V>>> entrySet() {
        if (mEntrySet == null) {
            mEntrySet = new EntrySet();
        }
        return mEntrySet;
    }

    /**
     * Get the value sets in this multimap.
     *
     * @return A view of the value sets in this multimap, see {@link ValueSets} for details.
     */
    Collection<Set<V>> valueSets() {
        if (mValueSets == null) {
            mValueSets = new ValueSets();
        }
        return mValueSets;
    }

    /**
     * Get a {@link Stream} over the value sets in this multimap.
     * <p>
     * This is equivalent to {@code asMap().values().stream()}, but should be used when a map view is not required.
     *
     * @return A sequential {@code Stream} over the value sets in this multimap.
     */
    public Stream<Set<V>> streamValueSets() {
        return valueSets().stream();
    }

    /**
     * Get a {@link Stream} over the values in this multimap.
     * <p>
     * This is a convenience method for {@code values().stream()}.
     *
     * @return A sequential {@code Stream} over the values in this multimap.
     */
    public Stream<V> streamValues() {
        return values().stream();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }

        SetMultimap<?, ?> other = (SetMultimap<?, ?>) obj;
        if (!mMap.equals(other.mMap)) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        return mMap.hashCode();
    }

    @Override
    public String toString() {
        return mMap.toString();
    }
}
