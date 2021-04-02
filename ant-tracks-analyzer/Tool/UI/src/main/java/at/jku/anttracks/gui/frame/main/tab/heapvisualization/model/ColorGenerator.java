
/**
 *
 */
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

import java.awt.*;
import java.awt.color.ColorSpace;

/**
 * @author christina
 */
public class ColorGenerator {

    private final Color startColor;
    @SuppressWarnings("unused")
    private final float[] startValues;

    private Color currentColor;

    public ColorGenerator(Color start) {
        startColor = start;
        startValues = startColor.getColorComponents(null);
    }

    public synchronized Color getNextColor() {
        if (currentColor == null) {
            currentColor = startColor;
        } else {
            ColorSpace hsl = new HSLColorSpace();
            float[] hslvalues = hsl.fromRGB(currentColor.getColorComponents(null));
            hslvalues[0] = (hslvalues[0] + 55.0f) % 360.0f;
            //			if((int)hslvalues[0] == (int)startValues[0]){
            //				hslvalues[2] = (hslvalues[2]+4)%101.f;
            //			}
            float[] rgb = hsl.toRGB(hslvalues);
            currentColor = new Color(rgb[0], rgb[1], rgb[2]);
        }
        return currentColor;
    }
}
