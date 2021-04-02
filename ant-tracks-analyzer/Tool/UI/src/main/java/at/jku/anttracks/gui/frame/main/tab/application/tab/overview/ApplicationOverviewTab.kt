
package at.jku.anttracks.gui.frame.main.tab.application.tab.overview

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.chart.extjfx.chartpanes.application.ReducedGCActivityChartPane
import at.jku.anttracks.gui.chart.extjfx.chartpanes.application.SimplifiedReducedMemoryChartPane
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.application.model.ApplicationTabModel
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.model.IAppInfo.ChangeType
import at.jku.anttracks.gui.model.Idea
import at.jku.anttracks.gui.model.does
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.SimpleStringProperty
import javafx.fxml.FXML
import javafx.scene.control.TextArea
import org.controlsfx.control.PopOver

class ApplicationOverviewTab : ApplicationBaseTab() {
    @FXML
    lateinit var simplifiedMemoryChartPane: SimplifiedReducedMemoryChartPane

    @FXML
    lateinit var gcActivityChartPane: ReducedGCActivityChartPane

    @FXML
    lateinit var metricText: TextArea

    val chartPanes
        get() = listOf(simplifiedMemoryChartPane, gcActivityChartPane)

    override val componentDescriptions by lazy {
        listOf(Triple(simplifiedMemoryChartPane,
                      Description("This chart shows the size of the heap at the end of every garbage collection.")
                              .linebreak()
                              .appendDefault("The red part of the chart indicates the heap memory occupied by objects in the old generation, that is, objects that have survived many garbage collections.")
                              .linebreak()
                              .appendDefault("The blue part of the chart indicates the heap memory occupied by objects in the young generation, that is, objects that survived only few garbage collections"),
                      PopOver.ArrowLocation.RIGHT_TOP),
               Triple(gcActivityChartPane,
                      Description("This chart shows you the portion of runtime your application has spent in garbage collections. It also gives a hint on how many garbage collections occurred in your application.")
                              .linebreak()
                              .appendDefault("The red portion stands for the time spent in garbage collections, the yellow portion for the time spent in between garbage collections.")
                              .linebreak()
                              .appendDefault("The width of each section represents a fixed number of garbage collections. Thus, if the chart is made up of many small sections it means that many garbage collections occurred."),
                      PopOver.ArrowLocation.LEFT_TOP))
    }

    override val initialTabIdeas by lazy {
        listOf(Idea("Application overview tab",
                    Description("This tab shows you the memory evolution and GC activity of your traced application.")
                            .linebreak()
                            .appendDefault("To get started, you should now select either a single point in time and analyse a heap state or select a time window and analyse the heap evolution."),
                    listOf("I don't understand those charts!" does { showComponentDescriptions() }),
                    null,
                    this))
    }

    init {
        FXMLUtil.load(this, ApplicationOverviewTab::class.java)
    }

    fun init(model: ApplicationTabModel) {
        super.init(model.appInfo,
                   SimpleStringProperty("Overview"),
                   SimpleStringProperty("General memory development and GC activity"),
                   model.longDescription,
                   Consts.CHART_ICON,
                   model.actions,
                   false)
        simplifiedMemoryChartPane.init(ReducedXYChartPane.Companion.Unit.BYTES)
        gcActivityChartPane.init()
    }

    fun plot() {
        simplifiedMemoryChartPane.plot(appInfo)
        gcActivityChartPane.plot(appInfo)
    }

    fun setMetricText(text: String) {
        metricText.text = text
    }

    override fun cleanupOnClose() {
    }

    override fun appInfoChangeAction(type: ChangeType) {

    }
}
