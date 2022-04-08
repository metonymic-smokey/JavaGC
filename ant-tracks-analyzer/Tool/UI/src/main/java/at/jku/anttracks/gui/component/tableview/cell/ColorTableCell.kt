package at.jku.anttracks.gui.component.tableview.cell

import javafx.scene.layout.Pane
import java.awt.Color

open class ColorTableCell<T> : AntTableCell<T, Color>() {
    override fun updateNode(item: Color) {
        var hex = Integer.toHexString(item.rgb and 0xffffff)
        while (hex.length < 6) {
            hex = "0$hex"
        }
        node.style = "-fx-background-color: #$hex; -fx-border-color: black;"
    }

    override val node = Pane()
}