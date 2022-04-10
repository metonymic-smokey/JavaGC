package at.jku.anttracks.gui.frame.main.tab.application.tab.detail.component.newrootchartpane

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.chart.extjfx.chartpanes.application.ReducedAliveDeadChartPane
import at.jku.anttracks.gui.chart.extjfx.chartpanes.application.ReducedMemoryChartPane
import at.jku.anttracks.gui.chart.extjfx.chartpanes.application.ReducedObjectKindsChartPane
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.fxml.FXML
import javafx.scene.layout.GridPane

class NewRootChartPane : GridPane() {

    @FXML
    lateinit var objectsChartPane: ReducedMemoryChartPane
    @FXML
    lateinit var bytesChartPane: ReducedMemoryChartPane
    @FXML
    lateinit var objectKindsChartPane: ReducedObjectKindsChartPane
    @FXML
    lateinit var aliveDeadChartPane: ReducedAliveDeadChartPane

    val chartPanes
        get() = listOf(objectsChartPane, bytesChartPane, objectKindsChartPane, aliveDeadChartPane)

    init {
        FXMLUtil.load(this, NewRootChartPane::class.java)
    }

    fun init() {
        objectsChartPane.init()
        bytesChartPane.init(ReducedXYChartPane.Companion.Unit.BYTES)
        objectKindsChartPane.init()
        aliveDeadChartPane.init()
    }

    fun plot(appInfo: AppInfo) {
        objectsChartPane.plot(appInfo)
        bytesChartPane.plot(appInfo)
        objectKindsChartPane.plot(appInfo)
        aliveDeadChartPane.plot(appInfo)
    }
}