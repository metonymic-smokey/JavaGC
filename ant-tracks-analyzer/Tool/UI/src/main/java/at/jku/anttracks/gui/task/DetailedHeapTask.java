package at.jku.anttracks.gui.task;

import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.heap.DetailedHeap;
import at.jku.anttracks.parser.heap.HeapTraceParser;
import at.jku.anttracks.util.TraceException;

import java.io.IOException;

public abstract class DetailedHeapTask extends HeapTask<DetailedHeap> {

    public DetailedHeapTask(AppInfo appInfo, long time) {
        super(appInfo, time);
    }

    @Override
    protected DetailedHeap parse(HeapTraceParser parser) throws InterruptedException, IOException, TraceException {
        return parser.parse(getCancelProperty());
    }
}