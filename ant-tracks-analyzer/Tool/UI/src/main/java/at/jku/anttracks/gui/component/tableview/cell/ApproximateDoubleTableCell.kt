package at.jku.anttracks.gui.component.tableview.cell

import at.jku.anttracks.gui.model.ApproximateDouble
import javafx.scene.Node
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.text.TextAlignment

class ApproximateDoubleTableCell<TREE_ITEM_TYPE> : AntTableCell<TREE_ITEM_TYPE, ApproximateDouble>() {

    private var label = Label().apply {
        contentDisplay = ContentDisplay.RIGHT
        textAlignment = TextAlignment.RIGHT
    }

    override val node: Node
        get() = label

    override fun updateNode(item: ApproximateDouble) {
        label.text = String.format("%s%,d", if (item.isExact) "" else "~ ", item.value.toLong())
    }
}
