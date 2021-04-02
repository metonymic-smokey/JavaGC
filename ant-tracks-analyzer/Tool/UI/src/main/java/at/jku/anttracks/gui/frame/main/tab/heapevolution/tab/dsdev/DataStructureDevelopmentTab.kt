
package at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev

import at.jku.anttracks.gui.component.actiontab.tab.ActionTab
import at.jku.anttracks.gui.frame.main.component.applicationbase.NonSelectableApplicationBaseTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.model.DataStructureDevelopmentInfo
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.tab.chart.DataStructureDevelopmentChartTab
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.dsdev.tab.classification.DataStructureDevelopmentClassificationTab
import at.jku.anttracks.gui.model.IAppInfo.ChangeType
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.SimpleStringProperty

class DataStructureDevelopmentTab : NonSelectableApplicationBaseTab() {
    private lateinit var dataStructureDevelopmentInfo: DataStructureDevelopmentInfo

    val chartTab: DataStructureDevelopmentChartTab by lazy {
        DataStructureDevelopmentChartTab().also { it.init(dataStructureDevelopmentInfo, this) }
    }
    val classificationTab: DataStructureDevelopmentClassificationTab by lazy {
        DataStructureDevelopmentClassificationTab().also { it.init(dataStructureDevelopmentInfo, this) }
    }

    override val autoSelectChildTab: ActionTab
        get() = chartTab

    init {
        FXMLUtil.load(this, DataStructureDevelopmentTab::class.java)
    }

    fun init(dataStructureDevelopmentInfo: DataStructureDevelopmentInfo) {
        this.dataStructureDevelopmentInfo = dataStructureDevelopmentInfo

        super.init(dataStructureDevelopmentInfo.heapEvolutionInfo.appInfo,
                   SimpleStringProperty("Data structure development analysis"),
                   SimpleStringProperty("Analyze how data structures developed over time"),
                   SimpleStringProperty("Find strongest growing data structures and display them in a chart. Classify all data structures that survived the time window or were born in it."),
                   Consts.DATA_STRUCTURE_ICON,
                   listOf(),
                   true)

        // TODO: This is on purpose after the super.init call, because otherwise the shown tab hierarchy breaks and we do not have more time to investigate
        // overviewTab is automatically added because it is the #autoSelectChildTab
        childTabs.add(classificationTab)
    }

    override fun cleanupOnClose() {}

    override fun appInfoChangeAction(type: ChangeType) {}
}
