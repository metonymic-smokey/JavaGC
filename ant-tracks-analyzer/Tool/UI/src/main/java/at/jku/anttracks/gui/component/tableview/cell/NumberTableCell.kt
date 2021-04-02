package at.jku.anttracks.gui.component.tableview.cell

import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.text.TextAlignment
import java.text.DecimalFormat

class NumberTableCell<T> : AntTableCell<T, Number>() {
    override val node: Label = Label().apply {
        textAlignment = TextAlignment.RIGHT
        contentDisplay = ContentDisplay.RIGHT
    }

    override fun updateNode(item: Number) {
        val result = item.toDouble()
        val text = numberFormat.format(result)
        node.text = text
    }

    companion object {
        private val numberFormat = DecimalFormat("###,###.#")
    }
}
