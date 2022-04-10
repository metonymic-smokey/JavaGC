
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.component;

import at.jku.anttracks.gui.frame.main.tab.heapvisualization.HeapVisualizationTab;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.PointersTask.PointerOperation;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.PixelDescription;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.VisualizationModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;

/**
 * @author Christina Rammerstorfer
 */
public class HeapKeyDescriptionPanel extends JComponent {
    public static final int MIN_WIDTH = 350;
    private static final int BORDER_X = 10;
    private static final int BORDER_Y = 15;
    private static final int LINE_HEIGHT = 20;
    private static final int TEXT_MARGIN = 10;
    private static final int TEXT_Y_CORRECTION = 10;
    private static final long serialVersionUID = 1L;
    private final VisualizationModel model;
    private final HeapVisualizationTab tab;
    private PixelDescription[] usedColors;
    private LayoutData[] coordinates;
    private int hoovered;
    private int height;
    private int width;
    private JPopupMenu menu;

    private static class LayoutData extends Rectangle {
        private static final long serialVersionUID = 1L;
        public final boolean selected;

        public LayoutData(int x, int y, int width, int height, boolean selected) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.selected = selected;
        }

    }

    public HeapKeyDescriptionPanel(VisualizationModel model, HeapVisualizationTab tab) {
        this.model = model;
        this.tab = tab;
        height = HeapKeyPanel.HEIGHT;
        width = MIN_WIDTH;
        hoovered = -1;
        MouseAdapter m = new MouseAdapter();
        addMouseListener(m);
        addMouseMotionListener(m);
    }

    public void setUsedColors(PixelDescription[] usedColors) {
        hoovered = -1;
        if (menu != null) {
            menu.setVisible(false);
        }
        this.usedColors = usedColors;
        height = BORDER_Y + usedColors.length * LINE_HEIGHT;
        FontMetrics fm = getFontMetrics(getFont());
        int max = 0;
        for (PixelDescription c : usedColors) {
            int width = fm.stringWidth(c.classification.toString());
            if (width > max) {
                max = width;
            }
        }
        width = BORDER_X + HeapKeyPanel.PIXEL_LENGTH + TEXT_MARGIN * 2 + max;
        coordinates = new LayoutData[usedColors.length];
    }

    @Override
    public void paint(Graphics g) {
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, Math.max(width, MIN_WIDTH), Math.max(height, HeapKeyPanel.HEIGHT));
        int i = 0;
        if (usedColors != null) {
            for (PixelDescription c : usedColors) {
                boolean isSelected = model.getCurrentPixelMap().isClassificationSelected(c.classification);
                g.setColor(c.color);
                g.fillRect(BORDER_X, BORDER_Y + i * LINE_HEIGHT, HeapKeyPanel.PIXEL_LENGTH, HeapKeyPanel.PIXEL_LENGTH);
                g.setColor(Color.BLACK);
                String classification = c.classification.toString();
                int x = BORDER_X + HeapKeyPanel.PIXEL_LENGTH + TEXT_MARGIN;
                int y = BORDER_Y + i * LINE_HEIGHT + TEXT_Y_CORRECTION;
                coordinates[i] = new LayoutData(x - 2, y - LINE_HEIGHT + 4, g.getFontMetrics().stringWidth(classification) + 4, LINE_HEIGHT, isSelected);
                if (i == hoovered) {
                    g.setColor(Color.LIGHT_GRAY);
                    g.fillRect(coordinates[i].x, coordinates[i].y, coordinates[i].width, coordinates[i].height);
                } else if (isSelected) {
                    g.setColor(Color.GRAY);
                    g.fillRect(coordinates[i].x, coordinates[i].y, coordinates[i].width, coordinates[i].height);
                }
                g.setColor(Color.BLACK);
                g.drawRect(coordinates[i].x, coordinates[i].y, coordinates[i].width, coordinates[i].height);
                g.drawString(classification, x, y);
                i++;
            }
        }

    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    private void createPopupMenu(int x, int y, int index) {
        if (menu != null) {
            menu.setVisible(false);
        }
        menu = new JPopupMenu();
        JMenuItem item;
        if (coordinates[index].selected) {
            item = new JMenuItem("deselect all");
            item.addActionListener(e -> {
                model.getCurrentPixelMap().removeSelectedClassification(usedColors[index].classification);
                if (model.getCurrentPixelMap().showPointers()) {
                    tab.startPointersTask(PointerOperation.BOTH);
                } else {
                    tab.startPaintTask();
                }
            });
        } else {
            item = new JMenuItem("select all");
            item.addActionListener(e -> {
                model.getCurrentPixelMap().addSelectedClassification(usedColors[index].classification);
                if (model.getCurrentPixelMap().showPointers()) {
                    tab.startPointersTask(PointerOperation.BOTH);
                } else {
                    tab.startPaintTask();
                }
            });
        }
        menu.add(item);
        menu.show(this, x, y);
    }

    private class MouseAdapter implements MouseListener, MouseMotionListener {

        @Override
        public void mouseClicked(MouseEvent e) {
            int i = 0;
            for (Rectangle r : coordinates) {
                if (r != null) {
                    if (r.contains(e.getX(), e.getY())) {
                        repaint();
                        if (e.getButton() == MouseEvent.BUTTON3) {
                            createPopupMenu(e.getX(), e.getY(), i);
                        }
                        return;
                    }
                }
                i++;
            }
        }

        @Override
        public void mousePressed(MouseEvent e) {
        }

        @Override
        public void mouseReleased(MouseEvent e) {
        }

        @Override
        public void mouseEntered(MouseEvent e) {}

        @Override
        public void mouseExited(MouseEvent e) {}

        @Override
        public void mouseDragged(MouseEvent e) {
        }

        @Override
        public void mouseMoved(MouseEvent e) {
            int i = 0;
            for (Rectangle r : coordinates) {
                if (r != null) {
                    if (r.contains(e.getX(), e.getY())) {
                        hoovered = i;
                        repaint();
                        return;
                    }
                }
                i++;
            }
            hoovered = -1;
        }

    }

}
