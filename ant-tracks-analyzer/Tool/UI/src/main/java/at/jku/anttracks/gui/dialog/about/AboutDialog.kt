
package at.jku.anttracks.gui.dialog.about

import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog

class AboutDialog : Dialog<Void>() {

    private lateinit var dialogPane: AboutDialogPane

    fun init() {
        AboutDialogPane().also {
            it.init()
            // DialogPane must have a close button that closing is handled properly
            it.buttonTypes.add(ButtonType.CLOSE)
            setDialogPane(it)
            dialogPane = it
        }
        dialogPane.lookupButton(ButtonType.CLOSE).also {
            it.managedProperty().bind(it.visibleProperty())
            it.isVisible = false
        }
    }
}
