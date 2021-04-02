
package at.jku.anttracks.gui.classification.filter.component.selectionpane;

import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.FilterFactory;
import at.jku.anttracks.gui.classification.component.selectionpane.ClassificationSelectionPane;
import at.jku.anttracks.gui.classification.dialog.properties.ClassificationPropertiesDialog;
import at.jku.anttracks.gui.classification.filter.OnlyDataStructureHeadsFilter;
import at.jku.anttracks.gui.classification.filter.dialog.create.CreateFilterDialog;
import at.jku.anttracks.gui.classification.filter.dialog.edit.EditFilterDialog;
import at.jku.anttracks.gui.component.clickablepane.ClickablePane;
import at.jku.anttracks.gui.model.IAvailableClassifierInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.WindowUtil;
import at.jku.anttracks.util.ImagePack;
import javafx.fxml.FXML;
import javafx.scene.control.Dialog;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Pane;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ObjectFilterSelectionPane extends ClassificationSelectionPane<Filter, FilterFactory> {

    @FXML
    public FlowPane available;

    List<Pane> availableFilterPaneList;

    public ObjectFilterSelectionPane() {
        FXMLUtil.load(this, ObjectFilterSelectionPane.class);
    }

    @Override
    public void init(IAvailableClassifierInfo availableClassifierInfo, int maxSelect, boolean prohibitDuplicates) {
        availableFilterPaneList = new ArrayList<>();
        super.init(availableClassifierInfo, maxSelect, prohibitDuplicates);

        Filter onlyDataStructureHeadsFilter = availableClassifierInfo.getAvailableFilter().get(OnlyDataStructureHeadsFilter.class);
        if (onlyDataStructureHeadsFilter != null) {
            addExcludedFromAvailable(onlyDataStructureHeadsFilter);
        }
        Filter onlyTopLevelDataStructureHeadsFilter = availableClassifierInfo.getAvailableFilter().get(OnlyDataStructureHeadsFilter.class);
        if (onlyTopLevelDataStructureHeadsFilter != null) {
            addExcludedFromAvailable(onlyTopLevelDataStructureHeadsFilter);
        }
        refresh();
    }

    // SELECTED

    @Override
    protected ImagePack getIcon(Filter filter) {
        return null;
    }

    @Override
    protected String getName(Filter filter) {
        return filter.getName();
    }

    @Override
    protected String[] getSelectedDescriptions(Filter filter) {
        return Stream.of(filter.configurableProperties()).filter(prop -> prop.getPresentationName() != null && prop.get() != null).map(prop -> {
            return prop.getPresentationName() + ": " + prop.get();
        }).toArray(String[]::new);
    }

    @Override
    protected String getSelectedToolTip(Filter filter) {
        return filter.getDesc() + (filter.configurableProperties().length > 0 ? "\nRight click to configure" : "");
    }

    @Override
    protected boolean selectedRightClick(Filter filter) {
        if (filter == null) {
            return false;
        }
        if (filter.configurableProperties().length == 0) {
            return false;
        }

        ClassificationPropertiesDialog dialog = new ClassificationPropertiesDialog(filter, availableClassifierInfo);
        WindowUtil.INSTANCE.centerInMainFrame(dialog);
        Optional<Boolean> dialogResult = dialog.showAndWait();
        if (dialogResult.isPresent() && dialogResult.get()) {
            // Refresh UI if some filter property has been changed
            refreshSelected();
            return true;
        }
        return false;
    }

    // AVAILABLE

    @Override
    protected ImagePack getAvailableIcon(FilterFactory fac) {
        return null;
    }

    @Override
    protected String getAvailableName(FilterFactory fac) {
        return fac.getName();
    }

    @Override
    protected String[] getAvailableDescriptions(FilterFactory fac) {
        return new String[]{};
    }

    @Override
    protected String getAvailableToolTip(FilterFactory fac) {
        return fac.getDesc() + (fac instanceof FilterFactory && fac.isOnTheFlyCompilable() ? "\nRight click to edit" : "");
    }

    @Override
    protected void addAvailablePane(FilterFactory fac, ClickablePane pane) {
        availableFilterPaneList.add(pane);
        available.getChildren().add(pane);
    }

    @Override
    protected boolean availableRightClickAvailable(FilterFactory fac) {
        return fac != null && fac instanceof FilterFactory && fac.isOnTheFlyCompilable();
    }

    @Override
    public List<FilterFactory> getAvailableList() {
        return availableClassifierInfo.getAvailableFilter().stream()
                                      .filter(fac -> !isExcludedFromAvailable(fac.create()))
                                      .collect(Collectors.toList());
    }

    @Override
    protected CreateFilterDialog getCreateDialog() throws IOException {
        CreateFilterDialog dialog = new CreateFilterDialog();
        dialog.init(availableClassifierInfo.getSymbolsSupplier(),
                    availableClassifierInfo.getFastHeapSupplier(),
                    availableClassifierInfo.getAvailableFilter());
        return dialog;
    }

    @Override
    protected Dialog<? extends FilterFactory> getEditDialog(FilterFactory fac) throws IOException {
        if (fac != null) {
            EditFilterDialog dialog = new EditFilterDialog();
            dialog.init(availableClassifierInfo.getSymbolsSupplier(),
                        availableClassifierInfo.getFastHeapSupplier(),
                        fac,
                        availableClassifierInfo.getAvailableFilter());
            return dialog;
        } else {
            return null;
        }
    }

    @Override
    protected Comparator<FilterFactory> comperator() {
        return (o1, o2) -> o1.getName().compareTo(o2.getName());
    }

    @Override
    protected void clearAvailablePanes() {
        available.getChildren().removeAll(availableFilterPaneList);
        availableFilterPaneList.clear();
    }

    @Override
    protected boolean removeSelected(FilterFactory y) {
        List<Filter> remainingSelected = getSelected().stream().filter(selected -> !selected.getName().equals(y.getName())).collect(Collectors.toList());
        if (getSelected().size() != remainingSelected.size()) {
            resetSelected(remainingSelected);
            return true;
        }
        return false;
    }

    @Override
    public List<Pane> getAvailablePaneList() {
        return availableFilterPaneList;
    }
}
