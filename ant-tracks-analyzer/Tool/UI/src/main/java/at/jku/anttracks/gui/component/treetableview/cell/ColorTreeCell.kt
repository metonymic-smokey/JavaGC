package at.jku.anttracks.gui.component.treetableview.cell

import javafx.scene.layout.Pane
import java.awt.Color

class ColorTreeCell<T> : AntTreeCell<T, Color>() {
    override fun updateNode(item: Color) {
        var hex = Integer.toHexString(item.rgb and 0xffffff)
        while (hex.length < 6) {
            hex = "0$hex"
        }
        node.style = "-fx-background-color: #$hex;"
    }

    override val node = Pane()
}