
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.component;

import at.jku.anttracks.gui.frame.main.tab.heapvisualization.HeapVisualizationTab;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.PointersTask.PointerOperation;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.component.listener.HeapPanelEvent;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.component.listener.HeapPanelListener;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.PixelDescription;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.PixelMap;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.VisualizationModel;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Christina Rammerstorfer
 */
public class HeapPanel extends JComponent {

    public static final int MIN_WIDTH = 1000;
    public static final int MIN_HEIGHT = 100;

    private static final long serialVersionUID = 1L;

    private final VisualizationModel model;
    private final HeapVisualizationTab heapVisualizationTab;

    private int width;
    private int height;

    private int clickX, clickY;

    private int selectionStartX, selectionStartY, selectionEndX, selectionEndY;

    private final List<HeapPanelListener> eventListeners;

    private final HeapMouseAdapter mouseAdapter;

    public HeapPanel(VisualizationModel model, HeapVisualizationTab heapVisualizationTab) {
        this.model = model;
        this.heapVisualizationTab = heapVisualizationTab;
        width = MIN_WIDTH;
        height = MIN_HEIGHT;
        eventListeners = new ArrayList<HeapPanelListener>();
        mouseAdapter = new HeapMouseAdapter();
        addMouseMotionListener(mouseAdapter);
        addMouseListener(mouseAdapter);
        clickX = -1;
        clickY = -1;
        selectionStartX = -1;
        selectionStartY = -1;
        selectionEndX = -1;
        selectionEndY = -1;
    }

    public void setLoading(boolean loading) {
        if (loading) {
            removeMouseListener(mouseAdapter);
            removeMouseMotionListener(mouseAdapter);
        } else {
            addMouseMotionListener(mouseAdapter);
            addMouseListener(mouseAdapter);
        }
    }

    @Override
    public int getWidth() {
        return width;
    }

    @Override
    public int getHeight() {
        return height;
    }

    public void updateWidthAndHeight() {
        PixelMap currentPixelMap = model.getCurrentPixelMap();
        if (currentPixelMap == null) {
            return;
        }
        Image image = currentPixelMap.getImageBuffer();
        width = image.getWidth(null);
        height = image.getHeight(null);
        selectionStartX = -1;
        selectionStartY = -1;
        selectionEndX = -1;
        selectionEndY = -1;
    }

    @Override
    public void paint(Graphics g) {
        Rectangle r = g.getClipBounds();
        PixelMap currentPixelMap = model.getCurrentPixelMap();
        if (currentPixelMap == null) {
            return;
        }
        BufferedImage image = currentPixelMap.getImageBuffer();
        width = image.getWidth();
        height = image.getHeight();
        BufferedImage imageClip = image.getSubimage(r.x, r.y, Math.min(r.width, width), Math.min(r.height, height - r.y - 1));
        g.drawImage(imageClip, r.x, r.y, null);
        int pixelSize = currentPixelMap.getPixelSize();
        int[] selection = currentPixelMap.getSelectedArea();
        int selectionStartX = selection[0];
        int selectionStartY = selection[1];
        int selectionEndX = selection[2];
        int selectionEndY = selection[3];
        if (r.contains(selectionStartX, selectionStartY)) {
            for (int i = selectionStartX; i < width; i += pixelSize) {
                Color c = new Color(image.getRGB(i, selectionStartY)).darker();
                g.setColor(c);
                g.fillRect(i, selectionStartY, pixelSize, pixelSize);
            }
        }
        if (selectionStartY != -1 && selectionEndY != -1) {
            for (int i = selectionStartY + pixelSize; i < selectionEndY && i < height; i += pixelSize) {
                for (int j = 0; j < width; j++) {
                    Color c = new Color(image.getRGB(j, i)).darker();
                    g.setColor(c);
                    g.fillRect(j, i, pixelSize, pixelSize);
                }
            }
        }
        if (r.contains(selectionEndX, selectionEndY)) {
            for (int i = 0; i < selectionEndX + pixelSize; i += pixelSize) {
                Color c = new Color(image.getRGB(i, selectionEndY)).darker();
                g.setColor(c);
                g.fillRect(i, selectionEndY, pixelSize, pixelSize);
            }
        }
        Rectangle[] coords = currentPixelMap.getObjectCoordinates(clickX, clickY);
        if (coords != null) {
            for (Rectangle c : coords) {
                g.setColor(Color.BLACK);
                g.fillRect(c.x, c.y, c.width, c.height);
            }
        }
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(width, height);
    }

    private class HeapMouseAdapter extends MouseAdapter {
        private boolean mouseClicked;

        @Override
        public void mouseMoved(MouseEvent e) {
            if (!mouseClicked) {
                notifyListeners(new HeapPanelEvent(e.getID(), e.getX(), e.getY()));
            }
        }

        @Override
        public void mouseExited(MouseEvent e) {
            if (!mouseClicked) {
                notifyListeners(new HeapPanelEvent(e.getID(), -1, -1));
            }
        }

        @Override
        public void mouseClicked(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                PixelMap m = model.getCurrentPixelMap();
                PixelDescription px = m.getPixel(e.getX(), e.getY());
                boolean inside = px != null && px != PixelDescription.GAP_PD && px.classification != PixelDescription.FILTERED;
                // view.setShowPointersEnabled(inside);
                if (inside) {
                    mouseClicked = true;
                    clickX = e.getX();
                    clickY = e.getY();
                    m.setSelectedObjects(clickX, clickY);
                    if (heapVisualizationTab.showPointers()) {
                        heapVisualizationTab.startPointersTask(PointerOperation.BOTH);
                    }
                    repaint();
                    notifyListeners(new HeapPanelEvent(e.getID(), clickX, clickY));
                }
                if (m != null) {
                    selectionStartX = -1;
                    selectionStartY = -1;
                    selectionEndX = -1;
                    selectionEndY = -1;
                    m.setSelectedArea(-1, -1, -1, -1);
                    repaint();
                }
            }
        }

        @Override
        public void mouseEntered(MouseEvent e) {
            mouseClicked = false;
        }

        @Override
        public void mousePressed(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                PixelMap m = model.getCurrentPixelMap();
                if (m != null) {
                    Rectangle r = m.getPixelCoordinates(e.getX(), e.getY());
                    if (r != null) {
                        selectionStartX = r.x;
                        selectionStartY = r.y;
                    }
                }
            }

        }

        @Override
        public void mouseReleased(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON1) {
                PixelMap m = model.getCurrentPixelMap();
                if (m != null) {
                    Rectangle r = m.getPixelCoordinates(e.getX(), e.getY());
                    if (r == null) {
                        r = m.getLastPixelCoordinates();
                    }
                    selectionEndX = r.x;
                    selectionEndY = r.y;
                    if (selectionStartY < selectionEndY) {
                        m.setSelectedArea(selectionStartX, selectionStartY, selectionEndX, selectionEndY);
                        repaint();
                    }
                }
            }
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            PixelMap m = model.getCurrentPixelMap();
            if (m != null) {
                Rectangle r = m.getPixelCoordinates(e.getX(), e.getY());
                if (r == null) {
                    r = m.getLastPixelCoordinates();
                }
                selectionEndX = r.x;
                selectionEndY = r.y;
                if (selectionStartY < selectionEndY) {
                    m.setSelectedArea(selectionStartX, selectionStartY, selectionEndX, selectionEndY);
                    repaint();
                }

            }
        }

    }

    @SuppressWarnings("unused")
    private boolean isInZoomArea(int x, int y) {
        if (selectionEndX == -1) {
            return false;
        }
        if (y == selectionStartY && x >= selectionStartX) {
            return true;
        }
        if (y == selectionEndY && x <= selectionEndX) {
            return true;
        }
        Rectangle r = new Rectangle(0, selectionStartY + 1, width, selectionEndY - selectionStartY);
        return r.contains(x, y);
    }

    private void notifyListeners(HeapPanelEvent evt) {
        for (HeapPanelListener l : eventListeners) {
            if (evt.id == MouseEvent.MOUSE_CLICKED) {
                l.mouseClicked(evt);
            }
            l.mouseMoved(evt);
        }
    }

    public void addHeapPanelListener(HeapPanelListener listener) {
        eventListeners.add(listener);
    }

    public void removeHeapPanelListener(HeapPanelListener listener) {
        eventListeners.remove(listener);
    }

    public void resetPixel() {
        clickX = -1;
        clickY = -1;
    }

}
