
package at.jku.anttracks.util;

public class DoubleCounter {

    private double count;
    private final boolean allowNegative;

    public DoubleCounter() {
        this(false);
    }

    public DoubleCounter(double init) {
        this(init, false);
    }

    public DoubleCounter(boolean allowNegative) {
        this(0, allowNegative);
    }

    public DoubleCounter(double init, boolean allowNegative) {
        this.allowNegative = allowNegative;
        count = init;
    }

    public double get() {
        return count;
    }

    public synchronized void add(double modifier) {
        count += modifier;
    }

    public synchronized void add(Counter otherCounter) {
        add(otherCounter.get());
    }

    public synchronized void add(DoubleCounter otherCounter) {
        add(otherCounter.get());
    }

    public synchronized void sub(double modifier) {
        count -= modifier;
        if (!allowNegative && count < 0) {
            count = 0;
        }
    }

    public void max(double c) {
        count = Math.max(count, c);
    }

    public void min(double c) {
        count = Math.min(count, c);
    }

    public synchronized void inc() {
        add(1);
    }

    public synchronized void dec() {
        sub(1);
    }

    @Override
    public String toString() {
        return String.valueOf(count);
    }

    public synchronized void reset() {
        count = 0;
    }
}
