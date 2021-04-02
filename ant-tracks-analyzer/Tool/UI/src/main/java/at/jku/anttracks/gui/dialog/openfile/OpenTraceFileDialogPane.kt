
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

internal class OpenTraceFileDialogPane : DialogPane() {
    @FXML
    var applicationNameTextField: TextField? = null
    @FXML
    var traceTextField: TextField? = null
    @FXML
    var traceChooseButton: Button? = null
    @FXML
    var traceClearButton: Button? = null
    @FXML
    var featuresTextField: TextField? = null
    @FXML
    var featuresChooseButton: Button? = null
    @FXML
    var featuresClearButton: Button? = null
    private val traceFileChooser = FileChooser()
    private val featuresChooser = FileChooser()
    fun init() {
        traceFileChooser.initialDirectory = File(ClientInfo.traceDirectory)
        // traceFileChooser.getExtensionFilters().add(new ExtensionFilter("Loadable files only", ".zip", "*.*"));
        featuresChooser.initialDirectory = File(ClientInfo.featureDirectory)
        // featuresChooser.getExtensionFilters().add(new ExtensionFilter("Loadable files only (*.features)", "*.features"));
        checkAcceptButtonEnabling()
        traceChooseButton!!.onAction = EventHandler { ae: ActionEvent? ->
            traceFileChooser.initialDirectory = searchExistingDirectory(traceFileChooser.initialDirectory)
            val traceFile = traceFileChooser.showOpenDialog(scene.window)
            if (traceFile != null) {
                if (applicationNameTextField!!.text == null || applicationNameTextField!!.text.isEmpty()) {
                    applicationNameTextField!!.text = traceFile.name
                }
                traceTextField!!.text = traceFile.absolutePath
            }
        }
        featuresChooseButton!!.onAction = EventHandler { ae: ActionEvent? ->
            featuresChooser.initialDirectory = searchExistingDirectory(featuresChooser.initialDirectory)
            val featuresFile = featuresChooser.showOpenDialog(scene.window)
            if (featuresFile != null) {
                featuresTextField!!.text = featuresFile.absolutePath
            }
        }
        traceClearButton!!.graphic = ImageUtil.getIconNode(Consts.DELETE_ICON)
        traceClearButton!!.onAction = EventHandler { ae: ActionEvent? -> traceTextField!!.clear() }
        featuresClearButton!!.graphic = ImageUtil.getIconNode(Consts.DELETE_ICON)
        featuresClearButton!!.onAction = EventHandler { ae: ActionEvent? -> featuresTextField!!.clear() }
        traceTextField!!.textProperty()
                .addListener { observable: ObservableValue<out String?>?, oldValue: String?, newValue: String? -> checkAcceptButtonEnabling() }
        featuresTextField!!.textProperty()
                .addListener { observable: ObservableValue<out String?>?, oldValue: String?, newValue: String? -> checkAcceptButtonEnabling() }
        checkAcceptButtonEnabling()
    }

    fun init(appInfo: AppInfo) {
        init()
        applicationNameTextField!!.text = appInfo.appName
        traceTextField!!.text = if (appInfo.selectedTraceFile == null) "" else appInfo.selectedTraceFile.absolutePath
        featuresTextField!!.text = if (appInfo.selectedFeaturesFile == null) "" else appInfo.selectedFeaturesFile.absolutePath
        if (appInfo.selectedTraceFile != null) {
            traceFileChooser.initialDirectory = File(appInfo.selectedTraceFile.parent)
        }
        if (appInfo.selectedFeaturesFile != null) {
            featuresChooser.initialDirectory = File(appInfo.selectedFeaturesFile.parent)
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

    val traceFile: File?
        get() = if (traceTextField!!.text == null || traceTextField!!.text.isEmpty()) null else File(traceTextField!!.text)

    val featuresFile: File?
        get() = if (featuresTextField!!.text == null || featuresTextField!!.text.isEmpty()) null else File(featuresTextField!!.text)

    private fun checkAcceptButtonEnabling() {
        val basicAppInfos = traceFile != null && applicationName != null
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
        FXMLUtil.load(this, OpenTraceFileDialogPane::class.java)
    }
}