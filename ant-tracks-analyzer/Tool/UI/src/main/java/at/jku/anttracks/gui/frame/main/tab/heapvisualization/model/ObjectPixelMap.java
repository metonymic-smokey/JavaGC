
/**
 *
 */
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.model;

import at.jku.anttracks.gui.model.DetailedHeapInfo;
import javafx.concurrent.Task;

import javax.swing.*;
import java.awt.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.CancellationException;

/**
 * @author Christina Rammerstorfer
 */
public class ObjectPixelMap extends PixelMap {

    public ObjectPixelMap(DetailedHeapInfo detailsInfo, JScrollPane container, ObjectVisualizationData data, boolean showPointers) {
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
            PixelDescription p = data.getPixelDescriptionForObject(idx * clusterSize + startIndex);
            if (showPointers() && !data.isPointerAddress(idx * clusterSize + startIndex)) {
                return new PixelDescription(toGreyScale(p.color), p.classification, p.id, true);
            } else {
                return p;
            }
        }
        Map<Object, Integer> objects = new HashMap<>();
        boolean isInPointerSet = false;
        for (long i = startIndex + idx * clusterSize; i < startIndex + idx * clusterSize + clusterSize && i < data.getObjectCount(); i++) {
            Object classifications = data.getClassificationsForObject(i);
            Integer value = objects.get(classifications);
            if (value == null) {
                objects.put(classifications, 1);
            } else {
                objects.put(classifications, value + 1);
            }
            if (showPointers() && !isInPointerSet) {
                isInPointerSet = data.isPointerAddress(i);
            }
        }
        Entry<Object, Integer> max = null;
        for (Entry<Object, Integer> j : objects.entrySet()) {
            if (max == null) {
                max = j;
            } else if (j.getValue() > max.getValue()) {
                max = j;
            }
        }
        PixelDescription p = data.getPixelDescriptionForClassification(max.getKey());
        if (showPointers() && !isInPointerSet) {
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
                return data.getObjectCount();
            }
            return integerDivisionCeil(data.getObjectCount(), clusterSize);
        }
    }

    @Override
    protected void paintHeap(Graphics g, int width, int height, Task<?> task) {
        ZoomStateData top = zoomStack.peek();
        int pixelSize = top.pixelSize;
        long clusterSize = top.clusterSize;
        long startIndex = computeStartIndex(), endIndex;
        if (top.endIndex == ZoomStateData.LAST_PIXEL) {
            endIndex = data.getObjectCount();
        } else {
            top = zoomStack.pop();
            endIndex = ((top.endIndex - top.startIndex + 1) * zoomStack.peek().clusterSize) + startIndex;
            if (endIndex > data.getObjectCount()) {
                endIndex = data.getObjectCount();
            }
            zoomStack.push(top);
        }
        paintHeap(g, width, height, clusterSize, startIndex, endIndex, pixelSize, task);
    }

    @Override
    protected void paintHeap(Graphics g, int width, int height, long clusterSize, long startIndex, long endIndex, int pixelSize, Task<?> task) {
        if (endIndex == ZoomStateData.LAST_PIXEL || endIndex > data.getObjectCount()) {
            endIndex = data.getObjectCount();
        }
        // Paint background white
        g.setColor(Color.WHITE);
        g.fillRect(0, height - 1 - pixelSize, width, pixelSize);
        int curX = 0, curY = 0;
        if (clusterSize == 1) {
            for (long i = startIndex; i < endIndex; i++) {
                if (task.isCancelled()) {
                    throw new CancellationException();
                }
                PixelDescription p = data.getPixelDescriptionForObject(i);
                if (isSelected(p.classification, i)) {
                    g.setColor(Color.BLACK);
                } else if (showPointers() && !data.isPointerAddress(i)) {
                    g.setColor(toGreyScale(p.color));
                } else {
                    g.setColor(p.color);
                }
                g.fillRect(curX, curY, pixelSize, pixelSize);
                if ((curX + pixelSize + pixelSize > width) || (curX + pixelSize == width)) {
                    curY += pixelSize;
                    curX = 0;
                } else {
                    curX += pixelSize;
                }
            }
        } else {
            int objectCount = 0;
            Map<Object, Integer> objects = new HashMap<>();
            boolean isInPointerSet = false;
            boolean isSelected = false;
            for (long i = startIndex; i < endIndex; i++) {
                if (task.isCancelled()) {
                    throw new CancellationException();
                }
                objectCount++;
                if (showPointers() && !isInPointerSet) {
                    isInPointerSet = data.isPointerAddress(i);
                }
                if (!isSelected) {
                    isSelected = isSelected(data.getClassificationsForObject(i), i);
                }
                if (objectCount == 1) {// first object creates new map
                    objects.clear();
                    if (!isSelected) {
                        objects.put(data.getClassificationsForObject(i), 1);
                    }
                } else {
                    if (!isSelected) {
                        Integer value = objects.get(data.getClassificationsForObject(i));
                        if (value == null) {
                            objects.put(data.getClassificationsForObject(i), 1);
                        } else {
                            objects.put(data.getClassificationsForObject(i), value + 1);
                        }
                    }
                    if (objectCount == clusterSize) {
                        if (isSelected) {
                            g.setColor(Color.BLACK);
                        } else {
                            Entry<Object, Integer> max = null;
                            for (Entry<Object, Integer> j : objects.entrySet()) {
                                if (max == null) {
                                    max = j;
                                } else if (j.getValue() > max.getValue()) {
                                    max = j;
                                }
                            }
                            if (showPointers() && !isInPointerSet) {
                                g.setColor(toGreyScale(data.getPixelDescriptionForClassification(max.getKey()).color));
                            } else {
                                g.setColor(data.getPixelDescriptionForClassification(max.getKey()).color);
                            }
                        }
                        objectCount = 0;

                        g.fillRect(curX, curY, pixelSize, pixelSize);
                        if ((curX + pixelSize + pixelSize > width) || (curX + pixelSize == width)) {
                            curY += pixelSize;
                            curX = 0;
                        } else {
                            curX += pixelSize;
                        }
                        isInPointerSet = false;
                        isSelected = false;
                    }
                }
            }
        }
    }

    @Override
    protected long computePixelCount(long clusterSize) {
        return integerDivisionCeil(data.getObjectCount(), clusterSize);
    }

    @Override
    public HeapStateObjectInfo[] getDetailedObjInfo(int x, int y) {
        long clusterSize = zoomStack.peek().clusterSize;
        long idx = getPixelIdx(x, y);
        if (idx >= getPixelCount() || idx < 0) {
            return new HeapStateObjectInfo[0];
        }
        int size;
        // I do this deliberately. I can't display more than Integer.MAX_VALUE object infos because I can't even draw that much - now
        // limited to 10 objects
        if (clusterSize > MAXIMUM_OBJ_INFO) {
            size = MAXIMUM_OBJ_INFO;
        } else {
            size = (int) clusterSize;
        }
        HeapStateObjectInfo[] arr = new HeapStateObjectInfo[size];
        long startIndex = computeStartIndex();
        for (int i = 0; i < size; i++) {
            if (startIndex + idx * clusterSize + i >= data.getObjectCount()) {
                break;
            }
            arr[i] = data.getDetailedObjInfoForObj(startIndex + idx * clusterSize + i);
        }
        return arr;
    }

    @Override
    public Rectangle[] getObjectCoordinates(int x, int y) {
        if (x == -1 || y == -1) {
            return null;
        }
        int pixelSize = zoomStack.peek().pixelSize;
        int x1 = x / pixelSize;
        int y1 = y / pixelSize;
        if (getPixelIdx(x, y) >= getPixelCount() || getPixelIdx(x, y) < 0) {
            return null;
        }
        return new Rectangle[]{new Rectangle(x1 * pixelSize, y1 * pixelSize, pixelSize, pixelSize)};
    }

    @Override
    public void setSelectedObjects(int x, int y) {
        long clusterSize = zoomStack.peek().clusterSize;
        long idx = getPixelIdx(x, y);
        if (idx >= getPixelCount() || idx < 0) {
            return;
        }
        int size;
        if (clusterSize > Integer.MAX_VALUE) {
            size = Integer.MAX_VALUE;
        } else {
            size = (int) clusterSize;
        }
        selectedObjectsIDs = new long[size];
        long startIndex = computeStartIndex();
        for (int i = 0; i < size; i++) {
            selectedObjectsIDs[i] = startIndex + idx * clusterSize + i;
        }
    }

    @Override
    public void setSelectedArea(int startX, int startY, int endX, int endY) {
        zoomStack.peek().setSelectedArea(startX, startY, endX, endY);
    }

    @Override
    public long getNumberOfObjectsAtPixel(int x, int y) {
        return getClusterSize();
    }

}
