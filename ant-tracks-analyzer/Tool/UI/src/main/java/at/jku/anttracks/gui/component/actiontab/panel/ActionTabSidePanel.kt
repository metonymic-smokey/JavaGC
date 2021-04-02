package at.jku.anttracks.gui.component.actiontab.panel

import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.Consts.DOT_ICON
import at.jku.anttracks.gui.utils.Consts.NODE_WITHOUT_ICON_ICON
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.ImageUtil
import javafx.beans.DefaultProperty
import javafx.beans.binding.BooleanExpression
import javafx.beans.property.SimpleBooleanProperty
import javafx.beans.property.SimpleStringProperty
import javafx.beans.property.StringProperty
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.Node
import javafx.scene.control.ContextMenu
import javafx.scene.control.Label
import javafx.scene.control.MenuItem
import javafx.scene.input.MouseButton
import javafx.scene.input.MouseEvent
import javafx.scene.layout.BorderPane
import javafx.scene.layout.HBox
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import java.util.*
import javax.swing.Icon

@DefaultProperty("childTabs")
class ActionTabSidePanel : BorderPane() {
    data class ActionTabPanelContextMenuOperation(val text: String, val operation: () -> Unit, val icon: Node? = null)

    @FXML
    private lateinit var uiTextFlow: TextFlow
    @FXML
    private lateinit var uiSubTextFlow: TextFlow
    @FXML
    private lateinit var left: HBox
    @FXML
    private lateinit var right: HBox

    var text: StringProperty = SimpleStringProperty("")
        private set
    var subText: StringProperty = SimpleStringProperty("")
        private set
    var icon: Icon? = null
        private set
    var expansionNode: Node? = null
        private set
    var enabled: BooleanExpression = SimpleBooleanProperty(true)
        private set
    var level: Int = 0
        private set

    val clickListeners: MutableList<() -> Unit> = ArrayList()

    fun init(text: StringProperty = this.text,
             subText: StringProperty = this.subText,
             icon: Icon? = this.icon,
             expansionNode: Node? = this.expansionNode,
             enabled: BooleanExpression = this.enabled,
             level: Int = this.level,
             contextMenuOperations: List<ActionTabPanelContextMenuOperation> = emptyList()) {
        FXMLUtil.load(this, ActionTabSidePanel::class.java)

        this.text = text
        this.subText = subText
        this.icon = icon
        this.expansionNode = expansionNode
        this.enabled = enabled
        this.level = level

        disableProperty().bind(enabled.not())
        val uiText = Text()
        uiText.textProperty().bind(text)
        uiTextFlow.children.add(uiText)
        val uiSubText = Text()
        uiSubText.textProperty().bind(text)
        uiSubText.visibleProperty().bind(uiSubText.textProperty().isNotEmpty)
        uiSubText.managedProperty().bind(uiSubText.visibleProperty())
        uiSubText.textProperty().bind(subText)
        uiSubTextFlow.children.add(uiSubText)

        if (contextMenuOperations.isNotEmpty()) {
            val contextMenu =
                    ContextMenu(
                            *contextMenuOperations.map { (name, operation, iconNode) ->
                                MenuItem(name, iconNode).apply { onAction = EventHandler { operation.invoke() } }
                            }.toTypedArray()
                    )
            onContextMenuRequested = EventHandler { contextMenu.show(this, it.screenX, it.screenY) }
        }

//        val icons: Array<Node> = Array(level + 1) { index ->
//            ImageUtil.getIconNode(when (index) {
//                                      level -> icon ?: NODE_WITHOUT_ICON_ICON
//                                      level - 1 -> TOP_RIGHT_ICON
//                                      else -> NODE_WITHOUT_ICON_ICON
//                                  })
//        }

//        val icons: Array<Node> = Array(level + 1) { index ->
//            ImageUtil.getIconNode(when (index) {
//                                      level -> icon ?: NODE_WITHOUT_ICON_ICON
//                                      else -> HORIZONTAL_ICON
//                                  })
//        }

        val icons: Array<Node> = Array(level + 1) { index ->
            ImageUtil.getIconNode(if (index == level) icon ?: DOT_ICON else NODE_WITHOUT_ICON_ICON,
                                  (Consts.DEFAULT_ICON_SIZE * 1.5).toInt(),
                                  (Consts.DEFAULT_ICON_SIZE * 1.5).toInt())
        }

        left.children.clear()
        icons.forEach { left.children.add(it) }
        left.children.add(Label(""))


        if (expansionNode != null) {
            right.children.clear()
            right.children.add(Label("   "))
            right.children.add(expansionNode)
        }

        addEventHandler(MouseEvent.MOUSE_CLICKED) { event ->
            if (event.button == MouseButton.PRIMARY) {
                if (enabled.get()) {
                    clickListeners.toTypedArray().forEach { it.invoke() }
                    event.consume()
                }
            }
        }
    }
}
