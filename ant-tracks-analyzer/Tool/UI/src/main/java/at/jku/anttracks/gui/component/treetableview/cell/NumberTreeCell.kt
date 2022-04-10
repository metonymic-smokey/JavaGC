package at.jku.anttracks.gui.component.treetableview.cell

import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.text.TextAlignment
import java.text.DecimalFormat

class NumberTreeCell<T> : AntTreeCell<T, Number>() {
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
