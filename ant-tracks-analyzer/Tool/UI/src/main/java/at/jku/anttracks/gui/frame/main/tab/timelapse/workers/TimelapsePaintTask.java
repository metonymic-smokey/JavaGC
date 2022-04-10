
package at.jku.anttracks.gui.frame.main.tab.timelapse.workers;

import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.*;
import at.jku.anttracks.gui.frame.main.tab.timelapse.TimelapseTab;
import at.jku.anttracks.gui.frame.main.tab.timelapse.model.HeapList;
import at.jku.anttracks.gui.frame.main.tab.timelapse.model.TimelapseModel;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.DetailedHeapInfo;
import at.jku.anttracks.gui.model.HeapVisualizationStatisticsInfo;
import at.jku.anttracks.gui.model.TimelapseStatisticsInfo;
import at.jku.anttracks.gui.utils.AntTask;

import java.awt.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * @author Christina Rammerstorfer
 */
public class TimelapsePaintTask extends AntTask<TimelapseModel> {

    private final AppInfo appInfo;
    private final HeapList heapList;
    private final TimelapseStatisticsInfo statisticsInfo;
    private final TimelapseTab tab;
    private long curClusterSize;
    private final Queue<PixelMap> pixelMaps;

    public TimelapsePaintTask(AppInfo appInfo, HeapList heapList, TimelapseStatisticsInfo statisticsInfo, TimelapseTab tab) {
        this.appInfo = appInfo;
        this.heapList = heapList;
        this.statisticsInfo = statisticsInfo;
        this.tab = tab;
        this.pixelMaps = new LinkedList<PixelMap>();
    }

    @Override
    protected TimelapseModel backgroundWork() throws Exception {
        curClusterSize = -1;
        ClusterLevel level = tab.getClusterLevel();
        Map<Object, Color> globalColorMap = new HashMap<>();
        ColorGenerator colorGenerator = new ColorGenerator(Color.BLUE);
        long startAddress = heapList.getMinStartAddress();
        DetailedHeapInfo current = heapList.getFirst();
        while (current != null) {
            HeapVisualizationStatisticsInfo s = new HeapVisualizationStatisticsInfo(current,
                                                                                    statisticsInfo.getSelectedClassifiers(),
                                                                                    statisticsInfo.getSelectedFilters());
            ObjectVisualizationData data = new ObjectVisualizationData(s, this, globalColorMap, colorGenerator);
            updateTitle("Timelapse: Generating pixel map for time " + current.getTime());
            if (startAddress < Long.MAX_VALUE) {
                data.generateData(startAddress);
            } else {
                data.generateData();
            }
            data.clearHeap();
            PixelMap map;
            if (level == ClusterLevel.OBJECTS) {
                map = new ObjectPixelMap(current, tab.getScrollPane(), data, false);
            } else {
                map = new BytePixelMap(current, tab.getScrollPane(), data, false);
            }
            pixelMaps.offer(map);
            long c = map.computeClusterSize();
            curClusterSize = (c > curClusterSize) ? c : curClusterSize;
            getLOGGER().info("Generated pixel map for time " + current.getTime());
            current = heapList.getFirst();
        }
        getLOGGER().info("Determined cluster size for timelapse: " + curClusterSize);
        TimelapseModel model = new TimelapseModel();
        for (PixelMap map : pixelMaps) {
            updateTitle("Timelapse: paiting heap for time " + map.getDetailsInfo().getTime());
            map.paintMap(curClusterSize, this);
            model.addImage(map.getDetailsInfo().getTime(), map.getImageBuffer());
        }
        return model;
    }

    @Override
    protected void finished() {
        try {
            TimelapseModel m = get();
            tab.setClusterSize(curClusterSize);
            tab.setPaintWorker(null);
            tab.setTimelapseModel(m);
            tab.updateTimelapse();
        } catch (InterruptedException | ExecutionException | CancellationException e) {
            e.printStackTrace();
        }
    }
}
