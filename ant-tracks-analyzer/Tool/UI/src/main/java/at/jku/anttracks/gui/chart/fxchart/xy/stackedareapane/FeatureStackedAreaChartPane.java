
package at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane;

import at.jku.anttracks.features.FeatureMap;
import at.jku.anttracks.gui.chart.base.ApplicationChartFactory.FeatureMemoryConsumptionUnit;
import at.jku.anttracks.gui.chart.base.AxisScaler;
import at.jku.anttracks.gui.chart.base.ChartSynchronizer;
import at.jku.anttracks.gui.chart.base.JavaFXAxisScaler;
import at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane.base.StackedAreaJavaFXChartPane;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.model.ClientInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.WindowUtil;
import at.jku.anttracks.heap.statistics.MemoryConsumption;
import at.jku.anttracks.heap.statistics.Statistics;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FeatureStackedAreaChartPane extends StackedAreaJavaFXChartPane<AppInfo> implements ShowFeature {
    Set<Integer> display;
    private FeatureMemoryConsumptionUnit memoryUnit;

    public FeatureStackedAreaChartPane() {
        FXMLUtil.load(this, FeatureStackedAreaChartPane.class);
    }

    public void init(ChartSynchronizer chartSynchronizer, FeatureMemoryConsumptionUnit memoryUnit) {
        super.init(chartSynchronizer, true);
        this.memoryUnit = memoryUnit;
        chart.setTitle(memoryUnit.getLabel() + " per Feature");
    }

    @Override
    protected ObservableList<XYChart.Series<Number, Number>> updateDataSet(AppInfo appInfo) {
        if (appInfo.getSymbols() == null) {
            return null;
        }

        FeatureMap features = appInfo.getSymbols().features;
        if (features == null) {
            return null;
        }

        double maxX = getMaxXValue(chart.getData());

        // Init series with first appInfo
        if (chart.getData().size() == 0) {
            for (int id = 0; id < features.getFeatureCount(); id++) {
                String featureName = features.getFeature(id).name;
                XYChart.Series<Number, Number> series = new XYChart.Series<>();
                series.setName(featureName);
                chart.getData().add(series);
            }
        }

        if (display == null || display.size() != features.getFeatureCount()) {
            display = new HashSet<>(features.getFeatureCount());
            IntStream.range(0, features.getFeatureCount()).forEach(display::add);
        }

        for (int i = 0; i < appInfo.getStatistics().size(); i++) {
            Statistics stat = appInfo.getStatistics().get(i);
            double time = stat.getInfo().getTime();

            if (time > maxX) {

                MemoryConsumption[] memoryConsumption_eden = stat.getEden().featureConsumptions;
                MemoryConsumption[] memoryConsumption_old = stat.getOld().featureConsumptions;
                MemoryConsumption[] memoryConsumption_survivor = stat.getSurvivor().featureConsumptions;

                for (int j = 0; j < features.getFeatureCount(); j++) {
                    long sum = 0;
                    switch (memoryUnit) {
                        case BYTES:
                            sum = memoryConsumption_eden[j].getBytes() + memoryConsumption_old[j].getBytes() + memoryConsumption_survivor[j].getBytes();
                            break;
                        case OBJECTS:
                            sum = memoryConsumption_eden[j].getObjects() + memoryConsumption_old[j].getObjects() + memoryConsumption_survivor[j].getObjects();
                            break;
                        default:
                            throw new IllegalStateException();
                    }

                    int featureIndex = j;
                    XYChart.Data<Number, Number> featureData = new Data<>(time, sum);

                    Platform.runLater(() -> {
                        chart.getData().get(featureIndex).getData().add(featureData);
                    });
                }
            }
        }

        return chart.getData();
    }

    @Override
    protected void updateDataLabelContent() {
    }

    @Override
    protected EventHandler<ActionEvent> getOnConfigureAction() {
        return ae -> {
            FeatureChartConfigurationDialog dialog = new FeatureChartConfigurationDialog();
            dialog.init(this);
            WindowUtil.INSTANCE.centerInMainFrame(dialog);
            dialog.showAndWait();
        };
    }

    @Override
    protected void initializeChartSeries() {

    }

    @Override
    public AxisScaler<ObservableList<XYChart.Series<Number, Number>>> getScaler() {
        switch (memoryUnit) {
            case OBJECTS:
                return new JavaFXAxisScaler.ObjectsScaler(this);
            case BYTES:
                return new JavaFXAxisScaler.BytesScaler(this);
            default:
                getLOGGER().warning("No scaler defined for " + memoryUnit);
                return new JavaFXAxisScaler.NoAxisScaler(this);
        }
    }

    @Override
    public int getFeatureCount() {
        return chart.getData().size();
    }

    @Override
    public Set<Integer> getDisplayedFeatures() {
        return null;
    }

    @Override
    public void updateDisplayFeature(boolean display, Integer... ids) {
        if (display) {
            this.display.addAll(Arrays.stream(ids).collect(Collectors.toList()));
        } else {
            this.display.removeAll(Arrays.stream(ids).collect(Collectors.toList()));
        }

        if (display) {
            // a series must be reshown -> rebuild complete dataset...
            updateDataSet(ClientInfo.getCurrentAppInfo());

            for (int i = 0; i < getFeatureCount(); i++) {
                // ... and remove unselected series again

                if (!this.display.contains(i)) {
                    // hide a single series
                    // clearing the respective series throws exceptions later on, thus:
                    chart.getData().get(i).getData().stream().forEach(data -> {
                        data.setYValue(0);
                        data.setXValue(0);
                    });
                    // note: previously undisplayed (i.e. already unselected) series have no node connected to them, thus no setVisible
                    // (false)
                }
            }
        } else {
            // hide series
            // clearing the respective series throws exceptions later on, thus:
            Arrays.stream(ids).forEach(id -> {
                chart.getData().get(id).getData().stream().forEach(data -> {
                    data.setYValue(0);
                    data.setXValue(0);
                });
                chart.getData().get(id).getData().stream().forEach(data -> data.getNode().setVisible(false));
            });
        }
    }
}
