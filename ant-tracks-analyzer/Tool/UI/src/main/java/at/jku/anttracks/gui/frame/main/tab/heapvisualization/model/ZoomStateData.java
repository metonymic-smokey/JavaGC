
/**
 *
 */
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

/**
 * @author Christina Rammerstorfer
 */
public class ZoomStateData {

    public static final long LAST_PIXEL = -2;

    public final long clusterSize;
    public final int pixelSize;
    public final long startIndex, endIndex;
    private int selectionStartX, selectionStartY, selectionEndX, selectionEndY;

    public ZoomStateData(long clusterSize, int pixelSize, long startIndex, long endIndex) {
        this.clusterSize = clusterSize;
        this.pixelSize = pixelSize;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        setSelectedArea(-1, -1, -1, -1);
    }

    public void setSelectedArea(int startX, int startY, int endX, int endY) {
        selectionStartX = startX;
        selectionStartY = startY;
        selectionEndX = endX;
        selectionEndY = endY;
    }

    public int[] getSelectedArea() {
        return new int[]{selectionStartX, selectionStartY, selectionEndX, selectionEndY};
    }

    public boolean hasSelectedArea() {
        return selectionStartX > -1 && selectionStartY > -1 && selectionEndX > -1 && selectionEndY > -1;
    }

}
