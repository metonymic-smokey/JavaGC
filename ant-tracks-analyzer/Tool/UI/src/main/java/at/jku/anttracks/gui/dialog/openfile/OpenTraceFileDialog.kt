
package at.jku.anttracks.gui.dialog.openfile

import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.ClientInfo
import javafx.scene.control.ButtonType
import javafx.scene.control.Dialog

import java.io.File

class OpenTraceFileDialog : Dialog<Boolean>() {

    private lateinit var dialogPane: OpenTraceFileDialogPane

    val applicationName: String
        get() = dialogPane.applicationName

    val traceFile: File?
        get() = dialogPane.traceFile

    val featuresFile: File?
        get() = dialogPane.featuresFile

    fun init(appInfo: AppInfo? = null) {
        OpenTraceFileDialogPane().also { openFileDialogPane ->
            if (appInfo != null) {
                openFileDialogPane.init(appInfo)
            } else {
                openFileDialogPane.init()
            }
            openFileDialogPane.applyButton.setOnAction {
                if (traceFile != null) {
                    ClientInfo.traceDirectory = traceFile!!.absolutePath.substring(0, traceFile!!.absolutePath.lastIndexOf(File.separator))
                }
                if (featuresFile != null) {
                    ClientInfo.featureDirectory = featuresFile!!.absolutePath.substring(0, featuresFile!!.absolutePath.lastIndexOf(File.separator))
                }
            }
            setDialogPane(openFileDialogPane)
            dialogPane = openFileDialogPane
        }
        setResultConverter { it == ButtonType.APPLY }
    }
}
