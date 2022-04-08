
package at.jku.anttracks.gui.model;

import java.text.DecimalFormat;

public class ApproximateDouble implements Comparable<ApproximateDouble> {

    public static final ApproximateDouble ZERO = new ApproximateDouble(0, true);
    public static final ApproximateDouble APPROXIMATE_ZERO = new ApproximateDouble(0, false);

    public final double value;
    public final boolean isExact;

    public ApproximateDouble(double value, boolean exact) {
        this.value = value;
        isExact = exact;
    }

    @Override
    public int compareTo(ApproximateDouble o) {
        return Double.compare(value, o.value);
    }

    @Override
    public String toString() {
        // cast this to a long, otherwise the alignment is off in tables, and we can neglect a single object/byte off
        return (isExact ? "" : "~ ") + new DecimalFormat("###,###.#").format((long) value);
    }
}
