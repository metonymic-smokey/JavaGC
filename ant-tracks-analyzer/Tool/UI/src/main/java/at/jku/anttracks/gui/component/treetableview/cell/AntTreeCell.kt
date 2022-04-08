package at.jku.anttracks.gui.component.treetableview.cell

import javafx.scene.Node
import javafx.scene.control.Tooltip
import javafx.scene.control.TreeTableCell

abstract class AntTreeCell<TREE_ITEM_TYPE, COLUMN_ITEM_TYPE> : TreeTableCell<TREE_ITEM_TYPE, COLUMN_ITEM_TYPE>() {
    private var lastItem: COLUMN_ITEM_TYPE? = null

    abstract val node: Node

    override fun updateItem(item: COLUMN_ITEM_TYPE?, empty: Boolean) {
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

    abstract fun updateNode(item: COLUMN_ITEM_TYPE)
    open fun calculateTooltip(item: COLUMN_ITEM_TYPE): Tooltip? = null
}
