
package at.jku.anttracks.gui.classification.component.selectionpane;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierFactory;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.FilterFactory;
import at.jku.anttracks.gui.classification.component.arrowpane.ArrowPane;
import at.jku.anttracks.gui.component.clickablepane.ClickablePane;
import at.jku.anttracks.gui.model.IAvailableClassifierInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.WindowUtil;
import at.jku.anttracks.util.CollectionsUtil.Factory;
import at.jku.anttracks.util.ImagePack;
import javafx.beans.DefaultProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@DefaultProperty("availablePane")
public abstract class ClassificationSelectionPane<SELECT_TYPE, FACTORY_TYPE extends Factory<SELECT_TYPE>> extends TitledPane {
    private static final JavaCompiler javac = ToolProvider.getSystemJavaCompiler();

    private Set<SELECT_TYPE> excludeFromAvailable;
    private Set<SELECT_TYPE> selectedButNotShown;

    // SELECTED //

    private int maximumSelect;

    protected abstract ImagePack getIcon(SELECT_TYPE x);

    protected abstract String getName(SELECT_TYPE x);

    protected abstract String[] getSelectedDescriptions(SELECT_TYPE x);

    protected abstract String getSelectedToolTip(SELECT_TYPE x);

    private boolean removeSelected(SELECT_TYPE x) {
        if (x == null) {
            return false;
        }
        int selectedListSize = selectedList.size();
        selectedList = selectedList.stream().filter(sel -> sel != x).collect(Collectors.toList());

        // Notify listeners
        listeners.forEach(l -> l.deselected(this, x));

        refreshSelected();

        return selectedListSize != selectedList.size();
    }

    protected abstract boolean removeSelected(FACTORY_TYPE y);

    protected abstract boolean selectedRightClick(SELECT_TYPE x);

    private void addSelectedPane(SELECT_TYPE x) {
        ClickablePane pane = new ClickablePane();
        pane.init(getIcon(x),
                  getName(x),
                  getSelectedDescriptions(x),
                  "X",
                  getSelectedToolTip(x),
                  (() -> removeSelected(operationsEnabled ? x : null)),
                  (() -> {
                      if (selectedRightClick(x)) {
                          // Notify listeners
                          listeners.forEach(l -> l.propertiesChanged(this, x));
                      }
                  }),
                  operationsEnabled);

        // ADD to all collections
        selectedPaneList.add(pane);
        selectedPane.getChildren().add(pane);
    }

    // AVAILABLE //

    protected abstract ImagePack getAvailableIcon(FACTORY_TYPE y);

    protected abstract String getAvailableName(FACTORY_TYPE y);

    protected abstract String[] getAvailableDescriptions(FACTORY_TYPE y);

    protected abstract String getAvailableToolTip(FACTORY_TYPE y);

    protected abstract void addAvailablePane(FACTORY_TYPE y, ClickablePane pane);

    private void addSelected(FACTORY_TYPE y) {
        addSelected(y, selectedList.size());
    }

    private void addSelected(FACTORY_TYPE y, int pos) {
        if (y == null || pos < 0 || pos > selectedList.size() || (maximumSelect > 1 && selectedList.size() == maximumSelect)) {
            return;
        }

        SELECT_TYPE selected = y.create();
        if (prohibitDuplicates && selectedList.contains(selected)) {
            return;
        }
        selectedList.add(pos, selected);

        // Notify listeners
        listeners.forEach(l -> l.selected(this, selected));

        refreshSelected();
    }

    protected abstract boolean availableRightClickAvailable(FACTORY_TYPE x);

    protected void availableRightClick(FACTORY_TYPE y) {
        ObjectProperty<Long> prop = new SimpleObjectProperty<>();
        SimpleObjectProperty<Long> wrapper = new SimpleObjectProperty<>();

        if (availableRightClickAvailable(y)) {
            if (javac == null) {
                Alert javacNotFoundAlert = new Alert(AlertType.ERROR);
                javacNotFoundAlert.setTitle("Javac not found");
                javacNotFoundAlert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                javacNotFoundAlert.setContentText("No javac found. (This probably means you are running with a JRE instead of a JDK.");
                WindowUtil.INSTANCE.centerInMainFrame(javacNotFoundAlert);
                javacNotFoundAlert.showAndWait();
            } else {
                try {
                    Dialog<? extends FACTORY_TYPE> dialog = getEditDialog(y);
                    WindowUtil.INSTANCE.centerInMainFrame(dialog);

                    // ifPresent: only executed if cancel has not been pressed
                    Optional<? extends FACTORY_TYPE> dialogResult = dialog.showAndWait();
                    if (dialogResult.isPresent()) {
                        // Anything except null has been returned
                        if (dialogResult.get() == y) {
                            // delete has been pressed
                            getAvailableList().remove(y);
                            removeSelected(y);
                        } else {
                            // edited custom classifier
                            // replace in available list
                            getAvailableList().remove(y);
                            getAvailableList().add(dialogResult.get());

                            // replace in selected list
                            HashMap<Integer, SELECT_TYPE> selectedToRemove = new HashMap<>();
                            for (int i = 0; i < getSelected().size(); i++) {
                                SELECT_TYPE s = getSelected().get(i);
                                if (getAvailableName(y).equals(getName(s))) {
                                    selectedToRemove.put(i, s);
                                }
                            }
                            selectedToRemove.entrySet().stream().forEach(entry -> {
                                removeSelected(entry.getValue());
                            });
                            selectedToRemove.entrySet().stream().forEach(entry -> {
                                addSelected(dialogResult.get(), entry.getKey());
                            });
                        }
                    } else {
                        // cancel or 'X'
                        // Nothing to do
                    }

                    refresh();
                } catch (Exception e1) {
                    Alert templateFileAlert = new Alert(AlertType.ERROR);
                    templateFileAlert.setTitle("Create Dialog Error");
                    templateFileAlert.setContentText("Could not open create dialog. (" + e1.toString() + ")");
                    templateFileAlert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                    WindowUtil.INSTANCE.centerInMainFrame(templateFileAlert);
                    templateFileAlert.showAndWait();
                    return;
                }

            }
        }
    }

    private void addAvailableClassifierPane(FACTORY_TYPE y) {
        ClickablePane pane = new ClickablePane();
        pane.init(getAvailableIcon(y),
                  getAvailableName(y),
                  getAvailableDescriptions(y),
                  "+",
                  getAvailableToolTip(y),
                  () -> addSelected(operationsEnabled ? y : null),
                  () -> availableRightClick(y),
                  operationsEnabled);
        addAvailablePane(y, pane);
    }

    // LISTENER //

    public interface ClassificationSelectionListener<SELECT_TYPE, FACTORY_TYPE extends Factory<SELECT_TYPE>> {
        void selected(ClassificationSelectionPane<SELECT_TYPE, FACTORY_TYPE> sender, SELECT_TYPE x);

        void deselected(ClassificationSelectionPane<SELECT_TYPE, FACTORY_TYPE> sender, SELECT_TYPE x);

        void propertiesChanged(ClassificationSelectionPane<SELECT_TYPE, FACTORY_TYPE> sender, SELECT_TYPE x);

        ClassificationSelectionListener<Classifier<?>, ClassifierFactory> NO_OP_CLASSIFIER_LISTENER =
                new ClassificationSelectionListener<Classifier<?>, ClassifierFactory>() {
                    @Override
                    public void selected(ClassificationSelectionPane<Classifier<?>, ClassifierFactory> sender, Classifier<?> x) {

                    }

                    @Override
                    public void deselected(ClassificationSelectionPane<Classifier<?>, ClassifierFactory> sender, Classifier<?> x) {

                    }

                    @Override
                    public void propertiesChanged(ClassificationSelectionPane<Classifier<?>, ClassifierFactory> sender, Classifier<?> x) {

                    }
                };

        ClassificationSelectionListener<Filter, FilterFactory> NOOP_FILTER_LISTENER =
                new ClassificationSelectionListener<Filter, FilterFactory>() {
                    @Override
                    public void selected(ClassificationSelectionPane<Filter, FilterFactory> sender, Filter x) {

                    }

                    @Override
                    public void deselected(ClassificationSelectionPane<Filter, FilterFactory> sender, Filter x) {

                    }

                    @Override
                    public void propertiesChanged(ClassificationSelectionPane<Filter, FilterFactory> sender, Filter x) {

                    }
                };
    }

    // Data
    protected IAvailableClassifierInfo availableClassifierInfo;

    public abstract List<FACTORY_TYPE> getAvailableList();

    // FXML
    @FXML
    private Label selectedLabel;
    @FXML
    private FlowPane selectedPane;
    @FXML
    private Separator separator;
    @FXML
    private HBox availableSuperPane;
    @FXML
    protected HBox availablePane;
    @FXML
    private Button availableAddButton;

    // Available -> ToDo by child
    public abstract List<Pane> getAvailablePaneList();

    // Selected classifier
    private List<SELECT_TYPE> selectedList;
    private List<Pane> selectedPaneList;

    CopyOnWriteArrayList<ClassificationSelectionListener<SELECT_TYPE, FACTORY_TYPE>> listeners;

    @SuppressWarnings("unused")
    private final Logger LOGGER = Logger.getLogger(this.getClass().getSimpleName());

    private boolean operationsEnabled = true;
    private boolean prohibitDuplicates;

    public ClassificationSelectionPane() {
        FXMLUtil.load(this, ClassificationSelectionPane.class);
    }

    public void init(IAvailableClassifierInfo availableClassifierInfo, int maximumSelect, boolean prohibitDuplicates) {
        this.availableClassifierInfo = availableClassifierInfo;

        selectedList = new ArrayList<>();
        selectedPaneList = new ArrayList<>();
        listeners = new CopyOnWriteArrayList<>();

        this.maximumSelect = maximumSelect;
        this.prohibitDuplicates = prohibitDuplicates;

        initializeAvailableAddButton();

        excludeFromAvailable = new HashSet<>();
        selectedButNotShown = new HashSet<>();

        refresh();
        switchToAnalysisPerspective();
    }

    protected abstract Dialog<? extends FACTORY_TYPE> getCreateDialog() throws IOException;

    protected abstract Dialog<? extends FACTORY_TYPE> getEditDialog(FACTORY_TYPE x) throws IOException;

    private void initializeAvailableAddButton() {
        availableAddButton.setOnAction(ae -> {
            if (javac == null) {
                Alert javacNotFoundAlert = new Alert(AlertType.ERROR);
                javacNotFoundAlert.setTitle("Javac not found");
                javacNotFoundAlert.setContentText("No javac found. (This probably means you are running with a JRE instead of a JDK.");
                WindowUtil.INSTANCE.centerInMainFrame(javacNotFoundAlert);
                javacNotFoundAlert.showAndWait();
            } else {
                try {
                    Dialog<? extends FACTORY_TYPE> dialog = getCreateDialog();
                    WindowUtil.INSTANCE.centerInMainFrame(dialog);
                    dialog.showAndWait().ifPresent(x -> {
                        getAvailableList().add(x);
                        refresh();
                    });
                } catch (Exception e1) {
                    e1.printStackTrace(System.err);
                    Alert templateFileAlert = new Alert(AlertType.ERROR);
                    templateFileAlert.setTitle("Create Dialog Error");
                    templateFileAlert.setContentText("Could not open create dialog. (" + e1.toString() + ")");
                    templateFileAlert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                    WindowUtil.INSTANCE.centerInMainFrame(templateFileAlert);
                    templateFileAlert.showAndWait();
                }
            }
        });
    }

    public void switchToConfigurationPerspective() {
        availableSuperPane.setVisible(true);
        availableSuperPane.setManaged(true);
        separator.setVisible(true);
        separator.setManaged(true);

        // Make sure it is expanded when we switch into configuration perspective
        setExpanded(true);

        enableOperations(true);
    }

    public void switchToAnalysisPerspective() {
        availableSuperPane.setVisible(false);
        availableSuperPane.setManaged(false);
        separator.setVisible(false);
        separator.setManaged(false);

        // Collapse in analysis view if nothing is selected
        setExpanded(selectedList.size() > 0);

        enableOperations(false);
    }

    public void addListener(ClassificationSelectionListener<SELECT_TYPE, FACTORY_TYPE> l) {
        listeners.add(l);
    }

    public void removeListener(ClassificationSelectionListener<SELECT_TYPE, FACTORY_TYPE> l) {
        listeners.remove(l);
    }

    private void resetAvailable() {
        clearAvailablePanes();
        getAvailableList().stream().sorted((o1, o2) -> comperator().compare(o1, o2)).forEach(this::addAvailableClassifierPane);
    }

    public void resetSelected(List<SELECT_TYPE> selected) {
        if (selected != null) {
            selected = new ArrayList<SELECT_TYPE>(selected);
        }
        // Update selected classifiers list
        selectedList.clear();
        if (selected != null) {
            selectedList.addAll(selected);
        }

        selectedPane.getChildren().removeAll(selectedPaneList);
        selectedPaneList.clear();

        int added = 0;
        for (int i = 0; i < selectedList.size(); i++) {
            if (!isSelectedButNotShown(selectedList.get(i))) {
                // Arrow
                if (added > 0) {
                    ArrowPane arrowPane = new ArrowPane();
                    selectedPaneList.add(arrowPane);
                    selectedPane.getChildren().add(arrowPane);
                }

                SELECT_TYPE selectedThing = selectedList.get(i);
                addSelectedPane(selectedThing);
                listeners.forEach(l -> l.selected(this, selectedThing));
                added++;
            }
        }
    }

    public boolean isExcludedFromAvailable(SELECT_TYPE x) {
        return excludeFromAvailable.stream().anyMatch(excluded -> excluded.getClass().equals(x.getClass()));
    }

    public void addExcludedFromAvailable(SELECT_TYPE x) {
        excludeFromAvailable.add(x);
    }

    public void removeExcludedFromAvailable(SELECT_TYPE x) {
        excludeFromAvailable = excludeFromAvailable.stream().filter(excluded -> !excluded.getClass().equals(x.getClass())).collect(Collectors.toSet());
    }

    public boolean isSelectedButNotShown(SELECT_TYPE x) {
        return selectedButNotShown.stream().anyMatch(hidden -> hidden.getClass().equals(x.getClass()));
    }

    public void addSelectedButNotShown(SELECT_TYPE x) {
        selectedButNotShown.add(x);
    }

    public void removeSelectedButNotShown(SELECT_TYPE x) {
        selectedButNotShown = selectedButNotShown.stream().filter(hidden -> !hidden.getClass().equals(x.getClass())).collect(Collectors.toSet());
    }

    protected abstract Comparator<FACTORY_TYPE> comperator();

    protected abstract void clearAvailablePanes();

    public void refresh() {
        resetAvailable();
        refreshSelected();
    }

    protected void refreshSelected() {
        resetSelected(new ArrayList<>(selectedList));
    }

    public List<SELECT_TYPE> getSelected() {
        List<SELECT_TYPE> selected = new ArrayList<>();
        // TODO: Not shown classifiers are always added first, this may be a problem with classifiers somewhen in the future...?
        selected.addAll(selectedButNotShown);
        selected.addAll(selectedList);
        return selected;
    }

    public void enableOperations(boolean b) {
        operationsEnabled = b;

        getAvailablePaneList().stream().filter(pane -> pane instanceof ClickablePane).map(pane -> (ClickablePane) pane).forEach(pane -> pane.setClickable(b));
        selectedPaneList.stream().filter(pane -> pane instanceof ClickablePane).map(pane -> (ClickablePane) pane).forEach(pane -> pane.setClickable(b));
    }

    public Button getAvailableAddButton() {
        return availableAddButton;
    }

    public FlowPane getSelectedPane() {
        return selectedPane;
    }

    public boolean getSelectedLabelVisible() {
        return selectedLabel.isVisible();
    }

    public void setSelectedLabelVisible(boolean b) {
        selectedLabel.setVisible(b);
        selectedLabel.setManaged(b);
    }

    public ObservableList<Node> getAvailablePane() {
        return availablePane.getChildren();
    }
}