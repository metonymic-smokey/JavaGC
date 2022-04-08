
package at.jku.anttracks.gui.component.clickablepane

import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.application.Platform
import javafx.beans.property.*
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.Label
import javafx.scene.control.Tooltip
import javafx.scene.image.ImageView
import javafx.scene.input.MouseButton
import javafx.scene.layout.HBox
import javafx.scene.text.Text
import javafx.scene.text.TextFlow
import java.util.*
import java.util.concurrent.CompletableFuture

class ClickablePane : HBox() {
    companion object {
        private const val SINGLE_CLICK_DELAY = 250
        private const val DESCRIPTION_ON_SINGLE_CLICK = false
    }

    @FXML
    private lateinit var container: HBox
    @FXML
    private lateinit var titleLabel: Label
    @FXML
    private lateinit var descriptionTextFlow: TextFlow
    @FXML
    private lateinit var button: Button

    private val clickableProperty = SimpleBooleanProperty()
    private val titleProperty: StringProperty = SimpleStringProperty()
    private val iconProperty = SimpleObjectProperty<ImageView>()
    private val descriptionsProperty = SimpleObjectProperty<List<String>>()
    private val buttonTextProperty: StringProperty = SimpleStringProperty()
    private val tooltipProperty: ObjectProperty<Tooltip> = SimpleObjectProperty()
    private val onRightClickProperty: ObjectProperty<Runnable> = SimpleObjectProperty()
    private val onLeftClickProperty = SimpleObjectProperty<Runnable>()
    private val onDoubleLeftClickProperty: ObjectProperty<Runnable> = SimpleObjectProperty()
    private var latestClickRunner: ClickRunner? = null

    var clickable: Boolean
        get() = clickableProperty.get()
        set(clickable) = clickableProperty.set(clickable)

    init {
        FXMLUtil.load(this, ClickablePane::class.java)
    }

    fun init(icon: at.jku.anttracks.util.ImagePack?,
             title: String,
             descriptions: Array<String>,
             buttonText: String,
             tooltip: String,
             onDoubleLeftClick: Runnable,
             onRightClick: Runnable,
             clickable: Boolean) {
        iconProperty.set(icon?.asNewNode)
        iconProperty.addListener { observable, oldValue, newValue -> titleLabel.graphic = newValue }

        titleProperty.set(title)
        titleProperty.addListener { observable, oldValue, newValue -> titleLabel.text = newValue }

        descriptionsProperty.addListener { observable, oldValue, newValue ->
            descriptionTextFlow.children.setAll(createTextFlowElements(newValue))
            /*
            descriptionTextFlow.getChildren().forEach(child -> {
                child.setVisible(descriptionTextFlow.isVisible());
                child.setManaged(descriptionTextFlow.isManaged());
            });*/
        }

        val desc = Arrays.copyOf(descriptions, descriptions.size + if (descriptions.isEmpty()) 1 else 2)
        for (i in desc.size - 1 downTo 2) {
            desc[i] = desc[i - 2]
        }
        desc[0] = tooltip
        if (desc.size > 1) {
            desc[1] = ""
        }

        descriptionsProperty.set(Arrays.asList(*desc))

        buttonTextProperty.set(buttonText)
        buttonTextProperty.addListener { observable, oldValue, newValue -> button.text = newValue }

        tooltipProperty.set(Tooltip(tooltip))
        tooltipProperty.addListener { observable, oldValue, newValue -> Tooltip.install(container, newValue) }

        if (DESCRIPTION_ON_SINGLE_CLICK) {
            onLeftClickProperty.set(Runnable {
                val newVisible = descriptionTextFlow.children.size != 0 && !descriptionTextFlow.isVisible
                descriptionTextFlow.isVisible = newVisible
                descriptionTextFlow.isManaged = newVisible
            })
        } else {
            onLeftClickProperty.set(null)
        }

        onDoubleLeftClickProperty.set(onDoubleLeftClick)
        onRightClickProperty.set(onRightClick)
        clickableProperty.set(clickable)

        initializeContainer()
        initializeTitleLabel()
        initializeButton()
    }

    private inner class ClickRunner(private val onSingleClick: Runnable) : Runnable {
        private var aborted = false

        fun abort() {
            this.aborted = true
        }

        override fun run() {
            try {
                Thread.sleep(SINGLE_CLICK_DELAY.toLong())
            } catch (e: InterruptedException) {
                e.printStackTrace()
            }

            if (!aborted) {
                // System.out.println("Execute Single Click");
                Platform.runLater { onSingleClick.run() }
            }
        }
    }

    private fun initializeContainer() {
        Tooltip.install(container, tooltipProperty.get())
        container.setOnMouseClicked { me ->
            when (me.button) {
                MouseButton.PRIMARY -> {
                    if (me.clickCount == 1 && onLeftClickProperty.get() != null) {
                        // System.out.println("Single Click");
                        latestClickRunner = ClickRunner(onLeftClickProperty.get())
                        CompletableFuture.runAsync(latestClickRunner!!)
                    }
                    if (me.clickCount == 2 && onDoubleLeftClickProperty.get() != null) {
                        // System.out.println("Double Click");
                        if (latestClickRunner != null) {
                            // System.out.println("-> Abort Single Click");
                            latestClickRunner!!.abort()
                        }
                        onDoubleLeftClickProperty.get().run()
                    }
                }
                MouseButton.SECONDARY -> if (onRightClickProperty.get() != null) {
                    onRightClickProperty.get().run()
                }
                else -> {
                }
            }
        }
    }

    private fun initializeTitleLabel() {
        titleLabel.graphic = iconProperty.get()
        titleLabel.text = titleProperty.get()
    }

    private fun initializeButton() {
        button.text = buttonTextProperty.get()
        button.setOnAction {
            if (onDoubleLeftClickProperty.get() != null) {
                onDoubleLeftClickProperty.get().run()
            }
        }
        button.visibleProperty().bind(clickableProperty)
        button.managedProperty().bind(clickableProperty)
    }

    private fun createTextFlowElements(descriptions: List<String>): List<Text> {
        return descriptions
                .map { description -> Text(description + if (descriptions.indexOf(description) != descriptions.size - 1) "\n" else "") }
    }
}
