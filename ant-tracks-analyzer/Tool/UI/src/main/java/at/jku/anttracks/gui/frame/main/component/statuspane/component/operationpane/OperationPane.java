
package at.jku.anttracks.gui.frame.main.component.statuspane.component.operationpane;

import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.gui.utils.Operation;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;

public final class OperationPane extends BorderPane {

    @FXML
    private Label titleLabel;
    @FXML
    private ProgressIndicator progressIndicator;
    @FXML
    private Label percentLabel;
    @FXML
    private Label remainingTextLabel;
    @FXML
    private Button cancelButton;

    private final Operation operation;

    public OperationPane(Operation operation) {
        this.operation = operation;
        FXMLUtil.load(this, OperationPane.class);
    }

    public void init() {
        Platform.runLater(() -> {
            titleLabel.textProperty().bind(Bindings.format("%s: %s", operation.titleProperty(), operation.messageProperty()));
            percentLabel.textProperty().bind(Bindings.format(" %s%% ||", operation.percentTextProperty()));
            remainingTextLabel.textProperty().bind(Bindings.format("%s", operation.remainingTimeTextProperty()));
            progressIndicator.progressProperty().bind(operation.progressProperty());
        });

        cancelButton.setGraphic(ImageUtil.getResourceImagePack("Cancel", "delete.png").getAsNewNode());
    }

    // Interface for old works that do not implement AntTask
    public void progress(double progress) {
        operation.progress(progress);
    }

    public void cancel() {
        operation.cancel();
    }
}
