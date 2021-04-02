
package at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane;

import at.jku.anttracks.gui.chart.base.ApplicationChartFactory.AliveDeadMemoryConsumptionUnit;
import at.jku.anttracks.gui.chart.base.AxisScaler;
import at.jku.anttracks.gui.chart.base.ChartSynchronizer;
import at.jku.anttracks.gui.chart.base.JavaFXAxisScaler;
import at.jku.anttracks.gui.chart.fxchart.xy.stackedareapane.base.StackedAreaJavaFXChartPane;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.gui.utils.FXMLUtil;
import at.jku.anttracks.heap.statistics.Statistics;
import javafx.application.Platform;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.chart.XYChart;
import javafx.scene.chart.XYChart.Data;
import javafx.scene.text.Text;

import java.util.logging.Logger;

public class AliveDeadStackedAreaChartPane extends StackedAreaJavaFXChartPane<AppInfo> {

    XYChart.Series<Number, Number> survivedSeries;
    XYChart.Series<Number, Number> diedSeries;

    private static final String NEGATIVE_DIED_COUNT_MESSAGE = "Number of objects increased during GC. (may be caused by a failed GC)";

    private final Logger LOGGER = Logger.getLogger(getClass().getSimpleName());
    private AliveDeadMemoryConsumptionUnit aliveDeadMemoryConsumptionUnit;

    public AliveDeadStackedAreaChartPane() {
        FXMLUtil.load(this, AliveDeadStackedAreaChartPane.class);
    }

    public void init(ChartSynchronizer chartSynchronizer, AliveDeadMemoryConsumptionUnit aliveDeadMemoryConsumptionUnit) {
        super.init(chartSynchronizer, true);
        this.aliveDeadMemoryConsumptionUnit = aliveDeadMemoryConsumptionUnit;
        chart.setTitle("Survived/Dead Objects");
    }

    @Override
    protected ObservableList<XYChart.Series<Number, Number>> updateDataSet(AppInfo appInfo) {
        double maxX = getMaxXValue(chart.getData());

        switch (aliveDeadMemoryConsumptionUnit) {
            case BYTES:
                for (int i = 0; i + 1 < appInfo.getStatistics().size(); i += 2) {
                    Statistics start = appInfo.getStatistics().get(i);
                    Statistics end = appInfo.getStatistics().get(i + 1);

                    double time = start.getInfo().getTime();

                    if (time > maxX) {
                        long before = start.getEden().memoryConsumption.getBytes() + start.getSurvivor().memoryConsumption.getBytes() + start.getOld().memoryConsumption.getBytes();
                        long after = end.getEden().memoryConsumption.getBytes() + end.getSurvivor().memoryConsumption.getBytes() + end.getOld().memoryConsumption.getBytes();

                        Platform.runLater(() -> {
                            survivedSeries.getData().add(new Data<Number, Number>(time, after));
                            diedSeries.getData().add(new Data<Number, Number>(time, before - after));
                        });
                    }
                }
                break;
            case OBJECTS:
                for (int i = 0; i + 1 < appInfo.getStatistics().size(); i += 2) {
                    Statistics start = appInfo.getStatistics().get(i);
                    Statistics end = appInfo.getStatistics().get(i + 1);

                    double time = start.getInfo().getTime();

                    if (time > maxX) {
                        long before = start.getEden().memoryConsumption.getObjects() + start.getSurvivor().memoryConsumption.getObjects() + start.getOld().memoryConsumption
                                .getObjects();
                        long after = end.getEden().memoryConsumption.getObjects() + end.getSurvivor().memoryConsumption.getObjects() + end.getOld().memoryConsumption.getObjects();

                        Platform.runLater(() -> {
                            survivedSeries.getData().add(new Data<Number, Number>(time, after));
                            diedSeries.getData().add(new Data<Number, Number>(time, before - after));
                        });
                    }
                }
                break;
            case RELATIVE:
                boolean consecutiveGC = false;
                Statistics start = null;
                Statistics end = null;

                for (int i = 0; i + 1 < appInfo.getStatistics().size(); i += 2) {
                    if (!consecutiveGC) {
                        start = appInfo.getStatistics().get(i);
                    }
                    end = appInfo.getStatistics().get(i + 1);
                    if (i + 2 < appInfo.getStatistics().size()) {
                        long timeDiff = appInfo.getStatistics().get(i + 2).getInfo().getTime() - end.getInfo().getTime();
                        consecutiveGC = timeDiff == 0;
                        if (consecutiveGC) {
                            LOGGER.finest("Consecutive GCs found at " + end.getInfo().getTime());
                            continue;
                        }
                    }

                    double time = start.getInfo().getTime();
                    double endTime = end.getInfo().getTime();

                    if (time > maxX) {
                        long before = start.getEden().memoryConsumption.getObjects() + start.getSurvivor().memoryConsumption.getObjects() + start.getOld().memoryConsumption
                                .getObjects();
                        long after = end.getEden().memoryConsumption.getObjects() + end.getSurvivor().memoryConsumption.getObjects() + end.getOld().memoryConsumption.getObjects();
                        double survivedRatio = 1.0 * after / before > 1 ? 1 : 1.0 * after / before; // survived ratio > 1 may happen if during a (concurrent) GC more
                        // objects are allocated than collected
                        double diedRatio = 1.0 - survivedRatio;

                        Data<Number, Number> survivedData = new Data<>(time, survivedRatio * 100.0);
                        Data<Number, Number> diedData = new Data<>(time, diedRatio * 100.0);

                        Platform.runLater(() -> {
                            survivedSeries.getData().add(survivedData);
                            diedSeries.getData().add(diedData);
                        });
                    }
                }
                break;
            default:
                throw new IllegalStateException();
        }

        return chart.getData();
    }

    @Override
    public AxisScaler<ObservableList<XYChart.Series<Number, Number>>> getScaler() {
        return new JavaFXAxisScaler.AliveDeadScaler(this, aliveDeadMemoryConsumptionUnit);
    }

    @Override
    protected void initializeChartSeries() {
        survivedSeries = new XYChart.Series<>();
        survivedSeries.setName("Survived");
        diedSeries = new XYChart.Series<>();
        diedSeries.setName("Died");

        chart.getData().add(survivedSeries);
        chart.getData().add(diedSeries);
    }

    @Override
    public void updateDataLabelContent() {
        chart.getData().forEach(series -> {
            if (chart.getData().indexOf(series) == 1) {
                series.getData().forEach(dataPoint -> {
                    if (dataPoint.getYValue().doubleValue() < 0) {
                        dataLabels.put(dataPoint, new Text(NEGATIVE_DIED_COUNT_MESSAGE));
                    }
                });
            }
        });
    }

    @Override
    protected EventHandler<ActionEvent> getOnConfigureAction() {
        return null;
    }
}
