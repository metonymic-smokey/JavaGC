
package at.jku.anttracks.gui.component.treetableview

import javafx.beans.value.ChangeListener
import javafx.scene.Node

class AutomatedTreeItem<CONTAINER, DATA>(
        private val container: CONTAINER,
        private val titleFunction: (CONTAINER) -> String,
        private val dataFunction: (CONTAINER) -> DATA,
        private val childFunction: (CONTAINER) -> Collection<CONTAINER>,
        private val subTreeLevelFunction: (CONTAINER) -> Int,
        private val iconFunction: ((CONTAINER) -> Node?)?,
        expansionListener: ChangeListener<Boolean>?,
        childFilterFunction: ((CONTAINER) -> Boolean)? = null) : AntTreeItem<DATA>(dataFunction(container), iconFunction?.invoke(container)) {

    val subTreeLevel: Int
        get() = subTreeLevelFunction(container)

    init {
        expandedProperty().addListener(expansionListener)

        children.addAll(childFunction(container).filter(childFilterFunction ?: { true }).map { childContainer ->
            AutomatedTreeItem(childContainer,
                              titleFunction,
                              dataFunction,
                              childFunction,
                              subTreeLevelFunction,
                              iconFunction,
                              expansionListener,
                              childFilterFunction)
        })
    }
}
