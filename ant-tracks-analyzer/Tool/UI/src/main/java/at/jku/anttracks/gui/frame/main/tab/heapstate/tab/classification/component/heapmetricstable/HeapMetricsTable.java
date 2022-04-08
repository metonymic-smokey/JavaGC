package at.jku.anttracks.gui.frame.main.tab.heapstate.tab.classification.component.heapmetricstable;

import at.jku.anttracks.gui.utils.FXMLUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;

public class HeapMetricsTable extends TableView<HeapMetric> {

    public HeapMetricsTable() {
        FXMLUtil.load(this, HeapMetricsTable.class);
    }

    public final HeapMetric treeNodesMetric = new HeapMetric("Tree Nodes (with or without data entry)", 0);
    public final HeapMetric dataTreeNodesMetric = new HeapMetric("Data Tree Nodes (nodes with at least one data entry)", 0);
    public final HeapMetric treeNodeDataCollectionPartsMetric = new HeapMetric("Tree Node Data Collection Parts (e.g., amount of key-value pairs in mapping nodes)", 0);

    public final HeapMetric avgTreeNodeDataCollectionPartsPerNodeMetric = new HeapMetric("Avg. Tree Node Data Collection Parts per Node", 0);
    public final HeapMetric avgTreeNodeDataCollectionPartsPerDataNodeMetric = new HeapMetric("Avg. Tree Node Data Collection Parts per Data Node", 0);
    public final HeapMetric avgObjectsPerNodeMetric = new HeapMetric("Avg. Objects per Node", 0);
    public final HeapMetric avgObjectsPerDataNodeMetric = new HeapMetric("Avg. Objects per Data Node", 0);
    public final HeapMetric avgObjectsPerTreeNodeDataCollectionPartMetric = new HeapMetric("Avg. Objects per Tree Node Data Collection Part", 0);

    public final HeapMetric nObjects = new HeapMetric("Number of objects", 0); // heap.getObjectCount()
    public final HeapMetric nToPointersMetric = new HeapMetric("Number of to-pointers", 0); // nToPointers.get()
    public final HeapMetric nToPointersWithoutNullMetric = new HeapMetric("Number of non-null to-pointers", 0); // nToPointersWithoutNull.get()
    public final HeapMetric nFromPointersMetric = new HeapMetric("Number of from-pointers", 0); //nFromPointers.get()
    public final HeapMetric nRootPointers = new HeapMetric("Number of root-pointers", 0); // heap.rootPtrs.values().stream().flatMap(List::stream).count()

    public final HeapMetric classificationTime = new HeapMetric("Classification time [sec]", 0);
    public final HeapMetric classificationThroughput = new HeapMetric("Classification throughput [obj/sec]", 0);
    public final HeapMetric samplingTime = new HeapMetric("Sampling time [sec]", 0);
    public final HeapMetric groupingInitTime = new HeapMetric("Grouping init time [sec]", 0);

    public void init() {
        // Define columns
        TableColumn<HeapMetric, String> labelColumn = new TableColumn<>("Metric");
        labelColumn.setCellValueFactory(param -> param.getValue().labelProperty);
        // default cell factory seems to be sufficient (cellitem.toString)

        TableColumn<HeapMetric, String> valueColumn = new TableColumn<>("Value");
        valueColumn.setCellValueFactory(param -> new SimpleStringProperty(HeapMetric.formatter.format(param.getValue().valueProperty.get())));

        getColumns().add(labelColumn);
        getColumns().add(valueColumn);

        // Define column widths
        labelColumn.prefWidthProperty().bind(widthProperty().divide(2));
        valueColumn.prefWidthProperty().bind(widthProperty().divide(2).subtract(20)); // no horizontal scrollbar please

        // Tree Node Info items
        this.getItems().add(treeNodesMetric);
        this.getItems().add(dataTreeNodesMetric);
        this.getItems().add(treeNodeDataCollectionPartsMetric);
        this.getItems().add(avgTreeNodeDataCollectionPartsPerNodeMetric);
        this.getItems().add(avgTreeNodeDataCollectionPartsPerDataNodeMetric);
        this.getItems().add(avgObjectsPerNodeMetric);
        this.getItems().add(avgObjectsPerDataNodeMetric);
        this.getItems().add(avgObjectsPerTreeNodeDataCollectionPartMetric);

        // Object Info items
        this.getItems().add(nObjects);

        // Pointer Info items
        this.getItems().add(nToPointersMetric);
        this.getItems().add(nToPointersWithoutNullMetric);
        this.getItems().add(nFromPointersMetric);
        this.getItems().add(nRootPointers);

        // classification info
        this.getItems().add(classificationTime);
        this.getItems().add(classificationThroughput);
        this.getItems().add(samplingTime);
        this.getItems().add(groupingInitTime);
    }

    public void showPointers(boolean pointers) {
        if (!pointers) {
            this.getItems().remove(nToPointersMetric);
            this.getItems().remove(nToPointersWithoutNullMetric);
            this.getItems().remove(nFromPointersMetric);
            this.getItems().remove(nRootPointers);
        }
    }
}
