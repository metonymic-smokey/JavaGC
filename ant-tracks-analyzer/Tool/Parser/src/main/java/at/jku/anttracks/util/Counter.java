
package at.jku.anttracks.util;

public class Counter {

    private long count;
    private final boolean allowNegative;

    public Counter() {
        this(false);
    }

    public Counter(long init) {
        this(init, false);
    }

    public Counter(boolean allowNegative) {
        this(0, allowNegative);
    }

    public Counter(long init, boolean allowNegative) {
        this.allowNegative = allowNegative;
        count = init;
    }

    public long get() {
        return count;
    }

    public long getAndInc() {
        long x = count;
        count++;
        return x;
    }

    public synchronized void add(int modifier) {
        count += modifier;
    }

    public synchronized void add(long modifier) {
        count += modifier;
    }

    public synchronized void add(Counter otherCounter) {
        add(otherCounter.get());
    }

    public synchronized void sub(long modifier) {
        count -= modifier;
        if (!allowNegative && count < 0) {
            count = 0;
        }
    }

    public void max(long c) {
        count = Math.max(count, c);
    }

    public void min(long c) {
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
