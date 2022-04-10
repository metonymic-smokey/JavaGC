package at.jku.anttracks.gui.frame.main.component.ideascontainer

import at.jku.anttracks.gui.frame.main.component.applicationbase.IdeasEnabledTab
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.model.Idea
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.binding.Bindings
import javafx.beans.property.SimpleObjectProperty
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ScrollPane
import javafx.scene.effect.DropShadow
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Popup

class IdeasPopup : Popup() {
    @FXML
    lateinit var closeButton: Button
    @FXML
    lateinit var ideasContainer: VBox
    @FXML
    lateinit var emptyIdeasIcon: ImageView
    @FXML
    lateinit var ideasContainerScrollPane: ScrollPane

    lateinit var ideasIcon: ImageView
    val ideasProperty = SimpleObjectProperty<ObservableList<Idea>>()
    private val ideasListChangeListener = ListChangeListener<Idea> { change ->
        while (change.next()) {
            change.removed.forEach { removeFromUI(it) }
            change.addedSubList.forEach { addToUI(it) }
            updateIdeasIconState()
        }
    }

    init {
        FXMLUtil.load(this, IdeasPopup::class.java)
    }

    fun init(ideas: ObservableList<Idea>, ideasIcon: ImageView) {
        width = 500.0
        // use scrollpane only when ideas can't fit the screen
        ideasContainerScrollPane.prefViewportHeightProperty().bind(Bindings.min(ClientInfo.mainFrame.mainTabbedPane.heightProperty(),
                                                                                ideasContainer.heightProperty()))
        emptyIdeasIcon.image = SwingFXUtils.toFXImage(Consts.ANT_CONFUSED_ICON, WritableImage(250, 200))
        closeButton.setOnAction { hide() }

        this.ideasIcon = ideasIcon
        // define open and close behavior of popup
        ideasIcon.setOnMouseClicked {
            if (!isShowing) {
                show(ideasIcon,
                     Math.max(ideasIcon.localToScreen(0.0, 0.0).x, 0.0),
                     Math.max(ideasIcon.localToScreen(0.0, 0.0).y, 0.0))
            } else {
                hide()
            }
        }
        setOnHidden { updateIdeasIconState() }

        // handle changes in ideas model
        ideasProperty.addListener { _, oldIdeas, newIdeas ->
            oldIdeas?.removeListener(ideasListChangeListener)
            newIdeas?.addListener(ideasListChangeListener)
            (oldIdeas ?: listOf<Idea>() - newIdeas).forEach { removeFromUI(it) }
            (newIdeas ?: listOf<Idea>() - oldIdeas).forEach { addToUI(it) }
            updateIdeasIconState()
        }
        ideasProperty.set(ideas)
    }

    private fun addToUI(newIdea: Idea) {
        val newIdeaBox = IdeaBox(newIdea, this)
        newIdeaBox.init()
        ideasContainer.children.add(0, newIdeaBox)

        emptyIdeasIcon.isVisible = false
    }

    private fun removeFromUI(idea: Idea?) {
        ideasContainer.children.removeIf { (it as IdeaBox).idea === idea }

        if (ideasContainer.children.isEmpty()) {
            emptyIdeasIcon.isVisible = true
        }
    }

    fun updateIdeasIconState() {
        when {
            ideasIcon.isDisable -> {
                ideasIcon.image = SwingFXUtils.toFXImage(Consts.LIGHTBULB_DISABLED_32_IMAGE, WritableImage(ICON_SIZE, ICON_SIZE))
                ideasIcon.effect = DropShadow(10.0, Color.color(0.0, 0.0, 0.0, 0.5))
            }

            ideasProperty.get()?.any { idea -> !idea.read } ?: false -> {
                // there are unread ideas => turn on lightbulb
                ideasIcon.image = SwingFXUtils.toFXImage(Consts.LIGHTBULB_ON_32_IMAGE, WritableImage(ICON_SIZE, ICON_SIZE))
                val shadow = DropShadow(20.0, Color.color(1.0, 1.0, 0.0, 1.0))
                shadow.offsetY = -3.0
                ideasIcon.effect = shadow
            }

            else -> {
                // no unread ideas => turn off lightbulb
                ideasIcon.image = SwingFXUtils.toFXImage(Consts.LIGHTBULB_OFF_32_IMAGE, WritableImage(ICON_SIZE, ICON_SIZE))
                ideasIcon.effect = DropShadow(10.0, Color.color(0.0, 0.0, 0.0, 0.5))
            }
        }
    }

    companion object {
        const val ICON_SIZE = 32
    }
}