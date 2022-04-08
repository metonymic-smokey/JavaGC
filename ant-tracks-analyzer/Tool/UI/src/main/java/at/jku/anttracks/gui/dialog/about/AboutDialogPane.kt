
package at.jku.anttracks.gui.dialog.about

import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.embed.swing.SwingFXUtils
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.DialogPane
import javafx.scene.control.Hyperlink
import javafx.scene.image.ImageView
import javafx.scene.text.TextFlow

internal class AboutDialogPane : DialogPane() {

    @FXML
    private lateinit var imageView: ImageView
    @FXML
    private lateinit var textFlow: TextFlow

    @FXML
    private lateinit var downloadLink: Hyperlink

    init {
        FXMLUtil.load(this, AboutDialogPane::class.java)
    }

    fun init() {
        // TODO fix image, it is not showing...
        imageView.image = SwingFXUtils.toFXImage(Consts.ANT_IMAGE, null)
        prefWidth = imageView.image.width + 175

        downloadLink.onAction = EventHandler { ClientInfo.application.hostServices.showDocument(downloadLink.text) }
    }

}
