
package at.jku.anttracks.gui.frame.main.tab.application.tab.detail

import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.application.model.ApplicationTabModel
import at.jku.anttracks.gui.frame.main.tab.application.tab.detail.component.newrootchartpane.NewRootChartPane
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

class ApplicationDetailTab : ApplicationBaseTab() {

    @FXML
    lateinit var rootChartPane: NewRootChartPane

    @FXML
    lateinit var metricText: TextArea

    override val componentDescriptions by lazy {
        listOf(Triple(rootChartPane.objectsChartPane,
                      Description("This chart shows the size of the heap at the end of every garbage collection.")
                              .linebreak()
                              .appendDefault("The red part of the chart represents objects in the old generation, that is, objects that have survived many garbage collections.")
                              .linebreak()
                              .appendDefault("The blue part of the chart represents objects in the survivor space, that is, objects that survived only few garbage collections.")
                              .linebreak()
                              .appendDefault("The green part of the chart represents objects in the eden space, that is, objects that have yet to be garbage collected."),
                      PopOver.ArrowLocation.RIGHT_TOP),
               Triple(rootChartPane.bytesChartPane,
                      Description("This chart shows you the same as the one to the left, but instead of object counts, it shows the bytes occupied by those objects."),
                      PopOver.ArrowLocation.LEFT_TOP),
                //Triple(rootChartPane.gcTypesApplicationChart,
                //       Description("This chart shows you at which point in time what kind of garbage collection happened in your application.")
                //               .linebreak()
                //               .appendDefault("The height and width of each bar represents the duration of the respective garbage collection."),
                //       PopOver.ArrowLocation.RIGHT_TOP),
               Triple(rootChartPane.aliveDeadChartPane,
                      Description("This chart shows you the ratio between objects that survived and objects that died during garbage collections and how this ration develops over your application's runtime."),
                      PopOver.ArrowLocation.LEFT_TOP),
                //Triple(rootChartPane.allocatingSubsystemApplicationChart,
                //       Description("This chart shows you which portion of objects in your application are allocated in C2-compiled, C1-compiled, interpreted or VM internal code."),
                //       PopOver.ArrowLocation.RIGHT_TOP),
               Triple(rootChartPane.objectKindsChartPane,
                      Description("This chart shows you how many objects in your application are normal objects, small arrays or large arrays."),
                      PopOver.ArrowLocation.LEFT_TOP))
    }

    override val initialTabIdeas by lazy {
        listOf(Idea("Application detail tab",
                    Description("This tab shows you the memory evolution and GC activity of your traced application.")
                            .linebreak()
                            .appendDefault("To get started, you should now select either a single point in time and analyse a heap state or select a time window and analyse the heap evolution."),
                    listOf("I don't understand those charts!" does { showComponentDescriptions() }),
                    null,
                    this))
    }

    init {
        FXMLUtil.load(this, ApplicationDetailTab::class.java)
    }

    fun init(model: ApplicationTabModel) {
        super.init(model.appInfo,
                   SimpleStringProperty("Details"),
                   SimpleStringProperty("Detailed information on object and memory development, as well as GC behavior"),
                   model.longDescription,
                   Consts.CHART_ICON,
                   model.actions,
                   false)
        rootChartPane.init()
    }

    fun plot() {
        rootChartPane.plot(appInfo)
    }

    fun setMetricText(text: String) {
        metricText.text = text
    }

    override fun cleanupOnClose() {
    }

    override fun appInfoChangeAction(type: ChangeType) {
        if (type == ChangeType.SHOW_FEATURES || type == ChangeType.ALIVE_DEAD_PANEL_TYPE) {
            rootChartPane.plot(appInfo)
        }
    }
}
