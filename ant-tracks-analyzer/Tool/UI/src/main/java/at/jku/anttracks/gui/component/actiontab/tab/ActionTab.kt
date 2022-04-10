package at.jku.anttracks.gui.component.actiontab.tab

import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.component.actiontab.pane.ActionTabbedPane
import at.jku.anttracks.gui.component.actiontab.panel.ActionTabSidePanel
import at.jku.anttracks.gui.utils.*
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import javafx.beans.binding.Bindings
import javafx.beans.property.BooleanProperty
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.css.PseudoClass
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.BorderPane
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.Icon

open class ActionTab : BorderPane() {
    private companion object {
        private val selectedPseudoClass: PseudoClass = PseudoClass.getPseudoClass("selected")
        var currentId = AtomicInteger()
    }

    var tabId = currentId.getAndIncrement()
    var name: StringProperty = SimpleStringProperty("")
    var shortDescription: StringProperty = SimpleStringProperty("")
    var longDescription: StringProperty = SimpleStringProperty("")
    var icon: Icon? = null
    var actions: List<ActionTabAction> = ArrayList()
    var closeable: Boolean = true

    val selected: BooleanProperty = SimpleBooleanProperty(false)

    val childTabs: ObservableList<ActionTab> = FXCollections.observableArrayList()
    val recursiveChildTabs: List<ActionTab>
        get() = childTabs + childTabs.flatMap(ActionTab::recursiveChildTabs)

    var parentTab: ActionTab? = null
    var tabbedPane: ActionTabbedPane? = null

    val level: Int by lazy {
        var l = 0
        var cur: ActionTab = this
        while (cur.parentTab != null) {
            l++
            cur = cur.parentTab!!
        }
        l
    }

    val tabSidePanel: ActionTabSidePanel
        get() {
            val text = name
            val subText = shortDescription
            val icon = icon
            val expansionNode: Node? = if (childTabs.isNotEmpty()) if (tabbedPane?.activeTabs?.contains(this) == true) Label("v") else Label(">") else null

            return ActionTabSidePanel()
                    .also { actionTabSidePanel ->
                        actionTabSidePanel.init(text,
                                                subText,
                                                icon,
                                                expansionNode,
                                                Bindings.createBooleanBinding({ true }, null),
                                                level,
                                                if (closeable) {
                                                    listOf(ActionTabSidePanel.ActionTabPanelContextMenuOperation("Close", {
                                                        tabbedPane?.remove(this)
                                                        closeListeners.forEach { it() }
                                                        recursiveChildTabs.flatMap { it.closeListeners }.forEach { it() }
                                                    }, ImageUtil.getIconNode(Consts.DELETE_IMAGE)))
                                                } else {
                                                    listOf()
                                                })
                        actionTabSidePanel.clickListeners.add {
                            tabbedPane?.select(this)
                        }
                        actionTabSidePanel.pseudoClassStateChanged(selectedPseudoClass, this.selected.get())
                    }
        }

    val closeListeners: MutableList<() -> Unit> = mutableListOf()

    init {
        FXMLUtil.load(this, ActionTab::class.java)
    }

    open fun init(name: StringProperty = this.name,
                  shortDescription: StringProperty = this.shortDescription,
                  longDescription: StringProperty = this.longDescription,
                  icon: Icon? = this.icon,
                  actions: List<ActionTabAction> = this.actions,
                  closeable: Boolean = this.closeable) {
        this.name = name
        this.shortDescription = shortDescription
        this.longDescription = longDescription
        this.icon = icon
        this.actions = actions
        this.closeable = closeable

        childTabs.addListener(ListChangeListener { change ->
            while (change.next()) {
                if (change.wasAdded()) {
                    change.addedSubList.forEach { it.parentTab = this }
                    change.addedSubList.forEach { it.tabbedPane = this.tabbedPane }
                }
            }
        })
        selected.addListener { obs, wasSelected, isSelected -> pseudoClassStateChanged(selectedPseudoClass, isSelected) }
    }

    fun hasInView(node: Node): Boolean =
            if (hasChild(node)) {
                // the given node is contained in this tab
                // we compute the intersection bewteen the node and the tab-containing scrollpane viewport
                // we then consider the node 'in view' if at least 75% of the node is visible (i.e. within the scrollpane viewport)
                val nodeBounds = node.localToScreen(node.boundsInLocal)
                val tabBounds = firstParentOfType(ScrollPane::class.java)!!.localToScreen(firstParentOfType(ScrollPane::class.java)!!.boundsInLocal)
                val nodeTabIntersection = nodeBounds.intersection(tabBounds, true)
                nodeTabIntersection.area() / nodeBounds.area() >= 0.75
            } else {
                // the given node is not contained in this node -> we can't say whether the node is in view
                true
            }

    open fun toJSON() = JsonObject().apply {
        add("tabId", JsonPrimitive(tabId));
        add("name", JsonPrimitive(name.get()))
        add("shortDescription", JsonPrimitive(shortDescription.get()))
        add("longDescription", JsonPrimitive(longDescription.get()))
    }
}

