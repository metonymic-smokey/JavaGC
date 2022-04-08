package at.jku.anttracks.gui.model

import at.jku.anttracks.gui.component.actiontab.tab.ActionTab
import javafx.beans.binding.BooleanBinding
import javafx.scene.Node
import java.time.LocalDateTime
import java.util.*

class Idea(val title: String,
           val description: Description,
           val actionsOnClick: List<ClickAction>? = null,
           val nodes: List<IdeaNode>? = null,
           val tab: ActionTab,
           val actionOnHover: HoverAction? = null,
           val expiredProperty: BooleanBinding? = null) {

    // creation time of this idea
    val time: LocalDateTime = LocalDateTime.now()

    var read: Boolean = false

    val actionsTriggered = BitSet(actionsOnClick?.size ?: 0)

    val isHoverable
        get() = !nodes.isNullOrEmpty() || actionOnHover != null

    val isExecutable
        get() = actionsOnClick != null

    fun highlightNode() {
        nodes?.forEach { it.node.styleClass.add("ideaHighlightedNode") }
    }

    fun unhighlightNode() {
        nodes?.forEach { it.node.styleClass.remove("ideaHighlightedNode") }
    }

    companion object {
        enum class Style(val styleClass: String) {
            DEFAULT("ideaBoxDescriptionDefault"),
            EMPHASIZE("ideaBoxDescriptionEmphasize"),
            CODE("ideaBoxDescriptionCode");
        }

    }

    enum class BulbPosition {
        TOP_LEFT,
        TOP_MIDDLE,
        TOP_RIGHT,
        MIDDLE_LEFT,
        MIDDLE_MIDDLE,
        MIDDLE_RIGHT,
        BOTTOM_LEFT,
        BOTTOM_MIDDLE,
        BOTTOM_RIGHT
    }

    data class ClickAction(val label: String, val action: () -> Unit, var triggered: Boolean = false)
    data class HoverAction(val action: () -> Unit, val undoAction: () -> Unit = {})
    data class IdeaNode(val node: Node, val bulbPosition: BulbPosition)
}

infix fun String.does(action: () -> Unit) = Idea.ClickAction(this, action)
infix fun String.performs(action: () -> Unit) = this does action

infix fun Node.at(bulbPosition: Idea.BulbPosition) = Idea.IdeaNode(this, bulbPosition)

infix fun (() -> Unit).revertVia(undoAction: () -> Unit) = Idea.HoverAction(this, undoAction)
