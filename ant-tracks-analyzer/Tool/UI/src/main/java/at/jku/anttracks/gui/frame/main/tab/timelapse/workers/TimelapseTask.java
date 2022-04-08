
package at.jku.anttracks.gui.frame.main.tab.timelapse.workers;

import at.jku.anttracks.gui.component.actiontab.tab.ActionTab;
import at.jku.anttracks.gui.frame.main.tab.timelapse.TimelapseTab;
import at.jku.anttracks.gui.frame.main.tab.timelapse.model.HeapList;
import at.jku.anttracks.gui.model.*;
import at.jku.anttracks.gui.utils.AntTask;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.heap.HeapAdapter;
import at.jku.anttracks.parser.ParserGCInfo;
import at.jku.anttracks.parser.ParsingInfo;
import at.jku.anttracks.parser.heap.HeapTraceParser;
import at.jku.anttracks.parser.heap.ListenerHeapTraceParser;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

/**
 * @author Christina Rammerstorfer
 */
public class TimelapseTask extends AntTask<HeapList> {

    public static final DetailedHeapInfo END = new DetailedHeapInfo(null, -1);

    private final AppInfo appInfo;
    private TimelapseTab tab;
    private ActionTab parentTab;
    private final TimelapseStatisticsInfo statisticsInfo;

    public TimelapseTask(AppInfo appInfo, ActionTab parentTab, TimelapseTab tab) {
        this.appInfo = appInfo;
        this.tab = tab;
        this.parentTab = parentTab;
        if (tab == null) {
            statisticsInfo = new TimelapseStatisticsInfo(new TimelapseInfo(appInfo));
        } else {
            statisticsInfo = tab.getStatisticsInfo();
        }
    }

    @Override
    protected HeapList backgroundWork() throws Exception {
        updateTitle("Timelapse: Parsing trace and generating all available heaps");
        if (tab == null) {
            tab = new TimelapseTab();
            tab.init(appInfo, parentTab, statisticsInfo);
            tab.setDefaultClassification();
            ClientInfo.mainFrame.addAndSelectTab(parentTab, tab);
        }
        tab.setWorker(this);
        HeapTraceParser parser;
        HeapList heapList = new HeapList();
        parser = new ListenerHeapTraceParser(appInfo.getSymbols(), detailedHeap -> new TimelapseHeapListener(heapList, detailedHeap));
        parser.parse(getCancelProperty());
        return heapList;
    }

    private class TimelapseHeapListener extends HeapAdapter {
        private final DetailedHeap heap;
        private final HeapList heaps;

        private TimelapseHeapListener(HeapList heaps, DetailedHeap parsedHeap) {
            parsedHeap.addListener(this);
            this.heaps = heaps;
            this.heap = parsedHeap;
        }

        @Override
        public void phaseChanging(
                @NotNull
                        Object sender,
                @NotNull
                        ParserGCInfo from,
                @NotNull
                        ParserGCInfo to,
                boolean failed,
                long position,
                @NotNull
                        ParsingInfo parsingInfo,
                boolean inSelectedTimeWindow) {
            updateProgress(position - parsingInfo.getFromByte(), parsingInfo.getTraceLength());
            put(heap.clone(), to.getTime());
        }

        @Override
        public void phaseChanged(
                @NotNull
                        Object sender,
                @NotNull
                        ParserGCInfo from,
                @NotNull
                        ParserGCInfo to,
                boolean failed,
                long position,
                @NotNull
                        ParsingInfo parsingInfo,
                boolean inSelectedTimeWindow) {
            put(heap.clone(), to.getTime());
        }

        private void put(DetailedHeap heap, long time) {
            if (heap.getObjectCount() > 0) {
                DetailedHeapInfo info = new DetailedHeapInfo(appInfo, time);
                info.setHeap(heap);
                heaps.add(info);
            }
        }
    }

    @Override
    protected void finished() {
        try {
            HeapList heapList = get();
            tab.setWorker(null);
            tab.startNewPaintWorker(heapList);
        } catch (InterruptedException | ExecutionException | CancellationException e) {
            e.printStackTrace();
        }
    }
}
