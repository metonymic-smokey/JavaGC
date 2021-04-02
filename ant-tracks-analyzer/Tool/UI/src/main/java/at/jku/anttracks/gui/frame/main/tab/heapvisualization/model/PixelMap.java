
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.PaintTask;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.PointersTask;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.component.BigBufferedImage;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.component.HeapPanel;
import at.jku.anttracks.gui.model.DetailedHeapInfo;
import javafx.concurrent.Task;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.*;
import java.util.concurrent.CancellationException;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * @author Christina Rammerstorfer
 */
public abstract class PixelMap {
    public static final long UNDEFINED_CLUSTER_SIZE = 0;
    private static final int BASIC_PIXEL_SIZE = 3;
    private static final int MAX_PIXEL_SIZE = 40 * BASIC_PIXEL_SIZE;
    protected static final int MAXIMUM_OBJ_INFO = 10;
    protected ObjectVisualizationData data;
    private BufferedImage imageBuffer;
    private BufferedImage original;
    protected long[] selectedObjectsIDs;
    private final Set<Object> selectedClassifications;
    private boolean showPointers;
    private final Logger logger;
    private final JScrollPane containingComponent;
    private final DetailedHeapInfo detailsInfo;
    protected final LinkedList<ZoomStateData> zoomStack;

    protected PixelMap(DetailedHeapInfo detailsInfo, JScrollPane containingComponent, ObjectVisualizationData data, boolean showPointers) {
        this.data = data;
        this.detailsInfo = detailsInfo;
        logger = Logger.getLogger(getClass().getSimpleName() + " " + detailsInfo.getAppInfo().getSymbols().root + " @ " + detailsInfo.getTime());
        this.containingComponent = containingComponent;
        zoomStack = new LinkedList<>();
        // First level is always 1px pixel size and display all objects
        zoomStack.push(new ZoomStateData(UNDEFINED_CLUSTER_SIZE, BASIC_PIXEL_SIZE, 0, ZoomStateData.LAST_PIXEL));
        selectedClassifications = new HashSet<Object>();
        this.showPointers = showPointers;
    }

    public void zoomIn(PaintTask task) {
        if (zoomStack.peek().hasSelectedArea()) {
            int pixelSize = zoomStack.peek().pixelSize;
            int[] selection = zoomStack.peek().getSelectedArea();
            int row = selection[1] / pixelSize;
            int col = selection[0] / pixelSize;
            long startIdx = getRelativePixelIdxRowCol(col, row);
            row = selection[3] / pixelSize;
            col = selection[2] / pixelSize;
            long endIdx = getRelativePixelIdxRowCol(col, row);
            if (endIdx < 0) {
                endIdx = getPixelCount() - 1;
            }
            zoomIn(startIdx, endIdx, true, task);
        } else {
            zoomIn(0, getPixelCount() - 1, false, task);
        }
    }

    private void zoomIn(long startPixelIdx, long endPixelIdx, boolean adaptive, PaintTask task) {
        int height = Math.max(containingComponent.getViewport().getHeight(), HeapPanel.MIN_HEIGHT);
        int width = computeWidth();
        long pixelCount = (endPixelIdx - startPixelIdx + 1) * zoomStack.peek().clusterSize;
        int pixelSize = zoomStack.peek().pixelSize;
        int cols = width / pixelSize;
        int rows = height / pixelSize;
        int availablePixels = cols * rows;
        long newClusterSize;
        if (adaptive) {
            if (pixelCount > availablePixels) {
                newClusterSize = integerDivisionCeil(pixelCount, availablePixels);
                newClusterSize = toPow(newClusterSize);
            } else {
                newClusterSize = 1;
            }
            pixelCount = integerDivisionCeil(pixelCount, newClusterSize);
            while (pixelCount < availablePixels) {
                int nPxSize = pixelSize + 1;
                int nWidth = (width % nPxSize == 0) ? width : width - width % nPxSize;
                int nHeight = (height % nPxSize == 0) ? height : height - height % nPxSize;
                availablePixels = (nWidth * nHeight) / (nPxSize * nPxSize);
                if (pixelCount <= availablePixels) {
                    pixelSize++;
                }
            }
            logger.info("Automatically determined zoom cluster size: " + newClusterSize);
            logger.info("Automatically determined zoom pixel size: " + pixelSize);
        } else {
            // if(zoomStack.peek().clusterSize > 1){
            // newClusterSize = decreaseClusterSize(zoomStack.peek().clusterSize);
            // }
            // else{
            newClusterSize = zoomStack.peek().clusterSize;
            pixelSize += BASIC_PIXEL_SIZE;
            // }
        }
        if (zoomStack.size() == 1) {
            original = imageBuffer;
        }
        zoomStack.push(new ZoomStateData(newClusterSize, pixelSize, startPixelIdx, endPixelIdx));
        createImage(width, task);
    }

    public void zoomOut(PaintTask task) {
        zoomStack.pop();
        if (zoomStack.size() == 1 && original != null) {
            imageBuffer = original;
        } else {
            createImage(computeWidth(), task);
        }
    }

    private void createImage(int width, Task<?> task) {
        int pixelSize = zoomStack.peek().pixelSize;
        int cols = width / pixelSize;
        width = cols * pixelSize;
        int height;
        long rows = integerDivisionCeil(getPixelCount(), cols);
        if (rows * pixelSize > Integer.MAX_VALUE) {
            height = Integer.MAX_VALUE;
        } else {
            height = (int) (rows * pixelSize) + 1;
        }
        if (height > containingComponent.getViewport().getHeight()) {
            int scrollBarWidth = scrollBarWidth();
            width = width - scrollBarWidth;
            cols = width / pixelSize;
            width = cols * pixelSize;
            rows = integerDivisionCeil(getPixelCount(), cols);
            if (rows * pixelSize > Integer.MAX_VALUE) {
                height = Integer.MAX_VALUE;
            } else {
                height = (int) (rows * pixelSize) + 1;
            }
        }
        // Uncomment for debugging
        // height = height*2;
        try {
            File f = Files.createTempDirectory("anttracksvisualization").toFile();
            imageBuffer = BigBufferedImage.create(f, width, height, BufferedImage.TYPE_INT_RGB);
            f.deleteOnExit();
        } catch (IOException e) {
            imageBuffer = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            logger.warning("Could not create BigBufferedImage, used BufferedImage instead");
        }
        paintHeap(imageBuffer.getGraphics(), width, height, task);
    }

    private int scrollBarWidth() {
        Integer scrollBarWidth = (Integer) UIManager.get("ScrollBar.width");
        if (scrollBarWidth == null) {
            scrollBarWidth = containingComponent.getVerticalScrollBar().getWidth();
        }
        return scrollBarWidth;
    }

    private int computeWidth() {
        int width = containingComponent.getViewport().getWidth();
        if (containingComponent.getVerticalScrollBar().isShowing()) {
            width = width + scrollBarWidth();
        }
        width = Math.max(width, HeapPanel.MIN_WIDTH);
        return width;
    }

    private static long toPow(long c) {
        if (c > 1024) {
            while (c % 1024 > 0) {
                c++;
            }
            if (c > 1024 * 1024) {
                while (c % (1024 * 1024) > 0) {
                    c++;
                }
                if (c > 1024 * 1024 * 1024) {
                    while (c % (1024 * 1024 * 1024) > 0) {
                        c++;
                    }
                }
            }
        }
        return c;
    }

    public long computeClusterSize() {
        int width = computeWidth();
        int height = Math.max(containingComponent.getViewport().getHeight(), HeapPanel.MIN_HEIGHT);
        return computeClusterSize(width / BASIC_PIXEL_SIZE, height / BASIC_PIXEL_SIZE);
    }

    public long computeClusterSize(int width, int height) {
        long c = computePixelCount(1) / (width * height) + 1;
        c = toPow(c);
        while (integerDivisionCeil(computePixelCount(c), width) * width > Integer.MAX_VALUE) {
            c = increaseClusterSize(c);
        }
        return c;
    }

    public int computeWidth(int height, long clusterSize) {
        long count = computePixelCount(clusterSize);
        long width = integerDivisionCeil(count, height);
        if (width > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) width;
    }

    public int computeHeight(int width, long clusterSize) {
        long count = computePixelCount(clusterSize);
        long height = integerDivisionCeil(count, width);
        if (height > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) height;
    }

    public void exportPNG(int width, int height, long clusterSize, String fileName, Task<?> task) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        paintHeap(image.getGraphics(), width, height, clusterSize, 0, ZoomStateData.LAST_PIXEL, 1, task);
        File outputfile = new File(fileName);
        ImageIO.write(image, "png", outputfile);
    }

    public void paintMap(long newClusterSize, Task<?> task) {
        int width = computeWidth();
        long height;
        if (zoomStack.size() == 1) {
            // No zoom, set new cluster size
            if (newClusterSize == UNDEFINED_CLUSTER_SIZE) {// Set cluster size
                long c = computeClusterSize();
                if (c != zoomStack.peek().clusterSize) {
                    setClusterSize(c);
                }
                logger.info("Automatically determined cluster size: " + c);
            } else {
                if (newClusterSize != zoomStack.peek().clusterSize) {
                    setClusterSize(newClusterSize);
                }
            }
            height = integerDivisionCeil(getPixelCount(), width);
            if (height * width > Integer.MAX_VALUE) {
                // TODO Maybe notify view here?
                long clusterSize = increaseClusterSize(zoomStack.peek().clusterSize);
                height = integerDivisionCeil(getPixelCount(), width);
                while (height * width > Integer.MAX_VALUE) {
                    clusterSize = increaseClusterSize(clusterSize);
                    height = integerDivisionCeil(getPixelCount(), width);
                }
                setClusterSize(clusterSize);
            }
        }
        createImage(width, task);
    }

    private static long increaseClusterSize(long c) {
        if (c > 1024 * 1024 * 1024) {
            c += 1024 * 1024 * 1024;
        } else if (c > 1024 * 1024) {
            c += 1024 * 1024;
        } else if (c > 1024) {
            c += 1024;
        } else {
            c++;
        }
        return c;
    }

    @SuppressWarnings("unused")
    private static long decreaseClusterSize(long c) {
        if (c > 1024 * 1024 * 1024) {
            c -= 1024 * 1024 * 1024;
        } else if (c > 1024 * 1024) {
            c -= 1024 * 1024;
        } else if (c > 1024) {
            c -= 1024;
        } else {
            c--;
        }
        return c;
    }

    public abstract PixelDescription getPixel(int x, int y);

    protected abstract long computePixelCount(long clusterSize);

    protected abstract void paintHeap(Graphics g, int width, int height, Task<?> task);

    protected abstract void paintHeap(Graphics g, int width, int height, long clusterSize, long startIndex, long endIndex, int pixelSize, Task<?> task);

    public BufferedImage getImageBuffer() {
        return imageBuffer;
    }

    public long getClusterSize() {
        return zoomStack.peek().clusterSize;
    }

    private void setClusterSize(long clusterSize) {
        ZoomStateData zoomData = zoomStack.pop();
        zoomStack.push(new ZoomStateData(clusterSize, zoomData.pixelSize, zoomData.startIndex, zoomData.endIndex));
    }

    public ObjectVisualizationData getData() {
        return data;
    }

    public ClassifierChain getSelectedClassifiers() {
        return data.getSelectedClassifiers();
    }

    protected static long integerDivisionCeil(long dividend, long divisor) {
        return (dividend / divisor + (dividend % divisor == 0 ? 0 : 1));
    }

    /**
     * Returns the coordinates of the pixel which contains the specified point.
     *
     * @param x x coordinate of the point
     * @param y y coordinate of the point
     * @return
     */
    public Rectangle getPixelCoordinates(int x, int y) {
        int pixelSize = zoomStack.peek().pixelSize;
        int row = y / pixelSize;
        int col = x / pixelSize;
        if (getRelativePixelIdxRowCol(col, row) < 0) {
            return null;
        }
        return new Rectangle(col * pixelSize, row * pixelSize, pixelSize, pixelSize);
    }

    /**
     * Returns the coordinates of the pixel at the specified index.
     *
     * @param idx idx-th pixel in the currently displayed image buffer
     * @return
     */
    public Rectangle getPixelCoordinates(long idx) {
        int pixelSize = zoomStack.peek().pixelSize;
        int cols = imageBuffer.getWidth() / pixelSize;
        long row = idx / cols;
        long col = idx % cols;
        if (row > Integer.MAX_VALUE || col > Integer.MAX_VALUE) {
            // Should not be happening
            return null;
        }
        return new Rectangle((int) col * pixelSize, (int) row * pixelSize, pixelSize, pixelSize);
    }

    public Rectangle getLastPixelCoordinates() {
        return getPixelCoordinates(getPixelCount() - 1);
    }

    protected long getRelativePixelIdxRowCol(int col, int row) {
        int pixelSize = zoomStack.peek().pixelSize;
        int cols = imageBuffer.getWidth() / pixelSize;
        if (col >= cols) {
            return -1;
        }
        long pixelCount = getPixelCount();
        long res = row * cols + col;
        if (res >= pixelCount) {
            return -1;
        }
        return res;
    }

    public long getPixelCount() {
        ZoomStateData top = zoomStack.pop();
        long count = integerDivisionCeil((top.endIndex - top.startIndex + 1) * zoomStack.peek().clusterSize, top.clusterSize);
        zoomStack.push(top);
        return count;
    }

    protected long getPixelIdx(int x, int y) {
        int pixelSize = zoomStack.peek().pixelSize;
        int row = y / pixelSize;
        int col = x / pixelSize;
        return getRelativePixelIdxRowCol(col, row);
    }

    public abstract HeapStateObjectInfo[] getDetailedObjInfo(int x, int y);

    public boolean isZoomed() {
        return zoomStack.size() > 1;
    }

    public abstract void setSelectedObjects(int x, int y);

    public void resetObjects() {
        selectedObjectsIDs = null;
    }

    public abstract Rectangle[] getObjectCoordinates(int x, int y);

    public void generatePointers(int level, boolean toPointers, PointersTask task) {
        if (selectedObjectsIDs != null && selectedClassifications.isEmpty()) {
            data.generatePointers(selectedObjectsIDs, level, toPointers, task);
        } else if (!selectedClassifications.isEmpty()) {
            ArrayList<Long> selectedObjects = new ArrayList<Long>();
            for (long i = 0; i < data.getObjectCount(); i++) {
                if (task.isCancelled()) {
                    throw new CancellationException();
                }
                if (selectedClassifications.contains(data.getClassificationsForObject(i))) {
                    selectedObjects.add(i);
                }
            }
            if (selectedObjects != null) {
                for (int i = 0; i < selectedObjectsIDs.length; i++) {
                    if (task.isCancelled()) {
                        throw new CancellationException();
                    }
                    if (!selectedObjects.contains(selectedObjectsIDs[i])) {
                        selectedObjects.add(selectedObjectsIDs[i]);
                    }
                }
            }
            Long[] boxed = selectedObjects.toArray(new Long[0]);
            data.generatePointers(Stream.of(boxed).mapToLong(Long::longValue).toArray(), level, toPointers, task);
        }
    }

    public static Color toGreyScale(Color c) {
        int grey = (c.getBlue() + c.getGreen() + c.getRed()) / 3;
        return new Color(grey, grey, grey);
    }

    public boolean showPointers() {
        return showPointers;
    }

    public void setShowPointers(boolean showPointers) {
        this.showPointers = showPointers;
    }

    public int getPixelSize() {
        return zoomStack.peek().pixelSize;
    }

    public boolean canZoomIn() {
        return zoomStack.peek().pixelSize < MAX_PIXEL_SIZE;
    }

    public boolean canZoomOut() {
        return zoomStack.size() > 1;
    }

    public abstract void setSelectedArea(int startX, int startY, int endX, int endY);

    public int[] getSelectedArea() {
        return zoomStack.peek().getSelectedArea();
    }

    public void setData(ObjectVisualizationData data) {
        this.data = data;
    }

    public void resetZoom() {
        while (zoomStack.size() > 1) {
            zoomStack.pop();
        }
    }

    public int getCurrentPixelSize() {
        return zoomStack.peek().pixelSize / BASIC_PIXEL_SIZE;
    }

    protected long computeStartIndex() {
        long startIndex = 0;
        int next = 1;
        while (next < zoomStack.size()) {
            startIndex = startIndex + zoomStack.get(next - 1).startIndex * zoomStack.get(next).clusterSize;
            next++;
        }
        return startIndex;
    }

    protected int getColumns() {
        return imageBuffer.getWidth() / zoomStack.peek().pixelSize;
    }

    public abstract long getNumberOfObjectsAtPixel(int x, int y);

    public void resetClassifications() {
        selectedClassifications.clear();
    }

    public void addSelectedClassification(Object classification) {
        selectedClassifications.add(classification);
    }

    public void removeSelectedClassification(Object classification) {
        selectedClassifications.remove(classification);
    }

    public boolean hasSelectedClassifications() {
        return !selectedClassifications.isEmpty();
    }

    public Collection<?> getSelectedClassifications() {
        return Collections.unmodifiableCollection(selectedClassifications);
    }

    public boolean isClassificationSelected(Object classification) {
        return selectedClassifications.contains(classification);
    }

    @SuppressWarnings("unused")
    private boolean isObjectIDSelected(long id) {
        if (id < 0) {
            return false;
        }
        if (selectedObjectsIDs != null) {
            for (long l : selectedObjectsIDs) {
                if (l == id) {
                    return true;
                }
            }
        }
        return false;
    }

    protected boolean isSelected(Object classification, long objectId) {
        return isClassificationSelected(classification); //|| isObjectIDSelected(objectId);
    }

    public List<Filter> getSelectedFilters() {
        return data.getSelectedFilters();
    }

    public DetailedHeapInfo getDetailsInfo() {
        return detailsInfo;
    }

}
