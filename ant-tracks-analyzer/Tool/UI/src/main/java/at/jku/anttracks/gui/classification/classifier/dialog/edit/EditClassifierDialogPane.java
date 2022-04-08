
package at.jku.anttracks.gui.classification.classifier.dialog.edit;

import at.jku.anttracks.classification.ClassifierFactory;
import at.jku.anttracks.classification.ClassifierFactoryList;
import at.jku.anttracks.classification.enumerations.ClassifierSourceCollection;
import at.jku.anttracks.classification.enumerations.ClassifierType;
import at.jku.anttracks.gui.classification.filter.dialog.create.CodeEditor;
import at.jku.anttracks.gui.utils.Consts;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.WindowUtil;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.symbols.Symbols;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.Region;

import java.io.IOException;
import java.util.function.Supplier;

import static at.jku.anttracks.util.ClassifierUtil.*;

public class EditClassifierDialogPane extends DialogPane {
    public final ButtonType saveButton = new ButtonType("Save", ButtonBar.ButtonData.APPLY);
    public final ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
    public final ButtonType deleteButton = new ButtonType("Delete", ButtonBar.ButtonData.OTHER);

    @FXML
    TextField name;
    @FXML
    TextField description;
    @FXML
    TextField example;
    @FXML
    ComboBox<String> cardinality;
    @FXML
    ComboBox<String> collectionType;
    @FXML
    TitledPane definitionContainer;
    @FXML
    CodeEditor definitionEditor;

    private ClassifierFactoryList availableClassifiers;
    private ClassifierFactory editedClassifier;
    private Supplier<IndexBasedHeap> fastHeapSupplier;
    private Supplier<Symbols> symbolsSupplier;
    private ClassifierFactory newClassifier;

    public EditClassifierDialogPane() {
        FXMLUtil.load(this, EditClassifierDialogPane.class);
    }

    public void init(ClassifierFactory editedClassifier,
                     ClassifierFactoryList availableClassifiers,
                     Supplier<IndexBasedHeap> fastHeapSupplier,
                     Supplier<Symbols> symbolsSupplier) throws IOException {
        this.availableClassifiers = availableClassifiers;
        this.editedClassifier = editedClassifier;
        this.fastHeapSupplier = fastHeapSupplier;
        this.symbolsSupplier = symbolsSupplier;

        definitionEditor.setCode(getClassifierTemplate());

        cardinality.getItems()
                   .addAll(ClassifierType.ONE.getText(), ClassifierType.MANY.getText(), ClassifierType.HIERARCHY.getText(), ClassifierType.MANY_HIERARCHY.getText());

        collectionType.getItems()
                      .addAll(ClassifierSourceCollection.DETAILEDHEAP.getText(), ClassifierSourceCollection.FASTHEAP.getText(), ClassifierSourceCollection.ALL.getText());

        this.name.setText(editedClassifier.getName());
        description.setText(editedClassifier.getDesc());
        this.example.setText(editedClassifier.getExample());
        cardinality.setValue(editedClassifier.getType().getText());
        collectionType.setValue(editedClassifier.getSourceCollection().getText());
        definitionEditor.setCode(editedClassifier.getSourceCode());

        getButtonTypes().add(saveButton);
        lookupButton(saveButton).addEventFilter(ActionEvent.ACTION, actionEvent -> {
            if (!save(actionEvent)) {
                // Prevents that the dialog gets closed
                actionEvent.consume();
            }
        });
        getButtonTypes().add(cancelButton);
        getButtonTypes().add(deleteButton);
        lookupButton(deleteButton).addEventHandler(ActionEvent.ACTION,
                                                   event -> deleteClassifierFile(editedClassifier.getName(), Consts.CLASSIFIERS_DIRECTORY));
        // deleting the classifier from the available/selected list is handled in dialog caller
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

        if (availableClassifiers.get(name.getText()) != null && !name.getText().equals(editedClassifier.getName())) {
            // renamed the classifier to a name that already exists
            Alert alert = new Alert(AlertType.ERROR);
            alert.setHeaderText("Invalid Input");
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.setContentText("A classifier with this name already exists");
            alert.showAndWait();
            return false;
        }

        if (cardinality.getSelectionModel().getSelectedItem() == null) {
            Alert alert = new Alert(AlertType.ERROR);
            alert.setHeaderText("Invalid Input");
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.setContentText("Cardinality must not be empty");
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
        String myExample = example.getText();
        String myCardinality = cardinality.getSelectionModel().getSelectedItem();
        String myCollectionType = collectionType.getSelectionModel().getSelectedItem();

        ClassifierFactory classifierFactory = null;
        try {
            classifierFactory = compile(myDefinition, myName, myDescription, myExample, myCardinality, myCollectionType, fastHeapSupplier, symbolsSupplier);
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

        if (classifierFactory != null) {
            newClassifier = classifierFactory;
            try {
                storeClassifierFile(myDefinition,
                                    myName,
                                    myDescription,
                                    myExample,
                                    myCardinality,
                                    myCollectionType,
                                    Consts.CLASSIFIERS_DIRECTORY,
                                    editedClassifier.getName());
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

    public ClassifierFactory getClassifier() {
        return newClassifier;
    }
}