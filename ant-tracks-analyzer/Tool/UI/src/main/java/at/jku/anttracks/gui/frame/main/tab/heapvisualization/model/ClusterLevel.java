
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

/**
 * @author Christina Rammerstorfer
 */
public enum ClusterLevel {
    OBJECTS(ObjectPixelMap.class, "object(s)"),
    BYTES(BytePixelMap.class, "byte(s)");

    public final Class<? extends PixelMap> value;
    private String label;

    ClusterLevel(Class<? extends PixelMap> value, String label) {
        this.value = value;
        this.label = label;
    }

    @Override
    public String toString() {
        return label;
    }

}
