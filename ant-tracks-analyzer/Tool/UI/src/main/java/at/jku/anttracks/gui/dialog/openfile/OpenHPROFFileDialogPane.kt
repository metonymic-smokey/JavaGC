
package at.jku.anttracks.gui.dialog.openfile

import at.jku.anttracks.gui.model.AppInfo
import at.jku.anttracks.gui.model.ClientInfo
import at.jku.anttracks.gui.utils.Consts
import at.jku.anttracks.gui.utils.FXMLUtil
import at.jku.anttracks.gui.utils.ImageUtil
import javafx.beans.value.ObservableValue
import javafx.event.ActionEvent
import javafx.event.EventHandler
import javafx.fxml.FXML
import javafx.scene.control.Button
import javafx.scene.control.ButtonType
import javafx.scene.control.DialogPane
import javafx.scene.control.TextField
import javafx.stage.FileChooser
import java.io.File

internal class OpenHPROFFileDialogPane : DialogPane() {
    @FXML
    var applicationNameTextField: TextField? = null
    @FXML
    var hprofTextField: TextField? = null
    @FXML
    var hprofChooseButton: Button? = null
    @FXML
    var hprofClearButton: Button? = null
    private val traceFileChooser = FileChooser()
    fun init() {
        traceFileChooser.initialDirectory = File(ClientInfo.hprofDirectory)
        // traceFileChooser.getExtensionFilters().add(new ExtensionFilter("Loadable files only", ".zip", "*.*"));
        checkAcceptButtonEnabling()
        hprofChooseButton!!.onAction = EventHandler { ae: ActionEvent? ->
            traceFileChooser.initialDirectory = searchExistingDirectory(traceFileChooser.initialDirectory)
            val traceFile = traceFileChooser.showOpenDialog(scene.window)
            if (traceFile != null) {
                if (applicationNameTextField!!.text == null || applicationNameTextField!!.text.isEmpty()) {
                    applicationNameTextField!!.text = traceFile.name
                }
                hprofTextField!!.text = traceFile.absolutePath
            }
        }
        hprofClearButton!!.graphic = ImageUtil.getIconNode(Consts.DELETE_ICON)
        hprofClearButton!!.onAction = EventHandler { ae: ActionEvent? -> hprofTextField!!.clear() }
        hprofTextField!!.textProperty()
                .addListener { observable: ObservableValue<out String?>?, oldValue: String?, newValue: String? -> checkAcceptButtonEnabling() }
        checkAcceptButtonEnabling()
    }

    fun init(appInfo: AppInfo) {
        init()
        applicationNameTextField!!.text = appInfo.appName
        hprofTextField!!.text = if (appInfo.selectedTraceFile == null) "" else appInfo.selectedTraceFile.absolutePath
        if (appInfo.selectedTraceFile != null) {
            traceFileChooser.initialDirectory = File(appInfo.selectedTraceFile.parent)
        }
        checkAcceptButtonEnabling()
    }

    private fun searchExistingDirectory(directory: File?): File? {
        var dir: File? = directory
        if (directory != null && !directory.isDirectory) {
            dir = directory.parentFile
        } else {
            dir = directory
        }
        if (dir != null) {
            return dir
        }
        dir = File(System.getProperty("user.home"))
        return if (dir.isDirectory) {
            dir
        } else null
    }

    val applicationName: String
        get() = applicationNameTextField!!.text

    val hprofFile: File?
        get() = if (hprofTextField!!.text == null || hprofTextField!!.text.isEmpty()) null else File(hprofTextField!!.text)

    private fun checkAcceptButtonEnabling() {
        val basicAppInfos = hprofFile != null
        applyButton.isDisable = !basicAppInfos
    }

    val applyButton: Button
        get() {
            val node = lookupButton(ButtonType.APPLY)
            return if (node is Button) {
                node
            } else {
                throw IllegalStateException("Apply Button not found - DialogPane may not be initialized correctly")
            }
        }

    init {
        FXMLUtil.load(this, OpenHPROFFileDialogPane::class.java)
    }
}