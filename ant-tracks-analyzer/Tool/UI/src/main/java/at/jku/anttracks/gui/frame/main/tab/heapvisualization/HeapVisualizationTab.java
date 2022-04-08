
package at.jku.anttracks.gui.frame.main.tab.heapvisualization;

import at.jku.anttracks.classification.ClassifierChain;
import at.jku.anttracks.classification.Filter;
import at.jku.anttracks.gui.frame.main.component.applicationbase.ApplicationBaseTab;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.PaintTask.PaintOperation;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.PointersTask.PointerOperation;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.component.*;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.component.paintconfigurationpane.PaintConfigurationPane;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.ClusterLevel;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.ObjectVisualizationData;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.PixelMap;
import at.jku.anttracks.gui.frame.main.tab.heapvisualization.model.VisualizationModel;
import at.jku.anttracks.gui.model.HeapVisualizationStatisticsInfo;
import at.jku.anttracks.gui.model.IAppInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.util.ThreadUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.embed.swing.SwingNode;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Region;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import java.awt.image.ImageObserver;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

public class HeapVisualizationTab extends ApplicationBaseTab {

    static {
        UIManager.put("Slider.paintValue", false);
    }

    public static final int DEFAULT_CLUSTER = 5;
    private static final int MAX_POINTER_LEVEL = 10;

    public static final String NO_PREFIX = "-", KILO = "k", MEGA = "M", GIGA = "G";
    public static final String NO_OBJECT_GROUPS = "no object groups selected";
    private VisualizationModel model;
    private HeapVisualizationStatisticsInfo statisticsInfo;
    private HeapPanel heapPanel;
    private JScrollPane scrollPane;
    private ObjectInfoPanel objectInfoPanel;
    private JSlider pointsToLevel;
    private JSlider pointersFromLevel;
    private JPanel south;
    private JPanel center;
    private JPanel pointerPanel;

    private HeapVisualizationUpdateTask currentHeapVisualizationTask;
    private PaintTask currentPaintTask;
    private PointersTask currentPointersTask;
    private long stalledClusterSize;

    private boolean enableListeners;

    @FXML
    private BorderPane borderPane;
    @FXML
    private BorderPane topPane;
    @FXML
    private HeapVisualizationConfigurationPane configurationPane;
    @FXML
    private SwingNode centerSwingNode;
    @FXML
    private BorderPane bottomPane;
    @FXML
    private SwingNode keyPanelSwingNode;
    @FXML
    private PaintConfigurationPane paintConfigurationPane;
    @FXML
    private SwingNode objInfoPanelSwingNode;
    @FXML
    private SwingNode bottomPaneBottom;

    public HeapVisualizationTab() {
        FXMLUtil.load(this, HeapVisualizationTab.class);
    }

    public void init(HeapVisualizationStatisticsInfo statisticsInfo) {
        super.init(statisticsInfo.getDetailsInfo().getAppInfo(),
                   new SimpleStringProperty("Heap visualization"),
                   new SimpleStringProperty(String.format("@ %,dms", statisticsInfo.getDetailsInfo().getTime())),
                   new SimpleStringProperty(""),
                   null,
                   new ArrayList<>(),
                   true);

        this.statisticsInfo = statisticsInfo;

        model = new VisualizationModel();
        stalledClusterSize = PixelMap.UNDEFINED_CLUSTER_SIZE;
        enableListeners = true;
        objectInfoPanel = new ObjectInfoPanel(model);
        pointerPanel = new JPanel(new GridLayout(1, 2));
        pointsToLevel = new JSlider(SwingConstants.HORIZONTAL, 0, MAX_POINTER_LEVEL, 1);
        pointersFromLevel = new JSlider(SwingConstants.HORIZONTAL, 0, MAX_POINTER_LEVEL, 0);
        configurationPane.init(statisticsInfo.getDetailsInfo().getAppInfo(), statisticsInfo.getDetailsInfo(), this);
        topPane.setCenter(configurationPane);
        borderPane.setTop(topPane);

        center = new JPanel(new BorderLayout());
        center.setBorder(BorderFactory.createTitledBorder("Heap"));
        heapPanel = new HeapPanel(model, this);
        scrollPane = new JScrollPane(heapPanel);
        scrollPane.addComponentListener(new ComponentListener() {

            @Override
            public void componentResized(ComponentEvent e) {
                if (stalledClusterSize == PixelMap.UNDEFINED_CLUSTER_SIZE && currentPaintTask == null && currentHeapVisualizationTask == null && currentPointersTask ==
                        null) {
                    // startPaintTask(PixelMap.UNDEFINED_CLUSTER_SIZE);
                }

            }

            @Override
            public void componentMoved(ComponentEvent e) {}

            @Override
            public void componentShown(ComponentEvent e) {}

            @Override
            public void componentHidden(ComponentEvent e) {}

        });
        center.add(BorderLayout.CENTER, scrollPane);
        center.addComponentListener(new ComponentListener() {

            @Override
            public void componentShown(ComponentEvent e) {}

            @Override
            public void componentResized(ComponentEvent e) {
                center.revalidate();
                center.repaint();
            }

            @Override
            public void componentMoved(ComponentEvent e) {}

            @Override
            public void componentHidden(ComponentEvent e) {}
        });
        centerSwingNode.setContent(center);

        initPointsToPanel();
        initPaintConfigurationPane();

        JPanel keyPanel = createKeyPanel();
        JPanel objInfoPanel = createObjInfoPanel();
        keyPanel.addComponentListener(new ComponentListener() {

            @Override
            public void componentShown(ComponentEvent e) {}

            @Override
            public void componentResized(ComponentEvent e) {
                keyPanel.revalidate();
                keyPanel.repaint();
            }

            @Override
            public void componentMoved(ComponentEvent e) {}

            @Override
            public void componentHidden(ComponentEvent e) {}
        });

        objInfoPanel.addComponentListener(new ComponentListener() {

            @Override
            public void componentShown(ComponentEvent e) {}

            @Override
            public void componentResized(ComponentEvent e) {
                objInfoPanel.revalidate();
                objInfoPanel.repaint();
            }

            @Override
            public void componentMoved(ComponentEvent e) {}

            @Override
            public void componentHidden(ComponentEvent e) {}
        });

        keyPanelSwingNode.setContent(keyPanel);
        objInfoPanelSwingNode.setContent(objInfoPanel);
    }

    private void initPaintConfigurationPane() {
        paintConfigurationPane.init();
        paintConfigurationPane.clusterSizeSpinner.valueProperty().addListener((observable, oldValue, newValue) -> {
            PixelMap pixelMap = model.getCurrentPixelMap();
            if (enableListeners && pixelMap != null && !pixelMap.isZoomed() && pixelMap.getClusterSize() != computeClusterSize()) {
                startPaintTask(computeClusterSize());
            }
        });
        paintConfigurationPane.unitComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            PixelMap pixelMap = model.getCurrentPixelMap();
            if (enableListeners && pixelMap != null && !pixelMap.isZoomed() && pixelMap.getClusterSize() != computeClusterSize()) {
                startPaintTask(computeClusterSize());
            }
        });
        paintConfigurationPane.clusterLevelComboBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            startPaintTask(PixelMap.UNDEFINED_CLUSTER_SIZE);
        });
        paintConfigurationPane.autoButton.setOnAction(ae -> {
            model.getCurrentPixelMap().resetZoom();
            startPaintTask(PixelMap.UNDEFINED_CLUSTER_SIZE);
        });
        paintConfigurationPane.zoomInButton.setOnAction(ae -> {
            if (currentPaintTask != null) {
                currentPaintTask.cancel(true);
            }
            currentPaintTask = new PaintTask(statisticsInfo, this, model.getCurrentPixelMap().getData(), model.getCurrentPixelMap(), PaintOperation.ZOOM_IN);
            ThreadUtil.startTask(currentPaintTask);
        });
        paintConfigurationPane.zoomOutButton.setOnAction(ae -> {
            if (currentPaintTask != null) {
                currentPaintTask.cancel(true);
            }
            currentPaintTask = new PaintTask(statisticsInfo, this, model.getCurrentPixelMap().getData(), model.getCurrentPixelMap(), PaintOperation.ZOOM_OUT);
            ThreadUtil.startTask(currentPaintTask);
        });

        paintConfigurationPane.showPointersCheckBox.setOnAction(ae -> {
            if (paintConfigurationPane.showPointersCheckBox.isSelected()) {
                bottomPaneBottom.setVisible(true);
                model.getCurrentPixelMap().setShowPointers(true);
                startPointersTask(PointerOperation.BOTH);
            } else {
                bottomPaneBottom.setVisible(false);
                model.getCurrentPixelMap().setShowPointers(false);
                startPaintTask();
            }
        });
        paintConfigurationPane.showPointersCheckBox.setSelected(false);
        paintConfigurationPane.resetObjectsButton.setOnAction(ae -> {
            model.getCurrentPixelMap().resetClassifications();
            if (model.getCurrentPixelMap().showPointers()) {
                startPointersTask(PointerOperation.BOTH);
            } else {
                startPaintTask();
            }
        });
        paintConfigurationPane.showPointersCheckBox.setDisable(!statisticsInfo.getDetailsInfo().getDetailedHeapSupplier().get().getSymbols().expectPointers);
    }

    private void initPointsToPanel() {
        JPanel pointsToPanel = new JPanel();
        pointsToPanel.setLayout(new BoxLayout(pointsToPanel, BoxLayout.PAGE_AXIS));
        JLabel sliderLabel = new JLabel("pointers from object(s)", SwingConstants.CENTER);
        sliderLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        pointsToLevel.setMajorTickSpacing(1);
        pointsToLevel.setPaintLabels(true);
        pointsToLevel.setPaintTicks(true);
        // Create the label table
        Hashtable<Integer, JLabel> labelTable = new Hashtable<>();
        labelTable.put(0, new JLabel("None"));
        labelTable.put(MAX_POINTER_LEVEL, new JLabel("All"));
        pointsToLevel.setLabelTable(labelTable);
        pointsToPanel.add(sliderLabel);
        pointsToPanel.add(pointsToLevel);
        pointerPanel.add(pointsToPanel);
        pointsToLevel.addChangeListener(e -> {
            if (!pointsToLevel.getValueIsAdjusting()) {
                stalledClusterSize = computeClusterSize();
                startPointersTask(PointerOperation.TO_POINTERS);
            }
        });
        JPanel pointersFromPanel = new JPanel();
        pointersFromPanel.setLayout(new BoxLayout(pointersFromPanel, BoxLayout.PAGE_AXIS));
        sliderLabel = new JLabel("pointers to object(s)", SwingConstants.CENTER);
        sliderLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        pointersFromLevel.setMajorTickSpacing(1);
        pointersFromLevel.setPaintLabels(true);
        pointersFromLevel.setPaintTicks(true);
        pointersFromLevel.setLabelTable(labelTable);
        pointersFromPanel.add(sliderLabel);
        pointersFromPanel.add(pointersFromLevel);
        pointersFromLevel.addChangeListener(e -> {
            if (!pointersFromLevel.getValueIsAdjusting()) {
                stalledClusterSize = computeClusterSize();
                startPointersTask(PointerOperation.FROM_POINTERS);
            }
        });
        pointerPanel.add(pointersFromPanel);

        bottomPaneBottom.setContent(pointerPanel);
        bottomPaneBottom.managedProperty().bind(bottomPaneBottom.visibleProperty());
        bottomPaneBottom.setVisible(false);
    }

    public VisualizationModel getModel() {
        return model;
    }

    private JPanel createObjInfoPanel() {
        JPanel objInfo = new JPanel();
        objInfo.setLayout(new BoxLayout(objInfo, BoxLayout.LINE_AXIS));
        objInfo.setBorder(BorderFactory.createTitledBorder("Object Info"));
        objInfo.add(objectInfoPanel);
        heapPanel.addHeapPanelListener(objectInfoPanel);
        return objInfo;
    }

    public boolean showPointers() {
        return paintConfigurationPane.showPointersCheckBox.isSelected();
    }

    public void startPointersTask(PointerOperation pointerOperation) {
        if (currentPointersTask != null) {
            currentPointersTask.cancel(true);
            // quick'n'dirty fix, does its job, maybe replace it later with
            // something better
            while (!currentPointersTask.isDone()) {
            }
        }
        if (currentPointersTask != null) {
            currentPointersTask.cancel(true);
        }
        currentPointersTask = new PointersTask(statisticsInfo, this, model.getCurrentPixelMap(), pointerOperation);
        (new Thread(currentPointersTask)).start();
    }

    public void startPaintTask() {
        startPaintTask(PixelMap.UNDEFINED_CLUSTER_SIZE);
    }

    private void startPaintTask(long clusterSize) {
        if (model.getCurrentPixelMap() != null) {
            stalledClusterSize = clusterSize;
            if (currentHeapVisualizationTask != null && !currentHeapVisualizationTask.isCancelled() && !currentHeapVisualizationTask.isDone()) {
                // If there's a VisualizationWorker runnning, let it finish and
                // just do nothing
            } else {
                // If there's a paintworker running already, cancel it first
                if (currentPaintTask != null) {
                    currentPaintTask.cancel(true);
                }
                currentPaintTask = new PaintTask(statisticsInfo, this, model.getCurrentPixelMap().getData(), model.getCurrentPixelMap(), PaintOperation.PAINT);
                (new Thread(currentPaintTask)).start();
            }
        }
    }

    public long computeClusterSize() {
        long clusterSize = paintConfigurationPane.clusterSizeSpinner.getValue();
        String unit = paintConfigurationPane.unitComboBox.getValue();
        switch (unit) {
            case NO_PREFIX:
                return clusterSize;
            case KILO:
                return clusterSize * 1024;
            case MEGA:
                return clusterSize * 1024 * 1024;
            case GIGA:
                return clusterSize * 1024 * 1024 * 1024;
        }
        return clusterSize;
    }

    private JPanel createKeyPanel() {
        JPanel key = new JPanel();
        key.setLayout(new BorderLayout());
        key.setBorder(BorderFactory.createTitledBorder("Key"));
        HeapKeyDescriptionPanel descriptionPanel = new HeapKeyDescriptionPanel(model, this);
        JScrollPane scrollPane = new JScrollPane(descriptionPanel);
        scrollPane.setViewportBorder(null);
        scrollPane.setBorder(null);
        scrollPane.setMaximumSize(new Dimension(ImageObserver.WIDTH, HeapKeyPanel.HEIGHT));
        scrollPane.setPreferredSize(new Dimension(HeapKeyDescriptionPanel.MIN_WIDTH, HeapKeyPanel.HEIGHT));
        HeapKeyPanel keyPanel = new HeapKeyPanel(model, descriptionPanel, scrollPane);
        key.add(keyPanel, BorderLayout.WEST);
        key.add(scrollPane, BorderLayout.CENTER);
        heapPanel.addHeapPanelListener(keyPanel);
        return key;
    }

    public void updateAfterPainting(PixelMap pixelMap) {
        if (model.getCurrentPixelMap() != pixelMap) {
            model.setCurrentPixelMap(pixelMap);
        }
        stalledClusterSize = PixelMap.UNDEFINED_CLUSTER_SIZE;
        heapPanel.updateWidthAndHeight();
        heapPanel.resetPixel();
        heapPanel.repaint();
        heapPanel.revalidate();
        scrollPane.repaint();
        scrollPane.revalidate();
        paintConfigurationPane.zoomInButton.setDisable(!pixelMap.canZoomIn());
        paintConfigurationPane.zoomOutButton.setDisable(!pixelMap.canZoomOut());
        setClusterSelectionEnabled(!pixelMap.isZoomed());
        int pixelSize = pixelMap.getCurrentPixelSize();
        paintConfigurationPane.zoomLabel.setText(pixelSize * 100 + "%");
        setClusterSize(pixelMap.getClusterSize());
        boolean selection = pixelMap.hasSelectedClassifications();
        paintConfigurationPane.resetObjectsButton.setDisable(!selection);
        if (selection) {
            StringBuilder b = new StringBuilder("Current selection:\n");
            for (Object o : pixelMap.getSelectedClassifications()) {
                // b.append("\n\t");
                b.append(o.toString());
                b.append("\n");
            }
            paintConfigurationPane.resetObjectsButton.setTooltip(new Tooltip(b.toString()));
        } else {
            paintConfigurationPane.resetObjectsButton.setTooltip(new Tooltip(NO_OBJECT_GROUPS));
        }
        // showPointers.setEnabled(false);
    }

    public void resetObjectInfo() {
        objectInfoPanel.reset();
    }

    public void setClusterSelectionEnabled(boolean enabled) {
        paintConfigurationPane.clusterSizeSpinner.setDisable(!enabled);
        paintConfigurationPane.clusterLevelComboBox.setDisable(!enabled);
        paintConfigurationPane.unitComboBox.setDisable(!enabled);
    }

    public JScrollPane getScrollPane() {
        return scrollPane;
    }

    public void setCurrentHeapVisualizationTask(HeapVisualizationUpdateTask currentHeapVisualizationTask) {
        this.currentHeapVisualizationTask = currentHeapVisualizationTask;
    }

    public void setCurrentPaintTask(PaintTask currentPaintTask) {
        this.currentPaintTask = currentPaintTask;
    }

    public void setCurrentPointersTask(PointersTask currentPointersTask) {
        this.currentPointersTask = currentPointersTask;
    }

    private void abortCurrentWorkers() {
        if (currentHeapVisualizationTask != null) {
            currentHeapVisualizationTask.cancel(true);
        }
        if (currentPaintTask != null) {
            currentPaintTask.cancel(true);
        }
        if (currentPointersTask != null) {
            currentPointersTask.cancel(true);
        }
    }

    public ClusterLevel getClusterLevel() {
        return paintConfigurationPane.clusterLevelComboBox.getValue();
    }

    public long getStalledClusterSize() {
        return stalledClusterSize;
    }

    public void setClusterSize(long clusterSize) {
        enableListeners = false;
        int unitIdx = 0;
        while (clusterSize > 1024) {
            clusterSize /= 1024;
            unitIdx++;
        }
        try {
            paintConfigurationPane.clusterSizeSpinner.getValueFactory().setValue((int) clusterSize);
            paintConfigurationPane.unitComboBox.getSelectionModel().select(unitIdx);
        } finally {
            enableListeners = true;
        }
    }

    public PixelMap getPixelMap() {
        return model.getCurrentPixelMap();
    }

    public int getPointsToLevel() {
        int val = pointsToLevel.getValue();
        if (val >= MAX_POINTER_LEVEL) {
            return ObjectVisualizationData.MAX_POINTERS;
        }
        return val;
    }

    public int getPointersFromLevel() {
        int val = pointersFromLevel.getValue();
        if (val >= MAX_POINTER_LEVEL) {
            return ObjectVisualizationData.MAX_POINTERS;
        }
        return val;
    }

    public void classifiersChanged(ClassifierChain chain) {
        abortCurrentWorkers();
        statisticsInfo.setSelectedClassifiers(chain);
        PixelMap current = model.getCurrentPixelMap();
        if (current == null || !chain.equals(current.getSelectedClassifiers())) {
            if (currentHeapVisualizationTask != null) {
                currentHeapVisualizationTask.cancel();
            }
            if (current != null) {
                current.resetClassifications();
            }
            currentHeapVisualizationTask = new HeapVisualizationUpdateTask(statisticsInfo, this);
            (new Thread(currentHeapVisualizationTask)).start();
        }
    }

    public void filtersChanged(List<Filter> selectedFilters) {
        abortCurrentWorkers();
        statisticsInfo.setSelectedFilters(selectedFilters);
        if (model.getCurrentPixelMap() != null) {
            model.getCurrentPixelMap().setShowPointers(false);
            paintConfigurationPane.showPointersCheckBox.setSelected(false);
            south.remove(pointerPanel);
        }
        currentHeapVisualizationTask = new HeapVisualizationUpdateTask(statisticsInfo, this);
        (new Thread(currentHeapVisualizationTask)).start();
    }

    public void setColorForClassification(Object classification, Color newColor) {
        ObjectVisualizationData data = model.getCurrentPixelMap().getData();
        if (!newColor.equals(data.getColorForClassification(classification))) {
            data.setColorForClassification(classification, newColor);
            startPaintTask();
        }
    }

    public void startPNGExport(int width, int height, long clusterSize, String targetFile) {
        PNGExportTask task = new PNGExportTask(statisticsInfo, this, width, height, clusterSize, targetFile);
        ThreadUtil.startTask(task);
    }

    public void showPNGExportDialog(boolean success) {
        if (success) {
            Alert alert = new Alert(AlertType.INFORMATION);
            alert.setTitle("PNG Export");
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.setHeaderText(null);
            alert.setContentText("PNG Export to file was successful!");

            alert.showAndWait();
        } else {
            Alert alert = new Alert(AlertType.WARNING);
            alert.setTitle("PNG Export");
            alert.getDialogPane().setMinHeight(Region.USE_PREF_SIZE);
            alert.setHeaderText(null);
            alert.setContentText("An error occured during the PNG export, please try again");

            alert.showAndWait();
        }
    }

    public void minimizeConfigView() {
        if (configurationPane.isConfigureMode()) {
            configurationPane.switchMode();
        }
    }

    @Override
    protected void cleanupOnClose() {

    }

    @Override
    protected void appInfoChangeAction(
            @NotNull
                    IAppInfo.ChangeType type) {

    }
}
