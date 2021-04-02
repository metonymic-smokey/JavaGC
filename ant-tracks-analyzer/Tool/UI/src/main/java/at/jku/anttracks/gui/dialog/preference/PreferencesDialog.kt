
package at.jku.anttracks.gui.dialog.preference

import javafx.scene.control.Dialog

class PreferencesDialog : Dialog<Void>() {
    init {
        title = "Preferences"
        isResizable = true
        dialogPane = PreferencesDialogPane().also { it.init() }
        setResultConverter { null }
    }

}
