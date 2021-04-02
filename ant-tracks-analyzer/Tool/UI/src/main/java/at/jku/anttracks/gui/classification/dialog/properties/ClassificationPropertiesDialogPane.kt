
package at.jku.anttracks.gui.classification.dialog.properties

import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.fxml.FXML
import javafx.scene.control.DialogPane
import javafx.scene.layout.VBox

class ClassificationPropertiesDialogPane internal constructor() : DialogPane() {

    @FXML
    lateinit var mainPane: VBox

    init {
        FXMLUtil.load(this, ClassificationPropertiesDialogPane::class.java)
    }

    fun init() {

    }
}
