package at.jku.anttracks.gui.frame.main.tab.heapstate.task

import at.jku.anttracks.gui.frame.main.tab.heapstate.HeapStateTab
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.model.FastHeapInfo
import at.jku.anttracks.gui.utils.AntTask
import at.jku.anttracks.parser.hprof.HprofParser
import at.jku.anttracks.parser.hprof.handler.HprofToFastHeapHandler
import at.jku.anttracks.util.ProgressListener

import java.io.File
import java.io.IOException
import java.util.concurrent.ExecutionException

import at.jku.anttracks.heap.FastHeap

class HprofParserTask(private val path: String) : AntTask<HprofToFastHeapHandler>() {
    var lastProgressUpdate = System.currentTimeMillis()

    init {
        this@HprofParserTask.updateTitle("HPROF parser")
    }

    @Throws(Exception::class)
    override fun backgroundWork(): HprofToFastHeapHandler {
        val handler = HprofToFastHeapHandler()
        val parser = HprofParser(handler, ProgressListener { progress, newMessage ->
            if (System.currentTimeMillis() - lastProgressUpdate > 10 || progress == 0.0 || progress == 1.0) {
                this@HprofParserTask.updateMessage(newMessage)
                this@HprofParserTask.updateProgress(progress, 1.0)
                lastProgressUpdate = System.currentTimeMillis()
            }
        })
        try {
            parser.parse(File(path))
        } catch (e: IOException) {
            System.err.println(e)
        }

        return handler
    }

    @Throws(ExecutionException::class, InterruptedException::class)
    override fun finished() {
        val heap = FastHeap(get())

        val detailsInfo = FastHeapInfo(AppInfo("Parsed from HPROF file",
                                               heap.symbols,
                                               null,
                                               null),
                                       get().time)
        detailsInfo.setHeap(heap)

        val heapStateTab = HeapStateTab()
        heapStateTab.init(detailsInfo, null)
        ClientInfo.mainFrame.addAndSelectTab(heapStateTab)
    }
}
