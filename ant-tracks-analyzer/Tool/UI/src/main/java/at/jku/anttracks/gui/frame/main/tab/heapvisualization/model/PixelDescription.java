
/**
 *
 */
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

import java.awt.*;

/**
 * @author Christina Rammerstorfer
 */
public class PixelDescription {
    public static final String GAP = "";
    public static final String FILTERED = "filtered object";
    public static final PixelDescription GAP_PD = new PixelDescription(Color.WHITE, GAP, -1);
    public Color color;
    public final Object classification;
    public final long id;
    public final boolean greyedOut;

    public PixelDescription(Color color, Object classification, long id, boolean greyedOut) {
        super();
        this.color = color;
        this.classification = classification;
        this.id = id;
        this.greyedOut = greyedOut;
    }

    public PixelDescription(Color color, Object classification, long id) {
        super();
        this.color = color;
        this.classification = classification;
        this.id = id;
        greyedOut = false;
    }

}
