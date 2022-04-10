
package at.jku.anttracks.gui.classification.classifier.dialog.create;

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
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.util.function.Supplier;

import static at.jku.anttracks.util.ClassifierUtil.*;

class CreateClassifierDialogPane extends DialogPane {

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

    private Supplier<IndexBasedHeap> fastHeapSupplier;
    private Supplier<Symbols> symbolsSupplier;

    private ClassifierFactoryList availableClassifier;
    private ClassifierFactory newClassifier;

    public final ButtonType createButton = new ButtonType("Create", ButtonBar.ButtonData.OTHER);
    public final ButtonType loadButton = new ButtonType("Load", ButtonBar.ButtonData.OTHER);
    public final ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

    public CreateClassifierDialogPane() {
        FXMLUtil.load(this, CreateClassifierDialogPane.class);
    }

    public void init(Supplier<Symbols> symbolsSupplier, Supplier<IndexBasedHeap> heapSupplier, ClassifierFactoryList availableClassifier) throws IOException {
        this.symbolsSupplier = symbolsSupplier;
        this.fastHeapSupplier = heapSupplier;
        this.availableClassifier = availableClassifier;
        definitionEditor.setCode(getClassifierTemplate());
        cardinality.getItems()
                   .addAll(ClassifierType.ONE.getText(), ClassifierType.MANY.getText(), ClassifierType.HIERARCHY.getText(), ClassifierType.MANY_HIERARCHY.getText());
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

        if (availableClassifier.contains(name.getText())) {
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
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Unknown Error");
            alert.setContentText(e.getMessage());
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            WindowUtil.INSTANCE.centerInMainFrame(alert);
            alert.showAndWait();
            return false;
        }

        if (classifierFactory != null) {
            newClassifier = classifierFactory;
            try {
                storeClassifierFile(myDefinition, myName, myDescription, myExample, myCardinality, myCollectionType, Consts.CLASSIFIERS_DIRECTORY, null);
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

    @FXML
    public void load(ActionEvent event) {
        FileChooser open = new FileChooser();
        open.setInitialDirectory(new File(Consts.CLASSIFIERS_DIRECTORY));
        open.getInitialDirectory().mkdirs();
        File file = open.showOpenDialog(null);

        ClassifierFactory classifierFactory = null;
        try {
            classifierFactory = loadClassifier(file, fastHeapSupplier, symbolsSupplier);
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
        } catch (IOException ioe) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Parsing error");
            alert.setContentText(ioe.getMessage());
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            WindowUtil.INSTANCE.centerInMainFrame(alert);
            alert.showAndWait();
        } catch (Exception e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setHeaderText("Unknown Error");
            alert.setContentText(e.getMessage());
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            WindowUtil.INSTANCE.centerInMainFrame(alert);
            alert.showAndWait();
        }

        if (classifierFactory != null) {
            name.setText(classifierFactory.getName());
            description.setText(classifierFactory.getDesc());
            example.setText(classifierFactory.getExample());
            cardinality.getSelectionModel().select(classifierFactory.getType().getText());
            collectionType.getSelectionModel().select(classifierFactory.getSourceCollection().getText());
            definitionEditor.setCode(classifierFactory.getSourceCode());
        }
    }

    public ClassifierFactory getNewClassifier() {
        return newClassifier;
    }
}