package at.jku.anttracks.gui.component.listview

import javafx.scene.Node
import javafx.scene.control.ListCell
import javafx.scene.control.Tooltip

abstract class AntListCell<T> : ListCell<T>() {
    private var lastItem: T? = null

    abstract val node: Node

    override fun updateItem(item: T?, empty: Boolean) {
        if (item === lastItem) {
            return
        }
        super.updateItem(item, empty)

        if (empty || item == null) {
            if (graphic != null) {
                text = null
                graphic = null
                tooltip = null
            }
        } else {
            text = null
            graphic = node
            updateNode(item)
            setTooltip(calculateTooltip(item))
        }
        lastItem = item
    }

    abstract fun updateNode(item: T)
    open fun calculateTooltip(item: T): Tooltip? = null
}
