
package at.jku.anttracks.gui.classification.classifier.component.selectionpane;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierFactory;
import at.jku.anttracks.gui.classification.classifier.dialog.create.CreateClassifierDialog;
import at.jku.anttracks.gui.classification.classifier.dialog.edit.EditClassifierDialog;
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane;
import at.jku.anttracks.gui.classification.dialog.properties.ClassificationPropertiesDialog;
import at.jku.anttracks.gui.component.clickablepane.ClickablePane;
import at.jku.anttracks.gui.model.IAvailableClassifierInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.util.ImagePack;
import javafx.fxml.FXML;
import javafx.scene.control.Dialog;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ObjectClassifierSelectionPane extends ClassificationSelectionPane<Classifier<?>, ClassifierFactory> {

    @FXML
    private FlowPane availableSingleCardinalitySubPane;

    @FXML
    private FlowPane availableMultiCardinalitySubPane;

    @FXML
    private FlowPane availableTreeCardinalitySubPane;

    @FXML
    private FlowPane availableMultiTreeCardinalitySubPane;

    private List<Pane> availableSingleCardinalityPaneList;
    private List<Pane> availableMultiCardinalityPaneList;
    private List<Pane> availableTreeCardinalityPaneList;
    private List<Pane> availableMultiTreeCardinalityPaneList;

    public ObjectClassifierSelectionPane() {
        super();
        FXMLUtil.load(this, ObjectClassifierSelectionPane.class);
    }

    @Override
    public void init(IAvailableClassifierInfo availableClassifierInfo, int maximumSelect, boolean prohibitDuplicates) {
        availableSingleCardinalityPaneList = new ArrayList<>();
        availableMultiCardinalityPaneList = new ArrayList<>();
        availableTreeCardinalityPaneList = new ArrayList<>();
        availableMultiTreeCardinalityPaneList = new ArrayList<>();
        super.init(availableClassifierInfo, maximumSelect, prohibitDuplicates);
        refresh();
    }

    // SELECTED

    @Override
    protected ImagePack getIcon(Classifier<?> classifier) {
        return classifier.getIcon(null);
    }

    @Override
    protected String getName(Classifier<?> classifier) {
        return classifier.getName();
    }

    @Override
    protected String[] getSelectedDescriptions(Classifier<?> classifier) {
        return Stream.of(classifier.configurableProperties())
                     .filter(prop -> prop.getPresentationName() != null && prop.get() != null)
                     .map(prop -> prop.getPresentationName() + ": " + prop.get())
                     .toArray(String[]::new);
    }

    @Override
    protected String getSelectedToolTip(Classifier<?> classifier) {
        return (classifier.getDesc() + "\n" + (classifier.configurableProperties().length > 0 ? "Right click to configure" : ""));
    }

    @Override
    protected boolean removeSelected(ClassifierFactory y) {
        List<Classifier<?>> remainingSelected = getSelected().stream().filter(selected -> !selected.getName().equals(y.getName())).collect(Collectors.toList());
        if (getSelected().size() != remainingSelected.size()) {
            resetSelected(remainingSelected);
            return true;
        }
        return false;
    }

    @Override
    public boolean selectedRightClick(Classifier<?> classifier) {
        if (ClassificationPropertiesDialog.showDialogForClassifier(classifier, availableClassifierInfo)) {
            refreshSelected();
            return true;
        }

        return false;
    }

    // AVAILABLE

    @Override
    protected ImagePack getAvailableIcon(ClassifierFactory fac) {
        return fac.getIcon();
    }

    @Override
    protected String getAvailableName(ClassifierFactory fac) {
        return fac.getName();
    }

    @Override
    protected String[] getAvailableDescriptions(ClassifierFactory fac) {
        return new String[]{"e.g. \"" + fac.getExample() + "\""};
    }

    @Override
    protected String getAvailableToolTip(ClassifierFactory fac) {
        return fac.getDesc() + (fac instanceof ClassifierFactory && fac.isOnTheFlyCompilable() ? "\nRight click to edit" : "");
    }

    @Override
    protected void addAvailablePane(ClassifierFactory fac, ClickablePane pane) {
        switch (fac.getType()) {
            case ONE:
                availableSingleCardinalityPaneList.add(pane);
                availableSingleCardinalitySubPane.getChildren().add(pane);
                break;
            case MANY:
                availableMultiCardinalityPaneList.add(pane);
                availableMultiCardinalitySubPane.getChildren().add(pane);
                break;
            case HIERARCHY:
                availableTreeCardinalityPaneList.add(pane);
                availableTreeCardinalitySubPane.getChildren().add(pane);
                break;
            case MANY_HIERARCHY:
                availableMultiTreeCardinalityPaneList.add(pane);
                availableMultiTreeCardinalitySubPane.getChildren().add(pane);
                break;
        }
    }

    @Override
    protected boolean availableRightClickAvailable(ClassifierFactory fac) {
        if (fac == null) {
            return false;
        }
        if (!(fac instanceof ClassifierFactory)) {
            return false;
        }
        ClassifierFactory f = fac;
        return f.isOnTheFlyCompilable();
    }

    @Override
    public List<ClassifierFactory> getAvailableList() {
        return availableClassifierInfo.getAvailableClassifier().stream()
                                      .filter(fac -> !isExcludedFromAvailable(fac.create()))
                                      .collect(Collectors.toList());
    }

    @Override
    protected CreateClassifierDialog getCreateDialog() throws IOException {
        CreateClassifierDialog dialog = new CreateClassifierDialog();
        dialog.init(availableClassifierInfo.getSymbolsSupplier(),
                    availableClassifierInfo.getFastHeapSupplier(),
                    availableClassifierInfo.getAvailableClassifier());
        return dialog;
    }

    @Override
    protected Dialog<? extends ClassifierFactory> getEditDialog(ClassifierFactory fac) throws IOException {
        if (fac instanceof ClassifierFactory) {
            return new EditClassifierDialog(availableClassifierInfo.getSymbolsSupplier(),
                                            availableClassifierInfo.getFastHeapSupplier(),
                                            fac,
                                            availableClassifierInfo.getAvailableClassifier());
        } else {
            return null;
        }
    }

    @Override
    protected Comparator<ClassifierFactory> comperator() {
        return Comparator.comparing(ClassifierFactory::getName);
    }

    @Override
    protected void clearAvailablePanes() {
        availableSingleCardinalitySubPane.getChildren().removeAll(availableSingleCardinalityPaneList);
        availableSingleCardinalityPaneList.clear();
        availableMultiCardinalitySubPane.getChildren().removeAll(availableMultiCardinalityPaneList);
        availableMultiCardinalityPaneList.clear();
        availableTreeCardinalitySubPane.getChildren().removeAll(availableTreeCardinalityPaneList);
        availableTreeCardinalityPaneList.clear();
        availableMultiTreeCardinalitySubPane.getChildren().removeAll(availableMultiTreeCardinalityPaneList);
        availableMultiTreeCardinalityPaneList.clear();
    }

    @Override
    public List<Pane> getAvailablePaneList() {
        List<Pane> availablePaneList = new ArrayList<>();
        availablePaneList.addAll(availableSingleCardinalityPaneList);
        availablePaneList.addAll(availableMultiCardinalityPaneList);
        availablePaneList.addAll(availableTreeCardinalityPaneList);
        availablePaneList.addAll(availableMultiTreeCardinalityPaneList);
        return availablePaneList;
    }

    public FlowPane getAvailableSingleCardinalitySubPane() {
        return availableSingleCardinalitySubPane;
    }
}