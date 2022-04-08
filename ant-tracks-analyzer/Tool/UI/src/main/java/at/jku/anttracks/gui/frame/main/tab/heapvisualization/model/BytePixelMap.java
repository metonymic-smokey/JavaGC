
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

import at.jku.anttracks.gui.model.DetailedHeapInfo;
import javafx.concurrent.Task;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Christina Rammerstorfer
 */
public class BytePixelMap extends PixelMap {

    private int curX, curY;
    private boolean isInPointerSet, isSelected;

    public BytePixelMap(DetailedHeapInfo detailsInfo, JScrollPane container, ObjectVisualizationData data, boolean showPointers) {
        super(detailsInfo, container, data, showPointers);
    }

    @Override
    public PixelDescription getPixel(int x, int y) {
        long idx = getPixelIdx(x, y);
        long clusterSize = zoomStack.peek().clusterSize;
        if (clusterSize == UNDEFINED_CLUSTER_SIZE || idx >= getPixelCount() || idx < 0) {
            return null;
        }
        long startIndex = computeStartIndex();
        if (clusterSize == 1) {
            PixelDescription p = data.getPixelDescriptionForByte(idx * clusterSize + startIndex);
            if (showPointers() && !data.isPointerAddress(idx * clusterSize + startIndex)) {
                return new PixelDescription(toGreyScale(p.color), p.classification, p.id, true);
            } else {
                return p;
            }
        }
        AtomicBoolean isInPointerSet = new AtomicBoolean(false);
        Map<PixelDescription, Long> bytes = data.getPixelDescriptionsForBytes(idx * clusterSize + startIndex, clusterSize, showPointers(), isInPointerSet);
        PixelDescription p = getPixelDescription(bytes);
        if (showPointers() && !isInPointerSet.get()) {
            return new PixelDescription(toGreyScale(p.color), p.classification, p.id, true);
        } else {
            return p;
        }
    }

    @Override
    public long getPixelCount() {
        if (zoomStack.size() > 1) {// We're zoomed, let superclass take care of it
            return super.getPixelCount();
        } else {
            long clusterSize = zoomStack.peek().clusterSize;
            if (clusterSize == UNDEFINED_CLUSTER_SIZE) {
                return data.getByteCount();
            }
            return integerDivisionCeil(data.getByteCount(), clusterSize);
        }
    }

    @Override
    protected void paintHeap(Graphics g, int width, int height, Task<?> task) {
        ZoomStateData top = zoomStack.peek();
        int pixelSize = top.pixelSize;
        long clusterSize = top.clusterSize;
        if (top.endIndex == ZoomStateData.LAST_PIXEL) {
            paintHeap(g, width, height, clusterSize, 0, ZoomStateData.LAST_PIXEL, pixelSize, task);
        } else {
            long startIndex = computeStartIndex(), endIndex;
            top = zoomStack.pop();
            endIndex = ((top.endIndex - top.startIndex + 1) * zoomStack.peek().clusterSize) + startIndex;
            zoomStack.push(top);
            paintHeap(g, width, height, clusterSize, startIndex, endIndex, pixelSize, task);
        }

    }

    @Override
    protected void paintHeap(Graphics g, int width, int height, long clusterSize, long startIndex, long endIndex, int pixelSize, Task<?> task) {
        // Paint background white
        g.setColor(Color.WHITE);
        g.fillRect(0, height - 1 - pixelSize, width, pixelSize);
        curX = 0;
        curY = 0;
        Iterator<BytesPixelDescription> iter;
        if (endIndex == ZoomStateData.LAST_PIXEL) {
            iter = data.iterator();
        } else {
            iter = data.iterator(startIndex, endIndex);
        }
        int cols = width / pixelSize;
        if (clusterSize == 1) {
            while (iter.hasNext()) {
                if (task.isCancelled()) {
                    throw new CancellationException();
                }
                BytesPixelDescription p = iter.next();
                if (isSelected(p.pd.classification, p.objectID)) {
                    g.setColor(Color.BLACK);
                } else if (showPointers() && p.pd != PixelDescription.GAP_PD && !data.isPointerAddress(p.objectID)) {
                    g.setColor(toGreyScale(p.pd.color));
                } else {
                    g.setColor(p.pd.color);
                }
                long remainingBytes = p.bytes;
                while (remainingBytes > 0) {
                    if (task.isCancelled()) {
                        throw new CancellationException();
                    }
                    int pixels = (int) Math.min((cols - curX / pixelSize), remainingBytes);
                    g.fillRect(curX, curY, pixels * pixelSize, pixelSize);
                    curX += pixels * pixelSize;
                    remainingBytes -= pixels;
                    if (curX == cols * pixelSize) {
                        curY += pixelSize;
                        curX = 0;
                    }
                }
            }
        } else {
            long pixelByteCount = 0;
            Map<PixelDescription, Long> objects = new HashMap<>();
            while (iter.hasNext()) {
                if (task.isCancelled()) {
                    throw new CancellationException();
                }
                BytesPixelDescription p = iter.next();
                long remainingBytes = p.bytes;
                while (remainingBytes > 0) {
                    if (task.isCancelled()) {
                        throw new CancellationException();
                    }
                    if (p.pd != PixelDescription.GAP_PD) {
                        if (showPointers() && !isInPointerSet) {
                            isInPointerSet = data.isPointerAddress(p.objectID);
                        }
                        if (!isSelected) {
                            isSelected = isSelected(p.pd.classification, p.objectID);
                        }
                    }
                    if (clusterSize - pixelByteCount > remainingBytes) {
                        if (!isSelected) {
                            insertNewEntry(objects, p.pd, remainingBytes);
                        }
                        pixelByteCount += remainingBytes;
                        remainingBytes = 0;
                    } else if (clusterSize - pixelByteCount == remainingBytes) {
                        if (!isSelected) {
                            insertNewEntry(objects, p.pd, remainingBytes);
                        }
                        drawClusterPixel(objects, g, cols, pixelSize);
                        objects.clear();
                        pixelByteCount = 0;
                        remainingBytes = 0;
                        isSelected = false;
                        isInPointerSet = false;
                    } else {
                        if (!isSelected) {
                            insertNewEntry(objects, p.pd, clusterSize - pixelByteCount);
                        }
                        remainingBytes -= (clusterSize - pixelByteCount);
                        drawClusterPixel(objects, g, cols, pixelSize);
                        objects.clear();
                        pixelByteCount = 0;
                        isSelected = false;
                        isInPointerSet = false;
                    }
                }
            }
        }
    }

    private void drawClusterPixel(Map<PixelDescription, Long> objects, Graphics g, int cols, int pixelSize) {
        PixelDescription pd = getPixelDescription(objects);
        if (isSelected) {
            g.setColor(Color.BLACK);
        } else if (showPointers() && !isInPointerSet) {
            g.setColor(toGreyScale(pd.color));
        } else {
            g.setColor(pd.color);
        }
        g.fillRect(curX, curY, pixelSize, pixelSize);
        curX += pixelSize;
        if (curX == cols * pixelSize) {
            curY += pixelSize;
            curX = 0;
        }
    }

    private PixelDescription getPixelDescription(Map<PixelDescription, Long> objects) {
        Entry<PixelDescription, Long> max = null, second = null;
        for (Entry<PixelDescription, Long> j : objects.entrySet()) {
            if (max == null) {
                max = j;
                second = j;
            } else if (j.getValue() > max.getValue()) {
                second = max;
                max = j;
            } else if (j.getValue() > second.getValue()) {
                second = j;
            }
        }
        if (second == null) {
            return max.getKey();
        }
        if (max.getKey() == PixelDescription.GAP_PD) {
            return second.getKey();
        }
        return max.getKey();
    }

    private void insertNewEntry(Map<PixelDescription, Long> objects, PixelDescription key, long value) {
        Long val = objects.get(key);
        if (val == null) {
            objects.put(key, value);
        } else {
            objects.put(key, value + val);
        }

    }

    @Override
    protected long computePixelCount(long clusterSize) {
        return integerDivisionCeil(data.getByteCount(), clusterSize);
    }

    @Override
    public HeapStateObjectInfo[] getDetailedObjInfo(int x, int y) {
        long clusterSize = zoomStack.peek().clusterSize;
        long idx = getPixelIdx(x, y);
        if (idx >= getPixelCount() || idx < 0) {
            return new HeapStateObjectInfo[0];
        }
        long end = getObjectEndIndex(idx);
        idx = getObjectStartIndex(idx);
        Set<HeapStateObjectInfo> set = new HashSet<HeapStateObjectInfo>();
        long startIndex = computeStartIndex();
        for (long i = 0; startIndex + idx * clusterSize + i <= startIndex + end * clusterSize; i++) {
            if (startIndex + idx * clusterSize + i >= data.getByteCount()) {
                break;
            }
            HeapStateObjectInfo obj = data.getDetailedObjInfoForByte(startIndex + idx * clusterSize + i);
            if (obj != null) {
                set.add(obj);
            }
            if (set.size() >= MAXIMUM_OBJ_INFO) {
                break;
            }
        }
        return set.toArray(new HeapStateObjectInfo[0]);
    }

    @Override
    public long getNumberOfObjectsAtPixel(int x, int y) {
        long clusterSize = zoomStack.peek().clusterSize;
        long idx = getPixelIdx(x, y);
        if (idx >= getPixelCount() || idx < 0) {
            return 0;
        }
        long startIndex = computeStartIndex();
        long end = getObjectEndIndex(idx) + 1;
        idx = getObjectStartIndex(idx);
        Iterator<BytesPixelDescription> iter = data.iterator(startIndex + idx * clusterSize, startIndex + end * clusterSize);
        int objects = 0;
        while (iter.hasNext()) {
            objects++;
            iter.next();
        }
        return objects;
    }

    private long getObjectStartIndex(long pixelIdx) {
        long startIndex = computeStartIndex();
        long clusterSize = zoomStack.peek().clusterSize;
        long index = pixelIdx * clusterSize + startIndex;
        long startAddress;
        do {
            startAddress = (data.getObjectStartAddressForByte(index) - startIndex);
            index--;
        } while (startAddress % clusterSize > 0);
        index = startAddress / clusterSize;
        return index;
    }

    private long getObjectEndIndex(long pixelIdx) {
        long startIndex = computeStartIndex();
        long clusterSize = zoomStack.peek().clusterSize;
        long index = pixelIdx * clusterSize + startIndex;
        index = (data.getObjectEndAddressForByte(index) - startIndex) / clusterSize;
        return index;
    }

    @Override
    public Rectangle[] getObjectCoordinates(int x, int y) {
        if (x == -1 || y == -1) {
            return null;
        }
        long pixelIdx = getPixelIdx(x, y);
        if (pixelIdx >= getPixelCount() || pixelIdx < 0) {
            return null;
        }
        long index = getObjectStartIndex(pixelIdx);
        Rectangle start = getPixelCoordinates(index);
        index = getObjectEndIndex(pixelIdx);
        Rectangle end = getPixelCoordinates(index);
        int pixelSize = zoomStack.peek().pixelSize;
        if (start.y == end.y) {
            return new Rectangle[]{new Rectangle(start.x, start.y, end.x - start.x + pixelSize, pixelSize)};
        }
        Rectangle[] res = new Rectangle[3];
        int columns = getColumns();
        res[0] = new Rectangle(start.x, start.y, columns * pixelSize - start.x, pixelSize);
        res[1] = new Rectangle(0, start.y + pixelSize, columns * pixelSize, end.y - start.y - pixelSize);
        res[2] = new Rectangle(0, end.y, end.x + pixelSize, pixelSize);
        return res;
    }

    @Override
    public void setSelectedObjects(int x, int y) {
        if (x == -1 || y == -1) {
            return;
        }
        long pixelIdx = getPixelIdx(x, y);
        if (pixelIdx >= getPixelCount() || pixelIdx < 0) {
            return;
        }
        long start = getObjectStartIndex(pixelIdx);
        long end = getObjectEndIndex(pixelIdx);
        long clusterSize = zoomStack.peek().clusterSize;
        long startIndex = computeStartIndex();
        selectedObjectsIDs = data.getObjectIndicesForBytes(startIndex + start * clusterSize, end * clusterSize - start * clusterSize);
    }

    @Override
    public void setSelectedArea(int startX, int startY, int endX, int endY) {
        if (startX == -1 || startY == -1 || endX == -1 || endY == -1) {
            zoomStack.peek().setSelectedArea(-1, -1, -1, -1);
            return;
        }
        long pixelIdx = getPixelIdx(startX, startY);
        long index = getObjectStartIndex(pixelIdx);
        Rectangle rs = getPixelCoordinates(index);
        pixelIdx = getPixelIdx(endX, endY);
        index = getObjectEndIndex(pixelIdx);
        Rectangle re = getPixelCoordinates(index);
        zoomStack.peek().setSelectedArea(rs.x, rs.y, re.x, re.y);
    }

}
