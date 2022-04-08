
package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.task;

import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.classification.trees.ListClassificationTree;
import at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.HeapStateClassificationTab;
import at.jku.anttracks.gui.model.ClientInfo;
import at.jku.anttracks.gui.model.HeapStateClassificationInfo;
import at.jku.anttracks.gui.utils.AntTask;
import at.jku.anttracks.gui.utils.WindowUtil;
import at.jku.anttracks.gui.utils.ideagenerators.HeapStateAnalysisIdeaGenerator;
import at.jku.anttracks.heap.ObjectStream;
import at.jku.anttracks.heap.statistics.Statistics;
import at.jku.anttracks.util.Counter;
import at.jku.anttracks.util.ThreadUtil;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.layout.Region;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class HeapStateClassificationTask extends AntTask<ListClassificationTree> {

    private String taskName;

    private final HeapStateClassificationTab heapStateClassificationTab;
    private final HeapStateClassificationInfo statisticsInfo;
    private Task<Void> closureInitTask;

    @SuppressWarnings("unused")
    private static boolean VERBOSE = false;

    public HeapStateClassificationTask(HeapStateClassificationInfo statisticsInfo, HeapStateClassificationTab heapStateClassificationTab) {
        this.statisticsInfo = statisticsInfo;
        this.heapStateClassificationTab = heapStateClassificationTab;

        // new grouping task -> cancel all currently running tasks in this tab because they concern current grouping
        heapStateClassificationTab.getTasks().forEach(Task::cancel);
        heapStateClassificationTab.getTasks().clear();
        heapStateClassificationTab.getTasks().add(this);

        try {
            String path = statisticsInfo.getHeapStateInfo().getAppInfo().getSymbolsFile() == null
                          ? ""
                          : (statisticsInfo.getHeapStateInfo().getAppInfo().getSymbolsFile().getCanonicalPath() + " - ");

            taskName = HeapStateClassificationTask.class.getSimpleName() + " - " +
                    path +
                    statisticsInfo.getHeapStateInfo().getTime() + " - " +
                    statisticsInfo.getSelectedClassifierInfo().getSelectedClassifiers().toString();
        } catch (IOException e) {
            getLOGGER().warning("Error on retrieving parser worker name:\n" + e);
            taskName = this.getClass().getSimpleName();
        }
    }

    @Override
    protected void cancelled() {
        super.cancelled();

        if (closureInitTask != null) {
            closureInitTask.cancel();
        }

        // cancel() suppresses succeeded() i.e. the treetable will not be updated
        // thus the last working classifier/filter configuration must be restored
        // heapStateClassificationTab.rollbackConfiguration();
    }

    private ListClassificationTree groupHeapObjects() {
        updateTitle("Heap State: ");
        updateMessage(String.format("Classify heap objects for time %,.3fs using classifiers %s",
                                    statisticsInfo.getHeapStateInfo().getTime() / 1000.0f,
                                    statisticsInfo.getSelectedClassifierInfo().getSelectedClassifiers()));
        long objectCount = statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get().getObjectCount();
        System.out.println("Hellllllllllllllo im in HeapStateClassificationTask");
        System.out.println("SelectedClassfierInfo: " + statisticsInfo.getSelectedClassifierInfo().getSelectedClassifiers());

        ListClassificationTree grouping = null;
        Counter objectsProcessed = new Counter();
        final long t = System.nanoTime();
        ObjectStream.IterationListener iterationListener = (oc) -> {
            objectsProcessed.add(oc);
            updateProgress(objectsProcessed.get(), objectCount);
            updateClassificationMetrics(objectsProcessed.get(), t);
        };

        grouping = statisticsInfo.getHeapStateInfo()
                                 .getFastHeapSupplier()
                                 .get()
                                 .groupListParallel(statisticsInfo.getSelectedClassifierInfo().getSelectedFilters().toArray(new Filter[0]),
                                                    statisticsInfo.getSelectedClassifierInfo().getSelectedClassifiers(),
                                                    true,
                                                    true,
                                                    iterationListener,
                                                    getCancelProperty());

        ClientInfo.meterRegistry.timer("classification." + statisticsInfo.getSelectedClassifierInfo().getSelectedClassifiers().toString() + ".per_object")
                                .record((long) ((1.0 * System.nanoTime() - t) / grouping.getRoot().getObjectCount() * 1_000_000), TimeUnit.NANOSECONDS);

        //        break;
        //}

        if (!isCancelled()) {
            updateClassificationMetrics(objectsProcessed.get(), t);

            updateMessage(String.format("Calculate object & byte counts and overall closure for time %,.3fs (This may take some seconds, please wait)",
                                        statisticsInfo.getHeapStateInfo().getTime() / 1000.0f));
            long t2 = System.nanoTime();
            grouping.init(statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get(), true, true, false, false);

            heapStateClassificationTab.getHeapMetricsTable().groupingInitTime.valueProperty.setValue((System.nanoTime() - t2) / 1_000_000_000.0);

            getLOGGER().log(Level.INFO, "Finished classification");
        }

        if (grouping.getRoot().getObjectCount() != statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get().getObjectCount()) {
            getLOGGER().info(String.format("Classification Tree Overall Object Count (%,d) does not match Fast Heap Overall Object Count (%,d)",
                                           grouping.getRoot().getObjectCount(),
                                           statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get().getObjectCount()));
        }

        if (grouping.getRoot().getByteCount(statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get()) != statisticsInfo.getHeapStateInfo()
                                                                                                                            .getFastHeapSupplier()
                                                                                                                            .get()
                                                                                                                            .getByteCount()) {
            getLOGGER().info(String.format("Classification Tree Overall Byte Count (%,d) does not match Fast Heap Overall Byte Count (%,d)",
                                           grouping.getRoot().getByteCount(statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get()),
                                           statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get().getByteCount()));
        }

        Optional<Statistics> stat = statisticsInfo.getHeapStateInfo()
                                                  .getAppInfo()
                                                  .getStatistics()
                                                  .stream()
                                                  .filter(statistics -> statistics.getInfo().getTime() == statisticsInfo.getHeapStateInfo().getTime())
                                                  .findFirst();

        // Stat is not present if HPROF file is used
        if (stat.isPresent()) {
            long statObjectCount = stat.get().getEden().memoryConsumption.getObjects() +
                    stat.get().getSurvivor().memoryConsumption.getObjects() +
                    stat.get().getOld().memoryConsumption.getObjects();
            long statMemoryConsumption = stat.get().getEden().memoryConsumption.getBytes() +
                    stat.get().getSurvivor().memoryConsumption.getBytes() +
                    stat.get().getOld().memoryConsumption.getBytes();
            if (grouping.getRoot().getObjectCount() != statObjectCount) {
                getLOGGER().info(String.format("Classification Tree Overall Object Count (%,d) does not match Statistics Object Count (%,d)",
                                               grouping.getRoot().getObjectCount(),
                                               statObjectCount));
            }

            if (grouping.getRoot().getByteCount(statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get()) != statMemoryConsumption) {
                getLOGGER().info(String.format("Classification Tree Overall Byte Count (%,d) does not match Statistics Byte Count (%,d)",
                                               grouping.getRoot().getByteCount(statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get()),
                                               statMemoryConsumption));
            }
        }

        return grouping;
    }

    private void updateClassificationMetrics(long objectsProcessed, long startTime) {
        double seconds = (System.nanoTime() - startTime) / 1_000_000_000.0;
        heapStateClassificationTab.getHeapMetricsTable().classificationTime.valueProperty.setValue(seconds);
        long objPerSec = (int) (objectsProcessed / seconds);
        heapStateClassificationTab.getHeapMetricsTable().classificationThroughput.valueProperty.setValue(objPerSec);
        heapStateClassificationTab.getHeapMetricsTable().refresh();
    }

    private void checkGrouping(ListClassificationTree grouping) {
        /*
         * if (VERBOSE) { long nNodes = 1; long nNodesWithAtLeastOneLeaf = Math.min(1, grouping.getLeafs().size()); long nHashMapEntries =
         * grouping.getLeafs().size(); List<? extends HeapObjectGrouping> stillToCheck = grouping.getChildren(); while
         * (!stillToCheck.isEmpty()) { Grouping g = stillToCheck.remove(0); nNodes += 1 + g.getChildren().stream().filter(child ->
         * child.getChildren().size() == 0).count(); nHashMapEntries += g.getLeafs().size() + g.getChildren().stream().filter(child ->
         * child.getChildren().size() == 0) .mapToLong(child -> child.getLeafs().size()).sum(); nNodesWithAtLeastOneLeaf += Math.min(1,
         * g.getLeafs().size()) + g.getChildren().stream().filter(child -> child.getChildren().size() == 0) .mapToLong(child -> Math.min(1,
         * child.getLeafs().size())).sum(); if (g.getChildren().size() == 0 && g.getLeafs().size() == 0) { logger.log(Level.WARNING,
         * "Somethings wrong with the grouping tree. Leaf node does not have data stored." ); }
         * stillToCheck.addAll(g.getChildren().stream().filter(child -> child.getChildren().size() > 0) .collect(Collectors.toList())); }
         *
         * logger.log(Level.FINE, grouping.toString()); logger.log(Level.FINE, "Number of nodes: " + nNodes); logger.log(Level.FINE,
         * "Number of nodes with at least one leaf: " + nNodesWithAtLeastOneLeaf); logger.log(Level.FINE, "Number of leaf HashMap
         * entries: "
         * + nHashMapEntries); logger.log(Level.FINE, "Number of objects: " + grouping.getSummedObjects());
         *
         * float avgHashMapEntries = 1.0f * nHashMapEntries / nNodes; double avgObjects = 1.0f * grouping.getSummedObjects() / nNodes;
         *
         * logger.log(Level.FINE, "Avg. number of leaf entries per node: " + avgHashMapEntries); logger.log(Level.FINE,
         * "Avg. number of objects per node: " + avgObjects); logger.log(Level.FINE, "avg(n_E) * x = avg(n_O) ... x = " + (avgObjects /
         * avgHashMapEntries)); }
         */
    }

    @Override
    protected ListClassificationTree backgroundWork() throws Exception {
        try {
            ListClassificationTree grouping = groupHeapObjects();
            checkGrouping(grouping);
            return grouping;
        } catch (Exception e) {
            e.printStackTrace(System.err);
            final FutureTask<Boolean> alertTask = new FutureTask<>(() -> {
                Alert alert = new Alert(AlertType.ERROR,
                                        "An internal error occured while classifying, do you want to retry?\n" + e.toString(),
                                        ButtonType.YES,
                                        ButtonType.NO);
                alert.setTitle("Error");
                alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
                WindowUtil.INSTANCE.centerInMainFrame(alert);
                Optional<ButtonType> retryChoice = alert.showAndWait();
                return retryChoice.isPresent() && retryChoice.get() == ButtonType.YES;
            });
            Platform.runLater(alertTask);
            if (alertTask.get()) {
                return call();
            } else {
                throw new Exception(e);
            }
        }
    }

    @Override
    protected void finished() {
        ListClassificationTree grouping = getValue();
        heapStateClassificationTab.updateGrouping(grouping);
        // heapStateClassificationTab.setConfigurationSavepoint();

        heapStateClassificationTab.removeAllButInitialIdeas();
        if (!heapStateClassificationTab.isInDataStructureView()) {
            // bottom up analysis uses shallow sizes so it can start right after classification
            HeapStateAnalysisIdeaGenerator.analyzeDefaultClassifiedTree(heapStateClassificationTab);
        }

        // start closure calculation after grouping
        closureInitTask = grouping.initClosureTask(statisticsInfo.getHeapStateInfo().getFastHeapSupplier().get(),
                                                   true,
                                                   true,
                                                   false,
                                                   false,
                                                   () -> {
                                                       if (heapStateClassificationTab.isInDataStructureView()) {
                                                           // top down analysis needs retained sizes so it only happens after closures have been calculated
                                                           HeapStateAnalysisIdeaGenerator.topDownAnalysis(heapStateClassificationTab);
                                                       }
                                                   });

        ThreadUtil.runDeferred(() -> {
            Platform.runLater(() -> {
                ClientInfo.operationManager.addNewOperation(closureInitTask);
                ThreadUtil.startTask(closureInitTask);
            });
            heapStateClassificationTab.getTasks().add(closureInitTask);
        }, ThreadUtil.DeferredPeriod.LONG);
    }
}
