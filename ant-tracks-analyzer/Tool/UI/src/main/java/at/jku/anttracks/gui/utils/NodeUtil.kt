package at.jku.anttracks.gui.utils

import javafx.geometry.Bounds
import javafx.scene.Node
import javafx.scene.Parent
import java.awt.Rectangle

fun Parent.hasChild(childNode: Node): Boolean = this.childrenUnmodifiable.contains(childNode) || this.childrenUnmodifiable.any { (it as? Parent)?.hasChild(childNode) ?: false }

fun Node.firstParentOfType(parentType: Class<out Node>): Parent? =
        if (parentType.isInstance(parent)) {
            parent
        } else {
            parent?.firstParentOfType(parentType)
        }

fun Bounds.intersection(otherBounds: Bounds, forceNonEmptyBounds: Boolean = false): Rectangle {
    val thisRect = Rectangle(minX.toInt(),
                             minY.toInt(),
                             if (forceNonEmptyBounds) Math.max(1, width.toInt()) else width.toInt(),
                             if (forceNonEmptyBounds) Math.max(1, height.toInt()) else height.toInt())
    val otherRect = Rectangle(otherBounds.minX.toInt(),
                              otherBounds.minY.toInt(),
                              if (forceNonEmptyBounds) Math.max(1, otherBounds.width.toInt()) else otherBounds.width.toInt(),
                              if (forceNonEmptyBounds) Math.max(1, otherBounds.height.toInt()) else otherBounds.height.toInt())
    return thisRect.intersection(otherRect)
}

fun Rectangle.area() = if (isEmpty) 0.0 else width * height.toDouble()

fun Bounds.area() = if (isEmpty) 0.0 else width * height