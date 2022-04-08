package at.jku.anttracks.gui.component.tableview.cell

import at.jku.anttracks.gui.model.Description
import javafx.geometry.Pos
import javafx.scene.layout.HBox
import javafx.scene.text.Text

class KeyTableCell<T> : AntTableCell<T, Description>() {
    override val node: HBox = HBox().apply {
        // Use default font, but make bold
        // font = Font.font(font.family, FontWeight.BOLD, font.size)
        alignment = Pos.CENTER_LEFT
    }

    override fun updateNode(item: Description) {
        node.children.clear()
        node.children.add(Text(" ")) // To increase space between icon and text
        node.children.addAll(item.toTextNodes())
    }
}