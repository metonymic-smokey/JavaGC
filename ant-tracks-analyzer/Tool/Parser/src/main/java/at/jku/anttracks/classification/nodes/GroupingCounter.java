
package at.jku.anttracks.classification.nodes;

import at.jku.anttracks.util.Counter;
import at.jku.anttracks.util.DoubleCounter;

public class GroupingCounter {
    public final Counter exact;
    public final DoubleCounter sampled;

    public GroupingCounter(long exactInit, double sampledInit, boolean allowNegative) {
        exact = new Counter(exactInit, allowNegative);
        sampled = new DoubleCounter(sampledInit, allowNegative);
    }

    public void add(GroupingCounter other) {
        exact.add(other.exact);
        sampled.add(other.sampled);
    }

    public double get() {
        return exact.get() + sampled.get();
    }

    @Override
    public String toString() {
        return (exact.get() + sampled.get()) + " (Exact: " + exact.get() + ", Sampled: " + sampled.get() + ")";
    }

}
