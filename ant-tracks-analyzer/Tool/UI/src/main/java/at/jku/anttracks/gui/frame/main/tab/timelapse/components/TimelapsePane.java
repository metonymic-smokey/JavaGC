
package at.jku.anttracks.gui.frame.main.tab.timelapse.components;

import at.jku.anttracks.gui.frame.main.tab.timelapse.model.TimelapseModel;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * @author Christina Rammerstorfer
 */
public class TimelapsePane extends JComponent {
    private static final long serialVersionUID = 5782861094854964575L;
    private static final int MIN_WIDTH = 1000;
    private static final int MIN_HEIGHT = 100;
    private TimelapseModel model;

    public void setModel(TimelapseModel model) {
        this.model = model;
    }

    @Override
    public void paint(Graphics g) {
        if (model == null) {
            return;
        }
        BufferedImage image = model.getCurrentImage();
        Rectangle r = g.getClipBounds();
        int width = image.getWidth();
        int height = image.getHeight();
        BufferedImage imageClip = image.getSubimage(r.x, r.y, Math.min(r.width, width), r.height > height ? height - 1 : Math.min(r.height, height - r.y - 1));
        g.drawImage(imageClip, r.x, r.y, null);
    }

    @Override
    public Dimension getPreferredSize() {
        if (model == null) {
            return new Dimension(MIN_WIDTH, MIN_HEIGHT);
        }
        return new Dimension(model.getCurrentImage().getWidth(), model.getCurrentImage().getHeight());
    }

}
