

package at.jku.anttracks.callcontext.util;

import java.util.*;

/**
 * Provides a way to associate a path of keys with a value. A {@code LookupTree} can be created by using a {@link Builder} instance obtained
 * from the static {@link #builder()} or {@link #greedyBuilder()} methods. Once built, a lookup tree is immutable and thread-safe.
 * <p>
 * There are two types of lookup tree: greedy and short-circuiting. A greedy lookup tree will only yield a result if a whole path matches,
 * whereas a short-circuiting lookup tree will yield a result as soon as a unique one can be determined.
 *
 * @param <K> The type of the keys.
 * @param <V> The type of value.
 * @author Peter Feichtinger
 */
public class LookupTree<K, V> {

    /**
     * Builder used to create and populate a {@link LookupTree}.
     *
     * @param <K> The type of the keys.
     * @param <V> The type of value.
     * @author Peter Feichtinger
     */
    public static class Builder<K, V> {
        private LookupTree<K, V> mTree;

        Builder(boolean shortCircuit) {
            mTree = new LookupTree<>(shortCircuit);
        }

        /**
         * Associate the specified path with the specified value.
         *
         * @param keys  The path of keys (may be empty).
         * @param value The value (may not be {@code null}).
         * @return This builder for convenience.
         * @throws IllegalStateException If {@link #build()} has already been called.
         */
        public Builder<K, V> put(Iterable<? extends K> keys, V value) {
            if (mTree == null) {
                throw new IllegalStateException();
            }
            mTree.put(keys, value);
            return this;
        }

        /**
         * Create the lookup tree.
         *
         * @return The populated tree.
         */
        public LookupTree<K, V> build() {
            final LookupTree<K, V> result = mTree;
            mTree = null;
            return result;
        }
    }

    /**
     * A node in the tree, there are {@linkplain GreedyNode greedy} and {@linkplain ShortCircuitingNode short-circuiting} nodes.
     *
     * @author Peter Feichtinger
     */
    abstract class Node {
        protected final Map<K, Node> mChildren = new HashMap<>();
        protected final Node mParent;
        protected V mValue;

        /**
         * Create a node with the specified parent and value.
         *
         * @param parent The parent node, may be {@code null}.
         * @param value  The value, may be {@code null}.
         */
        protected Node(Node parent, V value) {
            mParent = parent;
            mValue = value;
        }

        /**
         * Get whether this node is empty.
         *
         * @return {@code true} if this node does not have any children, {@code false} otherwise.
         */
        public boolean isEmpty() {
            return mChildren.isEmpty();
        }

        /**
         * Add a child node for the specified key and value.
         *
         * @param key   The key.
         * @param value The value to be mapped to {@code key}.
         * @return The child node mapped to {@code key}.
         */
        public abstract Node put(K key, V value);

        /**
         * Reset the value to {@code null} for this node and all ancestors.
         */
        protected void resetValue() {
            Node next = this;
            while (next != null && next.mValue != null) {
                next.mValue = null;
                next = next.mParent;
            }
        }

        /**
         * Get the child node for the specified key.
         *
         * @param key The key.
         * @return The child node, or {@code null} if there is no mapping to {@code key}.
         */
        public Node lookup(K key) {
            return mChildren.get(key);
        }

        /**
         * Determine whether this node has a value.
         *
         * @return {@code true} if this node has a value, {@code false} otherwise.
         * @see #getValue()
         */
        public boolean hasValue() {
            return mValue != null;
        }

        /**
         * Get the value of this node.
         *
         * @return The value of this node.
         * @throws NoSuchElementException If this node does not have a value.
         * @see #hasValue()
         */
        public V getValue() {
            if (mValue == null) {
                throw new NoSuchElementException("There is no value.");
            }
            return mValue;
        }

        @Override
        public String toString() {
            return Objects.toString(mValue, "<not unique>");
        }
    }

    /**
     * This type of node is used to build a lookup tree that will only deliver a unique result if a whole path matches, in contrast to
     * {@link ShortCircuitingNode}, which will deliver a result as soon as possible.
     *
     * @author Peter Feichtinger
     */
    class GreedyNode extends Node {
        public GreedyNode(GreedyNode parent, V value) {
            super(parent, value);
        }

        @Override
        public Node put(K key, V value) {
            Node n = mChildren.get(key);
            if (n == null) {
                n = new GreedyNode(this, value);
                mChildren.put(key, n);
                resetValue();
            } else if (!Objects.equals(n.mValue, value)) {
                n.resetValue();
            }
            return n;
        }
    }

    /**
     * This type of node is used to build a lookup tree that will short-circuit, that is deliver a result as soon as possible at lookup, in
     * contrast to {@link GreedyNode}, which will only deliver a result when a whole path matches.
     *
     * @author Peter Feichtinger
     */
    class ShortCircuitingNode extends Node {
        public ShortCircuitingNode(ShortCircuitingNode parent, V value) {
            super(parent, value);
        }

        @Override
        public Node put(K key, V value) {
            Node n = mChildren.get(key);
            if (n == null) {
                n = new ShortCircuitingNode(this, value);
                mChildren.put(key, n);
                if (mChildren.size() == 1) {
                    mValue = value;
                } else {
                    resetValue();
                }
            } else if (!Objects.equals(n.mValue, value)) {
                n.resetValue();
            }
            return n;
        }
    }

    private static final LookupTree<?, ?> EMPTY = new LookupTree<>();

    private final Node mRoot;
    // An empty path may be associated with a value as well
    private boolean mHasEmptyValue;
    private V mEmptyValue;

    /**
     * Create a {@link LookupTree.Builder} that can be used to create a short-circuiting {@link LookupTree}.
     *
     * @return A builder for populating a LookupTree.
     */
    public static <K, V> Builder<K, V> builder() {
        return new Builder<>(true);
    }

    /**
     * Create a {@link LookupTree.Builder} that can be used to create a greedy {@link LookupTree}.
     *
     * @return A builder for populating a LookupTree.
     */
    public static <K, V> Builder<K, V> greedyBuilder() {
        return new Builder<>(false);
    }

    /**
     * Get the empty lookup tree.
     *
     * @return An empty {@link LookupTree} instance.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> LookupTree<K, V> empty() {
        return (LookupTree<K, V>) EMPTY;
    }

    /**
     * Create the empty lookup tree.
     */
    private LookupTree() {
        mRoot = new Node(null, null) {
            @Override
            public Node put(K key, V value) {
                throw new Error();
            }
        };
    }

    /**
     * Create an empty {@link LookupTree}.
     */
    LookupTree(boolean shortCircuit) {
        if (shortCircuit) {
            mRoot = new ShortCircuitingNode(null, null);
        } else {
            mRoot = new GreedyNode(null, null);
        }
    }

    public int getMaxDepth() {
        return getMaxDepth(mRoot);
    }

    private int getMaxDepth(Node node) {
        int max = 0;
        for (Node child : node.mChildren.values()) {
            max = Math.max(max, getMaxDepth(child));
        }
        return 1 + max;
    }

    /**
     * Associate the specified path with the specified value.
     *
     * @param keys  The path of keys (may be empty).
     * @param value The value (may not be {@code null}).
     */
    void put(Iterable<? extends K> keys, V value) {
        Objects.requireNonNull(value);
        final Iterator<? extends K> it = keys.iterator();
        if (!it.hasNext()) {
            if (!mHasEmptyValue) {
                mEmptyValue = value;
                mHasEmptyValue = true;
            } else if (!value.equals(mEmptyValue)) {
                mEmptyValue = null;
            }
            return;
        }
        Node n = mRoot;
        while (it.hasNext()) {
            n = n.put(it.next(), value);
        }
    }

    /**
     * Get whether this tree is empty.
     *
     * @return {@code true} if this tree does not contain any mappings, {@code false} otherwise.
     */
    public boolean isEmpty() {
        return !mHasEmptyValue && mRoot.isEmpty();
    }

    /**
     * Look up a value in the tree for the specified path.
     *
     * @param keys The path of keys.
     * @return The only value corresponding to the path, or {@code null} if no unique value can be found.
     * @see #lookup(PeekIterator)
     */
    public V lookup(Iterable<? extends K> keys) {
        final Iterator<? extends K> it = keys.iterator();
        if (!it.hasNext()) {
            return mEmptyValue;
        }
        Node next = mRoot;
        while (it.hasNext() && (next = next.lookup(it.next())) != null) {
            if (next.hasValue()) {
                return next.getValue();
            }
        }
        return null;
    }

    /**
     * Look up a value in the tree for the next elements in the specified path.
     * <p>
     * For a greedy lookup tree, this method consumes as many keys from the iterator as possible, as long as there are matching path
     * elements. For example, a unique value for the path may already have been determined after the second element, however the path would
     * match another two elements. A short-circuiting lookup tree would break after the second element and immediately return the result, in
     * contrast, a greedy lookup tree would advance the iterator another two elements.
     *
     * @param keys An iterator of keys that will be advanced as long as keys match a path.
     * @return The only value corresponding to the path, or {@code null} if no unique value can be found.
     * @throws NoSuchElementException If {@code keys} is empty.
     * @see #lookup(Iterable)
     */
    public V lookup(at.jku.anttracks.callcontext.util.PeekIterator<? extends K> keys) {
        Node next = mRoot.lookup(keys.peek());
        if (next == null) {
            return mEmptyValue;
        }
        do {
            keys.next();
            if (next.hasValue()) {
                return next.getValue();
            }
        } while (keys.hasNext() && (next = next.lookup(keys.peek())) != null);
        return null;
    }
}
