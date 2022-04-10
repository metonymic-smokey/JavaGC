
/**
 *
 */
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

import java.awt.color.ColorSpace;

/**
 * @author Christina Rammerstorfer
 * Some code taken from http://www.camick.com/java/source/HSLColor.java
 */
public class HSLColorSpace extends ColorSpace {

    private static final long serialVersionUID = 1L;

    public HSLColorSpace() {
        super(ColorSpace.TYPE_HLS, 3);
    }

    /* (non-Javadoc)
     * @see java.awt.color.ColorSpace#toRGB(float[])
     */
    @Override
    public float[] toRGB(float[] colorvalue) {
        float h = colorvalue[0];
        float s = colorvalue[1];
        float l = colorvalue[2];
        if (s < 0.0f || s > 100.0f) {
            String message = "Color parameter outside of expected range - Saturation";
            throw new IllegalArgumentException(message);
        }

        if (l < 0.0f || l > 100.0f) {
            String message = "Color parameter outside of expected range - Luminance";
            throw new IllegalArgumentException(message);
        }
        //  Formula needs all values between 0 - 1.

        h = h % 360.0f;
        h /= 360f;
        s /= 100f;
        l /= 100f;

        float q = 0;

        if (l < 0.5) {
            q = l * (1 + s);
        } else {
            q = (l + s) - (s * l);
        }

        float p = 2 * l - q;

        float r = Math.max(0, hueToRGB(p, q, h + (1.0f / 3.0f)));
        float g = Math.max(0, hueToRGB(p, q, h));
        float b = Math.max(0, hueToRGB(p, q, h - (1.0f / 3.0f)));

        r = Math.min(r, 1.0f);
        g = Math.min(g, 1.0f);
        b = Math.min(b, 1.0f);

        return new float[]{r, g, b};
    }

    private static float hueToRGB(float p, float q, float h) {
        if (h < 0) {
            h += 1;
        }

        if (h > 1) {
            h -= 1;
        }

        if (6 * h < 1) {
            return p + ((q - p) * 6 * h);
        }

        if (2 * h < 1) {
            return q;
        }

        if (3 * h < 2) {
            return p + ((q - p) * 6 * ((2.0f / 3.0f) - h));
        }

        return p;
    }

    /* (non-Javadoc)
     * @see java.awt.color.ColorSpace#fromRGB(float[])
     */
    @Override
    public float[] fromRGB(float[] rgbvalue) {
        //  Get RGB values in the range 0 - 1
        float r = rgbvalue[0];
        float g = rgbvalue[1];
        float b = rgbvalue[2];

        //	Minimum and Maximum RGB values are used in the HSL calculations

        float min = Math.min(r, Math.min(g, b));
        float max = Math.max(r, Math.max(g, b));

        //  Calculate the Hue

        float h = 0;

        if (max == min) {
            h = 0;
        } else if (max == r) {
            h = ((60 * (g - b) / (max - min)) + 360) % 360;
        } else if (max == g) {
            h = (60 * (b - r) / (max - min)) + 120;
        } else if (max == b) {
            h = (60 * (r - g) / (max - min)) + 240;
        }

        //  Calculate the Luminance

        float l = (max + min) / 2;

        //  Calculate the Saturation

        float s = 0;

        if (max == min) {
            s = 0;
        } else if (l <= .5f) {
            s = (max - min) / (max + min);
        } else {
            s = (max - min) / (2 - max - min);
        }

        return new float[]{h, s * 100, l * 100};
    }

    @Override
    public float[] toCIEXYZ(float[] colorvalue) {
        return null;
    }

    @Override
    public float[] fromCIEXYZ(float[] colorvalue) {
        return null;
    }

}
