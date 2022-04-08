
package at.jku.anttracks.gui.frame.main.tab.heapvisualization;

import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.*;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.DetailedHeapInfo;
import at.jku.anttracks.gui.model.HeapVisualizationStatisticsInfo;
import at.jku.anttracks.gui.utils.AntTask;

public class PaintTask extends AntTask<PixelMap> {

    public enum PaintOperation {
        PAINT,
        ZOOM_IN,
        ZOOM_OUT
    }

    private final AppInfo appInfo;
    protected final DetailedHeapInfo detailsInfo;
    private final HeapVisualizationStatisticsInfo statisticsInfo;

    private final HeapVisualizationTab heapVisualizationTab;
    private final ObjectVisualizationData data;
    private PixelMap pixelMap;

    private final PaintOperation paintOperation;

    public PaintTask(HeapVisualizationStatisticsInfo statisticsInfo,
                     HeapVisualizationTab heapVisualizationTab,
                     ObjectVisualizationData data,
                     PixelMap pixelMap,
                     PaintOperation operation) {
        appInfo = statisticsInfo.getDetailsInfo().getAppInfo();
        detailsInfo = statisticsInfo.getDetailsInfo();
        this.statisticsInfo = statisticsInfo;
        this.heapVisualizationTab = heapVisualizationTab;
        this.data = data;
        this.pixelMap = pixelMap;
        paintOperation = operation;
    }

    @Override
    protected PixelMap backgroundWork() throws Exception {
        updateTitle("Visualization");
        updateMessage("Paint heap " + (detailsInfo.getTime() / 1000.0) + "s");

        switch (paintOperation) {
            case PAINT:
                if (pixelMap == null) {
                    if (heapVisualizationTab.getClusterLevel() == ClusterLevel.OBJECTS) {
                        pixelMap = new ObjectPixelMap(detailsInfo, heapVisualizationTab.getScrollPane(), data, false);
                    } else {
                        pixelMap = new BytePixelMap(detailsInfo, heapVisualizationTab.getScrollPane(), data, false);
                    }
                } else if (!heapVisualizationTab.getClusterLevel().value.isInstance(pixelMap)) {
                    if (heapVisualizationTab.getClusterLevel() == ClusterLevel.OBJECTS) {
                        pixelMap = new ObjectPixelMap(detailsInfo, heapVisualizationTab.getScrollPane(), data, pixelMap.showPointers());
                    } else if (heapVisualizationTab.getClusterLevel() == ClusterLevel.BYTES) {
                        pixelMap = new BytePixelMap(detailsInfo, heapVisualizationTab.getScrollPane(), data, pixelMap.showPointers());
                    }
                } else {
                    pixelMap.setData(data);
                }
                pixelMap.paintMap(heapVisualizationTab.getStalledClusterSize(), this);
                break;
            case ZOOM_IN:
                pixelMap.zoomIn(this);
                break;
            case ZOOM_OUT:
                pixelMap.zoomOut(this);
                break;
            default:
                break;
        }
        getLOGGER().info("Painting of heap finished.");
        return pixelMap;
    }

    @Override
    protected void finished() {
        PixelMap pixelMap = getValue();
        if (!pixelMap.showPointers()) {
            heapVisualizationTab.resetObjectInfo();
        }
        heapVisualizationTab.updateAfterPainting(pixelMap);
        heapVisualizationTab.setCurrentPaintTask(null);
    }

}
