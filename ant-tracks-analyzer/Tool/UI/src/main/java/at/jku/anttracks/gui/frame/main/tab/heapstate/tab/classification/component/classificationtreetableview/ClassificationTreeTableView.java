
package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.classificationtreetableview;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.nodes.GroupingNode;
import at.jku.anttracks.classification.nodes.IndexCollection;
import at.jku.anttracks.classification.nodes.ListGroupingNode;
import at.jku.anttracks.classification.trees.ClassificationTree;
import at.jku.anttracks.classification.trees.ListClassificationTree;
import at.jku.anttracks.gui.classification.classifier.LocalClassifier;
import at.jku.anttracks.gui.classification.classifier.PointedFromTransformer;
import at.jku.anttracks.gui.classification.classifier.PointsToTransformer;
import at.jku.anttracks.gui.component.actiontab.ActionTabAction;
import at.jku.anttracks.gui.component.treetableview.AntTreeTableView;
import at.jku.anttracks.gui.component.treetableview.AutomatedTreeItem;
import at.jku.anttracks.gui.component.treetableview.cell.KeyTreeCell;
import at.jku.anttracks.gui.component.treetableview.cell.SampledValueWithReferenceTreeCell;
import at.jku.anttracks.gui.component.treetableview.cell.ValueWithReferenceTreeCell;
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab;
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.tab.objectgroupinfo.ObjectGroupInfoTab;
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.HeapGraphVisualizationTab;
import at.jku.anttracks.gui.model.*;
import at.jku.anttracks.gui.utils.AntTask;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.heap.ObjectStream;
import at.jku.anttracks.util.Counter;
import at.jku.anttracks.util.ParallelizationUtil;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.NumberExpression;
import javafx.beans.binding.ObjectBinding;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.util.Duration;
import kotlin.Unit;
import kotlin.jvm.functions.Function1;
import org.controlsfx.control.Notifications;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import static at.jku.anttracks.gui.frame.main.tab.heapstate.tab.graphviz.nodes.GraphVisNodeKt.PATH_TO_MOST_INTERESTING_ROOTS_TYPED;
import static at.jku.anttracks.gui.utils.NumberFormatUtilKt.toShortMemoryUsageString;

public class ClassificationTreeTableView extends AntTreeTableView<GroupingNode> {
    private AutomatedTreeItem<GroupingNode, GroupingNode> root;

    private AppInfo appInfo;
    private IAvailableClassifierInfo availableClassifierInfo;

    // Timeline tableRefresher = null;

    protected Function1<GroupingNode, String> titleFunction = c -> c.getKey().toString();
    protected Function1<GroupingNode, GroupingNode> dataFunction = c -> c;
    protected Function1<GroupingNode, Collection<? extends GroupingNode>> childFunction = GroupingNode::getChildren;
    protected Function1<GroupingNode, Integer> subTreeLevelFunction = GroupingNode::getSubTreeLevel;
    // Root has no classifier, every other Grouping has a classifier
    protected Function1<GroupingNode, Node> iconFunction = c -> {
        List<ImageView> iconNodes = new ArrayList<>();
        if (c.getClassifier() != null) {
            Classifier<?> classifier = availableClassifierInfo.getDummyClassifier(c.getClassifier());
            assert classifier != null;
            ImageView iconNode = classifier.getIconNode(c.getKey());
            if (iconNode != null) {
                iconNodes.add(new ImageView(iconNode.getImage()));
            }
            if (c.getSubTreeLevel() > 0) {
                GroupingNode current = c.getParent();

                while (current.getParent() != null) {
                    if (current.getParent().getSubTreeLevel() != current.getSubTreeLevel()) {
                        classifier = availableClassifierInfo.getDummyClassifier(current.getClassifier());
                        assert classifier != null;
                        iconNode = classifier.getIconNode(current.getKey());
                        if (iconNode != null) {
                            iconNodes.add(0, new ImageView(iconNode.getImage()));
                        }
                    }
                    current = current.getParent();
                }
            }
        }

        return new HBox(2, iconNodes.toArray(new ImageView[iconNodes.size()]));
    };
    protected ChangeListener<Boolean> expansionListener = (observable, oldValue, newValue) -> {
        if (newValue) {
            sort();
        }
    };

    protected TreeTableColumn<GroupingNode, Description> nameColumn = new TreeTableColumn<>("Name");

    protected TreeTableColumn<GroupingNode, SampledValueWithReference> objectsColumn = new TreeTableColumn<>("Objects");

    protected TreeTableColumn<GroupingNode, SampledValueWithReference> shallowSizeColumn = new TreeTableColumn<>("Shallow size");

    protected TreeTableColumn<GroupingNode, ValueWithReference> transitiveClosureSizeColumn = new TreeTableColumn<>("Deep size");

    protected TreeTableColumn<GroupingNode, ValueWithReference> retainedSizeColumn = new TreeTableColumn<>("Retained size");

    protected TreeTableColumn<GroupingNode, ValueWithReference> dataStructureSizeColumn = new TreeTableColumn<>("Data structure size");

    protected TreeTableColumn<GroupingNode, ValueWithReference> deepDataStructureSizeColumn = new TreeTableColumn<>("Deep data structure size");

    private ClassificationTree classificationTree;

    // handle sorting of columns with expensive metrics
    private long lastSortingNotificationTime = 0;
    private ChangeListener<TreeTableColumn.SortType> closureSortListener = (obs, oldVal, newVal) -> {
        if (classificationTree != null && classificationTree instanceof ListClassificationTree) {
            ListClassificationTree tree = (ListClassificationTree) classificationTree;
            if (!tree.isClosuresCalculated() && System.currentTimeMillis() - lastSortingNotificationTime > 3_000) {
                Notifications.create()
                             .title("Sorting")
                             .text("Not all closures have been calculated yet!")
                             .hideAfter(new Duration(3_000))
                             .showWarning();
                lastSortingNotificationTime = System.currentTimeMillis();
            }
        }
    };

    /**
     * Sets the classifierchain-property of a given classifier (local classifier/pointsto/pointedfrom) and returns a boolean indicating success
     */
    private Function<Classifier<?>, Boolean> selectLocalClassifiers;

    /**
     * Takes a tab and integrates it into the UI
     */
    private Consumer<ApplicationBaseTab> addTab;

    /**
     * Takes a tab and switches to it (if it exists)
     */
    private Consumer<ApplicationBaseTab> switchToTab;

    /**
     * Adjusts the UI such that the given classifierchain is shown as the selected one
     */
    private Consumer<ClassifierChain> setSelectedClassifiers;

    private ContextMenu contextMenu = new ContextMenu();
    public List<ActionTabAction> actions = new ArrayList<>();

    public ClassificationTreeTableView() {
        FXMLUtil.load(this, ClassificationTreeTableView.class);
        // setup default context menu
        setupContextMenu();
    }

    public void init(AppInfo appInfo,
                     IAvailableClassifierInfo availableClassifierInfo,
                     Function<Classifier<?>, Boolean> selectLocalClassifiers,
                     Consumer<ApplicationBaseTab> addTab,
                     Consumer<ApplicationBaseTab> switchToTab,
                     Runnable showTTVOnScreen,
                     Consumer<ClassifierChain> setSelectedClassifiers) {
        super.init(showTTVOnScreen, groupingNode -> groupingNode.getKey().toString());
        this.appInfo = appInfo;
        this.availableClassifierInfo = availableClassifierInfo;
        this.selectLocalClassifiers = selectLocalClassifiers;
        this.addTab = addTab;
        this.switchToTab = switchToTab;
        this.setSelectedClassifiers = setSelectedClassifiers;

        // Define columns
        initColumns();
    }

    private void setupContextMenu() {
        ActionTabAction inspectObjectGroupAction = new ActionTabAction(
                "Inspect objects",
                "Detailed information on selected objects",
                "Analysis",
                validSelectionBinding(),
                null,
                () -> {
                    inspectObjectGroup(getSelectionModel().getSelectedItems());
                    return Unit.INSTANCE;
                });

        ActionTabAction localClassifyAction = new ActionTabAction(
                "Classify...",
                "Group selected objects in further groups",
                "Analysis",
                validSelectionBinding(),
                null,
                () -> {
                    handleLocalClassification(new ClassifierChain(new LocalClassifier()));
                    return Unit.INSTANCE;
                });

        ActionTabAction localPointsToAction = new ActionTabAction(
                "Points to...",
                "Inspect objects that are pointed by the selected objects",
                "References",
                validSelectionBinding(),
                null,
                () -> {
                    handleLocalClassification(new ClassifierChain(availableClassifierInfo.getAvailableClassifier().get(PointsToTransformer.class)));
                    return Unit.INSTANCE;
                });

        ActionTabAction localPointedFromAction = new ActionTabAction(
                "Pointed from...",
                "Inspect objects that reference the selected objects",
                "References",
                validSelectionBinding(),
                null,
                () -> {
                    handleLocalClassification(new ClassifierChain(availableClassifierInfo.getAvailableClassifier().get(PointedFromTransformer.class)));
                    return Unit.INSTANCE;
                });

        ActionTabAction visualizeAction = new ActionTabAction(
                "Graph visualization",
                "Visually inspect the selected objects in the object graph",
                "References",
                validSelectionBinding(),
                null,
                () -> {
                    visualizeObjectGroup(getSelectionModel().getSelectedItems());
                    return Unit.INSTANCE;
                });

        ActionTabAction visualizePathToMostInterestingRootsAction = new ActionTabAction(
                "Visualize path to most interesting GC roots",
                "Show the paths to the most interesting GC roots of the selected objects in a graph view",
                "References",
                validSelectionBinding(),
                null,
                () -> {
                    visualizePathToMostInterestingRoots(getSelectionModel().getSelectedItems());
                    return Unit.INSTANCE;
                });

        ActionTabAction calculateClosuresNowAction = new ActionTabAction(
                "Calculate closures now",
                "Force closure calculation for selected objects",
                "Utility",
                Bindings.isNotEmpty(getSelectionModel().getSelectedItems())
                        .and(Bindings.createBooleanBinding(() -> getSelectionModel().selectedItemProperty().get() != null &&
                                                                   getSelectionModel().selectedItemProperty().get().getValue() != null &&
                                                                   getSelectionModel().selectedItemProperty().get().getValue() instanceof ListGroupingNode,
                                                           getSelectionModel().selectedItemProperty()))
                        .and(Bindings.createBooleanBinding(() -> getSelectionModel().getSelectedItems()
                                                                                    .stream()
                                                                                    .map(TreeItem::getValue)
                                                                                    .anyMatch(node -> !node.isClosureSizeCalculated()),
                                                           getSelectionModel().selectedItemProperty())),
                null,
                () -> {
                    getSelectionModel().getSelectedItems()
                                       .stream()
                                       .map(TreeItem::getValue)
                                       .forEach(node -> node.fillClosureSizes(availableClassifierInfo.getFastHeapSupplier().get(),
                                                                              true,
                                                                              true,
                                                                              true,
                                                                              true));
                    return Unit.INSTANCE;
                });

        ActionTabAction clearSelectionModelAction = new ActionTabAction(
                "Clear selection",
                "Unselect all selected rows",
                "Utility",
                Bindings.isNotEmpty(getSelectionModel().getSelectedItems()),
                null,
                () -> {
                    getSelectionModel().clearSelection();
                    return Unit.INSTANCE;
                });

        contextMenu.getItems()
                   .addAll(actionTabActionToMenuItem(inspectObjectGroupAction),
                           actionTabActionToMenuItem(localClassifyAction),
                           actionTabActionToMenuItem(localPointsToAction),
                           actionTabActionToMenuItem(localPointedFromAction),
                           actionTabActionToMenuItem(visualizeAction),
                           actionTabActionToMenuItem(visualizePathToMostInterestingRootsAction),
                           actionTabActionToMenuItem(calculateClosuresNowAction),
                           actionTabActionToMenuItem(clearSelectionModelAction));
        actions.add(inspectObjectGroupAction);
        actions.add(calculateClosuresNowAction);
        actions.add(localClassifyAction);
        actions.add(localPointsToAction);
        actions.add(visualizeAction);
        actions.add(visualizePathToMostInterestingRootsAction);
        actions.add(localPointedFromAction);
        actions.add(clearSelectionModelAction);
    }

    private MenuItem actionTabActionToMenuItem(ActionTabAction ata) {
        MenuItem menuItem = new MenuItem(ata.getName());
        menuItem.visibleProperty().bind(ata.getEnabled());
        menuItem.setOnAction(click -> ata.getFunction().invoke());
        return menuItem;
    }

    private BooleanBinding validSelectionBinding() {
        return Bindings.isNotEmpty(getSelectionModel().getSelectedItems())
                       .and(Bindings.createBooleanBinding(() -> getSelectionModel().selectedItemProperty().get() != null &&
                                                                  getSelectionModel().selectedItemProperty().get().getValue() != null &&
                                                                  getSelectionModel().selectedItemProperty().get().getValue() instanceof ListGroupingNode,
                                                          getSelectionModel().selectedItemProperty()));
    }

    private void initColumns() {
        nameColumn.setCellValueFactory(param -> {
            Object key = param.getValue().getValue().getKey();
            if (key instanceof Description) {
                return new ReadOnlyObjectWrapper<>((Description) key);
            }
            return new ReadOnlyObjectWrapper<>(new Description(key.toString()));
        });
        nameColumn.setCellFactory(param -> new KeyTreeCell<>());

        objectsColumn.setCellValueFactory(
                param ->
                        getSampledValueWithReferenceCVF(
                                new SimpleLongProperty(param.getValue().getValue().getObjectCount()),
                                new SimpleLongProperty(getParent(param.getValue().getValue()).getObjectCount()),
                                param.getValue().getValue().isCalculatedOnSampling(),
                                new SimpleLongProperty(param.getValue().getValue().getNonSampledObjectCount())));

        shallowSizeColumn.setCellFactory(param -> new SampledValueWithReferenceTreeCell<>(true, false, value -> toShortMemoryUsageString(value.longValue())));
        shallowSizeColumn.setCellValueFactory(
                param ->
                        getSampledValueWithReferenceCVF(
                                new SimpleLongProperty(param.getValue().getValue().getByteCount(availableClassifierInfo.getFastHeapSupplier().get())),
                                new SimpleLongProperty(getParent(param.getValue().getValue()).getByteCount(availableClassifierInfo.getFastHeapSupplier().get())),
                                param.getValue().getValue().isCalculatedOnSampling(),
                                new SimpleLongProperty(param.getValue().getValue().getNonSampledByteCount(availableClassifierInfo.getFastHeapSupplier().get()))));

        transitiveClosureSizeColumn.setCellFactory(param -> new ValueWithReferenceTreeCell<>(false, vwr -> toShortMemoryUsageString((long) vwr.getValue())));
        transitiveClosureSizeColumn.setCellValueFactory(param -> {
            //ApplicationStatistics.getInstance().inc("closureSizeCountColumn CellValueFactory");

            //ApplicationStatistics.Measurement minner = ApplicationStatistics.getInstance().createMeasurement("cellvaluefactory.closuresize.inner");
            if (param.getValue().getValue().getAndSetIsClosuresBeingCalculated(true) && !param.getValue().getValue().isClosureSizeCalculated()) {
                ParallelizationUtil.submitFuture("UI-triggered Transitive Closure Size Calculation for " + param.getValue().getValue().getFullKeyAsString(), () -> {
                    //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("cellvaluefactory.closuresize.future");
                    //param.getValue().getValue().fillClosureSizes();
                    param.getValue().getValue().getAndSetIsClosuresBeingCalculated(false);
                    //m.end();
                }, ClientInfo.meterRegistry);
            }
            //minner.end();
            return getValueWithReferenceCVF(param.getValue().getValue().transitiveClosureSizeProperty(),
                                            getParent(param.getValue().getValue()).transitiveClosureSizeProperty());
        });

        retainedSizeColumn.setCellFactory(param -> new ValueWithReferenceTreeCell<>(false, vwr -> toShortMemoryUsageString((long) vwr.getValue())));
        retainedSizeColumn.setCellValueFactory(param -> {
            //ApplicationStatistics.getInstance().inc("gcSizeCountColumn CellValueFactory");

            //ApplicationStatistics.Measurement minner = ApplicationStatistics.getInstance().createMeasurement("cellvaluefactory.gcSizeCount.inner");
            if (param.getValue().getValue().getAndSetIsClosuresBeingCalculated(true) && !param.getValue().getValue().isClosureSizeCalculated()) {
                ParallelizationUtil.submitFuture("UI-triggered GC Closure Size Calculation for " + param.getValue().getValue().getFullKeyAsString(), () -> {
                    //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("cellvaluefactory.gcSizeCount.future");
                    //param.getValue().getValue().fillClosureSizes();
                    param.getValue().getValue().getAndSetIsClosuresBeingCalculated(false);
                    //m.end();
                }, ClientInfo.meterRegistry);
            }
            //minner.end();
            return getValueWithReferenceCVF(param.getValue().getValue().retainedSizeProperty(), getParent(param.getValue().getValue()).retainedSizeProperty());
        });

        dataStructureSizeColumn.setCellFactory(param -> new ValueWithReferenceTreeCell<>(false, vwr -> toShortMemoryUsageString((long) vwr.getValue())));
        dataStructureSizeColumn.setCellValueFactory(param -> {
            //ApplicationStatistics.getInstance().inc("dataStructureSizeCountColumn CellValueFactory");

            //ApplicationStatistics.Measurement minner = ApplicationStatistics.getInstance().createMeasurement("cellvaluefactory.dataStructureSizeCount.inner");
            if (param.getValue().getValue().getAndSetIsClosuresBeingCalculated(true) && !param.getValue().getValue().isClosureSizeCalculated()) {
                ParallelizationUtil.submitFuture("UI-triggered Data Structure Closure Size Calculation for " + param.getValue().getValue().getFullKeyAsString(), () -> {
                    //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("cellvaluefactory.dataStructureSizeCount.future");
                    //param.getValue().getValue().fillClosureSizes();
                    param.getValue().getValue().getAndSetIsClosuresBeingCalculated(false);
                    //m.end();
                }, ClientInfo.meterRegistry);
            }
            //minner.end();
            return getValueWithReferenceCVF(param.getValue().getValue().dataStructureSizeProperty(),
                                            getParent(param.getValue().getValue()).dataStructureSizeProperty());
        });

        deepDataStructureSizeColumn.setCellFactory(param -> new ValueWithReferenceTreeCell<>(false, vwr -> toShortMemoryUsageString((long) vwr.getValue())));
        deepDataStructureSizeColumn.setCellValueFactory(param -> {
            //ApplicationStatistics.getInstance().inc("deepDataStructureSizeCountColumn CellValueFactory");

            //ApplicationStatistics.Measurement minner = ApplicationStatistics.getInstance().createMeasurement("cellvaluefactory.deepDataStructureSizeCount.inner");
            if (param.getValue().getValue().getAndSetIsClosuresBeingCalculated(true) && !param.getValue().getValue().isClosureSizeCalculated()) {
                ParallelizationUtil.submitFuture("UI-triggered Deep Data Structure Closure Size Calculation for " + param.getValue().getValue().getFullKeyAsString(), () -> {
                    //ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("cellvaluefactory.deepDataStructureSizeCount.future");
                    //param.getValue().getValue().fillClosureSizes();
                    param.getValue().getValue().getAndSetIsClosuresBeingCalculated(false);
                    //m.end();
                }, ClientInfo.meterRegistry);
            }
            //minner.end();
            return getValueWithReferenceCVF(param.getValue().getValue().deepDataStructureSizeProperty(),
                                            getParent(param.getValue().getValue()).deepDataStructureSizeProperty());
        });

        getColumns().add(nameColumn);
        getColumns().add(objectsColumn);
        getColumns().add(shallowSizeColumn);
        getColumns().add(transitiveClosureSizeColumn);
        getColumns().add(retainedSizeColumn);
        getColumns().add(dataStructureSizeColumn);
        getColumns().add(deepDataStructureSizeColumn);

        // Define column widths
        // aaaand subtract scroll bar width, otherwise binding properties to width does not work at all
        nameColumn.prefWidthProperty().bind(widthProperty().multiply(9).divide(15).subtract(20)); // = 9/15
        objectsColumn.prefWidthProperty().bind(widthProperty().divide(7.5)); // 1/7.5 = 2/15
        shallowSizeColumn.prefWidthProperty().bind(widthProperty().divide(7.5)); // 1/7.5 = 2/15
        transitiveClosureSizeColumn.prefWidthProperty().bind(widthProperty().divide(7.5)); // 1/7.5 = 2/15
        retainedSizeColumn.prefWidthProperty().bind(widthProperty().divide(7.5)); // 1/7.5 = 2/15
        dataStructureSizeColumn.prefWidthProperty().bind(widthProperty().divide(7.5)); // 1/7.5 = 2/15
        deepDataStructureSizeColumn.prefWidthProperty().bind(widthProperty().divide(7.5)); // 1/7.5 = 2/15

        // must explicitly reset sort types such that sort type change listener triggers on first sort action
        nameColumn.setSortType(null);
        objectsColumn.setSortType(null);
        shallowSizeColumn.setSortType(null);
        transitiveClosureSizeColumn.setSortType(null);
        retainedSizeColumn.setSortType(null);
        dataStructureSizeColumn.setSortType(null);
        deepDataStructureSizeColumn.setSortType(null);

        transitiveClosureSizeColumn.sortTypeProperty().addListener(closureSortListener);
        retainedSizeColumn.sortTypeProperty().addListener(closureSortListener);
        dataStructureSizeColumn.sortTypeProperty().addListener(closureSortListener);
        deepDataStructureSizeColumn.sortTypeProperty().addListener(closureSortListener);

        // not shown by default
        transitiveClosureSizeColumn.setVisible(false);
        dataStructureSizeColumn.setVisible(false);
        deepDataStructureSizeColumn.setVisible(false);
    }

    public TreeItem<GroupingNode> getTreeItem(GroupingNode node) {
        List<TreeItem<GroupingNode>> candidates = filter(item -> item.getValue() == node);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    public List<TreeItem<GroupingNode>> getRecursiveParentTreeItems(GroupingNode childItemNode) {
        return getRecursiveParentTreeItems(getTreeItem(childItemNode));
    }

    public List<TreeItem<GroupingNode>> getRecursiveParentTreeItems(TreeItem<GroupingNode> childItem) {
        List<TreeItem<GroupingNode>> parents = new ArrayList<>();

        if (childItem != null) {
            for (TreeItem<GroupingNode> item = childItem.getParent(); item != null; item = item.getParent()) {
                parents.add(item);
            }
        }

        return parents;
    }

    private void inspectObjectGroup(List<TreeItem<GroupingNode>> items) {
        List<TreeItem<GroupingNode>> stableItemList = new ArrayList<>(items);   // copy list! given list might be modified (usually an observable list of selections...)
        ObjectGroupInfoTab objectGroupInfoTab = new ObjectGroupInfoTab();
        objectGroupInfoTab.init(appInfo,
                                items.stream().map(TreeItem::getValue).toArray(GroupingNode[]::new),
                                selectLocalClassifiers,
                                availableClassifierInfo,
                                addTab,
                                switchToTab,
                                (node) -> {
                                    TreeItem<GroupingNode> targetItem = stableItemList.stream().filter(item -> item.getValue() == node).findFirst().orElse(null);
                                    if (targetItem == null) {
                                        throw new IllegalArgumentException("Jumping back is only allowed for the originally given items!");
                                    }
                                    showAndSelect(targetItem);
                                },
                                true);
    }

    private void handleLocalClassification(ClassifierChain classifierChain) {
        // classify on same subtreelevel
        handleLocalClassification(classifierChain, -1);
    }

    private void handleLocalClassification(ClassifierChain classifierChain, int targetSubTreeLevel) {
        TreeItem<GroupingNode> selectedItem = getSelectionModel().getSelectedItem();

        if (!(selectedItem.getValue() instanceof ListGroupingNode)) {
            LOGGER.warning("Local classification only works for ListGroupingNodes!");
            return;
        }

        ListGroupingNode node = (ListGroupingNode) getSelectionModel().getSelectedItem().getValue();

        if (selectLocalClassifiers != null && classifierChain != null && classifierChain.getList().size() > 0 && classifierChain.get(0) != null) {
            // show classifier selection
            boolean dialogResult = selectLocalClassifiers.apply(classifierChain.get(0));
            boolean needsAtLeastOneClassifier = classifierChain.get(0) instanceof LocalClassifier;
            if (dialogResult && !(needsAtLeastOneClassifier && ((LocalClassifier) classifierChain.get(0)).getClassifiers().getList().isEmpty())) {
                // extract selected classifiers from LocalClassifier (only used for easy selection dialog) if necessary
                ClassifierChain finalClassifierChain = classifierChain.get(0) instanceof LocalClassifier ?
                                                       ((LocalClassifier) classifierChain.get(0)).getClassifiers() :
                                                       classifierChain;

                Task<Void> lct = localClassificationTask(selectedItem, node, finalClassifierChain, targetSubTreeLevel);

                ClientInfo.operationManager.addNewOperation(lct);
                ParallelizationUtil.submitTask(lct);
            }
        }
    }

    public Task<Void> localClassificationTask(TreeItem<GroupingNode> selectedItem,
                                              ListGroupingNode node,
                                              ClassifierChain classifierChain,
                                              int targetSubTreeLevel) {
        return localClassificationTask(selectedItem, node, classifierChain, targetSubTreeLevel, () -> { });
    }

    public Task<Void> localClassificationTask(TreeItem<GroupingNode> selectedItem,
                                              ListGroupingNode node,
                                              ClassifierChain classifierChain,
                                              int targetSubTreeLevel,
                                              Runnable completionCallback) {
        return new AntTask<Void>() {
            private Task<?> initClosureTask;

            @Override
            protected Void backgroundWork() throws Exception {
                updateTitle("Local classification");
                updateMessage(String.format("%,d objects in Node %s with:/n%s",
                                            node.getObjectCount(),
                                            node.getFullKeyAsString(),
                                            (classifierChain.get(0) instanceof LocalClassifier
                                             ? ((LocalClassifier) classifierChain.get(0)).getClassifiers().toString()
                                             : classifierChain.toString())));

                Counter objectsProcessed = new Counter();
                ObjectStream.IterationListener iterationListener = (oc) -> {
                    objectsProcessed.add(oc);
                    updateProgress(objectsProcessed.get(), node.getObjectCount());
                };

                if (node.getSubTreeLevel() == 0) {
                    // regular node
                    node.locallyClassify(availableClassifierInfo.getFastHeapSupplier().get(), classifierChain, iterationListener, getCancelProperty());
                } else {
                    // transformer node
                    if (targetSubTreeLevel >= 0) {
                        // valid target stl given
                        node.locallyClassifyTransformerOnLowerSubtreeLevel(availableClassifierInfo.getFastHeapSupplier().get(), targetSubTreeLevel, classifierChain,
                                                                           iterationListener, getCancelProperty());
                    } else {
                        node.locallyClassifyTransformerOnSameSubTreeLevel(availableClassifierInfo.getFastHeapSupplier().get(), classifierChain, iterationListener,
                                                                          getCancelProperty());
                    }
                }

                return null;
            }

            @Override
            protected void finished() {
                // display result of local classification
                TreeItem<GroupingNode> newItem;
                // if the classified item was selected we'll have to reselect the new item later
                boolean reselect = getSelectionModel().isSelected(getRow(selectedItem));
                // unselect the classified item because otherwise removing it from the treetable causes weird selection behaviour
                getSelectionModel().clearSelection(getRow(selectedItem));

                if (getRoot() == selectedItem) {
                    // the overall node is reclassified
                    setRoot(new ListClassificationTree(node,
                                                       classificationTree.getFilters(),
                                                       classifierChain),
                            true,
                            true,
                            null);
                    newItem = getRoot();
                    setSelectedClassifiers.accept(classifierChain);
                } else {
                    // some descendant of overall is locally classified
                    newItem = new AutomatedTreeItem<>(node,
                                                      titleFunction,
                                                      dataFunction,
                                                      childFunction,
                                                      subTreeLevelFunction,
                                                      iconFunction,
                                                      expansionListener,
                                                      null);
                    TreeItem<GroupingNode> selectionParent = selectedItem.getParent();
                    int idx = selectionParent.getChildren().indexOf(selectedItem);
                    selectionParent.getChildren().remove(idx);
                    selectionParent.getChildren().add(idx, newItem);
                }

                if (reselect) {
                    getSelectionModel().select(getRow(newItem));
                }
                // expand the new node to show the results of the classification
                newItem.setExpanded(true);

                // calculate closures of new tree items
                initClosureTask =
                        new ListClassificationTree(node, new Filter[0], classifierChain).initClosureTask(availableClassifierInfo.getFastHeapSupplier().get(),
                                                                                                         true,
                                                                                                         true,
                                                                                                         false,
                                                                                                         false,
                                                                                                         completionCallback);
                ClientInfo.operationManager.addNewOperation(initClosureTask);
                ParallelizationUtil.submitTask(initClosureTask);
            }
        };
    }

    public ObjectBinding<SampledValueWithReference> getSampledValueWithReferenceCVF(NumberExpression source,
                                                                                    NumberExpression parent,
                                                                                    boolean isSampled,
                                                                                    NumberExpression withoutSampling) {
        return Bindings.when(source.greaterThanOrEqualTo(0))
                       .then(Bindings.when(parent.greaterThanOrEqualTo(0))
                                     .then(getSampledValueWithReference(parent.doubleValue(),
                                                                        source.doubleValue(),
                                                                        isSampled,
                                                                        withoutSampling == null ? 0 : withoutSampling.doubleValue()))
                                     .otherwise(new SampledValueWithReference(-1.0, 1.0, false, -1.0)))
                       .otherwise(new SampledValueWithReference(-1.0, 1.0, false, -1.0));
    }

    public ObjectBinding<ValueWithReference> getValueWithReferenceCVF(NumberExpression source, NumberExpression parent) {
        return Bindings.when(source.greaterThanOrEqualTo(0))
                       .then(Bindings.when(parent.greaterThanOrEqualTo(0))
                                     .then(getValueWithReference(parent.doubleValue(), source.doubleValue()))
                                     .otherwise(new ValueWithReference(-1.0, 1.0)))
                       .otherwise(new ValueWithReference(-1.0, 1.0));
    }

    public SampledValueWithReference getSampledValueWithReference(double total, double x, boolean isSampled, double withoutSampling) {
        if (total == 0 || x == 0) {
            return new SampledValueWithReference(0.0, 1.0, false, 0.0);
        }
        return new SampledValueWithReference(x, total, isSampled, withoutSampling);
    }

    public ValueWithReference getValueWithReference(double total, double x) {
        if (total == 0 || x == 0) {
            return new ValueWithReference(0.0, 1.0);
        }
        return new ValueWithReference(x, total);
    }

    public void visualizeObjectGroup(ObservableList<TreeItem<GroupingNode>> items) {
        List<TreeItem<GroupingNode>> stableItemList = new ArrayList<>(items);   // copy list! given list might be modified (usually an observable list of selections...)
        IndexCollection objectGroupIndexCollection = new IndexCollection();
        objectGroupIndexCollection.unionWith(stableItemList.stream().map(node -> node.getValue().getData(true)).toArray(IndexCollection[]::new));
        int[] objectGroupArray = new int[objectGroupIndexCollection.getObjectCount()];
        for (int i = 0; i < objectGroupArray.length; i++) {
            objectGroupArray[i] = objectGroupIndexCollection.get(i);
        }

        HeapGraphVisualizationTab visualizationTab = new HeapGraphVisualizationTab();
        visualizationTab.init(appInfo, availableClassifierInfo.getFastHeapSupplier().get(), objectGroupArray, "");
        addTab.accept(visualizationTab);
        switchToTab.accept(visualizationTab);
    }

    public void visualizePathToMostInterestingRoots(ObservableList<TreeItem<GroupingNode>> items) {
        List<TreeItem<GroupingNode>> stableItemList = new ArrayList<>(items);   // copy list! given list might be modified (usually an observable list of selections...)
        IndexCollection objectGroupIndexCollection = new IndexCollection();
        objectGroupIndexCollection.unionWith(stableItemList.stream().map(node -> node.getValue().getData(true)).toArray(IndexCollection[]::new));
        int[] objectGroupArray = new int[objectGroupIndexCollection.getObjectCount()];
        for (int i = 0; i < objectGroupArray.length; i++) {
            objectGroupArray[i] = objectGroupIndexCollection.get(i);
        }

        HeapGraphVisualizationTab visualizationTab = new HeapGraphVisualizationTab();
        // TODO
        visualizationTab.init(appInfo, availableClassifierInfo.getFastHeapSupplier().get(), objectGroupArray, PATH_TO_MOST_INTERESTING_ROOTS_TYPED);
        addTab.accept(visualizationTab);
        switchToTab.accept(visualizationTab);
    }

    protected GroupingNode getParent(GroupingNode heapObjectGroupingNode) {
        int subTreeLevel = heapObjectGroupingNode.getSubTreeLevel();
        GroupingNode lastMatching = null;
        while (heapObjectGroupingNode != null) {
            if (heapObjectGroupingNode.getSubTreeLevel() == subTreeLevel) {
                lastMatching = heapObjectGroupingNode;
            }
            heapObjectGroupingNode = heapObjectGroupingNode.getParent();
        }
        return lastMatching;
    }

    public void setRoot(ClassificationTree grouping, boolean preserveSortOrder, boolean preserveExpansions, Function1<GroupingNode, Boolean> childFilter) {
        this.classificationTree = grouping;
        // remember sorting
        List<TreeTableColumn<GroupingNode, ?>> rememberedSortOrder = new ArrayList<>();
        rememberedSortOrder.addAll(getSortOrder());
        TreeSortMode rememberedSortMode = getSortMode();
        List<TreeTableColumn.SortType> rememberedSortTypes = new ArrayList<>();
        rememberedSortOrder.forEach(col -> rememberedSortTypes.add(col.getSortType()));

        rememberExpansion();
        root = new AutomatedTreeItem<GroupingNode, GroupingNode>(grouping.getRoot(),
                                                                 titleFunction,
                                                                 dataFunction,
                                                                 childFunction,
                                                                 subTreeLevelFunction,
                                                                 iconFunction,
                                                                 expansionListener,
                                                                 childFilter);
        // filterChanged(node -> node.getByteCount() > 10000);
        setRoot(root);
        setShowRoot(true);
        getRoot().setExpanded(true);

        if (preserveExpansions) {
            restoreExpansion();
        }
        if (preserveSortOrder) {
            // restore sorting
            getSortOrder().clear();
            setSortMode(rememberedSortMode);
            for (int i = 0; i < rememberedSortOrder.size(); i++) {
                rememberedSortOrder.get(i).setSortType(rememberedSortTypes.get(i));
            }
            getSortOrder().addAll(rememberedSortOrder);
            sort();
        } else {
            // standard sorting
            applyDefaultSortingSettings();
            sort();
        }


        /*
        if (tableRefresher != null) {
            tableRefresher.stop();
            tableRefresher = null;
        }
        tableRefresher = new Timeline(
                new KeyFrame(Duration.seconds(0)),
                new KeyFrame(Duration.seconds(10), actionEvent -> {
                    if (grouping.closuresInitialized()) {
                        System.out.println("Stop refreshing");
                        ClassificationTreeTableView.this.tableRefresher.stop();
                        ClassificationTreeTableView.this.tableRefresher = null;
                    } else {
                        System.out.println("Refresh");
                        ClassificationTreeTableView.this.refresh();
                    }
                }));
        tableRefresher.setCycleCount(Animation.INDEFINITE);
        tableRefresher.play();
        */
    }

    @Override
    public void applyDefaultSortingSettings() {
        getSortOrder().clear();
        setSortMode(TreeSortMode.ALL_DESCENDANTS);
        objectsColumn.setSortType(TreeTableColumn.SortType.DESCENDING);
        getSortOrder().add(objectsColumn);
    }

    @Override
    protected TreeTableRow<GroupingNode> createTreeTableRow() {
        TreeTableRow<GroupingNode> row = super.createTreeTableRow();
        // context menu
        row.setContextMenu(contextMenu);
        return row;
    }

    public ClassificationTree getClassificationTree() {
        return classificationTree;
    }

    public TreeTableColumn<GroupingNode, ValueWithReference> getDataStructureSizeColumn() {
        return dataStructureSizeColumn;
    }

    public TreeTableColumn<GroupingNode, SampledValueWithReference> getShallowSizeColumn() {
        return shallowSizeColumn;
    }

    public TreeTableColumn<GroupingNode, ValueWithReference> getDeepDataStructureSizeColumn() {
        return deepDataStructureSizeColumn;
    }

    public void sortByRetainedSize() {
        // sort by retained size
        getSortOrder().clear();
        getSortOrder().add(retainedSizeColumn);
        getSortOrder().get(0).setSortType(TreeTableColumn.SortType.DESCENDING);
        getSortOrder().get(0).setSortable(true);
        sort();
    }
}