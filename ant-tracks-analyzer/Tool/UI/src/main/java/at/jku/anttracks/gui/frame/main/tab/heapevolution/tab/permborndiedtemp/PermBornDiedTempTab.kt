
package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp

import at.jku.anttracks.gui.component.actiontab.tab.ActionTab
import at.jku.anttracks.gui.frame.main.component.applicationbase.NonSelectableApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.DataStructureDevelopmentTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PermBornDiedTempInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.tab.classification.PermBornDiedTempClassificationTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.tab.overview.PermBornDiedTempOverviewTab
import at.jku.anttracks.gui.model.IAppInfo
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.parser.heapevolution.HeapEvolutionUpdateListener
import javafx.beans.property.SimpleStringProperty

class PermBornDiedTempTab : NonSelectableApplicationBaseTab() {
    private lateinit var permBornDiedTempInfo: PermBornDiedTempInfo

    val overviewTab: PermBornDiedTempOverviewTab by lazy {
        PermBornDiedTempOverviewTab().also { it.init(permBornDiedTempInfo) }
    }
    val classificationTab: PermBornDiedTempClassificationTab by lazy {
        PermBornDiedTempClassificationTab().also { it.init(permBornDiedTempInfo) }
    }

    override val autoSelectChildTab: ActionTab
        get() = overviewTab
    val heapEvolutionUpdateListener: List<HeapEvolutionUpdateListener>
        get() = listOf(overviewTab.getHeapEvolutionUpdateListener(), classificationTab.getHeapEvolutionUpdateListener())

    init {
        FXMLUtil.load(this, DataStructureDevelopmentTab::class.java)
    }

    fun init(permBornDiedTempInfo: PermBornDiedTempInfo) {
        this.permBornDiedTempInfo = permBornDiedTempInfo

        super.init(permBornDiedTempInfo.heapEvolutionInfo.appInfo,
                   SimpleStringProperty("Perm/Born/Died/Temp analysis"),
                   SimpleStringProperty("Which objects survived, died, were born or lived only temporarily?"),
                   SimpleStringProperty("Analyze which objects were born before the selected time window and surived (PERM), were born within the time window and survived (BORN), were born before" +
                                                "the time window and died within it (DIED), or were born in and died in the time window."),
                   Consts.PERM_BORN_DIED_TEMP_ICON,
                   listOf(),
                   true)

        // TODO: This is on purpose after the super.init call, because otherwise the shown tab hierarchy breaks and we do not have more time to investigate
        // overviewTab is automatically added because it is the #autoSelectChildTab
        childTabs.add(classificationTab)
    }

    override fun cleanupOnClose() {}
    override fun appInfoChangeAction(type: IAppInfo.ChangeType) {
    }
}
