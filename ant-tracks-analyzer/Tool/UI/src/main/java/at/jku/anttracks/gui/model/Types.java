
package at.jku.anttracks.gui.model;

import java.util.Arrays;

public class Types implements Comparable<Types> {

    public final String[] types;

    public Types(String... types) {
        if (types == null) {
            throw new NullPointerException();
        }
        types = types.clone();
        Arrays.sort(types);
        this.types = types;
    }

    @Override
    public int compareTo(Types that) {
        for (int i = 0; i < Math.min(this.types.length, that.types.length); i++) {
            int diff = this.types[i].compareTo(that.types[i]);
            if (diff != 0) {
                return diff;
            }
        }
        return that.types.length - this.types.length;
    }

    @Override
    public String toString() {
        return Arrays.toString(types);
    }
}
