package at.jku.anttracks.gui.utils

import javafx.scene.Node
import javafx.scene.Scene

object JavaFXUtil {
    infix fun Node.isChildOf(parentInQuestion: Node): Boolean {
        var curParent = this.parent
        while (curParent != null) {
            if (curParent === parentInQuestion) {
                return true
            }
            curParent = curParent.parent
        }
        return false
    }

    infix fun Node.isChildOf(sceneInQuestion: Scene): Boolean {
        var rootOfNode: Node = this
        while (rootOfNode.parent != null) {
            rootOfNode = rootOfNode.parent
        }
        return sceneInQuestion.root === rootOfNode
    }
}