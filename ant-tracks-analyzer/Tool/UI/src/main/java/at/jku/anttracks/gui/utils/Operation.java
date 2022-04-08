
package at.jku.anttracks.gui.utils;

import at.jku.anttracks.gui.frame.main.component.statuspane.component.operationpane.OperationPane;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

import java.util.ArrayList;
import java.util.List;

public class Operation {

    private SimpleStringProperty title = new SimpleStringProperty();
    private SimpleStringProperty message = new SimpleStringProperty();
    private SimpleStringProperty percentText = new SimpleStringProperty();
    private SimpleStringProperty remainingTimeText = new SimpleStringProperty();
    private SimpleDoubleProperty progress = new SimpleDoubleProperty();

    private Task<?> task;
    private OperationPane operationPane;
    private List<Runnable> onClosedListeners;
    private ProgressCalc progressCalculator;

    // Interface for old workers that do not implement AntTask
    public Operation(String title) {
        this.title.set(title);
        this.message.set("");
        this.progressCalculator = new ProgressCalc();
        this.remainingTimeText.bind(progressCalculator.remainingTimeTextProperty);
        this.progress.bind(progressCalculator.progressProperty);

        setup();
    }

    // Interface for old workers that do not implement AntTask
    public void progress(double progress) {
        progressCalculator.update(progress);
    }

    /**
     * Creates a new operation based on a task's title, message and progress.
     * May only be called from within the JavaFX thread.
     *
     * @param task The task to bind to
     */
    public Operation(Task<?> task) {
        this.task = task;
        this.title.bind(task.titleProperty());
        this.message.bind(task.messageProperty());
        this.progressCalculator = new ProgressCalc(task.progressProperty());
        this.percentText.bind(progressCalculator.percentTextProperty);
        this.remainingTimeText.bind(progressCalculator.remainingTimeTextProperty);
        this.progress.bind(progressCalculator.progressProperty);

        setup();
    }

    private void setup() {
        onClosedListeners = new ArrayList<>();
        operationPane = new OperationPane(this);
        operationPane.init();
    }

    public Task<?> getTask() {
        return task;
    }

    public OperationPane getOperationPane() {
        return operationPane;
    }

    public void addClosedListener(Runnable listener) {
        onClosedListeners.add(listener);
    }

    public void removeClosedListener(Runnable listener) {
        onClosedListeners.remove(listener);
    }

    /**
     * Notifies all listeners, then clears the listener list (since close should only be called once)
     */
    public void close() {
        progressCalculator.close();
        onClosedListeners.forEach(Runnable::run);
        onClosedListeners.clear();
    }

    public void cancel() {
        task.cancel();
    }

    public StringProperty titleProperty() {
        return title;
    }

    public StringProperty messageProperty() {
        return message;
    }

    public StringProperty percentTextProperty() {
        return percentText;
    }

    public StringProperty remainingTimeTextProperty() {
        return remainingTimeText;
    }

    public DoubleProperty progressProperty() {
        return progress;
    }
}
