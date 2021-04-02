package at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.feature;

import at.jku.anttracks.features.FeatureMap;
import at.jku.anttracks.gui.chart.base.ApplicationChartFactory;
import at.jku.anttracks.gui.chart.base.AxisScaler;
import at.jku.anttracks.gui.chart.base.ChartSynchronizer;
import at.jku.anttracks.gui.chart.base.JFreeChartAxisScaler;
import at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane.FeatureChartConfigurationDialog;
import at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane.ShowFeature;
import at.jku.anttracks.gui.chart.jfreechart.base.JFreeChartFactory;
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.base.StackedAreaJFreeChartPane;
import at.jku.anttracks.gui.chart.jfreechart.xy.util.AddALotXYSeries;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.gui.utils.WindowUtil;
import at.jku.anttracks.heap.statistics.MemoryConsumption;
import at.jku.anttracks.heap.statistics.Statistics;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.data.xy.DefaultTableXYDataset;
import org.jfree.data.xy.TableXYDataset;
import org.jfree.data.xy.XYDataItem;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class FeatureStackedAreaJFreeChartPane extends StackedAreaJFreeChartPane<AppInfo, TableXYDataset> implements ShowFeature {
    HashMap<Integer, AddALotXYSeries> series = new HashMap<>();
    HashMap<Integer, List<XYDataItem>> seriesData = new HashMap<>();
    AppInfo lastAppInfo;

    Set<Integer> display;
    private ApplicationChartFactory.FeatureMemoryConsumptionUnit memoryUnit;

    public FeatureStackedAreaJFreeChartPane() {
        FXMLUtil.load(this, FeatureStackedAreaJFreeChartPane.class);
    }

    public void init(ChartSynchronizer chartSynchronizer, ApplicationChartFactory.FeatureMemoryConsumptionUnit memoryUnit) {
        this.memoryUnit = memoryUnit;
        super.init(chartSynchronizer, true);
    }

    public TableXYDataset createDataSet(AppInfo appInfo) {
        if (appInfo.getSymbols() == null) {
            return null;
        }

        FeatureMap features = appInfo.getSymbols().features;
        if (features == null) {
            return null;
        }

        if (display == null) {
            display = new HashSet<>(features.getFeatureCount());
            IntStream.range(0, features.getFeatureCount()).forEach(display::add);
        }

        series.clear();
        seriesData.clear();

        for (int id = 0; id < features.getFeatureCount(); id++) {
            if (display.contains(id)) {
                String featureName = features.getFeature(id).name;
                AddALotXYSeries s = new AddALotXYSeries(featureName, false);
                series.put(id, s);
                seriesData.put(id, new ArrayList<>());
            }
        }

        for (int i = 0; i < appInfo.getStatistics().size(); i++) {
            Statistics stat = appInfo.getStatistics().get(i);
            double time = stat.getInfo().getTime();

            MemoryConsumption[] memoryConsumption_eden = stat.getEden().featureConsumptions;
            MemoryConsumption[] memoryConsumption_old = stat.getOld().featureConsumptions;
            MemoryConsumption[] memoryConsumption_survivor = stat.getSurvivor().featureConsumptions;

            for (int featureIndex = 0; featureIndex < features.getFeatureCount(); featureIndex++) {
                if (display.contains(featureIndex)) {
                    long sum = 0;
                    switch (memoryUnit) {
                        case BYTES:
                            sum = memoryConsumption_eden[featureIndex].getBytes() + memoryConsumption_old[featureIndex].getBytes() +
                                    memoryConsumption_survivor[featureIndex]
                                            .getBytes();
                            break;
                        case OBJECTS:
                            sum = memoryConsumption_eden[featureIndex].getObjects() + memoryConsumption_old[featureIndex].getObjects() +
                                    memoryConsumption_survivor[featureIndex]
                                            .getObjects();
                            break;
                        default:
                            throw new IllegalStateException();
                    }

                    XYDataItem featureData = new XYDataItem(time, sum);
                    seriesData.get(featureIndex).add(featureData);
                }
            }
        }

        for (int featureIndex = 0; featureIndex < features.getFeatureCount(); featureIndex++) {
            if (display.contains(featureIndex)) {
                series.get(featureIndex).setData(seriesData.get(featureIndex), false);
            }
        }

        DefaultTableXYDataset dataset = new DefaultTableXYDataset();

        for (int featureIndex = 0; featureIndex < features.getFeatureCount(); featureIndex++) {
            if (display.contains(featureIndex)) {
                dataset.addSeries(series.get(featureIndex));
            }
        }

        lastAppInfo = appInfo;

        return dataset;
    }

    @Override
    protected void initializeChart() {
        chartViewer.setChart(JFreeChartFactory.INSTANCE.createStackedXYAreaChart(this,
                                                                                 memoryUnit.getLabel(),
                                                                                 null,
                                                                                 "x",
                                                                                 "y",
                                                                                 new DefaultTableXYDataset(),
                                                                                 null,
                                                                                 new StandardXYToolTipGenerator(),
                                                                                 null,
                                                                                 getChartSynchronizer(),
                                                                                 true));
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
    public AxisScaler<TableXYDataset> getScaler() {
        switch (memoryUnit) {
            case OBJECTS:
                return new JFreeChartAxisScaler.ObjectsScaler<>(this);
            case BYTES:
                return new JFreeChartAxisScaler.BytesScaler<>(this);
            default:
                getLOGGER().warning("No scaler defined for " + memoryUnit);
                return new JFreeChartAxisScaler.NoAxisScaler<>(this);
        }
    }

    @Override
    public int getFeatureCount() {
        return chartViewer.getChart().getXYPlot().getDataset().getSeriesCount();
    }

    @Override
    public Set<Integer> getDisplayedFeatures() {
        return this.display;
    }

    @Override
    public void updateDisplayFeature(boolean display, Integer... ids) {
        if (display) {
            this.display.addAll(Arrays.stream(ids).collect(Collectors.toList()));
        } else {
            this.display.removeAll(Arrays.stream(ids).collect(Collectors.toList()));
        }

        try {
            update(lastAppInfo);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
