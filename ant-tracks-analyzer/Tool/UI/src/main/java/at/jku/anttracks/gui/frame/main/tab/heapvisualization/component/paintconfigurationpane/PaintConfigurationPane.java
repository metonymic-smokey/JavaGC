
package at.jku.anttracks.gui.frame.main.tab.heapvisualization.component.paintconfigurationpane;

import at.jku.anttracks.gui.frame.main.tab.heapvisualization.HeapVisualizationTab;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.ClusterLevel;
import at.jku.anttracks.gui.utils.Consts;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.ImageUtil;
import javafx.fxml.FXML;
import javafx.scene.control.*;

public class PaintConfigurationPane extends TitledPane {

    @FXML
    public Spinner<Integer> clusterSizeSpinner;
    @FXML
    public ComboBox<String> unitComboBox;
    @FXML
    public ComboBox<ClusterLevel> clusterLevelComboBox;
    @FXML
    public Button autoButton;

    @FXML
    public Label zoomLabel;
    @FXML
    public Button zoomInButton;
    @FXML
    public Button zoomOutButton;
    @FXML
    public CheckBox showPointersCheckBox;
    @FXML
    public Button resetObjectsButton;

    public PaintConfigurationPane() {
        FXMLUtil.load(this, PaintConfigurationPane.class);
    }

    public void init() {
        clusterSizeSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 1024, HeapVisualizationTab.DEFAULT_CLUSTER, 1));

        unitComboBox.getItems().addAll(HeapVisualizationTab.NO_PREFIX, HeapVisualizationTab.KILO, HeapVisualizationTab.MEGA, HeapVisualizationTab.GIGA);
        unitComboBox.getSelectionModel().select(0);
        clusterLevelComboBox.getItems().addAll(ClusterLevel.values());
        clusterLevelComboBox.getSelectionModel().select(0);

        autoButton.setGraphic(ImageUtil.getIconNode(Consts.DISABLE_MAGNIFIER_ICON));
        autoButton.setTooltip(new Tooltip("Resets zoom and sets cluster size to fill the screen"));

        zoomInButton.setGraphic(ImageUtil.getIconNode(Consts.ZOOM_IN_ICON));
        zoomInButton.setTooltip(new Tooltip("Zoom in"));
        zoomOutButton.setGraphic(ImageUtil.getIconNode(Consts.ZOOM_OUT_ICON));
        zoomOutButton.setTooltip(new Tooltip("Zoom out"));

        resetObjectsButton.setTooltip(new Tooltip(HeapVisualizationTab.NO_OBJECT_GROUPS));
    }

}
