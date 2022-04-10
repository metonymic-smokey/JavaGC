package at.jku.anttracks.gui.utils

import javafx.scene.control.TreeItem

fun <T> TreeItem<T>.hasChild(child: TreeItem<T>): Boolean = this.children.any { it == child } || this.children.any { it.hasChild(child) }