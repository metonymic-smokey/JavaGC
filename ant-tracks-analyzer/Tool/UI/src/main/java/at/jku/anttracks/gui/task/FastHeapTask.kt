package at.jku.anttracks.gui.task

import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.heap.IndexBasedHeap
import at.jku.anttracks.parser.heap.HeapTraceParser
import at.jku.anttracks.util.TraceException
import java.io.IOException
import java.util.concurrent.ExecutionException

abstract class FastHeapTask(appInfo: AppInfo, time: Long) : HeapTask<IndexBasedHeap>(appInfo, time) {

    @Throws(InterruptedException::class, IOException::class, TraceException::class, ExecutionException::class)
    override fun parse(parser: HeapTraceParser): IndexBasedHeap {
        val detailedHeap = parser.parse(cancelProperty)
        /*
        AntTask<IndexBasedHeap> convertDetailedHeapToIndexBasedHeapTask =
                new AntTask<IndexBasedHeap>() {
                    @Override
                    protected IndexBasedHeap backgroundWork() throws Exception {
                        FastHeapTask.this.updateTitle("Calculate index-based heap based on reconstructed heap state");
                        // TODO Introduce listener to show progress during fast heap generation
                        return detailedHeap.toIndexBasedHeap();
                    }

                    @Override
                    protected void suceeded() { }
                };
        return ThreadUtil.startTask(convertDetailedHeapToIndexBasedHeapTask).get();
        */
        updateProgress(0, 1)
        updateTitle("Heap State")
        updateMessage("Final location in trace file reached, arrange heap state representation for time " + time / 1000.0 + "s (this may take some seconds, please wait)")
        return detailedHeap.toIndexBasedHeap { d: Double, s: String? ->
            if (s != null) {
                updateMessage("Final location in trace file reached, arrange heap state representation for time " + time / 1000.0 + "s: " + s)
            }
            updateProgress(d, 1.0)
        }
    }
}