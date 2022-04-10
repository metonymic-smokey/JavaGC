package at.jku.anttracks.gui.component.treetableview;

import at.jku.anttracks.gui.component.treetableview.cell.*;
import at.jku.anttracks.gui.frame.main.tab.heapevolution.tab.permborndiedtemp.model.PlotablePermBornDiedTempData;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.util.Counter;
import com.sun.javafx.scene.control.skin.TreeTableViewSkin;
import com.sun.javafx.scene.control.skin.VirtualFlow;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Popup;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Logger;

public class AntTreeTableView<T> extends TreeTableView<T> {
    protected static Logger LOGGER = Logger.getLogger(AntTreeTableView.class.getSimpleName());

    /**
     * Used to remember expansions e.g., can be used to re-expand previously expanded rows after re-classification
     */
    private final List<T> expanded = new ArrayList<T>();

    protected static PseudoClass ancestorOfSelection = PseudoClass.getPseudoClass("ancestor-of-selection");

    /**
     * When called makes sure that this treetableview becomes visible on the screen (e.g., by switching to the containing tab)
     */
    private Runnable showTTVOnScreen;

    private Function<T, String> keyFunction;

    public AntTreeTableView() {
        FXMLUtil.load(this, AntTreeTableView.class);
    }

    public void init(Runnable showTTVOnScreen, Function<T, String> keyFunction) {
        this.showTTVOnScreen = showTTVOnScreen;
        this.keyFunction = keyFunction;

        this.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);

        // Default cell factories and default sorting
        rootProperty().addListener((observableValue, oldVal, newValue) -> {
            if (newValue != null) {
                setDefaultCellFactories(getColumns());
                setDefaultComparators(getColumns());
            }
        });

        // selected row highlighting
        setRowFactory(ttv -> createTreeTableRow());

        setSortMode(TreeSortMode.ALL_DESCENDANTS);

        // add textual search feature (CTRL+F)
        initSearchFunction();
    }

    /**
     * Creates table rows that also highlight parent rows when selected
     *
     * @return a parent-highlighting table row
     */
    protected TreeTableRow<T> createTreeTableRow() {
        return new TreeTableRow<T>() {
            {
                getSelectionModel().getSelectedItems().addListener((ListChangeListener<TreeItem<T>>) change -> updateStyleClass());
            }

            @Override
            protected void updateItem(T item, boolean empty) {
                super.updateItem(item, empty);
                updateStyleClass();
            }

            private void updateStyleClass() {
                if (getPseudoClassStates().contains(ancestorOfSelection)) {
                    pseudoClassStateChanged(ancestorOfSelection, false);
                }
                for (TreeItem<T> treeItem : getSelectionModel().getSelectedItems()) {
                    if (treeItem != null) {
                        for (TreeItem<T> parent = treeItem.getParent(); parent != null; parent = parent.getParent()) {
                            if (parent == getTreeItem()) {
                                if (!getPseudoClassStates().contains(ancestorOfSelection)) {
                                    pseudoClassStateChanged(ancestorOfSelection, true);
                                }
                                break;
                            }
                        }
                    }
                }
            }
        };
    }

    /**
     * Sets AntTracks's default cell visualizations (e.g., bars for percentages)
     *
     * @param columns the table's columns
     */
    @SuppressWarnings("unchecked")
    protected void setDefaultCellFactories(ObservableList<TreeTableColumn<T, ?>> columns) {
        for (TreeTableColumn<T, ?> column : columns) {
            // Check if cell factory is still default type and has not been changed by dev
            if (column.getCellFactory().getClass().getName().startsWith("javafx.scene.control.TreeTableColumn$")) {
                // Exclude grouping columns
                if (column.getCellData(0) != null) {
                    switch (column.getCellData(0).getClass().getSimpleName()) {
                        case "PlotablePermBornDiedTempData":
                        case "OldPlotableDiffingData":
                            column.setCellFactory(param -> new PlotableTreePermBornDiedTempDataCell());
                            break;
                        case "ValueWithReference":
                            column.setCellFactory(param -> new ValueWithReferenceTreeCell());
                            break;
                        case "SampledValueWithReference":
                            column.setCellFactory(param -> new SampledValueWithReferenceTreeCell(true,
                                                                                                 false,
                                                                                                 x -> SampledValueWithReferenceTreeCell.Companion.getNumberFormat().format(x)));
                            break;
                        case "ApproximateDouble":
                            column.setCellFactory(param -> new ApproximateDoubleTreeCell());
                            break;
                        case "Percentage":
                            column.setCellFactory(param -> new PercentageTreeCell());
                            break;
                        case "Double":
                        case "Integer":
                        case "Long":
                        case "Number":
                            column.setCellFactory(param -> new NumberTreeCell());
                            break;
                        case "Color":
                            column.setCellFactory(param -> new ColorTreeCell());
                            break;
                    }
                } else {
                    LOGGER.warning("Could not determine column type for column " + column.getText());
                }
            }

            setDefaultCellFactories(column.getColumns());
        }
    }

    @SuppressWarnings("unchecked")

    protected void setDefaultComparators(ObservableList<TreeTableColumn<T, ?>> columns) {
        for (TreeTableColumn<T, ?> column : columns) {
            // Check if comperator is still default type and has not been changed by dev
            if (column.getComparator().getClass().getName().startsWith("javafx.scene.control.TableColumnBase")) {
                // Exclude grouping columns
                if (column.getCellData(0) != null) {
                    switch (column.getCellData(0).getClass().getSimpleName()) {
                        case "PlotablePermBornDiedTempData":
                        case "OldPlotableDiffingData":
                            ((TreeTableColumn<T, PlotablePermBornDiedTempData>) column)
                                    .setComparator(Comparator.comparingDouble(value -> value.getPlotStyle() == PlotablePermBornDiedTempData.PlotStyle.PermDiedBorn ?
                                                                                       value.getAfter() :
                                                                                       value.getTemp()));
                            break;
                    }
                }
            }

            setDefaultComparators(column.getColumns());
        }
    }

    private void initSearchFunction() {
        final Popup popup = new Popup();
        final VBox layout = new VBox();
        final Label titleLabel = new Label("Search for text...");
        final Label instructionsLabel = new Label("(Press ENTER for next or SHIFT+ENTER for previous match)");
        final TextField searchTextField = new TextField();
        final Label resultsLabel = new Label();

        popup.getContent().add(layout);
        popup.setAutoHide(true);
        layout.setBackground(new Background(new BackgroundFill(Color.BLACK.deriveColor(0, 1, 1, 0.6), new CornerRadii(10), new Insets(-5))));
        layout.getChildren().addAll(titleLabel, instructionsLabel, searchTextField, resultsLabel);
        titleLabel.setTextFill(Color.WHITE);
        instructionsLabel.setTextFill(Color.WHITE);
        resultsLabel.setTextFill(Color.WHITE);
        resultsLabel.setManaged(false);

        StringBuilder lastSearchTerm = new StringBuilder();    // mutable string for lambda, needed to detect whether new search or same as last time
        List<TreeItem<T>> matches = new ArrayList<>();
        Counter currentMatchIndex = new Counter(true);

        this.addEventHandler(KeyEvent.KEY_RELEASED, evt -> {
            if (!popup.isShowing() && evt.getCode() == KeyCode.F && evt.isControlDown()) {
                // reset search state
                lastSearchTerm.setLength(0);
                matches.clear();
                currentMatchIndex.reset();
                resultsLabel.setManaged(false);
                resultsLabel.setVisible(false);

                // show search box
                popup.show(this, localToScreen(getBoundsInLocal()).getMaxX() - 300, localToScreen(getBoundsInLocal()).getMaxY() - 100);
            }
        });

        searchTextField.addEventHandler(KeyEvent.KEY_RELEASED, evt -> {
            if (evt.getCode() == KeyCode.ENTER && searchTextField.getText().length() > 0) {
                if (!searchTextField.getText().equals(lastSearchTerm.toString())) {
                    // new search -> filter
                    lastSearchTerm.setLength(0);
                    lastSearchTerm.append(searchTextField.getText());
                    currentMatchIndex.reset();
                    matches.clear();
                    matches.addAll(filter(treeItem -> keyFunction.apply(treeItem.getValue()).contains(lastSearchTerm)));
                    if (matches.size() > 0) {
                        resultsLabel.setText(String.format("Showing matching item #%d (%d in total)", currentMatchIndex.get() + 1, matches.size()));
                        showAndSelect(matches.get((int) currentMatchIndex.get()));
                    } else {
                        resultsLabel.setText("No matching items found!");
                    }

                    resultsLabel.setVisible(true);
                    resultsLabel.setManaged(true);

                } else if (matches.size() > 0) {
                    // move within matches
                    if (evt.isShiftDown()) {
                        // previous match
                        currentMatchIndex.dec();
                        if (currentMatchIndex.get() < 0) {
                            currentMatchIndex.reset();
                            currentMatchIndex.add(matches.size() - 1);
                        }
                    } else {
                        // next match
                        currentMatchIndex.inc();
                        if (currentMatchIndex.get() >= matches.size()) {
                            currentMatchIndex.reset();
                        }
                    }

                    resultsLabel.setText(String.format("Showing matching item #%d (%d in total)", currentMatchIndex.get() + 1, matches.size()));
                    showAndSelect(matches.get((int) currentMatchIndex.get()));
                }

            } else if (evt.getCode() == KeyCode.ENTER && searchTextField.getText().length() == 0) {
                resultsLabel.setText("Please enter at least one character...");
                resultsLabel.setManaged(true);
                resultsLabel.setVisible(true);

            } else if (evt.getCode() == KeyCode.ESCAPE) {
                popup.hide();
            }
        });
    }

    public void showAndSelect(TreeItem<T> item) {
        // make tree table visible on the screen
        showTTVOnScreen.run();

        // expand all parents
        expandRecursivelyUpTo(item);

        // select
        this.getSelectionModel().clearSelection();
        this.getSelectionModel().select(item);

        // scroll if necessary (scroll to 5 items above if possible - adds some context to the selected item)
        if (!isItemInView(item)) {
            this.scrollTo(this.getRow(item) - 5);
        }
    }

    /**
     * Scrolls to and selects the first row in the tree table that matches the given search text
     *
     * @param searchText search text that the name cell of a row has to match
     */
    public void showAndSelect(String searchText) {
        List<TreeItem<T>> matches = filter(treeItem -> keyFunction.apply(treeItem.getValue()).contains(searchText));

        if (!matches.isEmpty()) {
            showAndSelect(matches.get(0));
        }
    }

    protected void rememberExpansion() {
        expanded.clear();
        rememberExpansion(getRoot());
    }

    private void rememberExpansion(TreeItem<T> item) {
        if (item != null) {
            if (item.isExpanded()) {
                expanded.add(item.getValue());
            }
            item.getChildren().forEach(this::rememberExpansion);
        }
    }

    protected void restoreExpansion() {
        restoreExpansion(getRoot());
    }

    private void restoreExpansion(TreeItem<T> item) {
        if (item != null) {
            item.setExpanded(expanded.contains(item.getValue()));
            item.getChildren().forEach(this::restoreExpansion);
        }
    }

    public void expandRecursivelyUpTo(TreeItem<T> item) {
        for (TreeItem<T> ti = item.getParent(); ti != null; ti = ti.getParent()) {
            ti.setExpanded(true);
        }
    }

    public void expandRecursivelyFrom(
            @NotNull
                    T value,
            boolean stopAtFirstBranch) {
        List<TreeItem<T>> matchingTreeItems = filter(treeItem -> treeItem.getValue() == value);
        if (!matchingTreeItems.isEmpty()) {
            expandRecursivelyFrom(matchingTreeItems.get(0), stopAtFirstBranch);
        }
    }

    public void expandRecursivelyFrom(
            @NotNull
                    TreeItem<T> treeItem,
            boolean stopAtFirstBranch) {
        treeItem.setExpanded(true);
        if (!(stopAtFirstBranch && treeItem.getChildren().size() > 1)) {
            treeItem.getChildren().forEach(child -> expandRecursivelyFrom(child, stopAtFirstBranch));
        }
    }

    public void scrollTo(TreeItem<T> item) {
        int[] visibleRange = getVisibleRange();
        int visibleRangeLength = visibleRange[1] - visibleRange[0];
        int offsetOfItemFromTopOfVisibleRange = getRow(item) - visibleRange[0];
        if (offsetOfItemFromTopOfVisibleRange <= visibleRangeLength * 0.25 || offsetOfItemFromTopOfVisibleRange >= visibleRangeLength * 0.75) {
            // item is not positioned 1/4 down the the visible range, try to adjust scroll position
            scrollTo(getRow(item) - (int) (visibleRangeLength * 0.25));
        }
    }

    protected int[] getVisibleRange() {
        TreeTableViewSkin<?> skin = (TreeTableViewSkin<?>) getSkin();
        if (skin == null) {
            return new int[]{0, 0};
        }
        VirtualFlow<?> flow = (VirtualFlow<?>) skin.getChildren().get(1);
        int indexFirst;
        int indexLast;
        if (flow != null && flow.getFirstVisibleCellWithinViewPort() != null && flow.getLastVisibleCellWithinViewPort() != null) {
            indexFirst = flow.getFirstVisibleCellWithinViewPort().getIndex();
            indexLast = flow.getLastVisibleCellWithinViewPort().getIndex();
        } else {
            indexFirst = 0;
            indexLast = 0;
        }
        return new int[]{indexFirst, indexLast};
    }

    public boolean isItemInView(TreeItem<T> item) {
        int[] visibleRange = getVisibleRange();

        return getRow(item) >= visibleRange[0] && getRow(item) <= visibleRange[1];
    }

    public List<TreeItem<T>> filter(Predicate<TreeItem<T>> predicate) {
        return filter(this.getRoot(), predicate);
    }

    public List<TreeItem<T>> filter(TreeItem<T> startItem, Predicate<TreeItem<T>> predicate) {
        List<TreeItem<T>> matches = new ArrayList<>();

        filter(startItem, matches, predicate);

        return matches;
    }

    private void filter(TreeItem<T> current, List<TreeItem<T>> matches, Predicate<TreeItem<T>> predicate) {
        if (predicate.test(current)) {
            matches.add(current);
        }

        current.getChildren().forEach(child -> filter(child, matches, predicate));
    }

    public void applyDefaultSortingSettings() {
        getSortOrder().clear();
        setSortMode(TreeSortMode.ALL_DESCENDANTS);
    }
}
