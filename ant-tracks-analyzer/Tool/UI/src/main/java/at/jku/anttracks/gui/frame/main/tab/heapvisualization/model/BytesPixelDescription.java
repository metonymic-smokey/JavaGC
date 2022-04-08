
/**
 *
 */
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

/**
 * @author Christina Rammerstorfer
 */
public class BytesPixelDescription {
    final PixelDescription pd;
    final long bytes;
    final long objectID;

    public BytesPixelDescription(PixelDescription pd, long bytes, long objectID) {
        this.pd = pd;
        this.bytes = bytes;
        this.objectID = objectID;
    }
}
