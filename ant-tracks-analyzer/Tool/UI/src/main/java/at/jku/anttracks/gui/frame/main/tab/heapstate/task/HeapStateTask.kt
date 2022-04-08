package at.jku.anttracks.gui.frame.main.tab.heapstate.task

import at.jku.anttracks.gui.component.actiontab.tab.ActionTab
import at.jku.anttracks.gui.frame.main.tab.heapstate.HeapStateTab
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.model.FastHeapInfo
import at.jku.anttracks.gui.task.FastHeapTask
import at.jku.anttracks.heap.IndexBasedHeap
import javafx.application.Platform

open class HeapStateTask(appInfo: AppInfo,
                         parentTab: ActionTab?,
                         time: Long,
                         switchToHeapStateTabAutomatically: Boolean = true,
                         val completionCallback: (HeapStateTask.() -> Unit)? = null) : FastHeapTask(appInfo, time) {

    val heapStateTab: HeapStateTab = HeapStateTab()
    val detailsInfo: FastHeapInfo = FastHeapInfo(appInfo, time)

    init {
        heapStateTab.init(detailsInfo, this)
        if (switchToHeapStateTabAutomatically) {
            if (parentTab != null) {
                ClientInfo.mainFrame.addAndSelectTab(parentTab, heapStateTab)
            } else {
                ClientInfo.mainFrame.addAndSelectTab(heapStateTab)
            }
        } else {
            if (parentTab != null) {
                ClientInfo.mainFrame.addTab(parentTab, heapStateTab)
            } else {
                ClientInfo.mainFrame.addTab(heapStateTab)
            }
        }
    }

    override fun handleTaskCancelled() {
        // close tab
        Platform.runLater { ClientInfo.mainFrame.removeTab(heapStateTab) }
    }

    private fun storeHeap() {
        val heap = value!!
        detailsInfo.setHeap(heap)
    }

    override fun finished() {
        storeHeap()
        completionCallback?.invoke(this)
    }

    override fun additionalBackgroundWork(heap: IndexBasedHeap) {

    }
}
