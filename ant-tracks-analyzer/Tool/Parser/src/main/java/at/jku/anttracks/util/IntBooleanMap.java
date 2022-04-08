
package at.jku.anttracks.util;

import java.util.Arrays;

public class IntBooleanMap {

    private final long[] data;
    private final int min;

    public IntBooleanMap(int min, int max, boolean initial) {
        this.data = new long[(max - min) / (8 * 8) + 1];
        this.min = min;
        Arrays.fill(data, initial ? (~0) : (0));
    }

    public boolean get(int key) {
        key = key - min;
        int index = key / (8 * 8);
        int shift = key % (8 * 8);
        return ((data[index] >> shift) & 1) != 0;
    }

    public boolean set(int key, boolean value) {
        key = key - min;
        int index = key / (8 * 8);
        int shift = key % (8 * 8);
        data[index] = value ? (data[index] | (1 << shift)) : (data[index] & ~(1 << shift));
        return value;
    }

}
