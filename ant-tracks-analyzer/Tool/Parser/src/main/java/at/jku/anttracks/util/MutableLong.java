
package at.jku.anttracks.util;

public class MutableLong implements Comparable<MutableLong> {

    private long val;

    public MutableLong() {
        this(0);
    }

    public MutableLong(int value) {
        this((long) value);
    }

    public MutableLong(long value) {
        this.val = value;
    }

    public long get() {
        return val;
    }

    public void set(long value) {
        this.val = value;
    }

    public void inc() {
        val++;
    }

    public void dec() {
        val--;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (int) (val ^ (val >>> 32));
        return result;
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
        MutableLong other = (MutableLong) obj;
        if (val != other.val) {
            return false;
        }
        return true;
    }

    public int compareTo(MutableLong other) {
        return val < other.val ? -1 : (val == other.val ? 0 : 1);
    }

    public String toString() {
        return String.valueOf(val);
    }

    public void add(long i) {
        val += i;
    }
}
