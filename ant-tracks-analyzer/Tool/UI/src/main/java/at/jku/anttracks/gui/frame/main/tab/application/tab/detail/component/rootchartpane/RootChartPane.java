
package at.jku.anttracks.gui.frame.main.tab.application.tab.detail.component.rootchartpane;

import at.jku.anttracks.gui.chart.base.AntChartPane;
import at.jku.anttracks.gui.chart.base.ApplicationChartFactory;
import at.jku.anttracks.gui.chart.base.ApplicationChartFactory.ApplicationChartType;
import at.jku.anttracks.gui.chart.base.ChartSynchronizer;
import at.jku.anttracks.gui.chart.jfreechart.xy.base.XYJFreeChartPane;
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.alivedead.AliveDeadStackedAreaJFreeChartPane;
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.allocatingsubsystem.AllocatingSubsystemStackedAreaJFreeChartPane;
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.feature.FeatureStackedAreaJFreeChartPane;
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.memory.MemoryStackedAreaJFreeChartPane;
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.objectkind.ObjectKindStackedAreaJFreeChartPane;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.ClientInfo;
import at.jku.anttracks.gui.model.IAppInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.PlatformUtil;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;
import org.jfree.data.xy.DefaultTableXYDataset;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;

public class RootChartPane extends GridPane {
    @SuppressWarnings("unused")
    private final Logger LOGGER = Logger.getLogger(this.getClass().getSimpleName());
    private ChartSynchronizer chartSynchronizer;

    // Object & Memory Panels (1 + 2)
    public AntChartPane<DefaultTableXYDataset, AppInfo> objectsApplicationChart;
    public AntChartPane<?, AppInfo> bytesApplicationChart;
    // GC type panel (3)
    public AntChartPane<?, AppInfo> gcTypesApplicationChart;
    // Alive / Dead panels (3 alternatives) (4)
    public AntChartPane<?, AppInfo> aliveDeadApplicationChart;
    public AntChartPane<?, AppInfo> aliveDeadRelativeApplicationChart;
    public AntChartPane<?, AppInfo> aliveDeadObjectsApplicationChart;
    public AntChartPane<?, AppInfo> aliveDeadBytesApplicationChart;
    // // Feature panels (5 + 6)
    public AntChartPane<?, AppInfo> featureObjectsApplicationChart;
    public AntChartPane<?, AppInfo> featureBytesApplicationChart;
    // // Extended info panels (7 + 8)
    public AntChartPane<?, AppInfo> allocatingSubsystemApplicationChart;
    public AntChartPane<?, AppInfo> objectKindsApplicationChart;

    private boolean showFeatures = false;
    private boolean showExtended = false;

    public RootChartPane() {
        FXMLUtil.load(this, RootChartPane.class);
    }

    public void init(ChartSynchronizer chartSynchronizer) {
        this.chartSynchronizer = chartSynchronizer;

        createAllBasicChartPanels();
    }

    @SuppressWarnings("unchecked")
    public final void plot(AppInfo appInfo) {
        boolean somethingChanged = showFeatureChartPanels(appInfo.isShowFeatures());
        somethingChanged |= showExtendedInfo(ClientInfo.isExtendedChartVisibility);
        somethingChanged |= setAliveDeadPanel(appInfo.getAliveDeadPanelType());
        if (somethingChanged) {
            reorderGrid();
        }

        getChildren().stream().map(node -> (AntChartPane<?, AppInfo>) node).forEach(chart -> {
            try {
                chart.update(appInfo);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
        DefaultTableXYDataset memoryDataSet = objectsApplicationChart.getDataset();
        chartSynchronizer.setZoomXBounds(memoryDataSet.getDomainLowerBound(false), memoryDataSet.getDomainUpperBound(false));
    }

    private boolean setAliveDeadPanel(IAppInfo.AliveDeadPanelType aliveDeadPanelType) {
        AntChartPane<?, AppInfo> newPanel = null;
        switch (aliveDeadPanelType) {
            case RELATIVE:
                newPanel = aliveDeadRelativeApplicationChart;
                break;
            case N_OBJECTS:
                newPanel = aliveDeadObjectsApplicationChart;
                break;
            case BYTE:
                newPanel = aliveDeadBytesApplicationChart;
                break;
        }
        if (aliveDeadApplicationChart != newPanel) {
            aliveDeadApplicationChart = newPanel;
            return true;
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private void createAllBasicChartPanels() {
        objectsApplicationChart = (AntChartPane<DefaultTableXYDataset, AppInfo>) ApplicationChartFactory.createChart(ApplicationChartType.OBJECTS, chartSynchronizer);
        bytesApplicationChart = ApplicationChartFactory.createChart(ApplicationChartType.BYTES, chartSynchronizer);
        gcTypesApplicationChart = ApplicationChartFactory.createChart(ApplicationChartType.GC_TYPES, chartSynchronizer);
        aliveDeadRelativeApplicationChart = ApplicationChartFactory.createChart(ApplicationChartType.ALIVE_DEAD_RELATIVE, chartSynchronizer);
        aliveDeadObjectsApplicationChart = ApplicationChartFactory.createChart(ApplicationChartType.ALIVE_DEAD_OBJECTS, chartSynchronizer);
        aliveDeadBytesApplicationChart = ApplicationChartFactory.createChart(ApplicationChartType.ALIVE_DEAD_BYTES, chartSynchronizer);
        setAliveDeadPanel(IAppInfo.AliveDeadPanelType.RELATIVE);
        featureObjectsApplicationChart = ApplicationChartFactory.createChart(ApplicationChartType.FEATURE_OBJECTS, chartSynchronizer);
        featureBytesApplicationChart = ApplicationChartFactory.createChart(ApplicationChartType.FEATURE_BYTES, chartSynchronizer);
        allocatingSubsystemApplicationChart = ApplicationChartFactory.createChart(ApplicationChartType.ALLOCATING_SUBSYSTEM, chartSynchronizer);
        objectKindsApplicationChart = ApplicationChartFactory.createChart(ApplicationChartType.OBJECT_KINDS, chartSynchronizer);
        reorderGrid();

        // alternate colors for each chart row
        // TODO replace hardcoded colors with -fx-color-50/100
        /*
        //this alternating looks bad with only two rows, it makes one row stick out although there is nothing special about them ...
		//it only makes sense for 4+ rows, otherwise its not alternating but highlighting
		objectsApplicationChart.chart.setStyle("-fx-background-color: #eceff1;");
		bytesApplicationChart.chart.setStyle("-fx-background-color: #eceff1;");

		gcTypesApplicationChart.chart.setStyle("-fx-background-color:  #cfd8dc;");
		aliveDeadRelativeApplicationChart.chart.setStyle("-fx-background-color:  #cfd8dc;");
		aliveDeadObjectsApplicationChart.chart.setStyle("-fx-background-color:  #cfd8dc;");
		aliveDeadBytesApplicationChart.chart.setStyle("-fx-background-color:  #cfd8dc;");

		featureObjectsApplicationChart.chart.setStyle("-fx-background-color: #eceff1;");
		featureBytesApplicationChart.chart.setStyle("-fx-background-color: #eceff1;");

		allocatingSubsystemApplicationChart.chart.setStyle("-fx-background-color:  #cfd8dc;");
		objectKindsApplicationChart.chart.setStyle("-fx-background-color:  #cfd8dc;");
		 */
    }

    private void reorderGrid() {
        PlatformUtil.runAndWait(() -> {
            getChildren().clear();
            getRowConstraints().clear();

            addRow(getRowCount(), objectsApplicationChart, bytesApplicationChart);
            addRow(getRowCount(), gcTypesApplicationChart, aliveDeadApplicationChart);
            if (showFeatures) {
                addRow(getRowCount(), featureObjectsApplicationChart, featureBytesApplicationChart);
            }
            if (showExtended) {
                addRow(getRowCount(), allocatingSubsystemApplicationChart, objectKindsApplicationChart);
            }

            getChildren().forEach(child -> {
                setVgrow(child, Priority.ALWAYS);
                setHgrow(child, Priority.ALWAYS);
            });
        });
    }

    private int getRowCount() {
        return getChildren().size() / 2;
    }

    public boolean showFeatureChartPanels(boolean show) {
        if (showFeatures != show) {
            showFeatures = show;
            return true;
        }
        return false;
    }

    public boolean showExtendedInfo(boolean show) {
        if (showExtended != show) {
            showExtended = show;
            return true;
        }
        return false;
    }

    private void updateMarker() {
        getChildren().stream().map(node -> (AntChartPane<?, ?>) node).forEach(AntChartPane::updateMarker);
    }

    public List<XYJFreeChartPane<?, ?>> getXYCharts() {
        return Arrays.asList((MemoryStackedAreaJFreeChartPane) objectsApplicationChart,
                             (MemoryStackedAreaJFreeChartPane) bytesApplicationChart,
                             (XYJFreeChartPane) gcTypesApplicationChart,
                             (AliveDeadStackedAreaJFreeChartPane) aliveDeadApplicationChart,
                             (AliveDeadStackedAreaJFreeChartPane) aliveDeadBytesApplicationChart,
                             (AliveDeadStackedAreaJFreeChartPane) aliveDeadObjectsApplicationChart,
                             (FeatureStackedAreaJFreeChartPane) featureBytesApplicationChart,
                             (FeatureStackedAreaJFreeChartPane) featureObjectsApplicationChart,
                             (AllocatingSubsystemStackedAreaJFreeChartPane) allocatingSubsystemApplicationChart,
                             (ObjectKindStackedAreaJFreeChartPane) objectKindsApplicationChart);
    }
}
