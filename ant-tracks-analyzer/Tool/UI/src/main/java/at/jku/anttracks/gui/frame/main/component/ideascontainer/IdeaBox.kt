package at.jku.anttracks.gui.frame.main.component.ideascontainer

import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.model.Idea
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.embed.swing.SwingFXUtils
import javafx.fxml.FXML
import javafx.scene.control.Label
import javafx.scene.image.ImageView
import javafx.scene.image.WritableImage
import javafx.scene.layout.HBox
import javafx.scene.layout.VBox
import javafx.scene.text.Text
import javafx.scene.text.TextFlow

class IdeaBox(val idea: Idea,
              private val ideasPopup: IdeasPopup) : VBox() {

    @FXML
    lateinit var title: Label

    @FXML
    lateinit var description: TextFlow

    @FXML
    lateinit var eyeIcon: ImageView

    @FXML
    lateinit var actionButtonsContainer: HBox

    init {
        FXMLUtil.load(this, IdeaBox::class.java)
    }

    fun init() {
        title.text = idea.title
        buildDescription()
        eyeIcon.image = SwingFXUtils.toFXImage(Consts.EYE_IMAGE, WritableImage(24, 16))
        eyeIcon.isVisible = idea.isHoverable
        eyeIcon.isManaged = idea.isHoverable
        actionButtonsContainer.isVisible = false

        // init 'read' status
        if (idea.read) {
            markRead()
        } else {
            markUnread()
        }

        // hovering over an idea box marks it as read, also shows available actions
        hoverProperty().addListener { obs, wasHovered, isHovered ->
            if (isHovered) {
                markRead()
                actionButtonsContainer.isVisible = true
            } else {
                actionButtonsContainer.isVisible = false
            }
        }

        // eye icon hover behaviour
        eyeIcon.hoverProperty().addListener { obs, wasHovered, isHovered ->
            if (isHovered) {
                // make the whole popup semitransparent
                ideasPopup.opacity = 0.1

                ClientInfo.mainFrame.selectTab(idea.tab)

                // highlight the relevant node and perform additional custom hover actions
                idea.highlightNode()
                idea.actionOnHover?.action?.invoke()

            } else {
                idea.actionOnHover?.undoAction?.invoke()
                idea.unhighlightNode()

                ideasPopup.opacity = 1.0
            }
        }

        // add a button for each action defined in the idea
        idea.actionsOnClick?.forEach { actionOnClick ->
            val actionPane = Label(actionOnClick.label.toUpperCase())

            actionPane.setOnMouseClicked {
                // remember that this action was already triggered
                actionOnClick.triggered = true
                actionPane.styleClass.add("ideaBoxActionPaneAlreadyTriggered")
                ideasPopup.hide()
                actionOnClick.action()
            }

            actionPane.styleClass.add("ideaBoxActionPane")
            if (actionOnClick.triggered) {
                actionPane.styleClass.add("ideaBoxActionPaneAlreadyTriggered")
            }

            actionButtonsContainer.children.add(actionPane)
        }
    }

    private fun buildDescription() {
        idea.description.styledDescriptionParts.forEach { formattedDescriptionPart ->
            val text = Text(formattedDescriptionPart.second)
            text.styleClass.add(formattedDescriptionPart.first.styleClass)
            description.children.add(text)
        }
    }

    private fun markUnread() {
        idea.read = false
        this.styleClass.add("ideaBoxUnread")
    }

    private fun markRead() {
        idea.read = true
        this.styleClass.remove("ideaBoxUnread")
    }

}