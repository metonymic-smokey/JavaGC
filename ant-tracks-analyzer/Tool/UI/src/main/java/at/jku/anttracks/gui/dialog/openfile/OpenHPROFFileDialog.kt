
package at.jku.anttracks.gui.dialog.openfile

import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.ClientInfo
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog

import java.io.File

class OpenHPROFFileDialog : Dialog<Boolean>() {
    private lateinit var dialogPane: OpenHPROFFileDialogPane

    val applicationName: String
        get() = dialogPane.applicationName

    val hprofFile: File?
        get() = dialogPane.hprofFile

    fun init(appInfo: AppInfo? = null) {
        OpenHPROFFileDialogPane().also { openFileDialogPane ->
            if (appInfo != null) {
                openFileDialogPane.init(appInfo)
            } else {
                openFileDialogPane.init()
            }
            openFileDialogPane.applyButton.setOnAction {
                if (hprofFile != null) {
                    ClientInfo.hprofDirectory = hprofFile!!.absolutePath.substring(0, hprofFile!!.absolutePath.lastIndexOf(File.separator))
                }
            }
            setDialogPane(openFileDialogPane)
            dialogPane = openFileDialogPane
        }
        setResultConverter { it == ButtonType.APPLY }
    }
}
