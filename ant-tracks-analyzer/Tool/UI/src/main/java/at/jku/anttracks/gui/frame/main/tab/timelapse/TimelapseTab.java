
package at.jku.anttracks.gui.frame.main.tab.timelapse;

import at.jku.anttracks.classification.Classifier;
import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.gui.classification.classifier.TypeClassifier;
import at.jku.anttracks.gui.component.actiontab.tab.ActionTab;
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.HeapVisualizationTab;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.ClusterLevel;
import at.jku.anttracks.gui.frame.main.tab.timelapse.components.TimelapseConfigurationPane;
import at.jku.anttracks.gui.frame.main.tab.timelapse.components.TimelapsePane;
import at.jku.anttracks.gui.frame.main.tab.timelapse.model.HeapList;
import at.jku.anttracks.gui.frame.main.tab.timelapse.model.TimelapseModel;
import at.jku.anttracks.gui.frame.main.tab.timelapse.workers.TimelapsePaintTask;
import at.jku.anttracks.gui.frame.main.tab.timelapse.workers.TimelapseTask;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.IAppInfo.ChangeType;
import at.jku.anttracks.gui.model.TimelapseStatisticsInfo;
import at.jku.anttracks.gui.utils.Consts;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.ImageUtil;
import at.jku.anttracks.util.ThreadUtil;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.util.Duration;

import javax.swing.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.util.ArrayList;
import java.util.Hashtable;

/**
 * @author Christina Rammerstorfer
 */
public class TimelapseTab extends ApplicationBaseTab {

    static {
        UIManager.put("Slider.paintValue", false);
    }

    @FXML
    private TimelapseConfigurationPane configurationPanel;

    @FXML
    private Slider slider;

    @FXML
    private Label timeLabel;

    @FXML
    private Button play;

    @FXML
    private ComboBox<ClusterLevel> clusterLevelBox;

    @FXML
    private Spinner<Integer> clusterSizeSpinner;

    @FXML
    private ComboBox<String> unitBox;

    @FXML
    private SwingNode swingCenter;

    @FXML
    private Timeline timer;

    private static final int ANIMATION_DELAY = 750;
    private static final String SUFFIX = " ms";

    private TimelapseStatisticsInfo statisticsInfo;
    private TimelapsePane heapPanel;
    private TimelapseModel model;

    private JScrollPane scrollPane;

    private boolean isPlaying;
    private TimelapseTask worker;
    private TimelapsePaintTask paintWorker;

    private ActionTab parentTab;

    public void setWorker(TimelapseTask worker) {
        this.worker = worker;
        getTasks().add(worker);
    }

    public void setPaintWorker(TimelapsePaintTask worker) {
        paintWorker = worker;
        getTasks().add(paintWorker);
    }

    public TimelapseTab() {
        FXMLUtil.load(this, TimelapseTab.class);
    }

    public void init(AppInfo appInfo, ActionTab parentTab, TimelapseStatisticsInfo statisticsInfo) {
        super.init(appInfo,
                   new SimpleStringProperty("Timelapse"),
                   new SimpleStringProperty(""),
                   new SimpleStringProperty(""),
                   null,
                   new ArrayList<>(),
                   true);
        this.statisticsInfo = statisticsInfo;
        this.parentTab = parentTab;

        // Top: Config panel
        configurationPanel.init(this, appInfo, statisticsInfo.timelapseInfo);

        // Center: Swing heap panel
        initSwingContent();

        // South: Text panel + slider
        timeLabel.textProperty().bind(Bindings.format("%.0f %s", slider.valueProperty(), SUFFIX));

        slider.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (model != null) {
                if (model.containsKey((long) slider.getValue())) {
                    model.setCurrentSelection((long) slider.getValue());
                } else {
                    slider.setValue((int) model.getClosestValue((long) slider.getValue()));
                }
                heapPanel.repaint();
            }
        });

        // South: Play button
        play.setGraphic(ImageUtil.getIconNode(Consts.PLAY_ICON));
        play.setDisable(true);

        // South: Cluser level combo box
        clusterLevelBox.getItems().addAll(ClusterLevel.values());
        clusterLevelBox.setValue(ClusterLevel.BYTES);
        clusterLevelBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            startNewWorker();
        });

        // South cluster size spinner
        clusterSizeSpinner.getValueFactory().setValue(HeapVisualizationTab.DEFAULT_CLUSTER);
        clusterSizeSpinner.setDisable(true);

        // South: unit box
        unitBox.getItems().addAll(HeapVisualizationTab.NO_PREFIX, HeapVisualizationTab.KILO, HeapVisualizationTab.MEGA, HeapVisualizationTab.GIGA);
        unitBox.setDisable(true);

        timer = new Timeline(new KeyFrame(Duration.millis(ANIMATION_DELAY), ae -> {
            int newValue = (int) model.getNextValue((long) slider.getValue());
            slider.setValue(newValue);
            if (newValue == slider.getMax()) {
                timer.stop();
                updatePlayButton();
            }
        }));
        timer.setCycleCount(Animation.INDEFINITE);
    }

    public void play() {
        if (isPlaying) {
            timer.stop();
        } else {
            if (slider.getValue() == slider.getMax()) {
                slider.setValue(slider.getMin());
            }
            timer.play();
        }
        updatePlayButton();
    }

    private void initSwingContent() {

        SwingUtilities.invokeLater(() -> {
            // HeapPanel + Scroll
            heapPanel = new TimelapsePane();
            scrollPane = new JScrollPane(heapPanel);
            heapPanel.setBorder(BorderFactory.createTitledBorder("Heap"));

            swingCenter.setContent(scrollPane);
            swingCenter.getContent().addComponentListener(new ComponentListener() {

                @Override
                public void componentShown(ComponentEvent e) {}

                @Override
                public void componentResized(ComponentEvent e) {
                    swingCenter.getContent().revalidate();
                    swingCenter.getContent().repaint();
                }

                @Override
                public void componentMoved(ComponentEvent e) {}

                @Override
                public void componentHidden(ComponentEvent e) {}
            });
        });

    }

    private void updatePlayButton() {
        if (isPlaying) {
            isPlaying = false;
            play.setGraphic(ImageUtil.getIconNode(Consts.PLAY_ICON));
        } else {
            isPlaying = true;
            play.setGraphic(ImageUtil.getIconNode(Consts.PAUSE_ICON));
        }
    }

    /*
     * @Override public String getHeaderText() { return "Heap visualization over time for " + getAppInfo().getAppName() + " (" +
     * getAppInfo().getSelectedTraceFile() + ")"; }
     *
     * @Override public Icon getIcon() { return Consts.PLAY_ICON; }
     *
     * @Override public String getTitle() { return "Timelapse - " + getAppInfo().getAppName(); }
     */

    public TimelapseStatisticsInfo getStatisticsInfo() {
        return statisticsInfo;
    }

    public void setDefaultClassification() {
        Classifier<?> types = statisticsInfo.timelapseInfo.getAvailableClassifier().get(TypeClassifier.class);
        if (types != null) {
            statisticsInfo.setSelectedClassifiers(new ClassifierChain(types));
        }
        configurationPanel.getFilterSelectionPane().resetSelected(statisticsInfo.getSelectedFilters());
        configurationPanel.getClassifierSelectionPane().resetSelected(statisticsInfo.getSelectedClassifiers().getList());
    }

    public ClusterLevel getClusterLevel() {
        return clusterLevelBox.getValue();
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public void setTimelapseModel(TimelapseModel m) {
        model = m;
        heapPanel.setModel(model);
    }

    public void updateTimelapse() {
        Hashtable<Integer, String> labelTable = new Hashtable<>();
        Long[] times = model.getTimes();
        slider.setMin(times[0].intValue());
        slider.setMax(times[times.length - 1].intValue());
        for (int i = 0; i < times.length; i++) {
            labelTable.put(times[i].intValue(), "|");
        }
        // TODO: Find working JavaFX alternative
        // slider.setLabelTable(labelTable);
        // slider.setLabelFormatter(new StringConverter<Double>(){
        //
        // @Override
        // public String toString(Double object) {
        // return labelTable.get(object.intValue());
        // }
        //
        // @Override
        // public Double fromString(String string) {
        // return null;
        // }
        //
        // });
        slider.setValue(times[0].intValue());
        // slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        model.setCurrentSelection((long) slider.getValue());
        heapPanel.repaint();
        heapPanel.revalidate();
        play.setDisable(false);
    }

    public void setClusterSize(long clusterSize) {
        // enableListeners = false;
        int unitIdx = 0;
        while (clusterSize > 1024) {
            clusterSize /= 1024;
            unitIdx++;
        }
        try {
            clusterSizeSpinner.getValueFactory().setValue((int) clusterSize);
            unitBox.setValue(unitBox.getItems().get(unitIdx));
        } finally {
            // enableListeners = true;
        }
    }

    public void startNewWorker() {
        if (worker != null) {
            worker.cancel();
        }
        if (paintWorker != null) {
            paintWorker.cancel();
        }
        worker = new TimelapseTask(getAppInfo(), parentTab, this);
        ThreadUtil.startTask(worker);
        getTasks().add(worker);
    }

    public void startNewPaintWorker(HeapList heapList) {
        if (paintWorker != null) {
            paintWorker.cancel();
        }
        paintWorker = new TimelapsePaintTask(getAppInfo(), heapList, statisticsInfo, this);
        ThreadUtil.startTask(paintWorker);
        getTasks().add(paintWorker);
    }

    @Override
    protected void appInfoChangeAction(ChangeType type) {
        // TODO
    }

    @Override
    protected void cleanupOnClose() {

    }
}
