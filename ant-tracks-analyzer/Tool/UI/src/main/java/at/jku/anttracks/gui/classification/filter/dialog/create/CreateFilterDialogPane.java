
package at.jku.anttracks.gui.classification.filter.dialog.create;

import at.jku.anttracks.classification.FilterFactory;
import at.jku.anttracks.classification.FilterFactoryList;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
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
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

public class CreateFilterDialogPane extends DialogPane {

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

    private FilterFactoryList availableFilter;
    private FilterFactory newFilter;

    public final ButtonType createButton = new ButtonType("Create", ButtonBar.ButtonData.OTHER);
    public final ButtonType loadButton = new ButtonType("Load", ButtonBar.ButtonData.OTHER);
    public final ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    private Supplier<Symbols> symbolsSupplier;
    private Supplier<IndexBasedHeap> fastHeapSupplier;

    public CreateFilterDialogPane() {
        FXMLUtil.load(this, CreateFilterDialogPane.class);
    }

    public void init(Supplier<Symbols> symbolsSupplier, Supplier<IndexBasedHeap> fastHeapSupplier, FilterFactoryList availableFilter) throws IOException {
        this.symbolsSupplier = symbolsSupplier;
        this.fastHeapSupplier = fastHeapSupplier;
        this.availableFilter = availableFilter;
        this.definitionEditor.setCode(FilterUtil.getFilterTemplate());

        collectionType.getItems()
                      .addAll(ClassifierSourceCollection.DETAILEDHEAP.getText(), ClassifierSourceCollection.FASTHEAP.getText(), ClassifierSourceCollection.ALL.getText());

        getButtonTypes().add(createButton);
        lookupButton(createButton).addEventFilter(ActionEvent.ACTION, actionEvent -> {
            if (!create(actionEvent)) {
                // Prevents that the dialog gets closed
                actionEvent.consume();
            }
        });
        getButtonTypes().add(loadButton);
        lookupButton(loadButton).addEventFilter(ActionEvent.ACTION, actionEvent -> {
            load(actionEvent);
            // Never close the dialog because the load button has been pressed
            actionEvent.consume();
        });
        getButtonTypes().add(cancelButton);
    }

    @FXML
    public boolean create(ActionEvent event) {
        if (name.getText().length() == 0) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setHeaderText("Invalid Input");
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.setContentText("Name must not be empty");
            alert.showAndWait();
            return false;
        }

        if (availableFilter.contains(name.getText())) {
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
                FilterUtil.storeFilterFile(myDefinition, myName, myDescription, myCollectionType, Consts.FILTERS_DIRECTORY, null);
            } catch (IOException ioe) {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setHeaderText("Save Error");
                alert.setContentText(ioe.getMessage());
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                WindowUtil.INSTANCE.centerInMainFrame(alert);
                alert.showAndWait();
            }
            return true;
        }

        return false;
    }

    @FXML
    public void load(ActionEvent event) {
        FileChooser open = new FileChooser();
        open.setInitialDirectory(new File(Consts.FILTERS_DIRECTORY));
        open.getInitialDirectory().mkdirs();
        File file = open.showOpenDialog(null);

        FilterFactory filterFactory = null;
        try {
            filterFactory = FilterUtil.loadFilter(file, fastHeapSupplier, symbolsSupplier);
        } catch (IOException ioe) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Parsing Error");
            alert.setContentText(ioe.getMessage());
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            WindowUtil.INSTANCE.centerInMainFrame(alert);
            alert.showAndWait();
        } catch (IllegalArgumentException iae) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Compile Error");
            alert.setContentText(iae.getMessage());
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            WindowUtil.INSTANCE.centerInMainFrame(alert);
            alert.showAndWait();
        } catch (InstantiationException ie) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Instantiation Error");
            alert.setContentText(ie.getMessage());
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            WindowUtil.INSTANCE.centerInMainFrame(alert);
            alert.showAndWait();
        } catch (Throwable t) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Unknown Error");
            alert.setContentText(t.getMessage());
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            WindowUtil.INSTANCE.centerInMainFrame(alert);
            alert.showAndWait();
        }

        if (filterFactory != null) {
            name.setText(filterFactory.getName());
            description.setText(filterFactory.getDesc());
            collectionType.getSelectionModel().select(filterFactory.getSourceCollection().getText());
            definitionEditor.setCode(filterFactory.getSourceCode());
        }
    }

    public FilterFactory getNewFilter() {
        return newFilter;
    }
}
