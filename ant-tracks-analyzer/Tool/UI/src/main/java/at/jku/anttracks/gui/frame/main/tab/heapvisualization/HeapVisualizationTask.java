package at.jku.anttracks.gui.frame.main.tab.heapvisualization;

import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.gui.component.actiontab.tab.ActionTab;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.ClientInfo;
import at.jku.anttracks.gui.model.DetailedHeapInfo;
import at.jku.anttracks.gui.model.HeapVisualizationStatisticsInfo;
import at.jku.anttracks.gui.task.DetailedHeapTask;
import at.jku.anttracks.heap.DetailedHeap;
import javafx.application.Platform;

import java.util.ArrayList;

public class HeapVisualizationTask extends DetailedHeapTask {
    private final HeapVisualizationStatisticsInfo statisticsInfo;
    private final HeapVisualizationTab heapVisualizationTab;

    public HeapVisualizationTask(AppInfo appInfo, ActionTab parentTab, long time) {
        super(appInfo, time);
        statisticsInfo = new HeapVisualizationStatisticsInfo(new DetailedHeapInfo(appInfo, time),
                                                             new ClassifierChain(),
                                                             new ArrayList<>());
        this.heapVisualizationTab = new HeapVisualizationTab();
        heapVisualizationTab.init(statisticsInfo);
        ClientInfo.mainFrame.addAndSelectTab(parentTab, heapVisualizationTab);
    }

    @Override
    protected void handleTaskCancelled() {
        // close tab
        Platform.runLater(() -> {
            ClientInfo.mainFrame.removeTab(heapVisualizationTab);
        });
    }

    @Override
    protected void additionalBackgroundWork(DetailedHeap heap) {

    }

    @Override
    protected void finished() {
        this.statisticsInfo.getDetailsInfo().setHeap(getValue());
        this.heapVisualizationTab.init(statisticsInfo);
    }
}
