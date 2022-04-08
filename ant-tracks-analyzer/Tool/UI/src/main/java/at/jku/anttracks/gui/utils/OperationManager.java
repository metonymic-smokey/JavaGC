
package at.jku.anttracks.gui.utils;

import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.scene.Node;

import java.util.ArrayList;
import java.util.List;

public class OperationManager {

    private final ObservableList<Node> operationPaneList;
    private final List<Operation> operations = new ArrayList<>();

    public OperationManager(ObservableList<Node> operationPaneList) {
        this.operationPaneList = operationPaneList;
    }

    public Operation addNewOperation(String title) {
        Operation o = new Operation(title);
        addOperation(o);
        return o;
    }

    public Operation addNewOperation(Task<?> task) {
        Operation o = new Operation(task);
        addOperation(o);
        return o;
    }

    public void addOperation(Operation operation) {
        // End on manual operation closing
        operation.addClosedListener(() -> {
            closeOperation(operation);
        });

        Platform.runLater(() -> {
            // End on task end
            operation.getTask().stateProperty().addListener((observable, oldValue, newValue) -> {
                switch (newValue) {
                    case CANCELLED:
                        closeOperation(operation);
                        break;
                    case FAILED:
                        closeOperation(operation);
                        break;
                    case READY:
                        break;
                    case RUNNING:
                        break;
                    case SCHEDULED:
                        break;
                    case SUCCEEDED:
                        closeOperation(operation);
                        break;
                    default:
                        break;
                }
            });

            // Add panel to view
            operationPaneList.add(operation.getOperationPane());
        });
    }

    private void closeOperation(Operation operation) {
        // Remove panel from view
        Platform.runLater(() -> operationPaneList.remove(operation.getOperationPane()));
    }

    public void cancelAll() {
        operations.forEach(operation -> operation.getTask().cancel());
    }
}
