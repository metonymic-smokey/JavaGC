
package at.jku.anttracks.gui.frame.main.tab.heapvisualization;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.PaintTask.PaintOperation;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.PixelMap;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.DetailedHeapInfo;
import at.jku.anttracks.gui.model.HeapVisualizationStatisticsInfo;
import at.jku.anttracks.gui.utils.AntTask;

import java.util.function.BinaryOperator;

public class PointersTask extends AntTask<PixelMap> {

    public enum PointerOperation {
        TO_POINTERS,
        FROM_POINTERS,
        BOTH
    }

    private final DetailedHeapInfo detailsInfo;
    private final AppInfo appInfo;
    private final HeapVisualizationTab heapVisualizationTab;
    private final PixelMap pixelMap;
    private final HeapVisualizationStatisticsInfo statisticsInfo;
    private final PointerOperation pointerOperation;

    public PointersTask(HeapVisualizationStatisticsInfo statisticsInfo, HeapVisualizationTab heapVisualizationTab, PixelMap pixelMap, PointerOperation pointerOperation) {
        detailsInfo = statisticsInfo.getDetailsInfo();
        this.statisticsInfo = statisticsInfo;
        appInfo = detailsInfo.getAppInfo();
        this.heapVisualizationTab = heapVisualizationTab;
        this.pixelMap = pixelMap;
        this.pointerOperation = pointerOperation;
    }

    @Override
    protected PixelMap backgroundWork() throws Exception {
        BinaryOperator<String> op = (a, b) -> a + " => " + b;
        updateTitle("Visualization: ");
        updateMessage("Retrieve pointers " + statisticsInfo.getSelectedClassifiers().getList()
                                                           .stream()
                                                           .map(Classifier::getName)
                                                           .reduce(op)
                                                           .orElse("") + " " + (detailsInfo.getTime() / 1000.0) + "s");
        switch (pointerOperation) {
            case TO_POINTERS:
                pixelMap.generatePointers(heapVisualizationTab.getPointsToLevel(), true, this);
                if (pixelMap.getData().getFromPointersLevel() != heapVisualizationTab.getPointersFromLevel()) {
                    pixelMap.generatePointers(heapVisualizationTab.getPointersFromLevel(), false, this);
                }
                break;
            case FROM_POINTERS:
                pixelMap.generatePointers(heapVisualizationTab.getPointersFromLevel(), false, this);
                if (pixelMap.getData().getPointersLevel() != heapVisualizationTab.getPointsToLevel()) {
                    pixelMap.generatePointers(heapVisualizationTab.getPointsToLevel(), true, this);
                }
                break;
            case BOTH:
                pixelMap.generatePointers(heapVisualizationTab.getPointsToLevel(), true, this);
                pixelMap.generatePointers(heapVisualizationTab.getPointersFromLevel(), false, this);
        }
        getLOGGER().info("Retrieval of pointers finished.");
        return pixelMap;
    }

    @Override
    protected void finished() {
        PixelMap data = getValue();
        heapVisualizationTab.setCurrentPointersTask(null);
        PaintTask paintTask = new PaintTask(statisticsInfo, heapVisualizationTab, data.getData(), data, PaintOperation.PAINT);
        heapVisualizationTab.setCurrentPaintTask(paintTask);
        (new Thread(paintTask)).start();
    }

}
