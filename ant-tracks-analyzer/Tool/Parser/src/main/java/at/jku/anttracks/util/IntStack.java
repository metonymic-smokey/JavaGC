package at.jku.anttracks.util;

import java.util.Arrays;

public class IntStack {
    private int[] elements;
    private int size = 0;
    private static final int DEFAULT_INITIAL_CAPACITY = 32;

    public IntStack() {
        this(DEFAULT_INITIAL_CAPACITY);
    }

    public IntStack(int initSize) {
        elements = new int[initSize];
    }

    public void push(int e) {
        ensureCapacity();
        elements[size++] = e;
    }

    public int pop() {
        --size;
        return elements[size];
    }

    /**
     * Ensure space for at least one more element, roughly
     * doubling the capacity each time the array needs to grow.
     */
    private void ensureCapacity() {
        if (elements.length == size) {
            elements = Arrays.copyOf(elements, 2 * size + 1);
        }
    }

    public boolean isEmpty() {
        return size == 0;
    }

    public int size() {
        return size;
    }
}