
package at.jku.anttracks.gui.utils;

import javax.swing.*;
import java.awt.*;

public class CollapsibleButton extends JButton {

    private static final long serialVersionUID = 1L;
    private final Polygon expandedArrow;
    private final Polygon collapsedArrow;
    private boolean expanded;

    public CollapsibleButton(int orientation, int size) {
        if (orientation == CollapsiblePanel.NORTH) {
            this.expandedArrow = arrowDown(size);
            this.collapsedArrow = arrowUp(size);
        } else if (orientation == CollapsiblePanel.SOUTH) {
            this.expandedArrow = arrowUp(size);
            this.collapsedArrow = arrowDown(size);
        } else if (orientation == CollapsiblePanel.EAST) {
            this.expandedArrow = arrowRight(size);
            this.collapsedArrow = arrowLeft(size);
        } else {
            this.expandedArrow = arrowLeft(size);
            this.collapsedArrow = arrowRight(size);
        }
        this.expanded = true;
    }

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        g.setColor(Color.GRAY);
        if (expanded) {
            g.translate(getSize().width / 2, getSize().height);
            g.fillPolygon(expandedArrow);
            this.setCursor(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR));
        } else {
            g.translate(getSize().width / 2, 0);
            g.fillPolygon(collapsedArrow);
            this.setCursor(Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR));
        }
        g.dispose();
    }

    public void updateExpanded(boolean expanded) {
        this.expanded = expanded;
    }

    private Polygon arrowDown(int size) {
        int[] xPoints = {0, size, -size};
        int[] yPoints = {0, -size, -size};
        return new Polygon(xPoints, yPoints, 3);
    }

    private Polygon arrowUp(int size) {
        int[] xPoints = {0, -size, size};
        int[] yPoints = {0, size, size};
        return new Polygon(xPoints, yPoints, 3);
    }

    private Polygon arrowLeft(int size) {
        int[] xPoints = {0, size, size};
        int[] yPoints = {0, size, -size};
        return new Polygon(xPoints, yPoints, 3);
    }

    private Polygon arrowRight(int size) {
        int[] xPoints = {0, -size, -size};
        int[] yPoints = {0, -size, size};
        return new Polygon(xPoints, yPoints, 3);
    }

}
