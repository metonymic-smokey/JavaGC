
package at.jku.anttracks.gui.component.actiontab.pane

import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.component.actiontab.panel.ActionTabSidePanel
import at.jku.anttracks.gui.component.actiontab.tab.ActionTab
import at.jku.anttracks.gui.frame.main.component.applicationbase.IdeasEnabledTab
import at.jku.anttracks.gui.frame.main.component.ideascontainer.IdeasPopup
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.application.Platform
import javafx.beans.DefaultProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.value.ChangeListener
import javafx.beans.value.ObservableValue
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.geometry.Orientation
import javafx.scene.Cursor
import javafx.scene.control.*
import javafx.scene.image.ImageView
import javafx.scene.layout.AnchorPane
import javafx.scene.layout.BorderPane
import javafx.scene.layout.VBox
import org.controlsfx.control.ToggleSwitch

@DefaultProperty("topLevelTabs")
open class ActionTabbedPane : BorderPane() {
    // ---------------------------------------------
    // ------------ Properties ---------------------
    // ---------------------------------------------
    @FXML
    lateinit var tabPanels: VBox
        private set
    @FXML
    lateinit var actionPanels: VBox
        private set
    @FXML
    lateinit var centerPane: BorderPane
    @FXML
    private lateinit var longDescLabel: Label
    @FXML
    lateinit var guidanceSwitch: ToggleSwitch
    @FXML
    lateinit var ideasIcon: ImageView
    @FXML
    private lateinit var collapseSidebarButton: Button
    @FXML
    private lateinit var expandSidebarButton: Button
    @FXML
    private lateinit var sidebar: BorderPane
    @FXML
    private lateinit var sidebarScrollPane: ScrollPane

    var topLevelTabs: ObservableList<ActionTab> = FXCollections.observableArrayList()
    val allTabs: List<ActionTab>
        get() = topLevelTabs + topLevelTabs.flatMap(ActionTab::recursiveChildTabs)
    var activeTab: ActionTab? = null
        private set
    val activeTabs: List<ActionTab>
        get() {
            val activeTabs: ArrayList<ActionTab> = ArrayList()
            var currentTab = activeTab
            while (currentTab != null) {
                activeTabs.add(currentTab)
                currentTab = currentTab.parentTab
            }
            return activeTabs
        }
    val selectedTabListeners: MutableList<() -> Unit> = mutableListOf()

    private val childTabListener: ListChangeListener<ActionTab> = object : ListChangeListener<ActionTab> {
        override fun onChanged(change: ListChangeListener.Change<out ActionTab>) {
            while (change.next()) {
                if (change.wasAdded()) {
                    change.addedSubList.forEach { added ->
                        added.tabbedPane = this@ActionTabbedPane
                        added.childTabs.addListener(this)
                        added.recursiveChildTabs.forEach { child ->
                            child.tabbedPane = this@ActionTabbedPane
                            child.childTabs.addListener(this)
                        }
                    }

                } else if (change.wasRemoved()) {
                    change.removed.forEach { rem ->
                        rem.tabbedPane = null
                        rem.childTabs.removeListener(this)
                        rem.recursiveChildTabs.forEach { child ->
                            child.tabbedPane = null
                            child.childTabs.removeListener(this)
                        }
                    }
                }
            }
            update()
        }
    }

    // ---------------------------------------------
    // ------------ Methods ------------------------
    // ---------------------------------------------

    init {
        FXMLUtil.load(this, ActionTabbedPane::class.java)
        topLevelTabs.addListener(childTabListener)
    }

    open fun init() {
        collapseSidebarButton.setOnAction {
            expandSidebarButton.isManaged = true
            expandSidebarButton.isVisible = true
            sidebar.isVisible = false
            sidebar.isManaged = false
            collapseSidebarButton.isManaged = false
            collapseSidebarButton.isVisible = false
        }

        expandSidebarButton.setOnAction {
            expandSidebarButton.isVisible = false
            expandSidebarButton.isManaged = false
            sidebar.isManaged = true
            sidebar.isVisible = true
            collapseSidebarButton.isManaged = true
            collapseSidebarButton.isVisible = true
        }

        // Set position of collapseSidebarButton depending on visibility of vertical scroll pane
        if (sidebarScrollPane.getSkin() == null) {
            // Skin is not yet attached, wait until skin is attached to access the scroll bars
            val skinChangeListener = object : ChangeListener<Skin<*>> {
                override fun changed(observable: ObservableValue<out Skin<*>>?, oldValue: Skin<*>?, newValue: Skin<*>?) {
                    sidebarScrollPane.skinProperty().removeListener(this)
                    accessScrollBar(sidebarScrollPane)
                }
            }
            sidebarScrollPane.skinProperty().addListener(skinChangeListener)
        } else {
            // Skin is already attached, just access the scroll bars
            accessScrollBar(sidebarScrollPane)
        }

        val globalIdeasPopup = IdeasPopup()
        fun activeTabIdeas() = (activeTab as? IdeasEnabledTab)?.ideas ?: FXCollections.observableArrayList()
        globalIdeasPopup.init(activeTabIdeas(), ideasIcon)
        selectedTabListeners.add {
            val action = { globalIdeasPopup.ideasProperty.set(activeTabIdeas()) }
            // selected tab changed! update ideas to show only those of current tab
            if (globalIdeasPopup.isShowing) {
                // Delay setting of new ideas until the currently open ideas popup is closed
                globalIdeasPopup.onCloseRequest = EventHandler {
                    action()
                    globalIdeasPopup.onCloseRequest = null
                }
            } else {
                // tab is currently closed, ideas can directly be shown
                action()
            }
            Unit
        }
        Tooltip.install(guidanceSwitch, Tooltip("Enable or disable guidance"))
        guidanceSwitch.selectedProperty().addListener { _, _, isSelected ->
            ideasIcon.isDisable = !isSelected
            globalIdeasPopup.updateIdeasIconState()
        }
        ideasIcon.cursor = Cursor.HAND
    }

    private fun accessScrollBar(scrollPane: ScrollPane) {
        for (node in scrollPane.lookupAll(".scroll-bar")) {
            if (node is ScrollBar) {
                if (node.orientation == Orientation.HORIZONTAL) {
                    // Do something with the horizontal scroll bar
                }
                if (node.orientation == Orientation.VERTICAL) {
                    // Do something with the vertical scroll bar
                    val visibleOffset = 21.0
                    val invisibleOffset = 7.0
                    AnchorPane.setRightAnchor(collapseSidebarButton, if (node.isVisible) visibleOffset else invisibleOffset)
                    node.visibleProperty().addListener { _, _, newValue -> AnchorPane.setRightAnchor(collapseSidebarButton, if (newValue) visibleOffset else invisibleOffset) }
                }

            }
        }
    }

    fun update() {
        Platform.runLater {
            updateSelection()
            updateCenterContent()

            updateTabPanels()
            updateActionPanels()
            updateLongDesc()
        }
    }

    private fun updateSelection() {
        // If current tab has been removed, select parent of the deselected one or empty screen
        if (!allTabs.contains(activeTab)) {
            if (topLevelTabs.isEmpty()) {
                select(null, false)
            } else {
                var parent = activeTab?.parentTab
                while (parent != null) {
                    if (allTabs.contains(parent)) {
                        select(parent, false)
                        return
                    }
                    parent = parent.parentTab
                }
                select(topLevelTabs.first(), false)
            }
        }
    }

    private fun updateTabPanels() {
        //val activeTabs = topLevelTabs

        fun addPanel(tab: ActionTab) {
            tabPanels.children.add(tab.tabSidePanel)
        }

        fun addPanels(tab: ActionTab) {
            addPanel(tab)
            if (activeTabs.contains(tab)) {
                tab.childTabs.forEach { addPanels(it) }
            }
        }

        tabPanels.children.forEach { (it as? ActionTabSidePanel)?.clickListeners?.clear() }
        tabPanels.children.clear()
        topLevelTabs.forEach {
            addPanels(it)
        }
    }

    private fun updateCenterContent() {
        centerPane.center = ScrollPane(activeTab).also {
            it.isFitToHeight = true
            it.isFitToWidth = true
            it.hbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
            it.vbarPolicy = ScrollPane.ScrollBarPolicy.AS_NEEDED
        }
    }

    private fun updateActionPanels() {
        fun createPanel(action: ActionTabAction): ActionTabSidePanel =
                ActionTabSidePanel().apply {
                    init(SimpleStringProperty(action.name), SimpleStringProperty(action.description), action.icon, enabled = action.enabled)
                    clickListeners.add {
                        action.function.invoke()
                    }
                }

        actionPanels.children.forEach { (it as? ActionTabSidePanel)?.clickListeners?.clear() }
        actionPanels.children.clear()

        if (activeTab != null && activeTab?.actions?.isNotEmpty() == true) {
            val actionsGroupedByCategory = activeTab?.actions?.groupBy { it.category ?: "" }?.toSortedMap()

            actionsGroupedByCategory?.keys?.forEach { category ->
                if (category.isNotEmpty()) {
                    actionPanels.children.add(Label("$category:").apply { styleClass.add("sub-header-text") }.apply { VBox.setMargin(this, Insets(0.0, 0.0, 0.0, 5.0)) })
                    actionPanels.children.add(Separator())
                }
                actionsGroupedByCategory[category]?.sortedBy { it.name }?.forEach {
                    actionPanels.children.add(createPanel(it))
                }
            }
        }
    }

    private fun updateLongDesc() {
        longDescLabel.textProperty().unbind()
        if (activeTab != null) {
            longDescLabel.textProperty().bind(activeTab!!.longDescription)
        }
    }

    fun select(tab: ActionTab?) = select(tab, true)

    private fun select(tab: ActionTab?, updateAfterwards: Boolean) {
        if (activeTab != tab) {
            allTabs.forEach { it.selected.set(false) }
            if (tab == null) {
                activeTab = null
                if (updateAfterwards) update()
            } else {
                if (!allTabs.contains(tab)) {
                    activeTab = null
                    if (updateAfterwards) update()
                } else {
                    activeTab = tab
                    activeTab?.selected?.set(true)
                    if (tab.selected.get()) {
                        selectedTabListeners.forEach { it() }
                        if (updateAfterwards) update()
                    }
                }
            }
        }
    }

    fun remove(tab: ActionTab) {
        if (allTabs.contains(tab)) {
            if (tab.parentTab != null) {
                tab.parentTab!!.childTabs.remove(tab)
            } else {
                topLevelTabs.remove(tab)
            }
        }
    }

    fun getCloseableTabCount(): Int = topLevelTabs.count { it.closeable }
}