package at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.alivedead;

import at.jku.anttracks.gui.chart.base.ApplicationChartFactory;
import at.jku.anttracks.gui.chart.base.AxisScaler;
import at.jku.anttracks.gui.chart.base.ChartSynchronizer;
import at.jku.anttracks.gui.chart.base.JFreeChartAxisScaler;
import at.jku.anttracks.gui.chart.jfreechart.base.JFreeChartFactory;
import at.jku.anttracks.gui.chart.jfreechart.xy.stackedarea.base.StackedAreaJFreeChartPane;
import at.jku.anttracks.gui.chart.jfreechart.xy.util.AddALotXYSeries;
import at.jku.anttracks.gui.model.AppInfo;
import at.jku.anttracks.heap.statistics.Statistics;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import org.jfree.chart.labels.StandardXYToolTipGenerator;
import org.jfree.data.xy.DefaultTableXYDataset;
import org.jfree.data.xy.TableXYDataset;
import org.jfree.data.xy.XYDataItem;

import java.util.ArrayList;
import java.util.List;

public class AliveDeadStackedAreaJFreeChartPane extends StackedAreaJFreeChartPane<AppInfo, TableXYDataset> {

    ApplicationChartFactory.AliveDeadMemoryConsumptionUnit aliveDeadMemoryConsumptionUnit;

    public AliveDeadStackedAreaJFreeChartPane() {

    }

    public void init(ChartSynchronizer chartSynchronizer, ApplicationChartFactory.AliveDeadMemoryConsumptionUnit aliveDeadMemoryConsumptionUnit) {
        this.aliveDeadMemoryConsumptionUnit = aliveDeadMemoryConsumptionUnit;
        super.init(chartSynchronizer, true);
    }

    @Override
    protected TableXYDataset createDataSet(AppInfo appInfo) {
        AddALotXYSeries survivedSeries = new AddALotXYSeries("Survived", false);
        List<XYDataItem> survivedData = new ArrayList<>();
        AddALotXYSeries diedSeries = new AddALotXYSeries("Died", false);
        List<XYDataItem> diedData = new ArrayList<>();

        switch (aliveDeadMemoryConsumptionUnit) {
            case BYTES:
                for (int i = 1; i + 1 < appInfo.getStatistics().size(); i += 2) {
                    Statistics start = appInfo.getStatistics().get(i);
                    Statistics end = appInfo.getStatistics().get(i + 1);

                    double time = start.getInfo().getTime();

                    long before = start.getEden().memoryConsumption.getBytes() + start.getSurvivor().memoryConsumption.getBytes() + start.getOld().memoryConsumption.getBytes();
                    long after = end.getEden().memoryConsumption.getBytes() + end.getSurvivor().memoryConsumption.getBytes() + end.getOld().memoryConsumption.getBytes();

                    survivedData.add(new XYDataItem(time, after));
                    diedData.add(new XYDataItem(time, before - after));
                }
                break;
            case OBJECTS:
                for (int i = 1; i + 1 < appInfo.getStatistics().size(); i += 2) {
                    Statistics start = appInfo.getStatistics().get(i);
                    Statistics end = appInfo.getStatistics().get(i + 1);

                    double time = start.getInfo().getTime();

                    long before =
                            start.getEden().memoryConsumption.getObjects() + start.getSurvivor().memoryConsumption.getObjects() + start.getOld().memoryConsumption.getObjects();
                    long after = end.getEden().memoryConsumption.getObjects() + end.getSurvivor().memoryConsumption.getObjects() + end.getOld().memoryConsumption.getObjects();

                    survivedData.add(new XYDataItem(time, after));
                    diedData.add(new XYDataItem(time, before - after));
                }
                break;
            case RELATIVE:
                boolean consecutiveGC = false;
                Statistics start = null;
                Statistics end = null;

                for (int i = 1; i + 1 < appInfo.getStatistics().size(); i += 2) {
                    if (!consecutiveGC) {
                        start = appInfo.getStatistics().get(i);
                    }
                    end = appInfo.getStatistics().get(i + 1);
                    if (i + 2 < appInfo.getStatistics().size()) {
                        long timeDiff = appInfo.getStatistics().get(i + 2).getInfo().getTime() - end.getInfo().getTime();
                        consecutiveGC = timeDiff == 0;
                        if (consecutiveGC) {
                            getLOGGER().finest("Consecutive GCs found at " + end.getInfo().getTime());
                            continue;
                        }
                    }

                    double time = start.getInfo().getTime();
                    double endTime = end.getInfo().getTime();

                    long before =
                            start.getEden().memoryConsumption.getObjects() + start.getSurvivor().memoryConsumption.getObjects() + start.getOld().memoryConsumption.getObjects();
                    long after = end.getEden().memoryConsumption.getObjects() + end.getSurvivor().memoryConsumption.getObjects() + end.getOld().memoryConsumption.getObjects();
                    double survivedRatio = 1.0 * after / before > 1 ? 1 : 1.0 * after / before; // survived ratio > 1 may happen if during a (concurrent) GC more
                    // objects are allocated than collected
                    double diedRatio = 1.0 - survivedRatio;

                    XYDataItem survivedDataItem = new XYDataItem(time, survivedRatio * 100.0);
                    XYDataItem diedDataItem = new XYDataItem(time, diedRatio * 100.0);

                    survivedData.add(survivedDataItem);
                    diedData.add(diedDataItem);
                }
                break;
            default:
                throw new IllegalStateException();
        }

        survivedSeries.setData(survivedData, false);
        diedSeries.setData(diedData, false);

        DefaultTableXYDataset dataset = new DefaultTableXYDataset();
        dataset.addSeries(diedSeries);
        dataset.addSeries(survivedSeries);

        return dataset;
    }

    @Override
    protected void initializeChart() {
        chartViewer.setChart(JFreeChartFactory.INSTANCE.createStackedXYAreaChart(this,
                                                                                 "Survived / Died: " + aliveDeadMemoryConsumptionUnit.getUnit(),
                                                                                 null,
                                                                                 "set by scaler",
                                                                                 "set by scaler",
                                                                                 new DefaultTableXYDataset(),
                                                                                 null,
                                                                                 new StandardXYToolTipGenerator(),
                                                                                 null,
                                                                                 getChartSynchronizer(),
                                                                                 true));
    }

    @Override
    protected EventHandler<ActionEvent> getOnConfigureAction() {
        return null;
    }

    @Override
    public AxisScaler<TableXYDataset> getScaler() {
        return new JFreeChartAxisScaler.AliveDeadScaler<>(this, aliveDeadMemoryConsumptionUnit);
    }

}
