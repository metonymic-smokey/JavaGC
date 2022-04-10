
package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived

import at.jku.anttracks.gui.component.actiontab.tab.ActionTab
import at.jku.anttracks.gui.frame.main.component.applicationbase.NonSelectableApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.model.ShortLivedObjectsInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.tab.classification.ShortLivedObjectsClassificationTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.shortlived.tab.overview.ShortLivedObjectsOverviewTab
import at.jku.anttracks.gui.model.IAppInfo.ChangeType
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.SimpleStringProperty

class ShortLivedObjectsTab : NonSelectableApplicationBaseTab() {
    private lateinit var shortLivedObjectsInfo: ShortLivedObjectsInfo

    val overviewTab: ShortLivedObjectsOverviewTab by lazy {
        ShortLivedObjectsOverviewTab().apply { init(shortLivedObjectsInfo, classificationTab) }
    }
    val classificationTab: ShortLivedObjectsClassificationTab by lazy {
        ShortLivedObjectsClassificationTab().apply { init(shortLivedObjectsInfo) }
    }

    override val autoSelectChildTab: ActionTab
        get() = overviewTab

    init {
        FXMLUtil.load(this, ShortLivedObjectsTab::class.java)
    }

    fun init(shortLivedObjectsInfo: ShortLivedObjectsInfo) {
        this.shortLivedObjectsInfo = shortLivedObjectsInfo

        super.init(shortLivedObjectsInfo.heapEvolutionInfo.appInfo,
                   SimpleStringProperty("Short-lived objects"),
                   SimpleStringProperty("Analyze garbage collection behaviour and produced garbage"),
                   SimpleStringProperty(""),
                   Consts.SHORT_LIVED_ICON,
                   listOf(),
                   true)

        // TODO: This is on purpose after the super.init call, because otherwise the shown tab hierarchy breaks and we do not have more time to investigate
        // overviewTab is automatically added because it is the #autoSelectChildTab
        childTabs.add(classificationTab)
    }

    override fun cleanupOnClose() {}

    override fun appInfoChangeAction(type: ChangeType) {}
}
