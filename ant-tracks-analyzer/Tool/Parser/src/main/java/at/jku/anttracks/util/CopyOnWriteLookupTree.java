
package at.jku.anttracks.util;

import at.jku.anttracks.callcontext.util.LookupTree;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * A lookup tree similar to {@link LookupTree LookupTree} used for mapping allocation traces to
 * extended
 * allocation sites.
 *
 * @param <K> The type of the keys.
 * @param <V> The type of value.
 * @author Peter Feichtinger
 */
public class CopyOnWriteLookupTree<K, V> {

    /**
     * A node in the tree, there are {@linkplain SmallNode small nodes} for at most {@value #SMALL_NODE_SIZE} children and
     * {@linkplain BigNode big nodes} for more than that.
     *
     * @author Peter Feichtinger
     */
    private abstract class Node {
        /**
         * The parent node, or {@code null} if this is the {@linkplain CopyOnWriteLookupTree#mRoot root node}.
         */
        protected final Node mParent;
        /**
         * The key this node is associated with in its parent (necessary for node replacement), or {@code null} if this is the
         * {@linkplain CopyOnWriteLookupTree#mRoot root node}.
         */
        protected final K mKey;
        /**
         * The value of this node, the {@linkplain CopyOnWriteLookupTree#nullValue() null sentinel} to signal a known {@code null} value,
         * the {@linkplain CopyOnWriteLookupTree#ambiguous() ambiguous sentinel} to signal an unknown (i.e. not unique) value, or
         * {@code null} if this node is on the path to a known {@code null} value.
         */
        protected V mValue;

        /**
         * Create a node with the specified parent, key, and value.
         *
         * @param parent The parent node, may be {@code null}.
         * @param key    The key that this node is mapped to.
         * @param value  The value, may be {@code null}.
         */
        protected Node(Node parent, K key, V value) {
            mParent = parent;
            mKey = key;
            mValue = value;
        }

        /**
         * Copy this node changing the parent node.
         *
         * @param tree   The {@link CopyOnWriteLookupTree} that will own the copy.
         * @param parent The parent node of the copy.
         * @return A deep copy of this node and all its children.
         */
        protected abstract Node copy(CopyOnWriteLookupTree<K, V> tree, Node parent);

        /**
         * Add a child node for the specified key and value.
         *
         * @param key   The key, must not be {@code null}.
         * @param value The value to be mapped to {@code key}, or {@code null}.
         * @return The child node mapped to {@code key}.
         */
        public Node put(K key, V value) {
            assert value != nullValue() && value != ambiguous() : "Must specify an actual value or null.";
            Node n = lookup(key);
            if (n == null) {
                n = new TinyNode(this, key, value);
                final Node self = doPut(key, n);
                if (self.getChildCount() == 1) {
                    self.mValue = value;
                } else if (!self.hasValue() || !mValueComparator.test(self.getValue(), value)) {
                    self.resetValue();
                }
            } else if (n.hasValue() && !mValueComparator.test(n.getValue(), value)) {
                n.resetValue();
            }
            return n;
        }

        /**
         * Associate the specified key with the specified child node. Note that this method may replace this node in its parent.
         *
         * @param key   The key.
         * @param child The child node.
         * @return This node, or the replacement node in case this node was full and was replaced in its parent.
         */
        protected abstract Node doPut(K key, Node child);

        /**
         * Get the child node for the specified key.
         *
         * @param key The key.
         * @return The child node, or {@code null} if there is no mapping to {@code key}.
         */
        public abstract Node lookup(K key);

        /**
         * Get the number of children.
         *
         * @return The number of children.
         */
        protected abstract int getChildCount();

        protected abstract Object[] getChildren();

        /**
         * Determine whether this node is at capacity.
         *
         * @return {@code true} if this node is full, or {@code false} if more children can be added.
         */
        protected abstract boolean isFull();

        /**
         * Reset the value to {@linkplain CopyOnWriteLookupTree#ambiguous() non-unique} for this node and all ancestors.
         */
        protected void resetValue() {
            Node next = this;
            while (next != null && next.mValue != ambiguous()) {
                next.mValue = ambiguous();
                next = next.mParent;
            }
        }

        /**
         * Determine whether this node has a value.
         *
         * @return {@code true} if this node has a value, {@code false} otherwise.
         * @see #getValue()
         */
        public boolean hasValue() {
            return mValue != null && mValue != ambiguous();
        }

        /**
         * Get the value of this node.
         *
         * @return The value of this node.
         * @throws NoSuchElementException If this node does not have a value.
         * @see #hasValue()
         */
        public V getValue() {
            if (!hasValue()) {
                throw new NoSuchElementException("There is no value.");
            }
            return (mValue == nullValue() ? null : mValue);
        }

        @Override
        public String toString() {
            StringBuilder string = new StringBuilder();
            if (mKey != null) {
                string.append(mKey);
                string.append(" -> ");
            }
            if (!hasValue()) {
                string.append("<not unique>");
            } else if (mValue == nullValue()) {
                string.append("null");
            } else {
                string.append("\n");
                string.append(mValue.toString().trim());
            }
            string.append("\n");
            for (Object child : getChildren()) {
                if (child != null) {
                    string.append("\t");
                    string.append(child.toString().replace("\n", "\n\t"));
                    string.append("\n");
                }
            }
            return string.toString().trim();
        }
    }

    /**
     * A small tree node that holds at most one child node.
     *
     * @author Philipp Lengauer
     */
    private final class TinyNode extends Node {
        private Node mChild;

        public TinyNode(Node parent, K key, V value) {
            super(parent, key, value);
        }

        @Override
        protected CopyOnWriteLookupTree<K, V>.Node copy(CopyOnWriteLookupTree<K, V> tree, CopyOnWriteLookupTree<K, V>.Node parent) {
            final TinyNode dolly = tree.new TinyNode(parent, mKey, mValue);
            dolly.mChild = (mChild != null ? mChild.copy(tree, dolly) : null);
            return dolly;
        }

        @Override
        protected CopyOnWriteLookupTree<K, V>.Node doPut(K key, CopyOnWriteLookupTree<K, V>.Node child) {
            if (mChild == null) {
                mChild = child;
                return this;
            } else if (mKeyComparator.test(child.mKey, key)) {
                mChild = child;
                return this;
            }
            final Node replacement = new SmallNode(mParent, mKey, mValue);
            replacement.doPut(mChild.mKey, mChild);
            replacement.doPut(key, child);
            if (mParent == null) {
                assert this == mRoot;
                mRoot = replacement;
            } else {
                mParent.doPut(mKey, replacement);
            }
            return replacement;
        }

        @Override
        public CopyOnWriteLookupTree<K, V>.Node lookup(K key) {
            if (mChild != null && mKeyComparator.test(key, mChild.mKey)) {
                return mChild;
            }
            return null;
        }

        @Override
        protected int getChildCount() {
            return mChild != null ? 1 : 0;
        }

        @Override
        protected Object[] getChildren() {
            return isFull() ? new Object[]{mChild} : new Object[0];
        }

        @Override
        protected boolean isFull() {
            return mChild != null;
        }

    }

    /**
     * A small tree node that can hold up to {@value #SMALL_NODE_SIZE} child nodes.
     *
     * @author Peter Feichtinger
     */
    private final class SmallNode extends Node {
        @SuppressWarnings("unchecked")
        private final K[] mKeys = (K[]) new Object[SMALL_NODE_SIZE];
        @SuppressWarnings("unchecked")
        private final Node[] mNodes = (CopyOnWriteLookupTree<K, V>.Node[]) new CopyOnWriteLookupTree<?, ?>.Node[SMALL_NODE_SIZE];

        /**
         * Create a small node with the specified parent, key, and value.
         *
         * @param parent The parent node, may be {@code null}.
         * @param key    The key that this node is mapped to.
         * @param value  The value, may be {@code null}.
         */
        public SmallNode(Node parent, K key, V value) {
            super(parent, key, value);
            assert key != null || parent == null;
        }

        /**
         * Create a small root node with {@code null} parent, key, and value.
         */
        SmallNode() {
            super(null, null, null);
        }

        @Override
        protected Node copy(CopyOnWriteLookupTree<K, V> tree, Node parent) {
            final SmallNode n = tree.new SmallNode(parent, mKey, mValue);
            for (int j = 0; j < mKeys.length && mKeys[j] != null; j++) {
                n.mKeys[j] = mKeys[j];
                n.mNodes[j] = mNodes[j].copy(tree, n);
            }
            return n;
        }

        /**
         * Copy all the entries in this node into the specified map.
         *
         * @param sink The map to copy all mappings to.
         */
        void copyEntries(Map<Key, Node> sink) {
            for (int j = 0; j < mKeys.length && mKeys[j] != null; j++) {
                sink.put(new Key(mKeys[j]), mNodes[j]);
            }
        }

        @Override
        protected Node doPut(K key, Node child) {
            assert key != null && child != null;
            for (int j = 0; j < mKeys.length; j++) {
                if (mKeys[j] == null) {
                    mKeys[j] = key;
                    mNodes[j] = child;
                    return this;
                } else if (mKeyComparator.test(mKeys[j], key)) {
                    mNodes[j] = child;
                    return this;
                }
            }
            final Node replacement = new BigNode(this);
            replacement.doPut(key, child);
            if (mParent == null) {
                assert this == mRoot;
                mRoot = replacement;
            } else {
                mParent.doPut(mKey, replacement);
            }
            return replacement;
        }

        @Override
        public Node lookup(K key) {
            for (int j = 0; j < mKeys.length && mKeys[j] != null; j++) {
                if (mKeyComparator.test(mKeys[j], key)) {
                    return mNodes[j];
                }
            }
            return null;
        }

        @Override
        protected int getChildCount() {
            int count = 0;
            while (count < mKeys.length && mKeys[count] != null) {
                count++;
            }
            return count;
        }

        @Override
        protected Object[] getChildren() {
            return mNodes.clone();
        }

        @Override
        protected boolean isFull() {
            return ArraysUtil.last(mKeys) != null;
        }
    }

    /**
     * A big tree node that can hold an arbitrary number of child nodes.
     *
     * @author Peter Feichtinger
     */
    private final class BigNode extends Node {
        private final Map<Key, Node> mChildren = new HashMap<>();

        /**
         * Create a big node with the specified parent, key, and value.
         *
         * @param parent The parent node, may be {@code null}.
         * @param key    The key that this node is mapped to.
         * @param value  The value, may be {@code null}.
         */
        public BigNode(Node parent, K key, V value) {
            super(parent, key, value);
            assert key != null || parent == null;
        }

        /**
         * Create a big node from the specified small node. The new node will contain all mappings from the small node and will have the
         * same key and value.
         *
         * @param node The small node to copy.
         */
        public BigNode(SmallNode node) {
            super(node.mParent, node.mKey, node.mValue);
            node.copyEntries(mChildren);
        }

        @Override
        protected Node copy(CopyOnWriteLookupTree<K, V> tree, Node parent) {
            final BigNode n = tree.new BigNode(parent, mKey, mValue);
            for (Entry<Key, Node> entry : mChildren.entrySet()) {
                n.mChildren.put(tree.new Key(entry.getKey().key), entry.getValue().copy(tree, n));
            }
            return n;
        }

        @Override
        protected Node doPut(K key, Node child) {
            mChildren.put(new Key(key), child);
            return this;
        }

        @Override
        public Node lookup(K key) {
            return mChildren.get(new Key(key));
        }

        @Override
        protected int getChildCount() {
            return mChildren.size();
        }

        @Override
        protected Object[] getChildren() {
            return mChildren.values().toArray();
        }

        @Override
        protected boolean isFull() {
            return false;
        }
    }

    /**
     * A wrapper around a key object that uses the tree's key comparator for testing equality.
     *
     * @author Peter Feichtinger
     */
    class Key {
        final K key;

        public Key(K key) {
            this.key = Objects.requireNonNull(key);
        }

        @SuppressWarnings("unchecked")
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
            return mKeyComparator.test(key, ((Key) obj).key);
        }

        @Override
        public int hashCode() {
            return key.hashCode();
        }
    }

    /**
     * The maximum number of children for small nodes.
     */
    static final int SMALL_NODE_SIZE = 4;

    private static final Object NULL_SENTINEL = new Object();
    private static final Object AMBIGUOUS_SENTINEL = new Object();

    final AbstractBiPredicate<? super K> mKeyComparator;
    final AbstractBiPredicate<? super V> mValueComparator;
    Node mRoot;

    /**
     * Create an empty {@link CopyOnWriteLookupTree} using {@link Objects#equals(Object, Object) equals} for comparing keys and values.
     *
     * @see #CopyOnWriteLookupTree(AbstractBiPredicate, AbstractBiPredicate)
     */
    public CopyOnWriteLookupTree() {
        this(null, null);
    }

    /**
     * Create an empty {@link CopyOnWriteLookupTree} using the specified predicate for comparing values.
     *
     * @param keyComparator   The predicate used to determine whether two keys are equal, or {@code null} to use {@link Objects#equals
     *                        (Object, Object)
     *                        equals}. The specified comparator must be consistent with the default {@link Object#hashCode() hashCode}
     *                        method of the key
     *                        type.
     * @param valueComparator The predicate used to determine whether two values are equal, or {@code null} to use {@link Objects#equals
     *                        (Object, Object)
     *                        equals}. The specified comparator need not be consistent with the {@link Object#hashCode() hashCode} method
     *                        of the value
     *                        type ({@code hashCode} will not be called on values).
     */
    public CopyOnWriteLookupTree(AbstractBiPredicate<? super K> keyComparator, AbstractBiPredicate<? super V> valueComparator) {
        mKeyComparator = (keyComparator != null ? keyComparator : new AbstractBiPredicate<K>() {
            @Override
            public boolean test(K key1, K key2) {
                return Objects.equals(key1, key2);
            }
        });
        mValueComparator = (valueComparator != null ? valueComparator : new AbstractBiPredicate<V>() {
            @Override
            public boolean test(V value1, V value2) {
                return Objects.equals(value1, value2);
            }
        });
        mRoot = new SmallNode();
    }

    /**
     * Create an {@link CopyOnWriteLookupTree} by deep copy.
     *
     * @param other The lookup to copy.
     */
    private CopyOnWriteLookupTree(CopyOnWriteLookupTree<K, V> other) {
        mKeyComparator = other.mKeyComparator;
        mValueComparator = other.mValueComparator;
        mRoot = other.mRoot.copy(this, null);
    }

    /**
     * Get the value that is used to mark {@code null} values put into the tree.
     *
     * @return A sentinel value used in the place of {@code null} values.
     */
    @SuppressWarnings("unchecked")
    V nullValue() {
        return (V) NULL_SENTINEL;
    }

    /**
     * Get the value that is used to mark a known non-unique value.
     *
     * @return A sentinel value used to indicate that there is no unique value.
     */
    @SuppressWarnings("unchecked")
    V ambiguous() {
        return (V) AMBIGUOUS_SENTINEL;
    }

    /**
     * Create a new {@linkplain CopyOnWriteLookupTree lookup} that is a copy of this tree with the specified path associated with the
     * specified value.
     *
     * @param keys  The path of keys (if empty then this tree is returned).
     * @param value The value (may be {@code null}).
     * @return The new {@link CopyOnWriteLookupTree}, or this instance if {@code keys} is empty.
     * @throws NullPointerException If {@code value} is {@code null}, or any of the elements in {@code keys} are {@code null}.
     */
    public CopyOnWriteLookupTree<K, V> put(AbstractIterator<? extends K> keys, V value) {
        if (!keys.hasNext()) {
            return this;
        }
        final CopyOnWriteLookupTree<K, V> copy = new CopyOnWriteLookupTree<>(this);
        copy.doPut(keys, value);
        return copy;
    }

    private void doPut(AbstractIterator<? extends K> it, V value) {
        assert it.hasNext();
        Node n = mRoot;
        while (it.hasNext()) {
            n = n.put(Objects.requireNonNull(it.next()), value);
        }
        if (value == null) {
            n.mValue = nullValue();
        }
    }

    /**
     * Look up a value in the tree for the specified path.
     *
     * @param keys The path of keys.
     * @return The only value corresponding to the path, or {@code null} if no unique value can be found (or {@code null} is mapped to the
     * path).
     */
    public V lookup(AbstractIterator<? extends K> keys) {
        return lookup(keys, null);
    }

    /**
     * Look up a value in the tree for the specified path.
     *
     * @param keys      The path of keys.
     * @param nullValue The default value to return if the path is mapped to {@code null}.
     * @return The only value corresponding to the path, {@code nullValue} if {@code null} is mapped to the path, or {@code null} if no
     * unique value can be found.
     */
    public V lookup(AbstractIterator<? extends K> keys, V nullValue) {
        Node n = mRoot;
        while (n != null && n.getChildCount() > 0 && keys.hasNext()) {
            K key = keys.next();
            n = n.lookup(key);
        }
        if (n != null && n.hasValue()) {
            return (n.getValue() == null ? nullValue : n.getValue());
        }
        return null;
    }

    @Override
    public String toString() {
        return mRoot.toString();
    }
}
