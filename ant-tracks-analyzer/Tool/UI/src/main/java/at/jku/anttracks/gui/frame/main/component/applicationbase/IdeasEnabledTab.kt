package at.jku.anttracks.gui.frame.main.component.applicationbase

import at.jku.anttracks.gui.component.actiontab.ActionTabAction
import at.jku.anttracks.gui.component.actiontab.tab.ActionTab
import at.jku.anttracks.gui.frame.main.component.ideascontainer.IdeasPopup
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.model.Description
import at.jku.anttracks.gui.model.Idea
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.JavaFXUtil.isChildOf
import javafx.beans.binding.Bindings
import javafx.beans.property.StringProperty
import javafx.collections.FXCollections
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.geometry.Insets
import javafx.scene.Cursor
import javafx.scene.Node
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.layout.BorderPane
import javafx.scene.layout.StackPane
import javafx.scene.text.TextFlow
import org.controlsfx.control.PopOver
import java.util.concurrent.Callable
import javax.swing.Icon

open class IdeasEnabledTab : ActionTab() {
    @FXML
    private lateinit var overlayPane: StackPane

    @FXML
    private lateinit var tabContent: BorderPane

    open val componentDescriptions: List<Triple<Node, Description, PopOver.ArrowLocation?>>
        get() = listOf()
    open val initialTabIdeas: List<Idea>
        get() = listOf()

    val ideas: ObservableList<Idea> = FXCollections.observableArrayList()
    private val ideasAtNodes = mutableMapOf<Node, IdeasPopup>()
    private val componentDescriptionPopOvers: List<Pair<Node, PopOver>> by lazy {
        componentDescriptions.map { componentDescription ->
            val textNodes = componentDescription.second.toTextNodes()
            textNodes.forEach { it.styleClass.add("componentDescriptionPopOverText") }
            val textFlow = TextFlow(*textNodes.toTypedArray())
            textFlow.lineSpacing = 1.5
            textFlow.prefWidth = 250.0
            textFlow.padding = Insets(10.0)
            val popOver = PopOver(textFlow)
            popOver.isCloseButtonEnabled = true
            popOver.isDetachable = false
            popOver.isAutoFix = true
            popOver.arrowLocation = componentDescription.third
            // we don't use the default popover styles
            popOver.root.stylesheets.clear()
            popOver.contentNode.hoverProperty().addListener { obs, wasHovered, isHovered ->
                if (isHovered) {
                    // hide all other popovers when one is hovered
                    componentDescriptionPopOvers.forEach { it.second.opacity = 0.1 }
                    popOver.opacity = 1.0
                    popOver.contentNode.toFront()

                    // highlight node described by currently hovered popover
                    componentDescription.first.styleClass.add("ideaHighlightedNode")
                } else {
                    componentDescriptionPopOvers.forEach { it.second.opacity = 1.0 }
                    componentDescription.first.styleClass.remove("ideaHighlightedNode")
                }
            }

            popOver.contentNode.setOnMouseClicked { evt ->
                componentDescription.first.styleClass.remove("ideaHighlightedNode")
                componentDescriptionPopOvers.forEach { it.second.hide() }
            }

            Pair(componentDescription.first, popOver)
        }
    }

    init {
        FXMLUtil.load(this, IdeasEnabledTab::class.java)
    }

    fun getTabContent(): BorderPane = tabContent

    override fun init(name: StringProperty,
                      shortDescription: StringProperty,
                      longDescription: StringProperty,
                      icon: Icon?,
                      actions: List<ActionTabAction>,
                      closeable: Boolean) {
        super.init(name, shortDescription, longDescription, icon, actions, closeable)

        ideas.addListener(ListChangeListener<Idea> { change ->
            while (change.next()) {
                if (change.wasAdded()) {
                    for (addedIdea in change.addedSubList) {
                        if (addedIdea.nodes != null) {
                            for ((node, position) in addedIdea.nodes) {
                                if (node isChildOf this) {
                                    if (ideasAtNodes[node] == null) {
                                        val nodeLeft = {
                                            if (node.localToScreen(node.boundsInLocal) == null || overlayPane.localToScreen(tabContent.boundsInLocal) == null) -100
                                            else (node.localToScreen(node.boundsInLocal).minX - overlayPane.localToScreen(tabContent.boundsInLocal).minX).toInt()
                                        }
                                        val nodeRight = {
                                            if (node.localToScreen(node.boundsInLocal) == null || overlayPane.localToScreen(tabContent.boundsInLocal) == null) -100
                                            else (node.localToScreen(node.boundsInLocal).maxX - overlayPane.localToScreen(tabContent.boundsInLocal).minX).toInt()
                                        }

                                        val nodeTop = {
                                            if (node.localToScreen(node.boundsInLocal) == null || overlayPane.localToScreen(tabContent.boundsInLocal) == null) -100
                                            else (node.localToScreen(node.boundsInLocal).minY - overlayPane.localToScreen(tabContent.boundsInLocal).minY).toInt()
                                        }
                                        val nodeBottom = {
                                            if (node.localToScreen(node.boundsInLocal) == null || overlayPane.localToScreen(tabContent.boundsInLocal) == null) -100
                                            else (node.localToScreen(node.boundsInLocal).maxY - overlayPane.localToScreen(tabContent.boundsInLocal).minY).toInt()
                                        }

                                        val ideasIcon = ImageView().apply {
                                            isPickOnBounds = true
                                            isPreserveRatio = true
                                            fitWidth = ICON_SIZE.toDouble()
                                            cursor = Cursor.HAND
                                            opacity = 0.5
                                            hoverProperty().addListener { _, _, isHovered ->
                                                if (isHovered) {
                                                    opacity = 1.0
                                                } else {
                                                    opacity = 0.5
                                                }
                                            }
                                            visibleProperty().bind(ClientInfo.mainFrame.mainTabbedPane.guidanceSwitch.selectedProperty())
                                            // keep ideasIcon floating over node
                                            translateXProperty().bind(Bindings.createIntegerBinding(
                                                    Callable {
                                                        when (position) {
                                                            Idea.BulbPosition.TOP_LEFT, Idea.BulbPosition.MIDDLE_LEFT, Idea.BulbPosition.BOTTOM_LEFT ->
                                                                nodeLeft()
                                                            Idea.BulbPosition.TOP_MIDDLE, Idea.BulbPosition.MIDDLE_MIDDLE, Idea.BulbPosition.BOTTOM_MIDDLE ->
                                                                nodeLeft() + (nodeRight() - nodeLeft() - ICON_SIZE) / 2
                                                            Idea.BulbPosition.TOP_RIGHT, Idea.BulbPosition.MIDDLE_RIGHT, Idea.BulbPosition.BOTTOM_RIGHT ->
                                                                nodeRight() - ICON_SIZE
                                                        }
                                                    },
                                                    node.layoutXProperty(),
                                                    node.layoutYProperty(),
                                                    node.layoutBoundsProperty(),
                                                    node.boundsInParentProperty()))

                                            translateYProperty().bind(Bindings.createIntegerBinding(
                                                    Callable {
                                                        when (position) {
                                                            Idea.BulbPosition.TOP_LEFT, Idea.BulbPosition.TOP_MIDDLE, Idea.BulbPosition.TOP_RIGHT ->
                                                                nodeTop()
                                                            Idea.BulbPosition.MIDDLE_LEFT, Idea.BulbPosition.MIDDLE_MIDDLE, Idea.BulbPosition.MIDDLE_RIGHT ->
                                                                nodeTop() + (nodeBottom() - nodeTop() - ICON_SIZE) / 2
                                                            Idea.BulbPosition.BOTTOM_LEFT, Idea.BulbPosition.BOTTOM_MIDDLE, Idea.BulbPosition.BOTTOM_RIGHT ->
                                                                nodeBottom() - ICON_SIZE
                                                        }
                                                    },
                                                    node.layoutXProperty(),
                                                    node.layoutYProperty(),
                                                    node.layoutBoundsProperty(),
                                                    node.boundsInParentProperty()))
                                        }
                                        val ideasPopup = IdeasPopup()
                                        ideasPopup.init(FXCollections.observableArrayList<Idea>(addedIdea), ideasIcon)
                                        overlayPane.children.add(ideasIcon)
                                        ideasAtNodes[node] = ideasPopup
                                    } else {
                                        // Lightbulb already exists for the idea's node
                                        // Add the idea to the list of the ligthbulb's ideas
                                        ideasAtNodes[node]!!.ideasProperty.get().add(addedIdea)
                                    }

                                    // expired ideas should be removed, and once all ideas of a node have expired, the ideaIcon for this node should be removed too
                                    addedIdea.expiredProperty?.addListener { _, _, isExpired ->
                                        if (isExpired) {
                                            ideas.remove(addedIdea)
                                            if (getIdeas(node).isNotEmpty()) {
                                                overlayPane.children.remove(ideasAtNodes[node]!!.ideasIcon)
                                                ideasAtNodes.remove(node)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        })
        initialTabIdeas.forEach { ideas.add(it) }
    }

    fun showComponentDescriptions() {
        if (ClientInfo.mainFrame.currentTab == this) {
            componentDescriptionPopOvers.forEach {
                if (it.first.isVisible && ClientInfo.mainFrame.currentTab.hasInView(it.first))
                // most of the node is visible on the screen
                    it.second.show(it.first)
            }
        }
    }

    fun getIdeas(node: Node) = ideas.filter { idea -> idea.nodes?.any { it.node == node } ?: false }

    fun removeAllButInitialIdeas() {
        ideas.clear()
    }

    companion object {
        const val ICON_SIZE = 44
    }
}