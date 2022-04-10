
package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.tab.objectgroupinfo;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.nodes.GroupingNode;
import at.jku.anttracks.classification.nodes.IndexCollection;
import at.jku.anttracks.classification.trees.ListClassificationTree;
import at.jku.anttracks.gui.classification.classifier.AllocationSiteClassifier;
import at.jku.anttracks.gui.classification.classifier.CallSitesClassifier;
import at.jku.anttracks.gui.classification.classifier.DirectGCRootsClassifier;
import at.jku.anttracks.gui.classification.classifier.TypeClassifier;
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab;
import at.jku.anttracks.gui.frame.main.tab.heapstate.HeapStateTab;
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.classificationtreetableview.ClassificationTreeTableView;
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.heapmetricstable.HeapMetric;
import at.jku.anttracks.gui.graph.Graph;
import at.jku.anttracks.gui.graph.GraphVis;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.IAppInfo;
import at.jku.anttracks.gui.model.IAvailableClassifierInfo;
import at.jku.anttracks.gui.utils.*;
import at.jku.anttracks.heap.Closures;
import at.jku.anttracks.heap.IndexBasedHeap;
import at.jku.anttracks.heap.datastructures.dsl.DSLDataStructure;
import at.jku.anttracks.heap.roots.RootPtr;
import at.jku.anttracks.util.Counter;
import at.jku.anttracks.util.Dot;
import at.jku.anttracks.util.ParallelizationUtil;
import at.jku.anttracks.util.Tuple;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class ObjectGroupInfoTab extends ApplicationBaseTab implements HeapStateTab.HeapStateSubTab {

    @FXML
    private ScrollPane objectGroupInfoTabSP;
    @FXML
    private VBox inspectedObjectGroupsVB;
    @FXML
    private TableView<Tuple<String, Number>> metricsTV;
    @FXML
    private TitledPane transitiveObjectsTP;
    @FXML
    private ClassificationTreeTableView transitiveObjectsTTV;
    @FXML
    private TitledPane retainedObjectsTP;
    @FXML
    private ClassificationTreeTableView retainedObjectsTTV;
    @FXML
    private TitledPane directRootsTP;
    @FXML
    private ClassificationTreeTableView directRootsTTV;
    @FXML
    private TitledPane indirectRootsTP;
    @FXML
    private ClassificationTreeTableView indirectRootsTTV;
    @FXML
    private TitledPane dataStructureHeadsTP;
    @FXML
    private ClassificationTreeTableView dataStructureHeadsTTV;

    @FXML
    private Button visPathsToAllRootsBT;
    // root path visualization options
    @FXML
    private CheckBox visPathsToAllRootsInvertEdgesCB;
    @FXML
    private CheckBox visPathsToAllRootsAggregateObjectsCB;
    @FXML
    private CheckBox visPathsToAllRootsAggregateDataStructuresCB;
    @FXML
    private CheckBox visPathsToAllRootsObjRootInfoCB;
    @FXML
    private CheckBox visPathToAllRootsObjIdxCB;
    @FXML
    private CheckBox visPathToAllRootsObjAddrCB;
    @FXML
    private CheckBox visPathToAllRootsObjTypeCB;
    @FXML
    private CheckBox visPathToAllRootsObjAllocSiteCB;
    @FXML
    private CheckBox visPathToAllRootsObjSizeCB;
    @FXML
    private CheckBox visPathToAllRootsObjDeepSizeCB;
    @FXML
    private CheckBox visPathToAllRootsObjRetainedSizeCB;
    @FXML
    private CheckBox visPathToAllRootsDSObjCountCB;
    @FXML
    private CheckBox visPathsToAllRootsStoreCB;
    @FXML
    private TextField visPathsToAllRootsFilenameTF;

    private IndexBasedHeap fastHeap;
    private GroupingNode[] nodes;
    private int[] objectGroupArray;
    private BitSet directRootObjectIndices;
    private BitSet indirectRootObjectIndices;
    private BitSet dataStructureHeadIndices;

    private IAvailableClassifierInfo availableClassifierInfo;
    private Consumer<GroupingNode> showOriginalNode;
    private Function<Classifier<?>, Boolean> performClassifierSelection;
    private Consumer<ApplicationBaseTab> addTab;
    private Consumer<ApplicationBaseTab> switchToTab;

    public ObjectGroupInfoTab() {
        FXMLUtil.load(this, ObjectGroupInfoTab.class);
    }

    public void init(AppInfo appInfo,
                     GroupingNode[] nodes,
                     Function<Classifier<?>, Boolean> performClassifierSelection,
                     IAvailableClassifierInfo availableClassifierInfo,
                     Consumer<ApplicationBaseTab> addTab,
                     Consumer<ApplicationBaseTab> switchToTab,
                     Consumer<GroupingNode> showOriginalNode,
                     boolean showTabImmediately) {
        if (nodes.length <= 0) {
            throw new IllegalArgumentException("Given node array must contain at least one element!");
        }

        this.nodes = nodes;
        fastHeap = availableClassifierInfo.getFastHeapSupplier().get();
        this.availableClassifierInfo = availableClassifierInfo;
        this.showOriginalNode = showOriginalNode;
        this.performClassifierSelection = performClassifierSelection;
        this.addTab = addTab;
        this.switchToTab = switchToTab;
        directRootObjectIndices = new BitSet();
        indirectRootObjectIndices = new BitSet();
        dataStructureHeadIndices = new BitSet();

        IndexCollection objectGroupIndexCollection = new IndexCollection();
        objectGroupIndexCollection.unionWith(Arrays.stream(nodes).map(node -> node.getData(true)).toArray(IndexCollection[]::new));
        objectGroupArray = new int[objectGroupIndexCollection.getObjectCount()];
        for (int i = 0; i < objectGroupArray.length; i++) {
            objectGroupArray[i] = objectGroupIndexCollection.get(i);
        }

        // tab title
        StringBuilder subTitle = new StringBuilder(nodes[0].getKey().toString());
        if (subTitle.length() > 25) {
            subTitle.delete(25, subTitle.length());
            subTitle.append("...");
        }
        if (nodes.length > 1) {
            subTitle.append(" and ").append(nodes.length - 1).append(" nodes more");
        }

        subTitle.insert(0, String.format("%,d objects in node ", objectGroupArray.length));

        super.init(appInfo,
                   new SimpleStringProperty("Object group"),
                   new SimpleStringProperty(subTitle.toString()),
                   new SimpleStringProperty(""),
                   Consts.GROUP_ICON,
                   Arrays.asList(),
                   true);

        Task<Void> initTask = new InitTask(showTabImmediately);
        getTasks().add(initTask);
        ParallelizationUtil.submitTask(initTask);
    }

    private void visualizePathsToAllRoots() {
        Stage stage = new Stage();
        BorderPane pane = new BorderPane();
        GraphVis<Integer> graphVis = new GraphVis<>();
        ScrollPane scroller = new ScrollPane(graphVis);
        Scene scene = new Scene(pane);

        pane.setCenter(scroller);
        graphVis.init(new Graph<>(
                // root node
                -1,

                // children
                idx -> {
                    List<Integer> childObjects;

                    if (idx == -1) {
                        // root node has initial object group as children
                        childObjects = Arrays.stream(objectGroupArray).boxed().collect(Collectors.toList());

                    } else {
                        // other object's children are all their successor objects in their root paths
                        childObjects = fastHeap.traceAllRootsDFS(idx, Integer.MAX_VALUE, false).stream()
                                               .filter(rootInfo -> rootInfo.path.length > 1)
                                               .map(rootInfo -> rootInfo.path[1])
                                               .distinct()
                                               .collect(Collectors.toList());
                    }

                    if (visPathsToAllRootsAggregateObjectsCB.isSelected()) {
                        // map child objects to their data structures (i.e. the head objects)
                        childObjects = childObjects.stream()
                                                   .map(objIndex -> {
                                                       Set<DSLDataStructure> dataStructures = fastHeap.getDataStructures(objIndex,
                                                                                                                         visPathsToAllRootsAggregateDataStructuresCB
                                                                                                                              .isSelected(),
                                                                                                                         visPathsToAllRootsAggregateDataStructuresCB
                                                                                                                              .isSelected());
                                                       if (dataStructures != null) {
                                                           return dataStructures.stream().mapToInt(DSLDataStructure::getHeadIdx).toArray();
                                                       }

                                                       // objIndex is not part of a data structure => don't transform it
                                                       return new int[]{objIndex};
                                                   })
                                                   .flatMap(headIndices -> Arrays.stream(headIndices).boxed())
                                                   .distinct()
                                                   .collect(Collectors.toList());
                    }

                    return childObjects;
                },

                // node title
                idx -> idx == -1 ? "Selected Objects" : fastHeap.getType(idx).getExternalName(true, true),

                // node color
                idx -> idx == -1 ? Color.YELLOW : fastHeap.getRoot(idx) == null ? Color.WHITE : Color.RED,

                // tooltip
                idx -> idx == -1 ? "Click me to see the selected objects" : fastHeap.getType(idx).getExternalName(false, true),

                // node icon
                idx -> idx == -1 ? null : ImageUtil.getResourceImagePack("Type", "type.png").getAsNewNode(),

                // css
                idx -> null,

                // additional text
                idx -> idx == -1 ? null : getPathToRootNodeAdditionalText(idx)));

        stage.setTitle("Paths to all roots visualization");
        stage.setScene(scene);
        stage.show();
        stage.setMaximized(true);

        // old visualization task
        Task<Void> visTask = new AntTask<Void>() {

            Process xdot;

            @Override
            protected Void backgroundWork() throws Exception {
                updateTitle("Visualize paths to all roots");
                updateMessage("Tracing paths...");

                Path dotFile = Paths.get(visPathsToAllRootsFilenameTF.getText());
                Dot.DotBuilder dotBuilder = Dot.builder(dotFile);

                Arrays.stream(objectGroupArray).forEach(objIndex -> {
                    String[] initialNodeStrings = getPathToRootNodeStrings(objIndex);
                    Arrays.stream(initialNodeStrings).forEach(nodeString -> dotBuilder.addNode(nodeString, new String[]{"style=filled", "fillcolor=yellow"}));
                });

                List<RootPtr.RootInfo> pathsToAllRoots = fastHeap.traceAllRootsDFS(objectGroupArray, Integer.MAX_VALUE, false);

                updateMessage("Creating graph...");
                Counter progressCounter = new Counter();
                Set<Tuple<Integer, Integer>> closedSet = new HashSet<>();
                pathsToAllRoots.stream().map(rootInfo -> rootInfo.path).forEach(pathToRoot -> {
                    for (int i = 0; i < pathToRoot.length - 1; i++) {
                        int fromIdx = visPathsToAllRootsInvertEdgesCB.isSelected() ? pathToRoot[i + 1] : pathToRoot[i];
                        int toIdx = visPathsToAllRootsInvertEdgesCB.isSelected() ? pathToRoot[i] : pathToRoot[i + 1];

                        // don't process two index combinations twice! (paths to roots can be very overlapping)
                        if (closedSet.add(new Tuple<>(fromIdx, toIdx))) {
                            String[] fromNodes = getPathToRootNodeStrings(fromIdx);
                            String[] toNodes = getPathToRootNodeStrings(toIdx);

                            for (String fromNode : fromNodes) {
                                for (String toNode : toNodes) {
                                    dotBuilder.addEdge(fromNode, toNode);
                                }
                            }
                        }
                    }

                    progressCounter.inc();
                    updateProgress(progressCounter.get(), pathsToAllRoots.size());
                });

                try {
                    dotBuilder.build();
                    updateMessage("Opening visualization...");
                    xdot = Runtime.getRuntime().exec("xdot " + dotFile.toString());

                    // wait for process to finish
                    xdot.waitFor();

                    // delete graph file if no longer needed
                    if (!visPathsToAllRootsStoreCB.isSelected()) {
                        Files.deleteIfExists(dotFile);
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                return null;
            }

            @Override
            protected void finished() {

            }

            @Override
            protected void cancelled() {
                super.cancelled();
                if (xdot != null) {
                    // stop xdot process when vis task is cancelled
                    xdot.destroy();
                }
            }
        };

        //        tasks.add(visTask);
        //        ThreadUtil.startTask(visTask);
    }

    private String[] getPathToRootNodeStrings(int objIndex) {
        if (visPathsToAllRootsAggregateObjectsCB.isSelected()) {
            Set<DSLDataStructure> dataStructures = fastHeap.getDataStructures(objIndex,
                                                                              visPathsToAllRootsAggregateDataStructuresCB.isSelected(),
                                                                              visPathsToAllRootsAggregateDataStructuresCB.isSelected());

            if (dataStructures != null && dataStructures.size() > 0) {
                List<String> dsNodeStrings = new ArrayList<>();
                for (DSLDataStructure ds : dataStructures) {
                    dsNodeStrings.add(getPathToRootNodeString(ds));
                }

                return dsNodeStrings.toArray(new String[dsNodeStrings.size()]);

            } else {
                // retrieve single object nodes instead
                return new String[]{getPathToRootNodeString(objIndex)};
            }

        } else {
            // retrieve single object nodes instead
            return new String[]{getPathToRootNodeString(objIndex)};
        }
    }

    private String getPathToRootNodeAdditionalText(int objIndex) {
        StringBuilder additionalTextBuilder = new StringBuilder();

        if (visPathsToAllRootsObjRootInfoCB.isSelected()) {
            if (fastHeap.getRoot(objIndex) != null) {
                fastHeap.getRoot(objIndex).forEach(rootPtr -> {
                    additionalTextBuilder.append("ROOT PTR: ").append(rootPtr.toShortString());
                    additionalTextBuilder.append("\n");
                });

                additionalTextBuilder.append("\n");
            }
        }
        if (visPathToAllRootsObjIdxCB.isSelected()) {
            additionalTextBuilder.append("IDX: ").append(objIndex).append("\n\n");
        }
        if (visPathToAllRootsObjAddrCB.isSelected()) {
            additionalTextBuilder.append("ADDR: ").append(fastHeap.getAddress(objIndex)).append("\n\n");
        }
        if (visPathToAllRootsObjAllocSiteCB.isSelected()) {
            additionalTextBuilder.append("ALLOCATION SITE: ").append(fastHeap.getAllocationSite(objIndex).getCallSites()[0]).append("\n\n");
        }
        if (visPathToAllRootsObjSizeCB.isSelected()) {
            additionalTextBuilder.append("SHALLOW SIZE: ").append(fastHeap.getSize(objIndex)).append(" bytes").append("\n");
        }
        Closures closures = null;
        if (visPathToAllRootsObjDeepSizeCB.isSelected()) {
            closures = fastHeap.getClosures(true, true, false, false, objIndex);
            additionalTextBuilder.append("DEEP SIZE: ").append(closures.getTransitiveClosureByteCount()).append(" bytes").append("\n");
        }
        if (visPathToAllRootsObjRetainedSizeCB.isSelected()) {
            if (closures == null) {
                closures = fastHeap.getClosures(false, true, false, false, objIndex);
            }
            additionalTextBuilder.append("RETAINED SIZE: ").append(closures.getGCClosureByteCount()).append(" bytes").append("\n");
        }

        return additionalTextBuilder.toString();
    }

    private String getPathToRootNodeString(int objIndex) {
        StringBuilder nodeStringBuilder = new StringBuilder();

        if (fastHeap.getRoot(objIndex) != null) {
            nodeStringBuilder.append("ROOT OBJECT");
        } else {
            nodeStringBuilder.append("OBJECT");
        }

        if (visPathsToAllRootsObjRootInfoCB.isSelected()) {
            if (fastHeap.getRoot(objIndex) != null) {
                fastHeap.getRoot(objIndex).forEach(rootPtr -> {
                    nodeStringBuilder.append("\\n");
                    nodeStringBuilder.append("ROOT PTR: ").append(rootPtr.toShortString());
                });
            }
        }
        if (visPathToAllRootsObjIdxCB.isSelected()) {
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("IDX: ").append(objIndex);
        }
        if (visPathToAllRootsObjAddrCB.isSelected()) {
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("ADDR: ").append(fastHeap.getAddress(objIndex));
        }
        if (visPathToAllRootsObjTypeCB.isSelected()) {
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("TYPE: ").append(fastHeap.getType(objIndex).getExternalName(false, true));
        }
        if (visPathToAllRootsObjAllocSiteCB.isSelected()) {
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("ALLOCATION SITE: ").append(fastHeap.getAllocationSite(objIndex).getCallSites()[0]);
        }
        if (visPathToAllRootsObjSizeCB.isSelected()) {
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("SHALLOW SIZE: ").append(fastHeap.getSize(objIndex)).append(" bytes");
        }
        Closures closures = null;
        if (visPathToAllRootsObjDeepSizeCB.isSelected()) {
            closures = fastHeap.getClosures(true, true, false, false, objIndex);
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("DEEP SIZE: ").append(closures.getTransitiveClosureByteCount()).append(" bytes");
        }
        if (visPathToAllRootsObjRetainedSizeCB.isSelected()) {
            if (closures == null) {
                closures = fastHeap.getClosures(false, true, false, false, objIndex);
            }
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("RETAINED SIZE: ").append(closures.getGCClosureByteCount()).append(" bytes");
        }

        return nodeStringBuilder.toString();
    }

    private String getPathToRootNodeString(DSLDataStructure ds) {
        StringBuilder nodeStringBuilder = new StringBuilder();

        if (fastHeap.getRoot(ds.getHeadIdx()) != null) {
            nodeStringBuilder.append("ROOT DATA STRUCTURE");
        } else {
            nodeStringBuilder.append("DATA STRUCTURE");
        }

        if (visPathsToAllRootsObjRootInfoCB.isSelected()) {
            if (fastHeap.getRoot(ds.getHeadIdx()) != null) {
                fastHeap.getRoot(ds.getHeadIdx()).forEach(rootPtr -> {
                    nodeStringBuilder.append("\\n");
                    nodeStringBuilder.append("ROOT PTR: ").append(rootPtr.toShortString());
                });
            }
        }
        if (visPathToAllRootsObjIdxCB.isSelected()) {
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("HEAD IDX: ").append(ds.getHeadIdx());
        }
        if (visPathToAllRootsObjAddrCB.isSelected()) {
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("HEAD ADDR: ").append(fastHeap.getAddress(ds.getHeadIdx()));
        }
        if (visPathToAllRootsObjTypeCB.isSelected()) {
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("TYPE: ").append(fastHeap.getType(ds.getHeadIdx()).getExternalName(false, true));
        }
        if (visPathToAllRootsObjAllocSiteCB.isSelected()) {
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("ALLOCATION SITE: ").append(fastHeap.getAllocationSite(ds.getHeadIdx()).getCallSites()[0]);
        }
        if (visPathToAllRootsDSObjCountCB.isSelected()) {
            nodeStringBuilder.append("\\n");
            nodeStringBuilder.append("DS OBJECT COUNT: ").append(ds.getObjectCount());
        }
        // TODO if needed can be implemented through ds visitor (...expensive)
        //        if(visPathToAllRootsObjSizeCB.isSelected()) {
        //            nodeStringBuilder.append("\\n");
        //            nodeStringBuilder.append("SHALLOW SIZE: ").append(fastHeap.getSize(objIndex)).append(" bytes");
        //        }
        //        Closures closures = null;
        //        if(visPathToAllRootsObjDeepSizeCB.isSelected()) {
        //            closures = fastHeap.getClosures(objIndex);
        //            nodeStringBuilder.append("\\n");
        //            nodeStringBuilder.append("DEEP SIZE: ").append(closures.getTransitiveClosureByteCount()).append(" bytes");
        //        }
        //        if(visPathToAllRootsObjRetainedSizeCB.isSelected()) {
        //            if(closures == null) {
        //                closures = fastHeap.getClosures(objIndex);
        //            }
        //            nodeStringBuilder.append("\\n");
        //            nodeStringBuilder.append("RETAINED SIZE: ").append(closures.getGCClosureByteCount()).append(" bytes");
        //        }

        return nodeStringBuilder.toString();
    }

    @Override
    protected void cleanupOnClose() {

    }

    @Override
    protected void appInfoChangeAction(IAppInfo.ChangeType type) {

    }

    @Override
    public void heapStateChanged() {
        // reopen tab...
        ObjectGroupInfoTab updatedTab = new ObjectGroupInfoTab();
        updatedTab.init(getAppInfo(), nodes, performClassifierSelection, availableClassifierInfo, addTab, switchToTab, showOriginalNode, false);

        // ...and close this one
        getParentTab().getChildTabs().remove(this);
    }

    private class InitTask extends AntTask<Void> {

        private boolean showTabImmediately;

        public InitTask(boolean showTabImmediately) {
            this.showTabImmediately = showTabImmediately;
        }

        @Override
        protected Void backgroundWork() throws Exception {
            updateTitle("Calculate object group info");

            updateMessage("Calculate closures...");
            // ApplicationStatistics.Measurement m = ApplicationStatistics.getInstance().createMeasurement("ObjectGroupInfoTab - Calculate Closures");
            Closures closures = fastHeap.getClosures(true, true, false, false, objectGroupArray);
            //m.end();

            // closures
            ClassifierChain defaultClassifiers = new ClassifierChain();
            defaultClassifiers.getList().add(availableClassifierInfo.getAvailableClassifier().get(TypeClassifier.class));
            defaultClassifiers.getList().add(availableClassifierInfo.getAvailableClassifier().get(AllocationSiteClassifier.class));
            defaultClassifiers.getList().add(availableClassifierInfo.getAvailableClassifier().get(CallSitesClassifier.class));
            updateMessage("Classify transitive objects with " + defaultClassifiers.toString() + "...");
            //m = ApplicationStatistics.getInstance().createMeasurement("ObjectGroupInfoTab - Classify transitive objects");
            ListClassificationTree transitiveObjectsGrouping = initTransitiveObjectsGrouping(closures, defaultClassifiers);
            ParallelizationUtil.submitTask(transitiveObjectsGrouping.initClosureTask(fastHeap, true, true, false, false));
            //m.end();
            updateMessage("Classify retained objects with " + defaultClassifiers.toString() + "...");
            //m = ApplicationStatistics.getInstance().createMeasurement("ObjectGroupInfoTab - Classify retained objects");
            ListClassificationTree retainedObjectsGrouping = initRetainedObjectGrouping(closures, defaultClassifiers);
            ParallelizationUtil.submitTask(retainedObjectsGrouping.initClosureTask(fastHeap, true, true, false, false));
            //m.end();

            // data structures
            updateMessage("Gather and classify data structures ...");
            //m = ApplicationStatistics.getInstance().createMeasurement("ObjectGroupInfoTab - Init and classify data structures");
            ListClassificationTree dataStructureHeadsGrouping = initDataStructureHeadsGrouping();
            ParallelizationUtil.submitTask(dataStructureHeadsGrouping.initClosureTask(fastHeap, true, true, false, false));
            //m.end();

            // roots
            ClassifierChain rootClassifiers = new ClassifierChain();
            DirectGCRootsClassifier directGCRootsClassifier = (DirectGCRootsClassifier) availableClassifierInfo.getAvailableClassifier().get(DirectGCRootsClassifier.class);
            directGCRootsClassifier.setOnlyShowVariables(true);
            rootClassifiers.getList().add(directGCRootsClassifier);
            updateMessage("Gather and classify direct roots ...");
            //m = ApplicationStatistics.getInstance().createMeasurement("ObjectGroupInfoTab - Gather and classify direct roots");
            ListClassificationTree directRootObjectsGrouping = initDirectRootObjectsGrouping(rootClassifiers);
            ParallelizationUtil.submitTask(transitiveObjectsGrouping.initClosureTask(fastHeap, true, true, false, false));
            //m.end();
            updateMessage("Gather and classify indirect roots ...");
            //m = ApplicationStatistics.getInstance().createMeasurement("ObjectGroupInfoTab - Gather and classify indirect roots");
            ListClassificationTree indirectRootObjectsGrouping = initIndirectRootObjectsGrouping(rootClassifiers);
            ParallelizationUtil.submitTask(indirectRootObjectsGrouping.initClosureTask(fastHeap, true, true, false, false));
            //m.end();

            if (!isCancelled()) {
                // init tab UI
                initUI(closures,
                       transitiveObjectsGrouping,
                       retainedObjectsGrouping,
                       dataStructureHeadsGrouping,
                       directRootObjectsGrouping,
                       indirectRootObjectsGrouping);

                // show tab
                Platform.runLater(() -> {
                    updateMessage("Opening representation...");
                    addTab.accept(ObjectGroupInfoTab.this);
                    if (showTabImmediately) {
                        switchToTab.accept(ObjectGroupInfoTab.this);
                    }
                });
            }

            return null;
        }

        private ListClassificationTree initIndirectRootObjectsGrouping(ClassifierChain rootClassifiers) {
            Filter indirectGCRootFilter = new Filter() {
                @Override
                protected Boolean classify() throws Exception {
                    return indirectRootObjectIndices.get(index());
                }
            };
            indirectGCRootFilter.setup(() -> fastHeap.getSymbols(), () -> fastHeap);
            fastHeap.indirectGCRoots(objectGroupArray, Integer.MAX_VALUE).forEach(root -> indirectRootObjectIndices.set(root.getIdx()));
            Counter c = new Counter();
            ListClassificationTree grouping = fastHeap.groupListParallel(new Filter[]{indirectGCRootFilter},
                                                                         rootClassifiers,
                                                                         false,
                                                                         false,
                                                                         objectCount -> {
                                                                             c.add(objectCount);
                                                                             updateProgress(c.get(), fastHeap.getObjectCount());
                                                                         },
                                                                         getCancelProperty());
            grouping.init(fastHeap, true, true, false, false);
            return grouping;
        }

        private ListClassificationTree initDirectRootObjectsGrouping(ClassifierChain rootClassifiers) {
            Filter directGCRootFilter = new Filter() {
                @Override
                protected Boolean classify() throws Exception {
                    return directRootObjectIndices.get(index());
                }
            };
            directGCRootFilter.setup(() -> fastHeap.getSymbols(), () -> fastHeap);
            Arrays.stream(objectGroupArray).forEach(objIndex -> {
                if (!isCancelled()) {
                    List<RootPtr> roots = fastHeap.getRoot(objIndex);
                    if (roots != null) {
                        directRootObjectIndices.set(objIndex);
                    }
                }
            });
            Counter c = new Counter();
            ListClassificationTree grouping = fastHeap.groupListParallel(new Filter[]{directGCRootFilter},
                                                                         rootClassifiers,
                                                                         false,
                                                                         false,
                                                                         objectCount -> {
                                                                             c.add(objectCount);
                                                                             updateProgress(c.get(), fastHeap.getObjectCount());
                                                                         },
                                                                         getCancelProperty());
            grouping.init(fastHeap, true, true, false, false);
            return grouping;
        }

        private ListClassificationTree initDataStructureHeadsGrouping() {
            ClassifierChain dataStructureClassifiers = new ClassifierChain();
            dataStructureClassifiers.getList().add(availableClassifierInfo.getAvailableClassifier().get(TypeClassifier.class));
            Arrays.stream(objectGroupArray)
                  .filter(objIndex -> DSLDataStructure.isDataStructureHead(objIndex, fastHeap))
                  .forEach(headIndex -> dataStructureHeadIndices.set(headIndex));
            Filter dataStructureHeadFilter = new Filter() {
                @Override
                protected Boolean classify() throws Exception {
                    return dataStructureHeadIndices.get(index());
                }
            };
            Counter c = new Counter();
            ListClassificationTree grouping = fastHeap.groupListParallel(new Filter[]{dataStructureHeadFilter},
                                                                         dataStructureClassifiers,
                                                                         false,
                                                                         false,
                                                                         objectCount -> {
                                                                             c.add(objectCount);
                                                                             updateProgress(c.get(), fastHeap.getObjectCount());
                                                                         },
                                                                         getCancelProperty());
            grouping.init(fastHeap, true, true, false, false);
            return grouping;
        }

        private ListClassificationTree initRetainedObjectGrouping(Closures closures, ClassifierChain defaultClassifiers) {
            Filter retainedObjectFilter = new Filter() {
                @Override
                protected Boolean classify() throws Exception {
                    return closures.getGCClosure().get(index());
                }
            };
            retainedObjectFilter.setup(() -> fastHeap.getSymbols(), () -> fastHeap);
            Counter c = new Counter();
            ListClassificationTree grouping = fastHeap.groupListParallel(new Filter[]{retainedObjectFilter},
                                                                         defaultClassifiers,
                                                                         false,
                                                                         false,
                                                                         objectCount -> {
                                                                             c.add(objectCount);
                                                                             updateProgress(c.get(), fastHeap.getObjectCount());
                                                                         },
                                                                         getCancelProperty());
            grouping.init(fastHeap, true, true, false, false);
            return grouping;
        }

        private ListClassificationTree initTransitiveObjectsGrouping(Closures closures, ClassifierChain defaultClassifiers) {
            Filter transitiveObjectFilter = new Filter() {
                @Override
                protected Boolean classify() throws Exception {
                    return closures.getTransitiveClosure().get(index());
                }
            };
            transitiveObjectFilter.setup(() -> fastHeap.getSymbols(), () -> fastHeap);
            Counter c = new Counter();
            ListClassificationTree grouping = fastHeap.groupListParallel(new Filter[]{transitiveObjectFilter},
                                                                         defaultClassifiers,
                                                                         false,
                                                                         false,
                                                                         objectCount -> {
                                                                             c.add(objectCount);
                                                                             updateProgress(c.get(), fastHeap.getObjectCount());
                                                                         },
                                                                         getCancelProperty());
            grouping.init(fastHeap, true, true, false, false);
            return grouping;
        }

        private void initUI(Closures closures,
                            ListClassificationTree transitiveObjectsGrouping,
                            ListClassificationTree retainedObjectsGrouping,
                            ListClassificationTree dataStructureHeadsGrouping,
                            ListClassificationTree directRootObjectsGrouping,
                            ListClassificationTree indirectRootObjectsGrouping) {
            Platform.runLater(() -> {
                // inspected object groups
                Arrays.stream(nodes).forEach(node -> {
                    HBox hbox = new HBox();
                    hbox.setAlignment(Pos.CENTER);
                    hbox.getChildren().add(new Label(Arrays.stream(node.getFullKey()).reduce((key1, key2) -> key1 + " -> " + key2).get().toString()));
                    Button jumpBackToNode = new Button("?");
                    jumpBackToNode.setPadding(new Insets(5));
                    jumpBackToNode.setTranslateX(10);
                    jumpBackToNode.setOnAction(evt -> showOriginalNode.accept(node));
                    hbox.getChildren().add(jumpBackToNode);
                    inspectedObjectGroupsVB.getChildren().add(hbox);
                });

                // metrics
                TableColumn<Tuple<String, Number>, String> labelColumn = new TableColumn<>("Metric");
                TableColumn<Tuple<String, Number>, String> valueColumn = new TableColumn<>("Value");
                labelColumn.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().a));
                valueColumn.setCellValueFactory(param -> new SimpleStringProperty(HeapMetric.formatter.format(param.getValue().b)));
                labelColumn.prefWidthProperty().bind(metricsTV.widthProperty().divide(2).subtract(10));
                valueColumn.prefWidthProperty().bind(metricsTV.widthProperty().divide(2).subtract(10));
                metricsTV.getColumns().add(labelColumn);
                metricsTV.getColumns().add(valueColumn);
                //metricsTV.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);   // no horizontal scrollbar
                metricsTV.getItems().add(new Tuple<>("Shallow size [objects]", objectGroupArray.length));
                metricsTV.getItems().add(new Tuple<>("Shallow size [bytes]", closures.getFlatByteCount()));
                metricsTV.getItems().add(new Tuple<>("Deep size [objects]", closures.getTransitiveClosureObjectCount()));
                metricsTV.getItems().add(new Tuple<>("Deep size [bytes]", closures.getTransitiveClosureByteCount()));
                metricsTV.getItems().add(new Tuple<>("Retained size [objects]", closures.getGCClosureObjectCount()));
                metricsTV.getItems().add(new Tuple<>("Retained size [bytes]", closures.getGCClosureByteCount()));
                metricsTV.setFixedCellSize(25);
                metricsTV.prefHeightProperty().bind(metricsTV.fixedCellSizeProperty().multiply(Bindings.size(metricsTV.getItems()).add(1.25)));

                // closures
                transitiveObjectsTTV.init(getAppInfo(),
                                          availableClassifierInfo,
                                          performClassifierSelection,
                                          addTab,
                                          switchToTab,
                                          () -> {
                                              addTab.accept(ObjectGroupInfoTab.this);
                                              switchToTab.accept(ObjectGroupInfoTab.this);
                                          },
                                          (newClassifierChain) -> {});
                transitiveObjectsTTV.setRoot(transitiveObjectsGrouping, false, false, null);
                retainedObjectsTTV.init(getAppInfo(),
                                        availableClassifierInfo,
                                        performClassifierSelection,
                                        addTab,
                                        switchToTab,
                                        () -> {
                                            addTab.accept(ObjectGroupInfoTab.this);
                                            switchToTab.accept(ObjectGroupInfoTab.this);
                                        },
                                        (newClassifierChain) -> {});
                retainedObjectsTTV.setRoot(retainedObjectsGrouping, false, false, null);

                // data structure heads
                dataStructureHeadsTTV.init(getAppInfo(),
                                           availableClassifierInfo,
                                           performClassifierSelection,
                                           addTab,
                                           switchToTab,
                                           () -> {
                                               addTab.accept(ObjectGroupInfoTab.this);
                                               switchToTab.accept(ObjectGroupInfoTab.this);
                                           },
                                           (newClassifierChain) -> {});
                dataStructureHeadsTTV.setRoot(dataStructureHeadsGrouping, false, false, null);
                dataStructureHeadsTTV.getShallowSizeColumn().setVisible(false);
                dataStructureHeadsTTV.getDataStructureSizeColumn().setVisible(true);
                dataStructureHeadsTTV.getDeepDataStructureSizeColumn().setVisible(true);

                // roots
                directRootsTTV.init(getAppInfo(),
                                    availableClassifierInfo,
                                    performClassifierSelection,
                                    addTab,
                                    switchToTab,
                                    () -> {
                                        addTab.accept(ObjectGroupInfoTab.this);
                                        switchToTab.accept(ObjectGroupInfoTab.this);
                                    },
                                    (newClassifierChain) -> {});
                directRootsTTV.setRoot(directRootObjectsGrouping, false, false, null);

                indirectRootsTTV.init(getAppInfo(),
                                      availableClassifierInfo,
                                      performClassifierSelection,
                                      addTab,
                                      switchToTab,
                                      () -> {
                                          addTab.accept(ObjectGroupInfoTab.this);
                                          switchToTab.accept(ObjectGroupInfoTab.this);
                                      },
                                      (newClassifierChain) -> {});
                indirectRootsTTV.setRoot(indirectRootObjectsGrouping, false, false, null);

                // allow scrolling of tree tables only when they are focused (and not just hovered)
                NodeUtil.ignoreScrollingUnlessFocused(transitiveObjectsTTV, objectGroupInfoTabSP);
                NodeUtil.ignoreScrollingUnlessFocused(retainedObjectsTTV, objectGroupInfoTabSP);
                NodeUtil.ignoreScrollingUnlessFocused(dataStructureHeadsTTV, objectGroupInfoTabSP);
                NodeUtil.ignoreScrollingUnlessFocused(directRootsTTV, objectGroupInfoTabSP);
                NodeUtil.ignoreScrollingUnlessFocused(indirectRootsTTV, objectGroupInfoTabSP);

                visPathsToAllRootsBT.setOnAction(evt -> visualizePathsToAllRoots());
                // disallow the combination of not aggregating objects but aggregating data structures:
                // disable aggregate ds checkbox when aggregate obj checkbox is not selected
                visPathsToAllRootsAggregateDataStructuresCB.disableProperty().bind(visPathsToAllRootsAggregateObjectsCB.selectedProperty().not());
                // unselect aggregate ds checkbox when aggregate obj checkbox is unselected
                visPathsToAllRootsAggregateObjectsCB.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                    if (!isSelected) {
                        visPathsToAllRootsAggregateDataStructuresCB.setSelected(false);
                    }
                });
                // disallow the combination of not aggregating objects but showing data structure size in nodes
                // disable show ds size checkbox when aggregate obj checkbox is not selected
                visPathToAllRootsDSObjCountCB.disableProperty().bind(visPathsToAllRootsAggregateObjectsCB.selectedProperty().not());
                // unselect show ds size checkbox when aggregate obj checkbox is unselected
                visPathsToAllRootsAggregateObjectsCB.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
                    if (!isSelected) {
                        visPathToAllRootsDSObjCountCB.setSelected(false);
                    }
                });
                visPathsToAllRootsFilenameTF.setText("paths_to_all_roots_" + System.currentTimeMillis() + ".dot");
            });
        }

        @Override
        protected void finished() {

//            System.out.println("testing dominators...");
//            fastHeap.initDominators();
//            SlowDominators slowDominators = new SlowDominators(fastHeap);
//            fastHeap.stream().forEach(i -> {
//                if (slowDominators.getImmediateDominator(i) != fastHeap.getImmediateDominator(i)) {
//                    System.out.print(String.format("Immediate dominator missmatch for index %d: %d vs %d",
//                                                   i,
//                                                   fastHeap.getImmediateDominator(i),
//                                                   slowDominators.getImmediateDominator(i)));
//                    if (fastHeap.getRoot(i) != null) {
//                        System.out.println(" (rooted)");
//                    } else {
//                        System.out.println();
//                    }
//                }
//            });
//            System.out.println("done testing dominators!");

//            fastHeap.initDominators();

        }
    }
}
