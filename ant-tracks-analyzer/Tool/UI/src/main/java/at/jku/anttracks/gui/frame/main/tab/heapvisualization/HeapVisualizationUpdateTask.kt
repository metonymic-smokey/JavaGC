package at.jku.anttracks.gui.frame.main.tab.heapvisualization

import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.ObjectVisualizationData
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.model.HeapVisualizationStatisticsInfo
import at.jku.anttracks.gui.utils.AntTask

class HeapVisualizationUpdateTask(private val statisticsInfo: HeapVisualizationStatisticsInfo, private val heapVisualizationTab: HeapVisualizationTab) : AntTask<ObjectVisualizationData>() {

    @Throws(Exception::class)
    override fun backgroundWork(): ObjectVisualizationData {
        updateTitle("Visualization")
        updateMessage("Generate pixel map " + statisticsInfo.selectedClassifiers.list.joinToString(" => ") { it.name } + " " + statisticsInfo.detailsInfo.time / 1000.0 + "s")

        val data = ObjectVisualizationData(statisticsInfo, this)
        data.generateData()

        LOGGER.info("Generation of pixel map finished.")

        return data
    }

    override fun finished() {
        val data = value
        heapVisualizationTab.setCurrentHeapVisualizationTask(null)
        val paintTask = PaintTask(statisticsInfo, heapVisualizationTab, data, heapVisualizationTab.pixelMap, PaintTask.PaintOperation.PAINT)
        ClientInfo.mainFrame.selectTab(heapVisualizationTab)
        Thread(paintTask).start()
        heapVisualizationTab.setCurrentPaintTask(paintTask)
    }

}
