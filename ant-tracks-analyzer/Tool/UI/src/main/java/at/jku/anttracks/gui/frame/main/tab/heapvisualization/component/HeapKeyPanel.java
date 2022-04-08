
/**
 *
 */
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.component;

import at.jku.anttracks.gui.frame.main.tab.heapvisualization.component.listener.HeapPanelEvent;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.component.listener.HeapPanelListener;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.PixelDescription;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.PixelMap;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.VisualizationModel;

import javax.swing.*;
import java.awt.*;
import java.util.HashSet;
import java.util.Set;

/**
 * @author Christina Rammerstorfer
 */
public class HeapKeyPanel extends JComponent implements HeapPanelListener {
    private static final int BORDER_X = 15, BORDER_Y = 15;
    public static final int PIXEL_LENGTH = 10, SIZE = 5, HEIGHT = 80, WIDTH = 70;
    private static final long serialVersionUID = -6547153035564772839L;

    private final VisualizationModel model;

    private int curX, curY;

    private final HeapKeyDescriptionPanel descriptionPanel;
    private final JScrollPane descriptionPanelScrollPane;

    public HeapKeyPanel(VisualizationModel model, HeapKeyDescriptionPanel descriptionPanel, JScrollPane descriptionPanelScrollPane) {
        this.model = model;
        curX = -1;
        curY = -1;
        this.descriptionPanel = descriptionPanel;
        this.descriptionPanelScrollPane = descriptionPanelScrollPane;
    }

    @Override
    public void paint(Graphics g) {
        // draw background white
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, WIDTH, HEIGHT);
        Set<PixelDescription> usedColors = new HashSet<PixelDescription>();
        if (curX > -1 && curY > -1) {
            int drawX = BORDER_X;
            int drawY = BORDER_Y;
            int x = curX - SIZE / 2;
            int y = curY - SIZE / 2;
            int endX = curX + SIZE / 2;
            int endY = curY + SIZE / 2;
            for (int i = x; i <= endX; i++) {
                drawY = BORDER_Y;
                for (int j = y; j <= endY; j++) {
                    if (i < 0 || j < 0) {
                        g.setColor(Color.WHITE);
                        g.fillRect(drawX, drawY, PIXEL_LENGTH, PIXEL_LENGTH);
                    } else {
                        PixelDescription c = getColorForCoordinates(i, j);
                        if (c != null) {
                            if (c != PixelDescription.GAP_PD && !c.greyedOut) {
                                usedColors.add(c);
                            }
                            g.setColor(c.color);
                            g.fillRect(drawX, drawY, PIXEL_LENGTH, PIXEL_LENGTH);
                        }

                    }
                    drawY += PIXEL_LENGTH;
                }
                drawX += PIXEL_LENGTH;
            }

        }
        descriptionPanel.setUsedColors(usedColors.toArray(new PixelDescription[0]));
        descriptionPanel.repaint();
        descriptionPanelScrollPane.repaint();
        descriptionPanelScrollPane.revalidate();
    }

    private PixelDescription getColorForCoordinates(int i, int j) {
        PixelMap currentPixelMap = model.getCurrentPixelMap();
        if (currentPixelMap != null) {
            return currentPixelMap.getPixel(i, j);
        }
        return null;
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    public Dimension getMaximumSize() {
        return new Dimension(WIDTH, HEIGHT);
    }

    @Override
    public void mouseMoved(HeapPanelEvent evt) {
        curX = evt.x;
        curY = evt.y;
        repaint();
    }

    @Override
    public void mouseClicked(HeapPanelEvent evt) {

    }

}
