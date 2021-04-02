
package at.jku.anttracks.gui.model;

public class ApproximateLong implements Comparable<ApproximateLong> {

    public static final ApproximateLong ZERO = new ApproximateLong(0, true);
    public static final ApproximateLong APPROXIMATE_ZERO = new ApproximateLong(0, false);

    public final long value;
    public final boolean isExact;

    public ApproximateLong(long value, boolean exact) {
        this.value = value;
        isExact = exact;
    }

    @Override
    public int compareTo(ApproximateLong o) {
        return Long.compare(value, o.value);
    }

    @Override
    public String toString() {
        return (isExact ? "" : "~ ") + value;
    }
}
