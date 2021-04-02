package at.jku.anttracks.gui.component.treetableview.cell

import at.jku.anttracks.gui.utils.toBytesMemoryUsageString
import at.jku.anttracks.gui.utils.toShortMemoryUsageString
import javafx.scene.control.ContentDisplay
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.text.TextAlignment

class MemoryTreeCell<T> : AntTreeCell<T, Number>() {
    override val node: Label = Label().apply {
        contentDisplay = ContentDisplay.RIGHT
        textAlignment = TextAlignment.RIGHT
    }

    override fun updateNode(item: Number) {
        node.text = toShortMemoryUsageString(item.toLong())
    }

    override fun calculateTooltip(item: Number): Tooltip? {
        return Tooltip(toBytesMemoryUsageString(item.toLong()))
    }
}
