package at.jku.anttracks.gui.component.treetableview.cell

import at.jku.anttracks.gui.component.treetableview.AutomatedTreeItem
import at.jku.anttracks.gui.utils.ColorUtil.toFXColor
import javafx.beans.property.DoubleProperty
import javafx.beans.property.SimpleDoubleProperty
import javafx.geometry.Insets
import javafx.geometry.Pos
import javafx.scene.control.Label
import javafx.scene.layout.StackPane
import javafx.scene.shape.Rectangle
import java.awt.Color

abstract class BarTreeCell<TREE_TYPE, COLUMN_TYPE> : AntTreeCell<TREE_TYPE, COLUMN_TYPE>() {
    var colorId = -1
    var fillRatioProperty: DoubleProperty = SimpleDoubleProperty(0.0)

    val filledRect = Rectangle().also {
        it.widthProperty().bind(this.widthProperty().multiply(fillRatioProperty))
        it.heightProperty().bind(this.heightProperty())
    }
    val label = Label()
    override val node = StackPane(filledRect, label)

    override fun updateNode(item: COLUMN_TYPE) {
        val oldColorId = colorId
        if (treeTableRow != null && treeTableRow.treeItem != null && treeTableRow.treeItem is AutomatedTreeItem<*, *>) {
            colorId = (treeTableRow.treeItem as AutomatedTreeItem<*, *>).subTreeLevel % PERCENTAGE_COLORS.size
        } else {
            colorId = 0
        }

        // Graphical
        StackPane.setAlignment(filledRect, Pos.CENTER_LEFT)
        StackPane.setMargin(filledRect, javafx.geometry.Insets(0.0, 0.0, 0.0, 0.0))

        if (colorId != oldColorId) {
            // Change style only if necessary, CSS is expensive!
            val color = PERCENTAGE_COLORS[colorId]
            filledRect.fill = color.toFXColor()
        }

        // Bar
        fillRatioProperty.value = defineFillRatio(item)

        // Text
        StackPane.setAlignment(label, Pos.CENTER_LEFT)
        StackPane.setMargin(label, Insets(0.0, 0.0, 0.0, 0.0))

        label.text = defineTextToShow(item)
    }

    protected abstract fun defineTextToShow(item: COLUMN_TYPE): String

    protected abstract fun defineFillRatio(item: COLUMN_TYPE): Double

    companion object {

        val PERCENTAGE_COLORS = arrayOf(
                Color(153, 213, 148, 255),
                Color(252, 141, 89, 255),
                // new Color(255, 204, 0), WTF YELLOW DOES NOT WORK!?
                Color(255, 255, 191, 255),
                // new Color( 255, 128, 0) WTF, ORANGE DOES NOT WORK EITHER!!??
                // new Color(222, 128, 0) Aaaand also not working
                Color(146, 197, 222, 255))
    }
}
