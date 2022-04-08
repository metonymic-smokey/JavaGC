
package at.jku.anttracks.gui.frame.main.tab.heapvisualization;

import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.DetailedHeapInfo;
import at.jku.anttracks.gui.model.HeapVisualizationStatisticsInfo;
import at.jku.anttracks.gui.utils.AntTask;

import java.util.concurrent.ExecutionException;
import java.util.function.BinaryOperator;

/**
 * @author Christina Rammerstorfer
 */
public class PNGExportTask extends AntTask<Boolean> {

    private final DetailedHeapInfo detailsInfo;
    private final AppInfo appInfo;
    private final HeapVisualizationStatisticsInfo statisticsInfo;
    private final HeapVisualizationTab heapVisualizationTab;
    private final int width;
    private final int height;
    private final long clusterSize;
    private final String fileName;

    public PNGExportTask(HeapVisualizationStatisticsInfo statisticsInfo,
                         HeapVisualizationTab heapVisualizationTab,
                         int width,
                         int height,
                         long clusterSize,
                         String fileName) {
        appInfo = statisticsInfo.getDetailsInfo().getAppInfo();
        detailsInfo = statisticsInfo.getDetailsInfo();
        this.statisticsInfo = statisticsInfo;
        this.heapVisualizationTab = heapVisualizationTab;
        this.width = width;
        this.height = height;
        this.clusterSize = clusterSize;
        this.fileName = fileName;
    }

    @Override
    protected Boolean backgroundWork() throws Exception {
        BinaryOperator<String> op = (a, b) -> a + " => " + b;
        updateTitle("Visualization: export PNG file " + statisticsInfo.getSelectedClassifiers().getList().stream().map(c -> {
            try {
                return c.getName();
            } catch (Exception e) {
                return "Unnamed classifier";
            }
        }).reduce(op).orElse("") + " " + (detailsInfo.getTime() / 1000.0) + "s");
        Boolean success;
        try {
            heapVisualizationTab.getPixelMap().exportPNG(width, height, clusterSize, fileName, this);
            success = true;
        } catch (Exception e) {
            success = false;
        }
        return success;
    }

    @Override
    protected void finished() {
        try {
            Boolean b = get();
            heapVisualizationTab.showPNGExportDialog(b);
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
        }
    }
}
