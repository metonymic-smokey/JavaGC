
package at.jku.anttracks.gui.dialog.preference

import at.jku.anttracks.gui.utils.FXMLUtil
import javafx.beans.property.SimpleStringProperty
import javafx.collections.FXCollections
import javafx.fxml.FXML
import javafx.scene.control.DialogPane
import javafx.scene.control.TableColumn
import javafx.scene.control.TableView
import javafx.scene.control.cell.TextFieldTableCell

class PreferencesDialogPane : DialogPane() {

    @FXML
    private lateinit var tableView: TableView<SettingsTableItem>
    @FXML
    private lateinit var nameTableColumn: TableColumn<SettingsTableItem, String>
    @FXML
    private lateinit var valueTableColumn: TableColumn<SettingsTableItem, String>

    private val settings = Settings.getInstance()
    private val data =
            FXCollections.observableArrayList(settings.all
                                                      .filter { p -> p.configurable }
                                                      .map { settingsProperty -> SettingsTableItem(settings, settingsProperty) })

    init {
        FXMLUtil.load(this, PreferencesDialogPane::class.java)
    }

    fun init() {
        nameTableColumn.cellFactory = TextFieldTableCell.forTableColumn()
        nameTableColumn.setCellValueFactory { cell -> cell.value.descriptionProperty }
        valueTableColumn.cellFactory = TextFieldTableCell.forTableColumn()
        valueTableColumn.setCellValueFactory { cell -> cell.value.valueProperty }
        tableView.items = data
    }

    private inner class SettingsTableItem(settings: Settings, settingsProperty: Settings.Property<*>) {
        val descriptionProperty: SimpleStringProperty = SimpleStringProperty(settingsProperty.description)
        val valueProperty: SimpleStringProperty = SimpleStringProperty(settingsProperty.get().toString())

        init {
            valueProperty.addListener { _, _, newValue ->
                settings.set(settingsProperty.name, newValue)
                valueProperty.set(settingsProperty.get().toString())
            }
        }
    }
}