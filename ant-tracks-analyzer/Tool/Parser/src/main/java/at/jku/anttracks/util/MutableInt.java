
package at.jku.anttracks.util;

public class MutableInt implements Comparable<MutableInt> {

    private int val;

    public MutableInt() {
        super();
    }

    public MutableInt(int value) {
        super();
        this.val = value;
    }

    public MutableInt(Number value) {
        super();
        this.val = value.intValue();
    }

    public int get() {
        return val;
    }

    public void set(int value) {
        this.val = value;
    }

    public void inc() {
        val++;
    }

    public void dec() {
        val--;
    }

    public boolean equals(Object obj) {
        if (obj instanceof MutableInt) {
            return val == ((MutableInt) obj).val;
        }
        return false;
    }

    public int hashCode() {
        return val;
    }

    public int compareTo(MutableInt other) {
        return val < other.val ? -1 : (val == other.val ? 0 : 1);
    }

    public String toString() {
        return String.valueOf(val);
    }

    public void add(int i) {
        val += i;
    }

    public void sub(int i) {
        val -= i;
    }
}
