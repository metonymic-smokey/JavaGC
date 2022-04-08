package at.jku.anttracks.gui.frame.main.component.applicationbase

import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.component.actiontab.tab.ActionTab
import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.IAppInfo
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.StringProperty
import javafx.concurrent.Task
import java.util.*
import java.util.function.Consumer
import javax.swing.Icon

abstract class ApplicationBaseTab : IdeasEnabledTab() {
    lateinit var appInfo: AppInfo
    var tasks: MutableList<Task<*>> = ArrayList()

    val appInfoListener: IAppInfo.AppInfoListener = IAppInfo.AppInfoListener { type -> appInfoChangeAction(type) }

    init {
        FXMLUtil.load(this, ApplicationBaseTab::class.java)
    }

    open fun init(appInfo: AppInfo,
                  name: StringProperty = this.name,
                  shortDescription: StringProperty = this.shortDescription,
                  longDescription: StringProperty = this.longDescription,
                  icon: Icon? = this.icon,
                  actions: List<ActionTabAction> = this.actions,
                  closeable: Boolean = this.closeable) {
        this.appInfo = appInfo
        this.appInfo.addListener { appInfoListener }
        this.closeListeners += {
            tasks.forEach(Consumer<Task<*>> { it.cancel() })
            appInfo.removeListener(appInfoListener)
            cleanupOnClose()
        }

        super.init(name, shortDescription, longDescription, icon, actions, closeable)
    }

    protected abstract fun cleanupOnClose()

    protected abstract fun appInfoChangeAction(type: IAppInfo.ChangeType)
}

abstract class NonSelectableApplicationBaseTab : ApplicationBaseTab() {
    abstract val autoSelectChildTab: ActionTab

    override fun init(appInfo: AppInfo,
                      name: StringProperty,
                      shortDescription: StringProperty,
                      longDescription: StringProperty,
                      icon: Icon?,
                      actions: List<ActionTabAction>,
                      closeable: Boolean) {
        super.init(name, shortDescription, longDescription, icon, actions, closeable)
        childTabs.add(autoSelectChildTab)
        selected.addListener { obs, wasSelected, isSelected ->
            if (isSelected!!) {
                selected.set(false)
                tabbedPane?.select(autoSelectChildTab)
            }
        }
        if (selected.get()) {
            tabbedPane?.select(autoSelectChildTab)
        }
    }
}