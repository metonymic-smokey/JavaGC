package at.jku.anttracks.gui.component.treetableview

import javafx.scene.Node
import javafx.scene.control.TreeItem

open class AntTreeItem<T>(data: T, graphic: Node?) : TreeItem<T>(data, graphic)
