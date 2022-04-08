
package at.jku.anttracks.gui.frame.main.tab.application

import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane
import at.jku.anttracks.gui.chart.extjfx.chartpanes.ReducedXYChartPane.Companion.SynchronizationOption.Action.*
import at.jku.anttracks.gui.chart.extjfx.chartpanes.desynchable
import at.jku.anttracks.gui.component.actiontab.tab.ActionTab
import at.jku.anttracks.gui.frame.main.component.applicationbase.NonSelectableApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.application.model.ApplicationTabModel
import at.jku.anttracks.gui.frame.main.tab.application.tab.detail.ApplicationDetailTab
import at.jku.anttracks.gui.frame.main.tab.application.tab.overview.ApplicationOverviewTab
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.IAppInfo.ChangeType
import at.jku.anttracks.gui.utils.AppLoader
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.application.Platform
import javafx.beans.property.SimpleStringProperty

class ApplicationTab : NonSelectableApplicationBaseTab() {

    val overviewTab: ApplicationOverviewTab by lazy {
        ApplicationOverviewTab().apply { init(model) }
    }
    val detailTab: ApplicationDetailTab by lazy {
        ApplicationDetailTab().apply { init(model) }
    }
    /*
    val webTab: ApplicationWebTab by lazy {
        ApplicationWebTab().apply { init(model) }
    }
    */

    val model = ApplicationTabModel()

    override val autoSelectChildTab: ActionTab
        get() = overviewTab

    init {
        FXMLUtil.load(this, ApplicationTab::class.java)
    }

    fun init(appInfo: AppInfo) {
        model.init(this, appInfo)

        super.init(appInfo,
                   SimpleStringProperty("Application - ${appInfo.appName}"),
                //SimpleStringProperty("Overview on the heap memory evolution reconstructed from trace file."),
                   SimpleStringProperty("Trace file: ${appInfo.selectedTraceFile.absolutePath}"),
                   model.longDescription,
                   Consts.APP_ICON,
                   model.actions,
                   true)

        // overviewTab is automatically added because it is the #autoSelectChildTab
        childTabs.add(detailTab)

        // childTabs.add(webTab)

        // synchronize charts
        ReducedXYChartPane.synchronize(overviewTab.chartPanes + detailTab.rootChartPane.chartPanes,
                                       SELECTION desynchable false,
                                       ZOOM desynchable true,
                                       PAN desynchable true)
    }

    fun plot() {
        overviewTab.plot()
        detailTab.plot()
    }

    fun setMetricText(text: String) {
        overviewTab.setMetricText(text)
        detailTab.setMetricText(text)
    }

    override fun cleanupOnClose() {
    }

    override fun appInfoChangeAction(type: ChangeType) {
        Platform.runLater { name.set("Application - " + appInfo.appName) }

        if (type == ChangeType.FILE || type == ChangeType.TRACE || type == ChangeType.FEATURE) {
//            model.chartSync.clear()
            AppLoader.parse(appInfo, this)
        }
    }
}
