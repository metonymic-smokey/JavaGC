
package at.jku.anttracks.gui.classification.filter.dialog.edit;

import at.jku.anttracks.classification.FilterFactory;
import at.jku.anttracks.classification.FilterFactoryList;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.gui.classification.filter.dialog.create.CodeEditor;
import at.jku.anttracks.gui.utils.Consts;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.WindowUtil;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.symbols.Symbols;
import at.jku.anttracks.util.FilterUtil;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.Region;

import java.io.IOException;
import java.util.function.Supplier;

public class EditFilterDialogPane extends DialogPane {
    public final ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.APPLY);
    public final ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    public final ButtonType deleteButton = new ButtonType("Delete", ButtonBar.ButtonData.OTHER);

    @FXML
    TextField name;
    @FXML
    TextField description;
    @FXML
    ComboBox<String> collectionType;
    @FXML
    TitledPane definitionContainer;
    @FXML
    CodeEditor definitionEditor;

    private FilterFactoryList availableFilters;
    private FilterFactory editedFilter;
    private Supplier<IndexBasedHeap> fastHeapSupplier;
    private Supplier<Symbols> symbolsSupplier;
    private FilterFactory newFilter;

    public EditFilterDialogPane() {
        FXMLUtil.load(this, EditFilterDialogPane.class);
    }

    public void init(FilterFactory editedFilter, FilterFactoryList availableFilters, Supplier<IndexBasedHeap> fastHeapSupplier, Supplier<Symbols> symbolsSupplier)
            throws IOException {
        this.availableFilters = availableFilters;
        this.editedFilter = editedFilter;
        this.fastHeapSupplier = fastHeapSupplier;
        this.symbolsSupplier = symbolsSupplier;

        this.name.setText(editedFilter.getName());
        this.description.setText(editedFilter.getDesc());
        this.definitionEditor.setCode(editedFilter.getSourceCode());

        collectionType.getItems()
                      .addAll(ClassifierSourceCollection.DETAILEDHEAP.getText(), ClassifierSourceCollection.FASTHEAP.getText(), ClassifierSourceCollection.ALL.getText());

        collectionType.setValue(editedFilter.getSourceCollection().getText());

        getButtonTypes().add(saveButton);
        lookupButton(saveButton).addEventFilter(ActionEvent.ACTION, actionEvent -> {
            if (!save(actionEvent)) {
                // Prevents that the dialog gets closed
                actionEvent.consume();
            }
        });
        getButtonTypes().add(cancelButton);
        getButtonTypes().add(deleteButton);
        lookupButton(deleteButton).addEventHandler(ActionEvent.ACTION, event -> {
            try {
                FilterUtil.deleteFilterFile(editedFilter.getName(), Consts.FILTERS_DIRECTORY);
            } catch (IOException ioe) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setHeaderText("Delete Error");
                alert.setContentText(ioe.getMessage());
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                WindowUtil.INSTANCE.centerInMainFrame(alert);
                alert.showAndWait();
            }
        });
        // deleting the filter from the available/selected list is handled in dialog caller

    }

    public boolean save(ActionEvent event) {
        if (name.getText().length() == 0) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setHeaderText("Invalid Input");
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.setContentText("Name must not be empty");
            alert.showAndWait();
            return false;
        }

        if (availableFilters.contains(name.getText()) && !name.getText().equals(editedFilter.getName())) {
            // renamed filter to an already existing name
            Alert alert = new Alert(AlertType.ERROR);
            alert.setHeaderText("Invalid Input");
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.setContentText("A filter with this name already exists");
            alert.showAndWait();
            return false;
        }

        if (collectionType.getSelectionModel().getSelectedItem() == null) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setHeaderText("Invalid Input");
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.setContentText("Collection type must not be empty");
            alert.showAndWait();
            return false;
        }

        String myDefinition = definitionEditor.getCodeAndSnapshot();
        String myName = name.getText();
        String myDescription = description.getText();
        String myCollectionType = collectionType.getSelectionModel().getSelectedItem();

        FilterFactory filterFactory = null;
        try {
            filterFactory = FilterUtil.compile(myDefinition, myName, myDescription, myCollectionType, fastHeapSupplier, symbolsSupplier);
        } catch (IllegalArgumentException iae) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Compile Error");
            alert.setContentText(iae.getMessage());
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            WindowUtil.INSTANCE.centerInMainFrame(alert);
            alert.showAndWait();
            return false;
        } catch (InstantiationException ie) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Instantiation Error");
            alert.setContentText(ie.getMessage());
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            WindowUtil.INSTANCE.centerInMainFrame(alert);
            alert.showAndWait();
            return false;
        } catch (Throwable t) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Unknown Error");
            alert.setContentText(t.getMessage());
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            WindowUtil.INSTANCE.centerInMainFrame(alert);
            alert.showAndWait();
            return false;
        }

        if (filterFactory != null) {
            newFilter = filterFactory;
            try {
                FilterUtil.storeFilterFile(myDefinition, myName, myDescription, myCollectionType, Consts.FILTERS_DIRECTORY, editedFilter.getName());
            } catch (IOException ioe) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setHeaderText("Save Error");
                alert.setContentText(ioe.getMessage());
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                WindowUtil.INSTANCE.centerInMainFrame(alert);
                alert.showAndWait();
                return false;
            }
            return true;
        }

        return false;
    }

    public FilterFactory getFilter() {
        return newFilter;
    }
}
