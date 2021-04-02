
package at.jku.anttracks.heap.symbols;

public class ClassDefinition {

    public static final int MAGIC_BYTE = 14;

    public final int id;
    public final byte[] data;

    public ClassDefinition(int id, byte[] data) {
        this.id = id;
        this.data = data;
    }

    @Override
    public String toString() {
        return "Class definition " + id;
    }
}
